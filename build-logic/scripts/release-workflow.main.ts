#!/usr/bin/env -S node --experimental-strip-types

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import type { SpawnSyncOptions } from "node:child_process";

type Command =
  | "help"
  | "doctor"
  | "bump-pre"
  | "publish-local"
  | "publish-portal"
  | "publish-central"
  | "tag-and-publish-pre-release"
  | "tag-and-publish-release";

type CliOptions = {
  version?: string;
  dryRun: boolean;
  noPush: boolean;
  skipPublish: boolean;
};

type Mapping = Record<string, string>;
type PublishSide = "local" | "portal" | "central";
type PublishDestination = {
  side: PublishSide;
  locationType: "path" | "url";
  location: string;
};
type ArtifactDetails = {
  groupId: string;
  artifactId: string;
  version: string;
  pluginId: string;
};

const SCRIPT_PATH = resolve(process.argv[1]);
const SCRIPT_DIR = dirname(SCRIPT_PATH);
const ROOT = resolve(SCRIPT_DIR, "../..");
const PLUGIN_BUILD_FILE = resolve(ROOT, "plugin/build.gradle.kts");
const PLUGIN_POM_FILE = resolve(ROOT, "plugin/build/publications/pluginMaven/pom-default.xml");
const SCRIPTS_DIR = resolve(ROOT, "build-logic/scripts");
const OP_API_KEYS_SCRIPT = resolve(SCRIPTS_DIR, "op-api-keys.main.kts");
const PUBLISH_PORTAL_SCRIPT = resolve(SCRIPTS_DIR, "publish-portal.main.kts");
const PUBLISH_CENTRAL_SCRIPT = resolve(SCRIPTS_DIR, "publish-central.main.kts");
const FALLBACK_ARTIFACT_ID = "release-plugin";

const REQUIRED_PORTAL_KEYS = [
  "GRADLE_PUBLISH_KEY",
  "GRADLE_PUBLISH_SECRET",
  "SIGNING_PASSPHRASE",
] as const;

const REQUIRED_CENTRAL_KEYS = [
  "ORG_GRADLE_PROJECT_mavenCentralUsername",
  "ORG_GRADLE_PROJECT_mavenCentralPassword",
  "ORG_GRADLE_PROJECT_signing_gnupg_passphrase",
] as const;

let authPromptAnnounced = false;

function fail(message: string, solution?: string): never {
  console.error(`\n❌ ${message}`);
  if (solution) {
    console.error(`\n✅ Solution:\n${solution}`);
  }
  process.exit(1);
}

function info(message: string): void {
  console.log(`ℹ️  ${message}`);
}

function ok(message: string): void {
  console.log(`✅ ${message}`);
}

function announcePublishAuthRequired(): void {
  if (authPromptAnnounced) {
    return;
  }
  authPromptAnnounced = true;

  const phrase = "Authentication required for Maven publishing.";
  const sayResult = runCommand("say", [phrase], { stdio: "ignore" });
  if (sayResult.status !== 0) {
    process.stdout.write("\u0007");
    info(phrase);
  }
}

function runCommand(
  command: string,
  args: string[],
  options: SpawnSyncOptions = {},
): { stdout: string; stderr: string; status: number } {
  const result = spawnSync(command, args, {
    cwd: ROOT,
    encoding: "utf8",
    ...options,
  });
  return {
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
    status: result.status ?? 1,
  };
}

function runCommandOrFail(
  command: string,
  args: string[],
  label: string,
  options: SpawnSyncOptions = {},
): void {
  const result = runCommand(command, args, options);
  if (result.status !== 0) {
    const details = [result.stdout.trim(), result.stderr.trim()]
      .filter(Boolean)
      .join("\n");
    fail(
      `${label} failed`,
      details || "No additional output",
    );
  }
}

function assertFileExists(filePath: string): void {
  if (!existsSync(filePath)) {
    fail(
      `Required file not found: ${filePath}`,
      "Restore the file or update this script configuration before running release commands.",
    );
  }
}

