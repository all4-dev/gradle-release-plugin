# Understanding 1Password Secret References

This guide explains how 1Password constructs secret references (paths) and how you can customize them to fit your needs.

## 🗺️ Path Structure

A 1Password secret reference follows this format:

```
op://Vault/Item/Section/Field
    │    │     │      │      └─ Field name
    │    │     │      └──────── Section name (optional)
    │    │     └─────────────── Item title
    │    └───────────────────── Vault name
    └────────────────────────── Protocol
```

### Examples:

```bash
# With section
op://Private/Gradle Publishing Credentials/Sonatype - Maven Central/username
       │            │                            │                    └─ field
       │            │                            └────────────────────── section
       │            └─────────────────────────────────────────────────── item
       └──────────────────────────────────────────────────────────────── vault

# Without section (top-level field)
op://Private/My API Keys/github_token
       │            │          └─ field
       │            └──────────── item
       └───────────────────────── vault
```

---

## 🎨 Customization: Use ANY Names You Want!

**The most important thing**: You can use **any names you prefer** for:
- ✅ Vault names
- ✅ Item titles
- ✅ Section names
- ✅ Field names

**The only requirement**: The names must **match exactly** between:
1. What you create in 1Password
2. What you reference in your configuration

---

## 📝 Examples with Different Naming Styles

### Example 1: Professional Style

**1Password structure:**
```
Vault: "Production"
  Item: "Publishing Credentials"
    Section: "Maven Central"
      Field: "username"
      Field: "password"
    Section: "GPG"
      Field: "keyId"
      Field: "passphrase"
```

**Secret references:**
```bash
op://Production/Publishing Credentials/Maven Central/username
op://Production/Publishing Credentials/Maven Central/password
op://Production/Publishing Credentials/GPG/keyId
op://Production/Publishing Credentials/GPG/passphrase
```

---

### Example 2: Simple Style

**1Password structure:**
```
Vault: "Work"
  Item: "gradle"
    Section: "maven"
      Field: "user"
      Field: "pass"
    Section: "gpg"
      Field: "key"
      Field: "phrase"
```

**Secret references:**
```bash
op://Work/gradle/maven/user
op://Work/gradle/maven/pass
op://Work/gradle/gpg/key
op://Work/gradle/gpg/phrase
```

---

### Example 3: Detailed Style (Recommended)

**1Password structure:**
```
Vault: "Private"
  Item: "Gradle Release Plugin - Publishing"
    Section: "Sonatype Maven Central"
      Field: "username"
      Field: "password"
    Section: "Gradle Plugin Portal"
      Field: "apiKey"
      Field: "apiSecret"
    Section: "GPG Signing Key"
      Field: "keyName"
      Field: "passphrase"
```

**Secret references:**
```bash
op://Private/Gradle Release Plugin - Publishing/Sonatype Maven Central/username
op://Private/Gradle Release Plugin - Publishing/Sonatype Maven Central/password
op://Private/Gradle Release Plugin - Publishing/Gradle Plugin Portal/apiKey
op://Private/Gradle Release Plugin - Publishing/Gradle Plugin Portal/apiSecret
op://Private/Gradle Release Plugin - Publishing/GPG Signing Key/keyName
op://Private/Gradle Release Plugin - Publishing/GPG Signing Key/passphrase
```

---

### Example 4: Without Sections (Flat Structure)

**1Password structure:**
```
Vault: "Private"
  Item: "Maven Credentials"
    Field: "username"
    Field: "password"
```

**Secret references:**
```bash
op://Private/Maven Credentials/username
op://Private/Maven Credentials/password
```

---

## 🔍 How 1Password Constructs the Path

### 1. Vault → First Part
The vault name you choose becomes the first segment:
```bash
op://YourVaultName/...
```

### 2. Item → Second Part
The item title becomes the second segment:
```bash
op://YourVaultName/Your Item Title/...
```

### 3. Section → Third Part (Optional)
If you use sections, the section name becomes the third segment:
```bash
op://YourVaultName/Your Item Title/Your Section Name/...
```

### 4. Field → Last Part
The field name (label) becomes the final segment:
```bash
op://YourVaultName/Your Item Title/Your Section Name/field_name
```

---

## 💡 Naming Best Practices

### ✅ Recommended

1. **Use descriptive names:**
   ```bash
   ✅ op://Private/Gradle Publishing/Maven Central/username
   ❌ op://Private/creds/m/u
   ```

2. **Use spaces for readability:**
   ```bash
   ✅ op://Private/Publishing Credentials/...
   ✅ op://Private/publishing-credentials/...
   Both work! Choose what you prefer.
   ```

3. **Group related credentials in sections:**
   ```bash
   ✅ Item: "Publishing Credentials"
       Section: "Maven Central"
       Section: "Gradle Portal"
       Section: "GPG"
   ```

4. **Include project name for clarity:**
   ```bash
   ✅ op://Private/MyProject - Publishing/...
   ✅ op://Private/MyProject Publishing/...
   ```

### ⚠️ Watch Out For

1. **Case sensitivity:**
   ```bash
   op://Private/Maven/username  ≠  op://private/Maven/username
   op://Private/Maven/username  ≠  op://Private/maven/username
   op://Private/Maven/username  ≠  op://Private/Maven/Username
   ```

