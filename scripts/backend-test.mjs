import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readdirSync } from "node:fs";
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
  const displayCommand = options.commandLine || [command, ...args].join(" ");
  console.log(`\n> ${displayCommand}`);
  const result = spawnSync(options.commandLine || command, options.commandLine ? [] : args, {
    cwd: options.cwd || repoRoot,
    stdio: "inherit",
    shell: options.shell ?? false,
    env: options.env || process.env
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

function findIntellijMaven() {
  if (process.platform !== "win32") return null;

  const jetBrainsRoot = path.join(process.env.ProgramFiles || "C:\\Program Files", "JetBrains");
  if (!existsSync(jetBrainsRoot)) return null;

  for (const directory of readdirSync(jetBrainsRoot, { withFileTypes: true })) {
    if (!directory.isDirectory()) continue;
    const ideRoot = path.join(jetBrainsRoot, directory.name);
    const mavenCmd = path.join(ideRoot, "plugins", "maven-plugin", "lib", "maven3", "bin", "mvn.cmd");
    if (existsSync(mavenCmd)) {
      return {
        command: mavenCmd,
        javaHome: path.join(ideRoot, "jbr")
      };
    }
  }

  return null;
}

const mvnwCmd = path.join(backendDir, process.platform === "win32" ? "mvnw.cmd" : "mvnw");
if (existsSync(mvnwCmd)) {
  run(mvnwCmd, ["test"], { cwd: backendDir });
}

const localMaven = process.platform === "win32" ? "mvn.cmd" : "mvn";
if (commandExists(localMaven)) {
  run(localMaven, ["test"], { cwd: backendDir });
}

const intellijMaven = findIntellijMaven();
if (intellijMaven) {
  console.log("Maven was not found on PATH. Running backend tests with IntelliJ's bundled Maven.");
  const commandLine = `call "${intellijMaven.command}" -DargLine=-Dnet.bytebuddy.experimental=true test`;
  run("", [], {
    cwd: backendDir,
    env: { ...process.env, JAVA_HOME: intellijMaven.javaHome },
    commandLine,
    shell: true
  });
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