function extractSecretMappings(scriptPath: string): Mapping {
  const content = readFileSync(scriptPath, "utf8");
  const mappings: Mapping = {};
  const regex = /"([A-Za-z_][A-Za-z0-9_]*)=(op:\/\/[^"]+)"/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(content)) !== null) {
    const key = match[1];
    const secretRef = match[2];
    mappings[key] = secretRef;
  }
  return mappings;
}

function assertRequiredKeys(
  label: string,
  mappings: Mapping,
  requiredKeys: readonly string[],
  sourcePath: string,
): void {
  const missing = requiredKeys.filter((key) => !mappings[key]);
  if (missing.length > 0) {
    fail(
      `${label} secret mapping is incomplete`,
      `Missing keys in ${sourcePath}:\n${missing.map((k) => `  - ${k}`).join("\n")}\n\nUpdate the script mappings first.`,
    );
  }
}

function validateScriptContracts(): { portal: Mapping; central: Mapping } {
  assertFileExists(OP_API_KEYS_SCRIPT);
  assertFileExists(PUBLISH_PORTAL_SCRIPT);
  assertFileExists(PUBLISH_CENTRAL_SCRIPT);
  assertFileExists(PLUGIN_BUILD_FILE);

  const opApiContent = readFileSync(OP_API_KEYS_SCRIPT, "utf8");
  if (!opApiContent.includes("parseMapping(") || !opApiContent.includes("op://")) {
    fail(
      "op-api-keys.main.kts contract check failed",
      `Expected parser logic not found in ${OP_API_KEYS_SCRIPT}.`,
    );
  }

  const portalMappings = extractSecretMappings(PUBLISH_PORTAL_SCRIPT);
  const centralMappings = extractSecretMappings(PUBLISH_CENTRAL_SCRIPT);

  assertRequiredKeys(
    "publish-portal.main.kts",
    portalMappings,
    REQUIRED_PORTAL_KEYS,
    PUBLISH_PORTAL_SCRIPT,
  );
  assertRequiredKeys(
    "publish-central.main.kts",
    centralMappings,
    REQUIRED_CENTRAL_KEYS,
    PUBLISH_CENTRAL_SCRIPT,
  );

  ok("Script contracts validated");
  return { portal: portalMappings, central: centralMappings };
}

function ensureOpInstalled(): void {
  const result = runCommand("op", ["--version"]);
  if (result.status !== 0) {
    fail(
      "1Password CLI ('op') not found",
      "Install it first: brew install 1password-cli\nThen authenticate with: op signin",
    );
  }
  ok(`1Password CLI detected (${result.stdout.trim()})`);
}

function authHintForOpFailure(details: string): string {
  const normalized = details.toLowerCase();
  if (
    normalized.includes("not signed in") ||
    normalized.includes("authorization timeout")
  ) {
    return [
      "1Password authentication is required.",
      "1) Run: op signin --force",
      "2) Approve the request in the 1Password desktop app (keep it unlocked).",
      "3) Ensure app integration is enabled: Settings > Developer > Integrate with 1Password CLI.",
      "4) Retry the command in the same terminal session.",
      "",
      "Alternative for automation/CI: set OP_SERVICE_ACCOUNT_TOKEN.",
    ].join("\n");
  }
  return "Verify vault/item/section/field names and access permissions.";
}

function readSecret(secretRef: string): string {
  const result = runCommand("op", ["read", secretRef]);
  if (result.status !== 0) {
    const details = [result.stdout.trim(), result.stderr.trim()]
      .filter(Boolean)
      .join("\n");
    fail(
      `Cannot read 1Password secret: ${secretRef}`,
      `${details || "No additional output"}\n\n${authHintForOpFailure(details)}`,
    );
  }
  return result.stdout.replace(/\r?\n$/, "");
}

function validateSecretAccess(mappings: Mapping, label: string): void {
  announcePublishAuthRequired();
  for (const [key, ref] of Object.entries(mappings)) {
    readSecret(ref);
    ok(`${label}: ${key}`);
  }
}