2. **Exact spacing:**
   ```bash
   op://Private/Maven Central/username  ≠  op://Private/Maven  Central/username
                      ^no extra space           ^extra space
   ```

3. **Special characters:**
   - Spaces are OK: `Maven Central` ✅
   - Dashes are OK: `maven-central` ✅
   - Underscores are OK: `maven_central` ✅
   - Avoid: `@`, `#`, `/`, `\` (can cause issues)

---

## 🛠️ Finding Your Exact Paths

### Method 1: Using 1Password App

1. Open the item in 1Password
2. Right-click field → "Copy Secret Reference"
3. Paste to see the exact path

### Method 2: Using CLI

```bash
# List all items in a vault
op item list --vault=Private

# Get item details (shows structure)
op item get "Your Item Title" --format=json

# Example output shows sections and fields:
{
  "fields": [...],
  "sections": [
    {
      "label": "Maven Central",
      "fields": [
        {"label": "username", ...},
        {"label": "password", ...}
      ]
    }
  ]
}
```

### Method 3: Test Reading

```bash
# Try reading with different paths until it works
op read "op://Private/Item/Section/field"
op read "op://Private/Item/field"  # if no section
```

---

## 🎯 Practical Examples: Mapping Your Structure

### Scenario: You already have an item

**What you have in 1Password:**
```
Item: "My Secrets"
  Field: "sonatype_user"
  Field: "sonatype_pass"
```

**How to reference:**
```bash
SONATYPE_USERNAME=op://Private/My Secrets/sonatype_user
SONATYPE_PASSWORD=op://Private/My Secrets/sonatype_pass
```

---

### Scenario: Adding sections to existing item

**Before:**
```
Item: "Publishing"
  Field: "maven_user"
  Field: "maven_pass"
  Field: "gradle_key"
  Field: "gradle_secret"
```

**After adding sections:**
```
Item: "Publishing"
  Section: "maven"
    Field: "user"
    Field: "pass"
  Section: "gradle"
    Field: "key"
    Field: "secret"
```

**New references:**
```bash
# Before (flat)
op://Private/Publishing/maven_user

# After (with sections)
op://Private/Publishing/maven/user
```

---

### Scenario: Creating from scratch with your own names

**You decide:**
- Vault: `"Work"`
- Item: `"gradle-deploy"`
- Section: `"sonatype"`
- Fields: `"u"`, `"p"`

**Create it:**
```bash
op item create \
  --vault=Work \
  --category=password \
  --title="gradle-deploy" \
  'sonatype.u[text]=your_username' \
  'sonatype.p[password]=your_password'
```

**Reference it:**
```bash
SONATYPE_USERNAME=op://Work/gradle-deploy/sonatype/u
SONATYPE_PASSWORD=op://Work/gradle-deploy/sonatype/p
```

---

## 🔗 Consistency is Key

The **only rule**: Your references must **exactly match** your 1Password structure.

```bash
# If you create with these names:
op item create --title="My Publishing Stuff" 'maven.user[text]=...'

# You MUST reference with those exact names:
op://Private/My Publishing Stuff/maven/user
              └─ must match ────┘  └─ must match ─┘
```

---

## 📋 Quick Reference Table

| Component | Can Customize? | Case Sensitive? | Spaces OK? | Special Chars? |
|-----------|---------------|-----------------|------------|----------------|
| Vault     | ✅ Yes         | ✅ Yes          | ✅ Yes     | ⚠️ Some        |
| Item      | ✅ Yes         | ✅ Yes          | ✅ Yes     | ⚠️ Some        |
| Section   | ✅ Yes         | ✅ Yes          | ✅ Yes     | ⚠️ Some        |
| Field     | ✅ Yes         | ✅ Yes          | ✅ Yes     | ⚠️ Some        |

**Safe special characters**: `-`, `_`, `.`, spaces
**Avoid**: `/`, `\`, `@`, `#`, `%`, `&`

---

## 💡 Pro Tips

1. **Document your structure** - Keep a reference of your paths in a team wiki
2. **Use consistent naming** - Pick a style and stick with it across projects
3. **Test references** - Always verify with `op read` before using in builds
4. **Copy references** - Use 1Password's "Copy Secret Reference" to avoid typos
5. **Share conventions** - If working in a team, agree on naming conventions

---

## 🔍 Troubleshooting Path Issues

### "No such item" error

```bash
# Check item exists
op item list --vault=Private | grep "Your Item"

# Check exact name
op item get "Your Item Title"
```

### "No such field" error

```bash
# View full structure
op item get "Your Item" --format=json

# Look for the exact field label in the output
```

### Spaces causing issues?

```bash
# Use quotes in shell
op read "op://Private/Item With Spaces/field"

# Or in config files (no quotes needed)
username=op://Private/Item With Spaces/field
```

---

## 📚 Related Documentation

- [Main 1Password Guide](README.md)
- [Setup Guide](SETUP.md)
- [Examples](EXAMPLES.md)
- [1Password Secret References](https://developer.1password.com/docs/cli/secret-references/)

---

## 🎓 Summary

Remember:
1. ✅ Use **ANY names** you want
2. ✅ Make them **descriptive** and **clear**
3. ✅ Ensure **exact match** between 1Password and your references
4. ✅ Test with `op read` before using in builds
5. ✅ Document your structure for your team

The flexibility is yours - choose names that make sense for your project! 🚀
