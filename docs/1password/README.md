# 1Password Publishing Guide

This guide explains the required 1Password setup for publishing with this repo's current release workflow.

## Scope
- Uses `build-logic/scripts/release-workflow.main.ts`.
- Covers `make release-doctor`, `make publish-portal`, `make publish-central`, and tag-and-publish commands.

## Prerequisites
1. Install 1Password CLI.
```bash
brew install 1password-cli
```
2. Sign in.
```bash
op signin --force
```
3. If prompted repeatedly, enable desktop app integration in 1Password app:
- `Settings > Developer > Integrate with 1Password CLI`

## Required Secret References
These references must exist and be readable by your account:

```text
op://Private/Gradle Plugin Portal/publishing/key
op://Private/Gradle Plugin Portal/publishing/secret
op://Private/Sonatype Maven Central/publishing/username
op://Private/Sonatype Maven Central/publishing/password
op://Private/GPG Signing Key/publishing/passphrase
```

## Daily Commands
1. Validate auth + mappings:
```bash
make release-doctor
```
2. Publish only Plugin Portal:
```bash
make publish-portal
```
3. Publish only Maven Central:
```bash
make publish-central
```

## Release Commands
1. Pre-release bump + tag + publish + push:
```bash
make tag-and-publish-pre-release
```
2. Stable release bump + tag + publish + push:
```bash
make tag-and-publish-release VERSION=x.y.z
```

## Troubleshooting
### `1Password authentication is required` or `authorization timeout`
1. Run:
```bash
op signin --force
```
2. Approve in 1Password desktop app.
3. Keep app unlocked and retry the same terminal command.

### `Cannot read 1Password secret: op://...`
- Validate vault/item/section/field names exactly (case-sensitive).
- Test directly:
```bash
op read "op://Private/..."
```

### Doctor passes but publish fails
- Verify Gradle Portal key/secret are active.
- Verify Sonatype credentials are valid.
- Verify local GPG setup and passphrase match your signing key.

## Security Notes
- Do not commit plain credentials.
- Keep secret refs in local/private config only when possible.
- Use `make release-doctor` before publishing to fail early.

## References
- [Path Structure Guide](PATHS.md)
- [1Password CLI docs](https://developer.1password.com/docs/cli/)
- [Secret references](https://developer.1password.com/docs/cli/secret-references/)
