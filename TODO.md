# Production Readiness TODO

Ordered from most urgent to least urgent.

## Critical: security and production blockers

- [ ] **Bundle Tailwind CSS locally**
  - Replace the Tailwind CDN script in `app/src/main/assets/web/index.html` with a production-generated static stylesheet bundled in the APK.

<!--- [ ] **Add authentication**
  - Protect directory listing, upload, download, move, duplicate, delete, and ZIP endpoints with a password, access token, or pairing flow.

- [ ] **Define a secure access model**
  - Decide whether the app will use a one-time token, password, QR pairing, HTTPS, or an explicitly documented trusted-network mode.
  - Do not present unauthenticated plaintext HTTP as secure file sharing.

- [ ] **Restrict the shared directory**
  - Replace whole-device external storage access with Android’s Storage Access Framework or a user-selected directory.-->

- [ ] **Fix path traversal validation**
  - Replace the plain string-prefix check in `FileServer.resolveSafeFile()` with a canonical path-boundary check that also handles symlinks safely.

<!--- [ ] **Sanitize upload filenames on the server**
  - Reject path separators and traversal components.
  - Validate the destination after resolving it.
  - Enforce overwrite, rename, or conflict behavior server-side.-->

<!--- [ ] **Prevent filename-based HTML/JavaScript injection**
  - Stop inserting file and folder names into `innerHTML` and inline `onclick` handlers.
  - Build dynamic rows with DOM APIs and `textContent`.-->

- [ ] **Add upload and ZIP resource limits**
  - Limit individual uploads, total upload size, ZIP source size, file count, concurrent jobs, and storage consumption.
  - Check available disk space before writing.

- [ ] **Bound and clean up ZIP jobs**
  - Replace the cached thread pool with a bounded executor.
  - Expire abandoned jobs, support cancellation, delete temporary files, and shut down the executor with the server.

<!--- [ ] **Avoid exposing raw exception messages**
  - Log detailed errors locally and return generic client-safe error messages from the HTTP server.-->

## High priority: Android reliability and release hardening

- [ ] **Fix server startup state handling**
  - Do not switch the UI to “Stop Server” until the foreground service confirms that the server actually started.
  - Show bind/startup failures to the user.

- [ ] **Replace silently swallowed exceptions**
  - Handle network and permission failures explicitly.
  - Add appropriate logging and actionable user feedback.

<!--- [ ] **Remove or justify broad storage permissions**
  - Reassess `MANAGE_EXTERNAL_STORAGE`, `requestLegacyExternalStorage`, and legacy read/write permissions.
  - Prefer scoped storage unless unrestricted access is an explicit, justified requirement.-->

- [ ] **Review foreground-service configuration**
  - Confirm the service type, permissions, notification behavior, and Android-version requirements are correct.

- [ ] **Add production app icons and branding**
  - Replace `@android:drawable/sym_def_app_icon` with launcher and adaptive icons.
  - Add production splash-screen branding.

- [ ] **Add release signing and versioning**
  - Configure a secure signing process outside source control.
  - Define version code/name management and a repeatable release build process.

- [ ] **Enable and verify R8/resource shrinking**
  - Turn on release minification and resource shrinking after adding required keep rules and testing the release APK.

- [ ] **Add or correct ProGuard/R8 rules**
  - Add the referenced `app/proguard-rules.pro` if custom rules are needed, or remove the reference if it is unnecessary.

## Medium priority: frontend quality and maintainability

<!--- [ ] **Remove inline JavaScript event handlers**
  - Replace inline `onclick` attributes with `addEventListener`.
  - This also makes a restrictive Content Security Policy possible.-->

- [ ] **Add loading, retry, and empty states**
  - Replace most basic alerts with visible UI states for loading, empty folders, network failures, upload failures, and ZIP failures.

- [ ] **Prevent stale asynchronous responses**
  - Cancel requests or use request IDs so an older directory/upload response cannot overwrite newer UI state.

- [ ] **Make collision handling server-authoritative**
  - Move overwrite/rename/conflict decisions to atomic server-side operations instead of relying on browser-side prechecks.
  - Why??

<!--- [ ] **Improve accessibility**
  - Use accessible icons and labels, keyboard-friendly file actions, non-drag/drop upload alternatives, and reliable focus management for previews.-->

<!--- [ ] **Move Android strings into resources**
  - Extract hardcoded UI strings from Kotlin and XML into `res/values/strings.xml`.
  - Add translations as appropriate.-->

## Lower priority: testing, documentation, and maintenance

- [ ] **Add automated tests**
  - Cover path traversal, filename sanitization, range downloads, ZIP creation/cleanup, duplicate/move/delete conflicts, authorization, large files, and interrupted transfers.

<!--- [ ] **Add an Android/manual test matrix**
  - Test supported Android versions, Wi-Fi, hotspot, IPv4/IPv6, network changes, rotation, backgrounding, service restarts, and low-storage conditions.-->

- [ ] **Add a README**
  - Document installation, supported Android versions, storage behavior, network/security model, limitations, and build/release instructions.

- [ ] **Add license and privacy documentation**
  - Add the project license.
  - Document what files and network data the app exposes and when the server is active.

<!--- [ ] **Update stale dependencies**
  - Review and update the AndroidX and Material dependencies reported by lint.
  - Run compatibility and release tests after updating.-->