function ensureCleanGitTree(): void {
  const result = runCommand("git", ["status", "--porcelain"]);
  if (result.status !== 0) {
    fail("Cannot inspect git status", result.stderr.trim() || result.stdout.trim());
  }
  if (result.stdout.trim().length > 0) {
    fail(
      "Working tree is dirty",
      "Commit or stash changes before running release commands.",
    );
  }
  ok("Git working tree is clean");
}

function getCurrentVersion(): string {
  const content = readFileSync(PLUGIN_BUILD_FILE, "utf8");
  const match = content.match(/^version\s*=\s*"([^"]+)"\s*$/m);
  if (!match) {
    fail(
      "Unable to read plugin version",
      `Expected line like: version = "x.y.z" in ${PLUGIN_BUILD_FILE}`,
    );
  }
  return match[1];
}

function getCurrentGroupId(): string {
  const content = readFileSync(PLUGIN_BUILD_FILE, "utf8");
  const match = content.match(/^group\s*=\s*"([^"]+)"\s*$/m);
  if (!match) {
    fail(
      "Unable to read plugin group",
      `Expected line like: group = "x.y.z" in ${PLUGIN_BUILD_FILE}`,
    );
  }
  return match[1];
}

function getPluginId(): string {
  const content = readFileSync(PLUGIN_BUILD_FILE, "utf8");
  const match = content.match(/create\("release"\)\s*\{[\s\S]*?id\s*=\s*"([^"]+)"/m);
  if (!match) {
    fail(
      "Unable to read Gradle plugin ID",
      `Expected id = "..." in gradlePlugin.plugins block inside ${PLUGIN_BUILD_FILE}`,
    );
  }
  return match[1];
}

function getPublishedArtifactIdOrFallback(): string {
  if (!existsSync(PLUGIN_POM_FILE)) {
    return FALLBACK_ARTIFACT_ID;
  }
  const pom = readFileSync(PLUGIN_POM_FILE, "utf8");
  const match = pom.match(/<artifactId>([^<]+)<\/artifactId>/);
  return match?.[1]?.trim() || FALLBACK_ARTIFACT_ID;
}

function getArtifactDetails(): ArtifactDetails {
  return {
    groupId: getCurrentGroupId(),
    artifactId: getPublishedArtifactIdOrFallback(),
    version: getCurrentVersion(),
    pluginId: getPluginId(),
  };
}

function formatRepoPath(groupId: string): string {
  return groupId.split(".").join("/");
}

function buildDestination(side: PublishSide, artifact: ArtifactDetails): PublishDestination {
  switch (side) {
    case "local": {
      const home = process.env.HOME;
      const suffix = `${formatRepoPath(artifact.groupId)}/${artifact.artifactId}/${artifact.version}`;
      return {
        side,
        locationType: "path",
        location: home
          ? `${home}/.m2/repository/${suffix}`
          : `~/.m2/repository/${suffix}`,
      };
    }
    case "portal":
      return {
        side,
        locationType: "url",
        location: `https://plugins.gradle.org/plugin/${artifact.pluginId}`,
      };
    case "central":
      return {
        side,
        locationType: "url",
        location: `https://repo1.maven.org/maven2/${formatRepoPath(artifact.groupId)}/${artifact.artifactId}/${artifact.version}/`,
      };
  }
}

function printPublishReport(sides: PublishSide[]): void {
  const uniqueSides = [...new Set(sides)];
  if (uniqueSides.length === 0) {
    return;
  }

  const artifact = getArtifactDetails();
  const destinations = uniqueSides.map((side) => buildDestination(side, artifact));

  console.log("\n📦 Publish mini-report");
  console.log(`artifact = ${artifact.groupId}:${artifact.artifactId}:${artifact.version}`);
  console.log(`pluginId = ${artifact.pluginId}`);
  console.log("");

  for (const destination of destinations) {
    const prefix = destination.locationType === "path" ? "PATH" : "URL ";
    console.log(`${prefix} ${destination.side}: ${destination.location}`);
  }

  console.log("\nCopy/Paste (libs.versions.toml)");
  console.log(`[versions]\nall4Release = "${artifact.version}"`);
  console.log(`[plugins]\nall4-release = { id = "${artifact.pluginId}", version.ref = "all4Release" }`);
  console.log(`[libraries]\nall4-release-plugin = { module = "${artifact.groupId}:${artifact.artifactId}", version.ref = "all4Release" }`);
  console.log("");
  console.log(`all4-release = { id = "${artifact.pluginId}", version = "${artifact.version}" }`);
  console.log(`all4-release-plugin = { module = "${artifact.groupId}:${artifact.artifactId}", version = "${artifact.version}" }`);
}

