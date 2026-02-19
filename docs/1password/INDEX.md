# 1Password Integration Documentation

<style>pre{overflow-x:auto}pre code{white-space:pre!important;display:inline-block;min-width:120ch}</style>

Complete guide for using 1Password CLI with the Gradle Release Plugin.

## 📚 Documentation Index

### [Main Guide (README.md)](README.md)
Complete integration guide covering:
- Prerequisites and installation
- Setup options (single item vs multiple items)
- Usage methods (gradle.properties, env vars, scripts)
- Verification and troubleshooting
- Best practices

**Start here if you're new to 1Password integration!**

---

### [Path Structure Guide (PATHS.md)](PATHS.md) ⭐
**Essential reading!** Explains how 1Password secret references work:
- Path format: `op://Vault/Item/Section/Field`
- How to customize names (vault, item, section, field)
- Case sensitivity and special characters
- Finding exact paths
- Naming best practices
- Troubleshooting path issues

**Key takeaway**: You can use ANY names you want - just make sure they match!

---

## 🎯 Quick Start

### 1. Install & Sign In
```bash
brew install 1password-cli
op signin
```

### 2. Create Credentials Item
```bash
op item create \
  --category=password \
  --title="Gradle Publishing Credentials" \
  --vault=Private \
  'Sonatype - Maven Central.username[text]=YOUR_USERNAME' \
  'Sonatype - Maven Central.password[password]=YOUR_PASSWORD' \
  'Gradle Plugin Portal.key[text]=YOUR_KEY' \
  'Gradle Plugin Portal.secret[password]=YOUR_SECRET' \
  'GPG Signing.keyName[text]=YOUR_GPG_KEY_ID' \
  'GPG Signing.passphrase[password]=YOUR_PASSPHRASE'
```

### 3. Use in Your Project
```bash
# Method 1: Via Makefile (easiest)
make publish-portal
make publish-central

# Method 2: Via gradle.properties
sonatype.username=op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/username
sonatype.password=op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/password

# Then run with:
op run -- ./gradlew publish
```

---

## 🔑 Key Concepts

### Secret References
Format: `op://Vault/Item/Section/Field`

Example:
```
op://Private/Gradle Publishing Credentials/Maven Central/username
     │        │                             │              └─ Field name
     │        │                             └──────────────── Section name
     │        └──────────────────────────────────────────────── Item title
     └───────────────────────────────────────────────────────── Vault name
```

### Naming Freedom
- ✅ Use **ANY names** you want for vaults, items, sections, and fields
- ✅ Names can include spaces, dashes, underscores
- ⚠️ Names are **case-sensitive**
- ✅ Only requirement: references must **exactly match** your 1Password structure

See [PATHS.md](PATHS.md) for complete details.

---

## 🎨 Naming Examples

All of these are valid - choose what works for you:

### Professional Style
```
op://Production/Publishing Credentials/Maven Central/username
```

### Simple Style
```
op://Work/gradle/maven/user
```

### Detailed Style
```
op://Private/MyProject - Publishing Credentials/Sonatype/username
```

### Flat Style (no sections)
```
op://Private/Maven Credentials/username
```

---

## 🚀 Common Use Cases

### Publishing to Maven Central
```bash
# Store credentials
op item create --title="Maven" 'user[text]=myuser' 'pass[password]=mypass'

# Reference in gradle.properties
sonatype.username=op://Private/Maven/user
sonatype.password=op://Private/Maven/pass

# Publish
op run -- ./gradlew publishToMavenCentral
```

### Publishing to Gradle Portal
```bash
# Store credentials
op item create --title="Portal" 'key[text]=mykey' 'secret[password]=mysecret'

# Use with Makefile
make publish-portal
```

### Multi-Destination Publishing
```bash
# All credentials in one item with sections
op item create --title="Publishing" \
  'maven.user[text]=user1' \
  'maven.pass[password]=pass1' \
  'portal.key[text]=key1' \
  'portal.secret[password]=secret1'

# Reference them
SONATYPE_USERNAME=op://Private/Publishing/maven/user
GRADLE_PUBLISH_KEY=op://Private/Publishing/portal/key
```

---

## 🔍 Verification

### Check if 1Password CLI is installed
```bash
op --version
```

### Test reading a secret
```bash
op read "op://Private/Your Item/Your Field"
```

### View item structure
```bash
op item get "Your Item" --format=json
```

---

## 🛡️ Security Best Practices

1. ✅ Use 1Password references instead of plain text
2. ✅ Add `.env*` and `*.op.env` files to `.gitignore`
3. ✅ Use descriptive names for easy management
4. ✅ Use sections to organize related credentials
5. ❌ Don't commit credentials (even references) to public repos
6. ❌ Don't share vault access unnecessarily

---

## 🐛 Troubleshooting

### "No such item" error
- Check you're signed in: `op signin`
- Verify item name is correct (case-sensitive)
- List items: `op item list --vault=Private`

### "Session expired" error
- Sign in again: `op signin`

### Secrets not resolving
- Make sure to use: `op run -- <command>`
- Verify path is correct: `op read "op://..."`

### Path issues
- Check exact structure: `op item get "Item" --format=json`
- Ensure names match exactly (case-sensitive, spacing)
- See [PATHS.md](PATHS.md) for detailed troubleshooting

---

## 📖 Additional Resources

- [1Password CLI Documentation](https://developer.1password.com/docs/cli/)
- [Secret References Syntax](https://developer.1password.com/docs/cli/secret-references/)
- [Main Plugin Documentation](../../README.md)

---

## 💬 Need Help?

- 🐛 Report issues: [GitHub Issues](https://github.com/all4-dev/gradle-release-plugin/issues)
- 💬 Ask questions: [GitHub Discussions](https://github.com/all4-dev/gradle-release-plugin/discussions)
- 📧 Contact: dev@all4.dev
