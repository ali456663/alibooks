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
const withBackend = args.has("--with-backend");
const withDockerBuild = args.has("--with-docker-build");

const frontendSteps = [
  ["build", "Build frontend"],
  ["check:professional-loop", "Check professional accounting loop"],
  ["check:speedledger-parity", "Check SpeedLedger-style feature parity"],
  ["check:acceptance", "Check MVP acceptance coverage"],
  ["check:api-contract", "Check frontend/backend API contract"],
  ["check:ready", "Check AliBooks readiness"],
  ["check:backend-wiring", "Check backend constructor and record wiring"],
  ["check:git-parser", "Check git status parser regression"],
  ["check:backup", "Check backup and restore readiness"],
  ["check:ci", "Check GitHub Actions pipeline"],
  ["check:docs", "Check docs consistency"],
  ["check:evidence", "Check MVP release evidence"],
  ["check:schema", "Check database schema policy"],
  ["check:migrations", "Check controlled schema migration"],
  ["check:schema-bootstrap", "Check full schema bootstrap runbook"],
  ["check:secrets", "Check committed secrets"],
  ["check:dependencies", "Check dependency risk"],
  ["check:data-safety", "Check destructive data safety"],
  ["check:docker", "Check Docker config"],
  ["check:prod", "Check production readiness"],
  ["check:go-live-risks", "Check go-live risk register"],
  ["check:manual-go-live", "Check manual go-live evidence requirements"],
  ["check:startklar", "Check local MVP start readiness"],
  ["check:mvp-use", "Check 20-step MVP use readiness"],
  ["check:finance-ui", "Check professional finance UI theme"],
  ["check:release-traceability", "Check release traceability"],
  ["check:views", "Check frontend view routes"],
  ["smoke:runtime", "Smoke test frontend render"]
];

function run(command, commandArgs, cwd, label) {
  console.log("");
  console.log(`==> ${label}`);
  console.log(`$ ${[command, ...commandArgs].join(" ")}`);

  const executable = isWindows && command.endsWith(".cmd")
      ? process.env.ComSpec || "cmd.exe"
      : command;
  const args = isWindows && command.endsWith(".cmd")
      ? ["/d", "/c", "call", command, ...commandArgs]
      : commandArgs;

  const result = spawnSync(executable, args, {
    cwd,
    stdio: "inherit",
    shell: false
  });

  if (result.error) {
    console.error(`Release gate failed to start '${label}': ${result.error.message}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    console.error(`Release gate failed at '${label}'.`);
    process.exit(result.status || 1);
  }
}

function runNpmScript(script, label) {
  if (npmExecPath) {
    run(process.execPath, [npmExecPath, "run", script], frontendDir, label);
    return;
  }

  run(npmCommand, ["run", script], frontendDir, label);
}

for (const [script, label] of frontendSteps) {
  runNpmScript(script, label);
}

if (withBackend) {
  runNpmScript("test:backend", "Run backend tests");
} else {
  console.log("");
  console.log("Skipping backend tests. Add --with-backend before push or release if you want the full local gate.");
}

if (withDockerBuild) {
  run("docker", ["build", "-t", "alibooks-backend:release-gate", "./backend"], repoRoot, "Build backend Docker image");
  run("docker", ["build", "-f", "./frontend/Dockerfile.prod", "-t", "alibooks-frontend:release-gate", "./frontend"], repoRoot, "Build frontend Docker image");
} else {
  console.log("Skipping Docker image builds. Add --with-docker-build before deploy if Docker is available.");
}

console.log("");
console.log("AliBooks release gate passed.");
