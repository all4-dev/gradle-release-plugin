#!/usr/bin/env -S node --experimental-strip-types

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import type { SpawnSyncOptions } from "node:child_process";

type Command =
  | "help"
  | "doctor"
  | "bump-pre"
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

const SCRIPT_PATH = resolve(process.argv[1]);
const SCRIPT_DIR = dirname(SCRIPT_PATH);
const ROOT = resolve(SCRIPT_DIR, "../..");
const PLUGIN_BUILD_FILE = resolve(ROOT, "plugin/build.gradle.kts");
const SCRIPTS_DIR = resolve(ROOT, "build-logic/scripts");
const OP_API_KEYS_SCRIPT = resolve(SCRIPTS_DIR, "op-api-keys.main.kts");
const PUBLISH_PORTAL_SCRIPT = resolve(SCRIPTS_DIR, "publish-portal.main.kts");
const PUBLISH_CENTRAL_SCRIPT = resolve(SCRIPTS_DIR, "publish-central.main.kts");

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

function ensureOpSignedIn(): void {
  const result = runCommand("op", ["whoami"]);
  if (result.status !== 0) {
    fail(
      "1Password session is not active",
      "Run: op signin\nThen retry this command.",
    );
  }
  ok("1Password session is active");
}

function readSecret(secretRef: string): string {
  const result = runCommand("op", ["read", secretRef]);
  if (result.status !== 0) {
    const details = [result.stdout.trim(), result.stderr.trim()]
      .filter(Boolean)
      .join("\n");
    fail(
      `Cannot read 1Password secret: ${secretRef}`,
      `${details || "No additional output"}\n\nVerify vault/item/section/field names and access permissions.`,
    );
  }
  return result.stdout.replace(/\r?\n$/, "");
}

function validateSecretAccess(mappings: Mapping, label: string): void {
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

function publishPortal(portalMappings: Mapping, dryRun: boolean): void {
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
  ensureOpSignedIn();
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

function runTagAndPublishPreRelease(
  options: CliOptions,
  portalMappings: Mapping,
  centralMappings: Mapping,
): void {
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
    info("Publishing to Maven Central...");
    publishCentral(centralMappings, options.dryRun);
  }

  if (!options.noPush) {
    pushRelease(next, options.dryRun);
  }
}

function runTagAndPublishRelease(
  options: CliOptions,
  portalMappings: Mapping,
  centralMappings: Mapping,
): void {
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
    info("Publishing to Maven Central...");
    publishCentral(centralMappings, options.dryRun);
  }

  if (!options.noPush) {
    pushRelease(targetVersion, options.dryRun);
  }
}

function main(): void {
  const { command, options } = parseCli(process.argv.slice(2));
  if (command === "help") {
    printHelp();
    return;
  }

  const { portal, central } = validateScriptContracts();

  switch (command) {
    case "doctor":
      runDoctor(portal, central);
      break;
    case "bump-pre":
      runBumpPre(options);
      break;
    case "publish-portal":
      ensureOpInstalled();
      ensureOpSignedIn();
      publishPortal(portal, options.dryRun);
      break;
    case "publish-central":
      ensureOpInstalled();
      ensureOpSignedIn();
      publishCentral(central, options.dryRun);
      break;
    case "tag-and-publish-pre-release":
      runTagAndPublishPreRelease(options, portal, central);
      break;
    case "tag-and-publish-release":
      runTagAndPublishRelease(options, portal, central);
      break;
    case "help":
      printHelp();
      break;
  }
}

main();
