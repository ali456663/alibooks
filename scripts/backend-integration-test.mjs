import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const docker = process.platform === "win32" ? "docker.exe" : "docker";
const compose = ["compose", "-p", `alibooks-test-${process.pid}`, "-f", "docker-compose.test.yml"];
function run(args) {
  const result = spawnSync(docker, [...compose, ...args], { cwd: repoRoot, stdio: "inherit" });
  if (result.error) console.error(result.error.message);
  return result.status ?? 1;
}

// This compose project has an ephemeral database and no production env file.
let status = 1;
try {
  status = run(["up", "--abort-on-container-exit", "--exit-code-from", "backend-test"]);
} finally {
  const cleanup = run(["down", "--volumes"]);
  if (cleanup !== 0) status = cleanup;
}
process.exitCode = status;