function setVersion(newVersion: string): void {
  const content = readFileSync(PLUGIN_BUILD_FILE, "utf8");
  const updated = content.replace(
    /^version\s*=\s*"([^"]+)"\s*$/m,
    `version = "${newVersion}"`,
  );
  if (updated === content) {
    fail(
      "Version update failed",
      `Unable to replace version declaration in ${PLUGIN_BUILD_FILE}`,
    );
  }
  writeFileSync(PLUGIN_BUILD_FILE, updated, "utf8");
}

function nextPreReleaseVersion(current: string): string {
  const alphaMatch = current.match(/^(.*)-alpha\.(\d+)$/);
  if (alphaMatch) {
    const base = alphaMatch[1];
    const number = Number(alphaMatch[2]);
    return `${base}-alpha.${number + 1}`;
  }
  return `${current}-alpha.1`;
}

function validateStableVersion(version: string): void {
  if (!/^\d+\.\d+\.\d+$/.test(version)) {
    fail(
      `Invalid release version: ${version}`,
      "Use semantic version format without suffix: x.y.z (example: 1.2.3).",
    );
  }
}

function commitAndTag(version: string, dryRun: boolean): void {
  if (dryRun) {
    info(`[dry-run] git add plugin/build.gradle.kts`);
    info(`[dry-run] git commit -m "chore: bump version to ${version}"`);
    info(`[dry-run] git tag -a v${version} -m "Release ${version}"`);
    return;
  }

  runCommandOrFail("git", ["add", "plugin/build.gradle.kts"], "git add");
  runCommandOrFail(
    "git",
    ["commit", "-m", `chore: bump version to ${version}`],
    "git commit",
  );
  runCommandOrFail(
    "git",
    ["tag", "-a", `v${version}`, "-m", `Release ${version}`],
    "git tag",
  );
  ok(`Created commit + tag v${version}`);
}

function commitVersion(version: string, dryRun: boolean): void {
  if (dryRun) {
    info(`[dry-run] git add plugin/build.gradle.kts`);
    info(`[dry-run] git commit -m "chore: bump version to ${version}"`);
    return;
  }

  runCommandOrFail("git", ["add", "plugin/build.gradle.kts"], "git add");
  runCommandOrFail(
    "git",
    ["commit", "-m", `chore: bump version to ${version}`],
    "git commit",
  );
  ok(`Created commit for version ${version}`);
}

function pushRelease(version: string, dryRun: boolean): void {
  if (dryRun) {
    info("[dry-run] git push origin HEAD");
    info(`[dry-run] git push origin v${version}`);
    return;
  }
  runCommandOrFail("git", ["push", "origin", "HEAD"], "git push HEAD");
  runCommandOrFail("git", ["push", "origin", `v${version}`], "git push tag");
  ok(`Pushed branch + tag v${version}`);
}

function publishLocal(dryRun: boolean): void {
  if (dryRun) {
    info("[dry-run] ./gradlew :plugin:publishToMavenLocal");
    return;
  }
  runCommandOrFail(
    "./gradlew",
    [":plugin:publishToMavenLocal"],
    "Maven local publish",
    { stdio: "inherit" },
  );
}

function publishPortal(portalMappings: Mapping, dryRun: boolean): void {
  announcePublishAuthRequired();
  const key = readSecret(portalMappings.GRADLE_PUBLISH_KEY);
  const secret = readSecret(portalMappings.GRADLE_PUBLISH_SECRET);
  const passphrase = readSecret(portalMappings.SIGNING_PASSPHRASE);

  if (dryRun) {
    info("[dry-run] ./gradlew :plugin:publishPlugins -Pgradle.publish.key=*** -Pgradle.publish.secret=*** -Psigning.gnupg.passphrase=***");
    return;
  }

  runCommandOrFail(
    "./gradlew",
    [
      ":plugin:publishPlugins",
      `-Pgradle.publish.key=${key}`,
      `-Pgradle.publish.secret=${secret}`,
      `-Psigning.gnupg.passphrase=${passphrase}`,
    ],
    "Gradle Plugin Portal publish",
    { stdio: "inherit" },
  );
}

