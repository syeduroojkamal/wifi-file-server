# Security Policy

## Current security status

The current application is intended for trusted local-network testing only. It serves files over unauthenticated HTTP and does not yet provide a complete authentication or pairing mechanism.

Do not expose the server to the public internet or use it on an untrusted network. Anyone who can reach the displayed server address may be able to access file operations.

## Reporting a vulnerability

Please do not disclose security vulnerabilities in a public issue. Use GitHub's private vulnerability reporting feature if it is enabled for this repository. Otherwise, contact the repository maintainers privately through the contact method listed on the repository profile.

Include:

- A clear description of the issue.
- The affected version or commit.
- Steps to reproduce.
- The security impact.
- A suggested mitigation, if known.

Please avoid including personal data or sensitive files in a report.

## Scope

Security reports are especially valuable for:

- Path traversal or unauthorized file access.
- Authentication or authorization bypasses.
- Unsafe upload, ZIP, move, duplicate, or delete behavior.
- Remote code execution or malicious content handling.
- Denial-of-service through unbounded resource use.
- Exposure of credentials or private data.

## Planned improvements

The project plans to add authentication, a stronger access model, scoped storage, resource limits, and broader automated security testing. Until those changes are complete, the limitations above remain part of the current product behavior.
