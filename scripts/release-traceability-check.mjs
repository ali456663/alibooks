import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];
const warnings = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function warn(name, ok, detail) {
  warnings.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

function git(commandArgs) {
  const result = spawnSync("git", commandArgs, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false
  });

  if (result.error || result.status !== 0) {
    return "";
  }

  return result.stdout.trim();
}

const rootPackageJson = json("package.json");
const packageJson = json("frontend/package.json");
const dockerhub = read(".github/workflows/dockerhub.yml");
const composeProd = read("docker-compose.prod.yml");
const prodEnvExample = read(".env.production.example");
const ec2Deploy = read("scripts/ec2-deploy.sh");
const releaseEvidence = read("docs/release-evidence.md");
const goLiveChecklist = read("docs/go-live-checklista.md");
const dockerhubGuide = read("docs/dockerhub-release.md");
const riskRegister = read("docs/go-live-riskregister.md");
const prePushGate = read("scripts/pre-push-gate.mjs");
const gitStatusScript = read("scripts/git-release-status.mjs");

const version = packageJson.version || "";
const rootVersion = rootPackageJson.version || "";
check(
  "Frontend package has semver version",
  /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(version),
  "frontend/package.json should expose a traceable MVP version."
);

check(
  "Root package version matches frontend",
  rootVersion === version,
  "Root package.json should stay aligned with frontend/package.json for release traceability."
);

check(
  "Dockerhub publishes immutable SHA tags",
  includesAll(dockerhub, ["type=sha,prefix=sha-", "cloudshop-backend", "cloudshop-frontend"]),
  "Dockerhub workflow should publish sha-* tags for backend and frontend images."
);

check(
  "Dockerhub publishes release tags",
  includesAll(dockerhub, ["type=ref,event=tag", 'tags:', '"v*"']),
  "Dockerhub workflow should publish version tags such as v1.0.0."
);

check(
  "Production compose deploys selected image tag",
  includesAll(composeProd, ["${IMAGE_TAG:-latest}", "cloudshop-frontend", "cloudshop-backend"]),
  "docker-compose.prod.yml should let EC2 select latest, sha-* or v* images through IMAGE_TAG."
);

check(
  "Production env template exposes image tag",
  includesAll(prodEnvExample, ["DOCKERHUB_USERNAME=", "IMAGE_TAG=latest"]),
  ".env.production.example should show how EC2 selects Dockerhub images."
);

check(
  "EC2 deploy pulls tagged images before up",
  includesAll(ec2Deploy, ["docker compose", "pull", "up -d", "--env-file"]),
  "scripts/ec2-deploy.sh should pull the selected Dockerhub images before starting containers."
);

check(
  "Go-live docs explain fixed release tags",
  includesAll(`${goLiveChecklist}\n${dockerhubGuide}`, ["git tag v1.0.0", "IMAGE_TAG=v1.0.0", "sha-"]),
  "Go-live docs should explain both fixed version tags and commit SHA image tags."
);

check(
  "Release evidence keeps sync and external proof visible",
  includesAll(releaseEvidence, ["npm run check:sync", "GitHub Actions", "Dockerhub workflow", "EC2", "RDS"]),
  "Release evidence should connect local gates to external CI, Dockerhub and cloud proof."
);

check(
  "Risk register blocks unsynced code from production",
  includesAll(riskRegister, ["GitHub sync", "BLOCKERAR SKARP DRIFT", "check:sync"]),
  "Go-live risk register should prevent using old GitHub/cloud code by mistake."
);

check(
  "Pre-push gate combines release, clean git and sync",
  includesAll(prePushGate, ["check:release", "check:git", "check:sync", "--allow-ahead"]),
  "Pre-push gate should prove local release state and require sync unless explicitly preparing to push."
);

check(
  "Git release status reports ahead and behind",
  includesAll(gitStatusScript, ["Commits ahead of upstream", "Commits behind upstream", "--require-pushed"]),
  "Git status check should make pending push/pull state visible."
);

const head = git(["rev-parse", "--short=12", "HEAD"]);
const branch = git(["branch", "--show-current"]);
const upstream = git(["rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"]);
const aheadBehind = upstream ? git(["rev-list", "--left-right", "--count", `${upstream}...HEAD`]) : "";
const [behindText = "0", aheadText = "0"] = aheadBehind.split(/\s+/);
const behind = Number(behindText || 0);
const ahead = Number(aheadText || 0);

check(
  "Git branch has upstream",
  Boolean(upstream),
  "A release candidate should know which GitHub branch it will be compared with."
);

check(
  "Git branch is not behind upstream",
  !upstream || behind === 0,
  "Pull/review remote changes before release if origin has commits not present locally."
);

warn(
  "Local commits are already pushed",
  !upstream || ahead === 0,
  ahead > 0
    ? `${ahead} local commit(s) are pending push before GitHub Actions and Dockerhub can prove this release.`
    : "Local branch is in sync with upstream."
);

const failures = checks.filter((result) => !result.ok);
const activeWarnings = warnings.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

for (const result of warnings) {
  console.log(`${result.ok ? "OK" : "WARN"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`Current release candidate: ${version} ${branch || "unknown-branch"} ${head || "unknown-commit"}`);
if (upstream) {
  console.log(`Upstream: ${upstream}, ahead ${ahead}, behind ${behind}`);
}
console.log(`AliBooks release traceability check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (activeWarnings.length > 0) {
  console.log(`${activeWarnings.length} traceability warning(s) need review before external go-live proof.`);
}

if (failures.length > 0) {
  console.error(`${failures.length} release traceability check(s) failed.`);
  process.exit(1);
}