function publishCentral(centralMappings: Mapping, dryRun: boolean): void {
  announcePublishAuthRequired();
  const username = readSecret(centralMappings.ORG_GRADLE_PROJECT_mavenCentralUsername);
  const password = readSecret(centralMappings.ORG_GRADLE_PROJECT_mavenCentralPassword);
  const passphrase = readSecret(centralMappings.ORG_GRADLE_PROJECT_signing_gnupg_passphrase);

  if (dryRun) {
    info("[dry-run] ./gradlew :plugin:publishAllPublicationsToMavenCentralRepository --no-configuration-cache -Psigning.gnupg.passphrase=***");
    return;
  }

  runCommandOrFail(
    "./gradlew",
    [
      ":plugin:publishAllPublicationsToMavenCentralRepository",
      "--no-configuration-cache",
      `-Psigning.gnupg.passphrase=${passphrase}`,
    ],
    "Maven Central publish",
    {
      stdio: "inherit",
      env: {
        ...process.env,
        ORG_GRADLE_PROJECT_mavenCentralUsername: username,
        ORG_GRADLE_PROJECT_mavenCentralPassword: password,
      },
    },
  );
}

function printHelp(): void {
  console.log(`
Release Workflow CLI

Usage:
  build-logic/scripts/release-workflow.main.ts <command> [options]

Commands:
  help
  doctor
  bump-pre
  publish-local
  publish-portal
  publish-central
  tag-and-publish-pre-release
  tag-and-publish-release --version x.y.z

Options:
  --dry-run        Show actions without mutating git/version/publishing
  --no-push        Skip git push for tag-and-publish commands
  --skip-publish   Skip publish steps for tag-and-publish commands
  --version x.y.z  Required for tag-and-publish-release
`);
}

function parseCli(rawArgs: string[]): { command: Command; options: CliOptions } {
  const args = [...rawArgs];
  const rawCommand = (args.shift() ?? "help").trim();
  const command = rawCommand as Command;
  const allowed: Command[] = [
    "help",
    "doctor",
    "bump-pre",
    "publish-local",
    "publish-portal",
    "publish-central",
    "tag-and-publish-pre-release",
    "tag-and-publish-release",
  ];

  if (!allowed.includes(command)) {
    fail(
      `Unknown command: ${rawCommand}`,
      `Use one of: ${allowed.join(", ")}`,
    );
  }

  const options: CliOptions = {
    dryRun: false,
    noPush: false,
    skipPublish: false,
  };

  while (args.length > 0) {
    const arg = args.shift()!;
    if (arg === "--dry-run") {
      options.dryRun = true;
    } else if (arg === "--no-push") {
      options.noPush = true;
    } else if (arg === "--skip-publish") {
      options.skipPublish = true;
    } else if (arg === "--version") {
      options.version = args.shift();
      if (!options.version) {
        fail("Missing value for --version", "Example: --version 1.2.3");
      }
    } else if (arg.startsWith("--version=")) {
      options.version = arg.split("=", 2)[1];
    } else {
      fail(`Unknown option: ${arg}`);
    }
  }

  return { command, options };
}

function runDoctor(portalMappings: Mapping, centralMappings: Mapping): void {
  ensureOpInstalled();
  validateSecretAccess(portalMappings, "portal");
  validateSecretAccess(centralMappings, "central");
  ok("Release doctor checks passed");
}

function runBumpPre(options: CliOptions): void {
  ensureCleanGitTree();
  const current = getCurrentVersion();
  const next = nextPreReleaseVersion(current);
  info(`Version bump: ${current} -> ${next}`);

  if (options.dryRun) {
    info(`[dry-run] update ${PLUGIN_BUILD_FILE}`);
  } else {
    setVersion(next);
  }

  commitVersion(next, options.dryRun);
}

