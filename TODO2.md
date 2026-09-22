# Production Android App and GitHub Plan

This plan covers the current scope:

- Prepare a production Android release build.
- Add placeholder open-source documentation.
- Prepare the GitHub repository.
- Defer security hardening and automated testing until later.

The app should be treated as a production build candidate, not fully production-secure, until the deferred security work is completed.

## Phase 1: Release build configuration

- [x] Define production versioning.
  - Set `versionCode` to `100` for the initial `1.0.0` release.
  - Increment `versionCode` for every subsequent release.
- [x] Configure release signing.
  - Read signing values from a local `key.properties` file.
  - Keep the keystore and passwords outside Git.
  - Add keystore-related files to `.gitignore`.
  - Added `key.properties.example` as the signing configuration template.
- [x] Improve release build settings.
  - Enabled R8/minification and resource shrinking.
  - Added `app/proguard-rules.pro`.
  - Dependency-specific keep rules should be added if release testing requires them.
- [x] Replace development branding.
  - Added project-owned placeholder launcher and adaptive icons.
  - Replaced the default Android system icon.
  - Centralized the application name in `strings.xml`.
- [x] Verify the release command:

  ```bash
  ./gradlew clean assembleRelease
  ```

  The release APK was generated under `app/build/outputs/apk/release/`.
  Without a local `key.properties` file, Gradle produces an unsigned release APK.

Signing credentials must never be committed to GitHub.

## Phase 2: Placeholder open-source documentation

The following files have been added:

```text
README.md
LICENSE
CONTRIBUTING.md
SECURITY.md
CHANGELOG.md
```

The initial `README.md` includes:

- Project name and purpose.
- Current project status.
- Basic installation and build instructions.
- How to start the Wi-Fi server.
- How another device connects.
- Supported Android versions.
- Current permissions.
- Known limitations.
- A warning that authentication and stronger storage restrictions are not implemented yet.

`SECURITY.md` explains how to report vulnerabilities and states that the current version uses local HTTP file sharing without the future authentication model.

`CHANGELOG.md` begins with:

```text
## Unreleased

- Initial open-source project preparation
- Android Wi-Fi file server
- Release build and GitHub automation preparation
```

### License choice

The project uses the Apache License 2.0 as its initial license.

| License | Main characteristic |
|---|---|
| MIT | Very permissive and short; allows proprietary derivatives |
| Apache-2.0 | Permissive and includes explicit patent protection |
| GPL-3.0 | Distributed derivatives generally must remain under GPL |

## Phase 3: Repository hygiene

Status: complete. The ignore rules were verified, and no signing secrets or generated build artifacts are tracked.

- [x] Review `.gitignore`.
- [x] Ensure these are ignored:

  ```text
  local.properties
  key.properties
  *.jks
  *.keystore
  app/build/
  .gradle/
  .idea/
  ```

- [x] Confirm no secrets or private machine configuration are tracked.
- [x] Do not commit generated APKs; attach them to GitHub Releases instead.
- [x] Keep source files and Gradle wrapper files in the repository.

Check before pushing:

```bash
git status
git ls-files | grep -E 'local.properties|key.properties|\.jks$|\.keystore$'
```

The second command produces no output.

## Phase 4: GitHub repository configuration

The tag-triggered signed APK release workflow has been added at `.github/workflows/release.yml`.

Before triggering it, configure the following GitHub Actions secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Add GitHub project configuration:

```text
.github/
├── workflows/
│   └── android.yml
├── ISSUE_TEMPLATE/
│   ├── bug_report.md
│   └── feature_request.md
└── dependabot.yml
```

The GitHub Actions workflow should:

- Check out the repository.
- Set up Java.
- Run Gradle.
- Run lint.
- Run available tests.
- Build the debug APK.
- Optionally build the unsigned release artifact.

Initial CI command:

```bash
./gradlew lint test assembleDebug
```

Do not put release signing credentials in the repository. Signing can later be added through GitHub Actions secrets.

## Phase 5: Create the GitHub repository

- [x] Create the GitHub repository.
- [x] Use the name `wifi-file-server`.
- [x] Set the repository to public.
- [x] Do not create an additional README, license, or `.gitignore` from GitHub.
- [x] Add the remote:

  ```bash
  git remote add origin https://github.com/syeduroojkamal/wifi-file-server.git
  ```

- [x] Commit the project:

  ```bash
  git add .
  git commit -m "Initial open-source release preparation"
  ```

- [x] Push the source branch:

  ```bash
  git push -u origin master
  ```

Repository URL: https://github.com/syeduroojkamal/wifi-file-server

## Phase 6: GitHub settings

- Status: configured for solo development. Pull-request requirements and Discussions remain disabled intentionally.

- [x] Add a clear repository description.
- [x] Add topics:
  - `android`
  - `kotlin`
  - `file-server`
  - `wifi`
  - `open-source`
- [x] Enable Issues.
- [ ] Enable Discussions only if needed.
- [x] Enable Dependabot version-update configuration and security updates.
- [x] Enable vulnerability alerts, secret scanning, and push protection.
- [x] Protect the `master` branch against force-pushes and deletion.
- [ ] Require pull requests for future changes if collaborating with others.

## Phase 7: Initial release

- [x] Test the release APK manually.
- [x] Create a changelog entry.
- [x] Create and push the Git tag `v1.0.0`:

  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```

- [x] Create the GitHub Release named `v1.0.0`.
- [x] Attach the signed APK and SHA-256 checksum.
- [x] Include installation instructions and known limitations.

## Deferred work

The following are intentionally outside the current implementation phase:

- Authentication.
- HTTPS or a stronger access model.
- Scoped storage/user-selected directories.
- Upload and ZIP resource limits.
- Automated server and file-operation tests.
- Full Android device and network test matrix.
- Final production security claim.

## Execution order

1. Configure release versioning and signing.
2. Add R8/resource shrinking and production branding placeholders.
3. Add README, Apache-2.0 license, security, contribution, and changelog files.
4. Harden `.gitignore` and check for secrets.
5. Add GitHub Actions CI and repository templates.
6. Create and push the GitHub repository.
7. Run CI and build the release APK.
8. Create the initial `v1.0.0` GitHub Release.
9. Return later to security hardening and automated testing.
