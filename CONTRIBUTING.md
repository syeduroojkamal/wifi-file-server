# Contributing

Thank you for contributing to WiFi File Server — FOSS.

## Before making changes

1. Check the existing issues and documentation.
2. For substantial changes, open an issue first to discuss the approach.
3. Do not include passwords, signing keys, private certificates, or local machine files.
4. Keep changes focused and update relevant documentation.

## Development setup

Requirements:

- Android Studio or an equivalent Android development environment.
- JDK 17.
- Android SDK with API 34 installed.

Build and validate locally:

```bash
./gradlew lint test assembleDebug
```

The project supports Android API 21 and newer. Test behavior on the Android versions and network configurations affected by your change.

## Pull requests

Pull requests should:

- Explain the problem and the solution.
- Include relevant tests or manual verification steps.
- Keep unrelated changes out of the patch.
- Update the README, changelog, or security documentation when behavior changes.
- Avoid claiming that the current local HTTP server is secure for untrusted networks.

## Commit messages

Use short, descriptive commit messages, for example:

```text
fix: handle interrupted ZIP downloads
docs: clarify local network limitations
```

## Reporting security issues

Do not open a public issue for a vulnerability. Follow the instructions in [SECURITY.md](SECURITY.md).
