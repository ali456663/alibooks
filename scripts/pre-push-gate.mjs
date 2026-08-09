import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const frontendDir = path.join(repoRoot, "frontend");
const isWindows = process.platform === "win32";
const npmCommand = isWindows ? "npm.cmd" : "npm";
const npmExecPath = process.env.npm_execpath || "";
const args = new Set(process.argv.slice(2));
const quick = args.has("--quick");
const allowAhead = args.has("--allow-ahead");

function run(command, commandArgs, cwd, label) {
  console.log("");
  console.log(`==> ${label}`);
  console.log(`$ ${[command, ...commandArgs].join(" ")}`);

  const executable = isWindows && command.endsWith(".cmd")
    ? process.env.ComSpec || "cmd.exe"
    : command;
  const finalArgs = isWindows && command.endsWith(".cmd")
    ? ["/d", "/c", "call", command, ...commandArgs]
    : commandArgs;

  const result = spawnSync(executable, finalArgs, {
    cwd,
    stdio: "inherit",
    shell: false
  });

  if (result.error) {
    console.error(`Pre-push gate failed to start '${label}': ${result.error.message}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    console.error(`Pre-push gate failed at '${label}'.`);
    process.exit(result.status || 1);
  }
}

function runNpmScript(script, scriptArgs, label) {
  if (npmExecPath) {
    run(process.execPath, [npmExecPath, "run", script, "--", ...scriptArgs], frontendDir, label);
    return;
  }

  run(npmCommand, ["run", script, "--", ...scriptArgs], frontendDir, label);
}

const releaseArgs = quick ? [] : ["--with-backend", "--with-docker-build"];
runNpmScript("check:release", releaseArgs, quick ? "Run quick release gate" : "Run full release gate");
runNpmScript("check:git", ["--strict"], "Check clean local release state");

if (allowAhead) {
  console.log("");
  console.log("Skipping GitHub sync check because --allow-ahead was provided.");
  console.log("Use this only immediately before the actual git push.");
} else {
  runNpmScript("check:sync", [], "Check GitHub sync state");
}

console.log("");
console.log("AliBooks pre-push gate passed.");
