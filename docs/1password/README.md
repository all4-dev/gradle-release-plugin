# 1Password Integration Guide

This guide explains how to use 1Password CLI to securely manage credentials for publishing.

> 💡 **Important**: You can use **ANY names** you want for vaults, items, sections, and fields! The only requirement is that your secret references must **exactly match** the names in 1Password. See the [Path Structure Guide](PATHS.md) for details.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Setup Options](#setup-options)
  - [Option 1: Single Item (Recommended)](#option-1-single-item-recommended)
  - [Option 2: Multiple Items](#option-2-multiple-items)
- [Usage](#usage)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. Install 1Password CLI

```bash
# macOS
brew install 1password-cli

# Linux / Windows
# See: https://developer.1password.com/docs/cli/get-started#install
```

### 2. Sign in to 1Password

```bash
op signin
```

Verify it works:
```bash
op whoami
```

---

## Setup Options

### Option 1: Single Item (Recommended)

Create one item with multiple sections - easier to manage.

#### Create the Item

```bash
op item create \
  --category=password \
  --title="Gradle Publishing Credentials" \
  --vault=Private \
  'Sonatype - Maven Central.username[text]=YOUR_SONATYPE_USERNAME' \
  'Sonatype - Maven Central.password[password]=YOUR_SONATYPE_PASSWORD' \
  'Gradle Plugin Portal.key[text]=YOUR_GRADLE_PORTAL_KEY' \
  'Gradle Plugin Portal.secret[password]=YOUR_GRADLE_PORTAL_SECRET' \
  'GPG Signing.keyName[text]=YOUR_GPG_KEY_ID' \
  'GPG Signing.passphrase[password]=YOUR_GPG_PASSPHRASE'
```

> **Note**: Replace `YOUR_*` placeholders with your actual credentials.

#### Secret References

Use these references in your configuration:

```bash
op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/username
op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/password
op://Private/Gradle Publishing Credentials/Gradle Plugin Portal/key
op://Private/Gradle Publishing Credentials/Gradle Plugin Portal/secret
op://Private/Gradle Publishing Credentials/GPG Signing/keyName
op://Private/Gradle Publishing Credentials/GPG Signing/passphrase
```

---

### Option 2: Multiple Items

Create separate items for each service.

#### Sonatype / Maven Central

```bash
op item create \
  --category=login \
  --title="Sonatype Maven Central" \
  --vault=Private \
  'publishing.username[text]=YOUR_USERNAME' \
  'publishing.password[password]=YOUR_PASSWORD'
```

**References:**
```
op://Private/Sonatype Maven Central/publishing/username
op://Private/Sonatype Maven Central/publishing/password
```

#### Gradle Plugin Portal

```bash
op item create \
  --category="API Credential" \
  --title="Gradle Plugin Portal" \
  --vault=Private \
  'publishing.key[text]=YOUR_API_KEY' \
  'publishing.secret[password]=YOUR_API_SECRET'
```

> **How to get credentials**: Visit [plugins.gradle.org](https://plugins.gradle.org) → Sign in → API Keys

**References:**
```
op://Private/Gradle Plugin Portal/publishing/key
op://Private/Gradle Plugin Portal/publishing/secret
```

#### GPG Signing Key

```bash
op item create \
  --category=password \
  --title="GPG Signing Key" \
  --vault=Private \
  'publishing.keyName[text]=YOUR_KEY_ID' \
  'publishing.passphrase[password]=YOUR_PASSPHRASE'
```

> **How to find your GPG key ID**: `gpg --list-secret-keys --keyid-format=long`

**References:**
```
op://Private/GPG Signing Key/publishing/keyName
op://Private/GPG Signing Key/publishing/passphrase
```

---

## Usage

### Method 1: Direct in gradle.properties

```properties
# gradle.properties or ~/.gradle/gradle.properties
sonatype.username=op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/username
sonatype.password=op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/password
```

Then use `op run`:
```bash
op run -- ./gradlew publishToMavenCentral
```

### Method 2: Environment Variables

Create `gradle.op.env`:
```bash
SONATYPE_USERNAME=op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/username
SONATYPE_PASSWORD=op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/password
GRADLE_PUBLISH_KEY=op://Private/Gradle Publishing Credentials/Gradle Plugin Portal/key
GRADLE_PUBLISH_SECRET=op://Private/Gradle Publishing Credentials/Gradle Plugin Portal/secret
SIGNING_KEY_NAME=op://Private/Gradle Publishing Credentials/GPG Signing/keyName
SIGNING_PASSPHRASE=op://Private/Gradle Publishing Credentials/GPG Signing/passphrase
```

> **Important**: Add `gradle.op.env` to `.gitignore`

Use with `op run`:
```bash
op run --env-file=gradle.op.env -- ./gradlew publish
```

### Method 3: With Scripts

Use the provided scripts:

```bash
# Publish to Gradle Plugin Portal
make publish-portal

# Publish to Maven Central
make publish-central
```

---

## Verification

### Test Individual References

```bash
# Test reading a secret
op read "op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/username"

# Should output your username
```

### View Item Details

```bash
# View full item
op item get "Gradle Publishing Credentials" --format json

# Get specific field
op item get "Gradle Publishing Credentials" --fields label=username
```

---

## Troubleshooting

### "No such item" Error

Make sure:
1. You're signed in: `op signin`
2. Item name is correct (case-sensitive)
3. Vault name is correct

### "Session expired" Error

Sign in again:
```bash
op signin
```

### CLI Not Found

Install 1Password CLI:
```bash
# macOS
brew install 1password-cli

# Verify
op --version
```

### Permission Denied

Make sure scripts are executable:
```bash
chmod +x build-logic/scripts/*.main.kts
```

---

## Best Practices

### ✅ Do

- ✅ Use 1Password references instead of plain text credentials
- ✅ Add `gradle.op.env` and `.env*` files to `.gitignore`
- ✅ Use sections to organize credentials in a single item
- ✅ Use strong, unique passwords for each service

### ❌ Don't

- ❌ Commit credentials (plain or references) to public repositories
- ❌ Share your 1Password vault access widely
- ❌ Hardcode credentials in build files
- ❌ Use the same password for multiple services

---

## Plugin Integration

This plugin **automatically detects** 1Password references!

Simply use the `op://` format in any credential field:

```properties
# gradle.properties
sonatype.username=op://Private/Maven/username
sonatype.password=op://Private/Maven/password
```

The plugin will automatically:
1. ✅ Detect the `op://` prefix
2. ✅ Check if 1Password CLI is available
3. ✅ Resolve the secret at build time
4. ✅ Log helpful warnings if something goes wrong

---

## Where to Find Your Credentials

### Sonatype / Maven Central
1. Visit [s01.oss.sonatype.org](https://s01.oss.sonatype.org)
2. Sign up / Log in
3. Generate token or use username/password

### Gradle Plugin Portal
1. Visit [plugins.gradle.org](https://plugins.gradle.org)
2. Sign in with your account
3. Go to "API Keys" section
4. Create new API key

### GPG Key
```bash
# List your GPG keys
gpg --list-secret-keys --keyid-format=long

# Output will show:
# sec   rsa4096/ABCD1234EFGH5678 2024-01-01
#                 ^^^^^^^^^^^^^^^^ This is your key ID
```

---

## Additional Resources

- [Setup Guide](SETUP.md) - Quick start guide
- [**Path Structure Guide**](PATHS.md) - Understanding how 1Password paths work ⭐
- [Examples](EXAMPLES.md) - Real-world usage examples
- [1Password CLI Documentation](https://developer.1password.com/docs/cli/)
- [Secret References Syntax](https://developer.1password.com/docs/cli/secret-references/)

> 💡 **New to 1Password paths?** Check out the [Path Structure Guide](PATHS.md) to understand how vault/item/section/field names map to secret references!

---

## Need Help?

- 📖 Read the [main documentation](../../README.md)
- 🐛 Report issues on [GitHub](https://github.com/all4-dev/gradle-release-plugin/issues)
- 💬 Ask in [Discussions](https://github.com/all4-dev/gradle-release-plugin/discussions)