function runPublishLocal(options: CliOptions): void {
  publishLocal(options.dryRun);
  printPublishReport(["local"]);
}

function runTagAndPublishPreRelease(
  options: CliOptions,
  portalMappings: Mapping,
  centralMappings: Mapping,
): void {
  const publishedSides: PublishSide[] = [];
  ensureCleanGitTree();
  runDoctor(portalMappings, centralMappings);

  const current = getCurrentVersion();
  const next = nextPreReleaseVersion(current);
  info(`Version bump: ${current} -> ${next}`);

  if (options.dryRun) {
    info(`[dry-run] update ${PLUGIN_BUILD_FILE}`);
  } else {
    setVersion(next);
  }

  commitAndTag(next, options.dryRun);

  if (!options.skipPublish) {
    info("Publishing to Gradle Plugin Portal...");
    publishPortal(portalMappings, options.dryRun);
    publishedSides.push("portal");
    info("Publishing to Maven Central...");
    publishCentral(centralMappings, options.dryRun);
    publishedSides.push("central");
  }

  if (!options.noPush) {
    pushRelease(next, options.dryRun);
  }

  printPublishReport(publishedSides);
}

function runTagAndPublishRelease(
  options: CliOptions,
  portalMappings: Mapping,
  centralMappings: Mapping,
): void {
  const publishedSides: PublishSide[] = [];
  const targetVersion = options.version;
  if (!targetVersion) {
    fail(
      "Missing --version for tag-and-publish-release",
      "Example: tag-and-publish-release --version 1.2.3",
    );
  }
  validateStableVersion(targetVersion);

  ensureCleanGitTree();
  runDoctor(portalMappings, centralMappings);

  const current = getCurrentVersion();
  if (current === targetVersion) {
    fail(
      `Version is already ${targetVersion}`,
      "Provide a different version or run bump-pre for pre-release flow.",
    );
  }

  info(`Version bump: ${current} -> ${targetVersion}`);
  if (options.dryRun) {
    info(`[dry-run] update ${PLUGIN_BUILD_FILE}`);
  } else {
    setVersion(targetVersion);
  }

  commitAndTag(targetVersion, options.dryRun);

  if (!options.skipPublish) {
    info("Publishing to Gradle Plugin Portal...");
    publishPortal(portalMappings, options.dryRun);
    publishedSides.push("portal");
    info("Publishing to Maven Central...");
    publishCentral(centralMappings, options.dryRun);
    publishedSides.push("central");
  }

  if (!options.noPush) {
    pushRelease(targetVersion, options.dryRun);
  }

  printPublishReport(publishedSides);
}

function main(): void {
  const { command, options } = parseCli(process.argv.slice(2));
  if (command === "help") {
    printHelp();
    return;
  }

  let mappingsCache: { portal: Mapping; central: Mapping } | null = null;
  const getMappings = (): { portal: Mapping; central: Mapping } => {
    if (!mappingsCache) {
      mappingsCache = validateScriptContracts();
    }
    return mappingsCache;
  };

  switch (command) {
    case "doctor": {
      const { portal, central } = getMappings();
      runDoctor(portal, central);
      break;
    }
    case "bump-pre":
      runBumpPre(options);
      break;
    case "publish-local":
      runPublishLocal(options);
      break;
    case "publish-portal": {
      const { portal } = getMappings();
      ensureOpInstalled();
      publishPortal(portal, options.dryRun);
      printPublishReport(["portal"]);
      break;
    }
    case "publish-central": {
      const { central } = getMappings();
      ensureOpInstalled();
      publishCentral(central, options.dryRun);
      printPublishReport(["central"]);
      break;
    }
    case "tag-and-publish-pre-release": {
      const { portal, central } = getMappings();
      runTagAndPublishPreRelease(options, portal, central);
      break;
    }
    case "tag-and-publish-release": {
      const { portal, central } = getMappings();
      runTagAndPublishRelease(options, portal, central);
      break;
    }
    case "help":
      printHelp();
      break;
  }
}

main();
