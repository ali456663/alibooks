import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const backendDir = path.join(repoRoot, "backend");
const mavenCacheDir = path.join(repoRoot, ".m2-cache");

function commandExists(command, versionArg = "--version") {
  const result = spawnSync(command, [versionArg], { stdio: "ignore", shell: false });
  return result.status === 0;
}

function run(command, args, options = {}) {
  console.log(`\n> ${[command, ...args].join(" ")}`);
  const result = spawnSync(command, args, {
    cwd: options.cwd || repoRoot,
    stdio: "inherit",
    shell: false
  });

  if (result.error) {
    console.error(result.error.message);
    process.exit(1);
  }

  process.exit(result.status ?? 1);
}

function dockerCommand() {
  return process.platform === "win32" ? "docker.exe" : "docker";
}

const mvnwCmd = path.join(backendDir, process.platform === "win32" ? "mvnw.cmd" : "mvnw");
if (existsSync(mvnwCmd)) {
  run(mvnwCmd, ["test"], { cwd: backendDir });
}

const localMaven = process.platform === "win32" ? "mvn.cmd" : "mvn";
if (commandExists(localMaven)) {
  run(localMaven, ["test"], { cwd: backendDir });
}

const docker = dockerCommand();
if (!commandExists(docker)) {
  console.error("Backend tests need Maven, Maven Wrapper or Docker. Install one of them and run this command again.");
  process.exit(1);
}

console.log("Maven was not found locally. Running backend tests with Docker Maven image instead.");
mkdirSync(mavenCacheDir, { recursive: true });
run(docker, [
  "run",
  "--rm",
  "-v",
  `${backendDir}:/workspace`,
  "-v",
  `${mavenCacheDir}:/root/.m2`,
  "-w",
  "/workspace",
  "maven:3.9.9-eclipse-temurin-21",
  "mvn",
  "test"
]);
