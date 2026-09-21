package org.foss.wififileserver

import android.content.Context
import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {

    private val rootDir: File = Environment.getExternalStorageDirectory()
    private val zipExecutor = Executors.newCachedThreadPool()
    private val zipJobs = ConcurrentHashMap<String, ZipJob>()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return try {
            when {
                uri == "/" || uri == "/index.html" -> serveAsset("web/index.html", "text/html")
                uri == "/api/list" -> handleList(session)
                uri == "/api/download" -> handleDownload(session)
                uri == "/api/zip" && session.method == Method.POST -> handleStartZip(session)
                uri == "/api/zip-progress" -> handleZipProgress(session)
                uri == "/api/upload" && session.method == Method.POST -> handleUpload(session)
                uri == "/api/create-folder" && session.method == Method.POST -> handleCreateFolder(session)
                uri == "/api/delete" && session.method == Method.POST -> handleDelete(session)
                uri == "/api/duplicate" && session.method == Method.POST -> handleDuplicate(session)
                uri == "/api/move" && session.method == Method.POST -> handleMove(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun resolveSafeFile(subPath: String?): File {
        val cleanSubPath = if (subPath.isNullOrBlank()) "" else URLDecoder.decode(subPath, "UTF-8")
        val target = File(rootDir, cleanSubPath).canonicalFile
        if (!target.path.startsWith(rootDir.canonicalPath)) {
            throw SecurityException("Access denied: Path outside root boundary.")
        }
        return target
    }

    private fun serveAsset(assetPath: String, mime: String): Response {
        val stream: InputStream = context.assets.open(assetPath)
        return newChunkedResponse(Response.Status.OK, mime, stream)
    }

    private fun handleList(session: IHTTPSession): Response {
        val pathParam = session.parameters["path"]?.firstOrNull() ?: ""
        val targetDir = resolveSafeFile(pathParam)

        if (!targetDir.exists() || !targetDir.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Folder not found")
        }

        val array = JSONArray()
        targetDir.listFiles()?.forEach { file ->
            val obj = JSONObject()
            obj.put("name", file.name)
            obj.put("isDir", file.isDirectory)
            obj.put("size", if (file.isDirectory) 0 else file.length())
            array.put(obj)
        }

        val res = newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
        res.addHeader("Access-Control-Allow-Origin", "*")
        return res
    }

    private fun handleDownload(session: IHTTPSession): Response {
        val jobId = session.parameters["job"]?.firstOrNull()
        if (jobId != null) {
            val job = zipJobs[jobId]
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "ZIP job not found")
            val zipFile = job.zipFile
                ?: return newFixedLengthResponse(Response.Status.CONFLICT, MIME_PLAINTEXT, "ZIP is not ready")
            if (!zipFile.exists()) {
                zipJobs.remove(jobId)
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "ZIP file not found")
            }

            val stream = object : FilterInputStream(FileInputStream(zipFile)) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        zipFile.delete()
                        zipJobs.remove(jobId)
                    }
                }
            }
            val res = newFixedLengthResponse(Response.Status.OK, "application/zip", stream, zipFile.length())
            res.addHeader("Content-Disposition", "attachment; filename=\"${job.name}.zip\"")
            return res
        }

        val pathParam = session.parameters["path"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing path")
        val file = resolveSafeFile(pathParam)

        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }

        if (file.isDirectory) {
            val zipFile = File.createTempFile("download-", ".zip", context.cacheDir)
            try {
                createZip(file, zipFile)
            } catch (e: Exception) {
                zipFile.delete()
                throw e
            }
            val stream = object : FilterInputStream(FileInputStream(zipFile)) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        zipFile.delete()
                    }
                }
            }
            val res = newFixedLengthResponse(Response.Status.OK, "application/zip", stream, zipFile.length())
            res.addHeader("Content-Disposition", "attachment; filename=\"${file.name}.zip\"")
            return res
        }

        val mime = getMimeType(file.name)
        val rangeHeader = session.headers["range"] ?: session.headers["Range"]
        val range = parseRange(rangeHeader, file.length())
        if (rangeHeader != null && range == null) {
            val res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, mime, "")
            res.addHeader("Content-Range", "bytes */${file.length()}")
            return res
        }

        val inline = session.parameters["inline"]?.firstOrNull() == "true"
        val disposition = if (inline) "inline" else "attachment"
        val start = range?.first ?: 0L
        val length = range?.let { it.last - it.first + 1 } ?: file.length()
        val stream = FileInputStream(file).apply {
            if (start > 0) skipFully(this, start)
        }
        val responseStatus = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val res = newFixedLengthResponse(responseStatus, mime, LimitedInputStream(stream, length), length)
        res.addHeader("Accept-Ranges", "bytes")
        res.addHeader("Content-Disposition", "$disposition; filename=\"${file.name}\"")
        if (range != null) {
            res.addHeader("Content-Range", "bytes ${range.first}-${range.last}/${file.length()}")
        }
        return res
    }

    private fun parseRange(header: String?, fileLength: Long): LongRange? {
        if (header == null) return null
        if (!header.startsWith("bytes=") || fileLength <= 0) return null

        val value = header.removePrefix("bytes=").split(",").first().trim()
        val dash = value.indexOf('-')
        if (dash < 0) return null

        val startText = value.substring(0, dash).trim()
        val endText = value.substring(dash + 1).trim()
        val start: Long
        val end: Long
        try {
            if (startText.isEmpty()) {
                val suffixLength = endText.toLong()
                if (suffixLength <= 0) return null
                start = (fileLength - suffixLength).coerceAtLeast(0)
                end = fileLength - 1
            } else {
                start = startText.toLong()
                end = if (endText.isEmpty()) fileLength - 1 else endText.toLong()
            }
        } catch (_: NumberFormatException) {
            return null
        }

        if (start < 0 || start >= fileLength || end < start) return null
        return start..end.coerceAtMost(fileLength - 1)
    }

    private fun skipFully(stream: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() == -1) throw java.io.EOFException("Could not seek to requested range")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private class LimitedInputStream(
        input: InputStream,
        private var remaining: Long
    ) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = super.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }
    }

    private fun createZip(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { output ->
            addToZip(sourceDir, sourceDir.name, output)
        }
    }

    private fun addToZip(file: File, entryPath: String, output: ZipOutputStream) {
        if (file.isDirectory) {
            output.putNextEntry(ZipEntry("$entryPath/"))
            output.closeEntry()
            file.listFiles()?.forEach { child ->
                addToZip(child, "$entryPath/${child.name}", output)
            }
        } else {
            output.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { input -> input.copyTo(output) }
            output.closeEntry()
        }
    }

    private fun handleStartZip(session: IHTTPSession): Response {
        val pathParam = session.parameters["path"]?.firstOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing path")
        val sourceDir = resolveSafeFile(pathParam)
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Folder not found")
        }

        val jobId = UUID.randomUUID().toString()
        val job = ZipJob(jobId, sourceDir.name, calculateTotalBytes(sourceDir))
        zipJobs[jobId] = job
        zipExecutor.execute {
            val zipFile = File.createTempFile("download-", ".zip", context.cacheDir)
            try {
                createZip(sourceDir, zipFile, job)
                job.zipFile = zipFile
                job.state = "complete"
            } catch (e: Exception) {
                zipFile.delete()
                job.error = e.message ?: "ZIP creation failed"
                job.state = "failed"
            }
        }

        val result = JSONObject().put("jobId", jobId)
        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
    }

    private fun handleZipProgress(session: IHTTPSession): Response {
        val jobId = session.parameters["job"]?.firstOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing job")
        val job = zipJobs[jobId]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "ZIP job not found")
        val result = JSONObject()
            .put("state", job.state)
            .put("progress", job.progress)
            .put("processedBytes", job.processedBytes)
            .put("totalBytes", job.totalBytes)
        job.error?.let { result.put("error", it) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
    }

    private fun createZip(sourceDir: File, zipFile: File, job: ZipJob) {
        ZipOutputStream(FileOutputStream(zipFile)).use { output ->
            addToZip(sourceDir, sourceDir.name, output, job)
        }
    }

    private fun addToZip(file: File, entryPath: String, output: ZipOutputStream, job: ZipJob) {
        if (file.isDirectory) {
            output.putNextEntry(ZipEntry("$entryPath/"))
            output.closeEntry()
            file.listFiles()?.forEach { child ->
                addToZip(child, "$entryPath/${child.name}", output, job)
            }
        } else {
            output.putNextEntry(ZipEntry(entryPath))
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = input.read(buffer)
                while (count != -1) {
                    output.write(buffer, 0, count)
                    job.processedBytes += count
                    job.progress = if (job.totalBytes == 0L) 100 else
                        ((job.processedBytes * 100) / job.totalBytes).toInt().coerceAtMost(99)
                    count = input.read(buffer)
                }
            }
            output.closeEntry()
        }
    }

    private fun calculateTotalBytes(file: File): Long {
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { calculateTotalBytes(it) } ?: 0L
    }

    private class ZipJob(
        val id: String,
        val name: String,
        val totalBytes: Long,
        @Volatile var state: String = "compressing",
        @Volatile var processedBytes: Long = 0,
        @Volatile var progress: Int = 0,
        @Volatile var zipFile: File? = null,
        @Volatile var error: String? = null
    )

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)

        val params = session.parameters
        val targetPath = params["path"]?.firstOrNull() ?: ""
        val destDir = resolveSafeFile(targetPath)

        files.forEach { (formField, tempFilePath) ->
            if (formField != "path") {
                val originalName = params[formField]?.firstOrNull() ?: "upload_${System.currentTimeMillis()}"
                val tempFile = File(tempFilePath)
                val targetFile = File(destDir, originalName)

                FileInputStream(tempFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "Upload successful")
    }

    private fun handleCreateFolder(session: IHTTPSession): Response {
        val pathParam = session.parameters["path"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing folder path")
        val folder = resolveSafeFile(pathParam)
        val created = folder.mkdirs() || folder.exists()
        return if (created) {
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Created")
        } else {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not create folder")
        }
    }

    private fun handleDelete(session: IHTTPSession): Response {
        val pathParam = session.parameters["path"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing path")
        val target = resolveSafeFile(pathParam)
        if (target.canonicalPath == rootDir.canonicalPath) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Cannot delete root directory")
        }

        val deleted = if (target.isDirectory) target.deleteRecursively() else target.delete()
        return if (deleted) {
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
        } else {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Delete failed")
        }
    }

    private fun handleDuplicate(session: IHTTPSession): Response {
        val sourcePath = session.parameters["from"]?.firstOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing source path")
        val destinationPath = session.parameters["to"]?.firstOrNull() ?: ""
        val source = resolveSafeFile(sourcePath)
        val destinationDir = if (destinationPath.isBlank()) rootDir else resolveSafeFile(destinationPath)

        if (!source.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Source not found")
        }
        if (source.canonicalPath == rootDir.canonicalPath) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Cannot duplicate root directory")
        }
        if (!destinationDir.exists() || !destinationDir.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Destination folder not found")
        }

        val destination = File(destinationDir, generateUniqueDuplicateName(source.name, destinationDir)).canonicalFile
        return try {
            if (source.isDirectory) {
                copyDirectory(source, destination)
            } else {
                copyFile(source, destination)
            }
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Duplicated")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Duplicate failed")
        }
    }

    private fun handleMove(session: IHTTPSession): Response {
        val sourcePath = session.parameters["from"]?.firstOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing source path")
        val destinationPath = session.parameters["to"]?.firstOrNull() ?: ""
        val source = resolveSafeFile(sourcePath)
        val destinationDir = resolveSafeFile(destinationPath)

        if (!source.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Source not found")
        }
        if (source.canonicalPath == rootDir.canonicalPath) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Cannot move root directory")
        }
        if (!destinationDir.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Destination folder not found")
        }

        val destination = File(destinationDir, source.name).canonicalFile
        if (destination.path == source.canonicalPath) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Source is already in that folder")
        }
        if (source.isDirectory &&
            destinationDir.canonicalPath.startsWith(source.canonicalPath + File.separator)
        ) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Cannot move a folder into itself")
        }
        if (destination.exists()) {
            return newFixedLengthResponse(Response.Status.CONFLICT, MIME_PLAINTEXT, "An item with that name already exists")
        }

        return if (source.renameTo(destination)) {
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Moved")
        } else {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Move failed")
        }
    }

    private fun generateUniqueDuplicateName(originalName: String, parentDir: File): String {
        val dotIndex = originalName.lastIndexOf('.')
        val stem = if (dotIndex > 0 && dotIndex < originalName.length - 1) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0 && dotIndex < originalName.length - 1) originalName.substring(dotIndex) else ""

        var candidate = if (extension.isEmpty()) "${stem}_copy" else "${stem}_copy$extension"
        var counter = 2

        while (File(parentDir, candidate).exists()) {
            candidate = if (extension.isEmpty()) "${stem}_copy_$counter" else "${stem}_copy_$counter$extension"
            counter += 1
        }

        return candidate
    }

    private fun copyDirectory(source: File, destination: File) {
        destination.mkdirs()
        source.listFiles()?.forEach { child ->
            val childDestination = File(destination, child.name)
            if (child.isDirectory) {
                copyDirectory(child, childDestination)
            } else {
                copyFile(child, childDestination)
            }
        }
    }

    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun getMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "ogv" -> "video/ogg"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
