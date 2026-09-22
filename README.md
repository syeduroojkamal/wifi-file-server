# WiFi File Server — FOSS

WiFi File Server — FOSS is an Android application that starts a local web-based file server. Once the server is running, another device on the same Wi-Fi network can use a browser to browse and transfer files.

## Current status

This project is being prepared for its first `1.0.0` open-source release. The current build is suitable for local testing and trusted networks, but it is not yet a complete secure file-sharing solution.

**Important:** The current server uses unauthenticated HTTP. Anyone who can reach the displayed address may be able to access the exposed file operations. Do not use it on an untrusted network or to share sensitive files.

Planned security work includes authentication, a stronger access model, scoped storage, and additional resource limits.

## Features

- Start and stop a local HTTP file server.
- Browse directories from a web browser.
- Upload and download files.
- Download directories as ZIP archives.
- Create folders.
- Move, duplicate, and delete files.
- Resume file downloads with HTTP ranges.
- Run the server as an Android foreground service.

## Requirements

- Android 5.0 (API 21) or newer.
- A Wi-Fi network or device hotspot.
- Storage access granted to the application.

## Using the app

1. Install the APK.
2. Connect the phone to Wi-Fi or start a hotspot.
3. Open the app and grant the requested storage access.
4. Tap **Start Server**.
5. Open the displayed address in a browser on another device connected to the same network.
6. Tap **Stop Server** when file sharing is finished.

The server is intended for local-network use. It is not designed to be exposed directly to the public internet.

## Building

Clone the repository, then run:

```bash
./gradlew lint test assembleDebug
```

To build the release variant:

```bash
./gradlew assembleRelease
```

Without a local signing configuration, Gradle creates an unsigned release APK. For a signed release, copy `key.properties.example` to `key.properties`, configure a release keystore, and keep both the keystore and `key.properties` outside source control.

The release APK is generated under:

```text
app/build/outputs/apk/release/
```

## Permissions and storage

The current version requests network access, Wi-Fi state access, foreground-service access, and broad external-storage access. Broad storage access is temporary project behavior and will be reassessed as part of the planned scoped-storage work.

## Known limitations

- No authentication or pairing.
- HTTP is not encrypted with HTTPS.
- The shared root is currently broad external storage.
- Resource limits and low-storage handling need further hardening.
- Automated coverage and the full device/network test matrix are still planned.
- Sideloaded APKs may be scanned by Google Play Protect.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development and pull-request guidance.

## Security

Please read [SECURITY.md](SECURITY.md) before reporting a vulnerability.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
