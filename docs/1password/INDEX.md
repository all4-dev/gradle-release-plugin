# 1Password Docs Index

## Recommended Reading
- [Main publishing guide](README.md)
- [Secret reference path guide](PATHS.md)

## Quick Start
1. Sign in:
```bash
op signin --force
```
2. Validate setup:
```bash
make release-doctor
```
3. Publish:
```bash
make publish-portal
make publish-central
```

## Full Release Flow
- Pre-release:
```bash
make tag-and-publish-pre-release
```
- Stable release:
```bash
make tag-and-publish-release VERSION=x.y.z
```

## Required 1Password Refs
```text
op://Private/Gradle Plugin Portal/publishing/key
op://Private/Gradle Plugin Portal/publishing/secret
op://Private/Sonatype Maven Central/publishing/username
op://Private/Sonatype Maven Central/publishing/password
op://Private/GPG Signing Key/publishing/passphrase
```
