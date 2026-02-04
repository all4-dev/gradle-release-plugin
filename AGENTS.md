# AI Agent Instructions

## Global Rules

**Path**: `$AI_GLOBAL_RULES` (environment variable pointing to the rules directory)

> ⚠️ **Required**: Read and follow these rules before proceeding.
>
> 💡 If `$AI_GLOBAL_RULES` is empty or not found, run `source ~/.zshrc` (or `source ~/.bashrc`) to refresh the environment, then retry.
>
> 🚨 If the path still does not exist or any rule files are missing, **notify the user immediately**.

| Rule File | Command | Description |
|-----------|---------|-------------|
| `gradle-version-catalog-rules.md` | `@ai fix:version-catalog` | Gradle version catalog conventions |
| `serialization-rules.md` | `@ai fix:serialization` | Kotlin serialization patterns |
| `logger-rules.md` | `@ai fix:logger` | Logger usage and formatting |
| `documentation_output_spec.md` | `@ai fix:docs` | Documentation output format |
| `submodule-rules.md` | `@ai context:detect` | Submodule detection, rule hierarchy, multi-project reports |
| `developer-environment-rules.md` | `@ai env:status` | Projects index, session bitácora, diagnostics |

> 💡 **Commands**: `@ai fix:<rule>` will scan the codebase for violations, report findings, and automatically fix them.

---

## Local Rules
> 📁 Read and follow all instructions from the `.ai/` directory.

| File | Description |
|------|-------------|
| `.ai/rules/rules.md` | TODO: Project-specific rules |
| `.ai/prompts/prompt.md` | TODO: Project-specific prompts |

---

## Documentation

| File | Description |
|------|-------------|
| `docs/index.md` | Documentation index and overview |
| `README.md` | Project overview and quick start |