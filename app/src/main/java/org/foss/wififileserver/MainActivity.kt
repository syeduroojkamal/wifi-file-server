package org.foss.wififileserver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.InetAddress
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private var isStarting = false
    private var pendingAddress: String? = null
    private val port = 8080

    private lateinit var textAddress: TextView
    private lateinit var textHint: TextView
    private lateinit var btnToggle: Button

    private val serverStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ServerService.BROADCAST_SERVER_STARTED -> {
                    isStarting = false
                    isRunning = true
                    btnToggle.isEnabled = true
                    textAddress.text = pendingAddress
                    pendingAddress = null
                    textHint.visibility = View.VISIBLE
                    btnToggle.text = "Stop Server"
                }
                ServerService.BROADCAST_SERVER_FAILED -> {
                    isStarting = false
                    isRunning = false
                    pendingAddress = null
                    btnToggle.isEnabled = true
                    textAddress.text = "Connect to Wi-Fi to start"
                    textHint.visibility = View.GONE
                    btnToggle.text = "Start Server"
                    val message = intent.getStringExtra(ServerService.EXTRA_ERROR_MESSAGE)
                        ?: "Unable to start the server"
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textAddress = findViewById(R.id.textAddress)
        textHint = findViewById(R.id.textHint)
        btnToggle = findViewById(R.id.btnToggleServer)
        ContextCompat.registerReceiver(
            this,
            serverStatusReceiver,
            IntentFilter().apply {
                addAction(ServerService.BROADCAST_SERVER_STARTED)
                addAction(ServerService.BROADCAST_SERVER_FAILED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        textAddress.setOnClickListener {
            if (isRunning) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Server address", textAddress.text))
                Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggle.setOnClickListener {
            if (isStarting) {
                return@setOnClickListener
            } else if (isRunning) {
                stopServer()
            } else {
                if (hasStoragePermission()) {
                    startServer()
                } else {
                    requestStoragePermission()
                }
            }
        }
    }

    private fun startServer() {
        val ip = getDeviceIpAddress()
        if (ip == null) {
            Toast.makeText(this, "Connect to Wi-Fi or Hotspot first", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, ServerService::class.java).apply {
            putExtra("PORT", port)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isStarting = true
        btnToggle.isEnabled = false
        pendingAddress = "http://$ip:$port 📋"
        textAddress.text = "Starting server..."
        textHint.visibility = View.GONE
        btnToggle.text = "Start Server"
    }

    private fun stopServer() {
        val serviceIntent = Intent(this, ServerService::class.java)
        stopService(serviceIntent)

        isStarting = false
        isRunning = false
        btnToggle.isEnabled = true
        textAddress.text = "Connect to Wi-Fi to start"
        textHint.visibility = View.GONE
        btnToggle.text = "Start Server"
    }

    override fun onDestroy() {
        unregisterReceiver(serverStatusReceiver)
        super.onDestroy()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }
    }

    private fun getDeviceIpAddress(): String? {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wm.connectionInfo.ipAddress
            if (ipInt != 0) {
                @Suppress("DEPRECATION")
                return Formatter.formatIpAddress(ipInt)
            }
        } catch (_: Exception) {}

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        if (!sAddr.contains(':')) return sAddr
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }
}
