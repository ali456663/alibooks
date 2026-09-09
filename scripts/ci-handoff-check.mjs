import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { isAliBooksRemote } from "./lib/git-remote.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

function gitRemote() {
  const result = spawnSync("git", ["remote", "get-url", "origin"], {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false
  });
  return result.status === 0 ? result.stdout : "";
}

const doc = read("docs/ci-handoff-efter-push.md");
const ci = read(".github/workflows/ci.yml");
const dockerhub = read(".github/workflows/dockerhub.yml");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");
const remote = gitRemote();

check("CI handoff doc names GitHub repo", includesAll(doc, ["https://github.com/ali456663/alibooks", "GitHub Actions"]), "The user should know exactly where to inspect CI.");
check("Git remote points to AliBooks repo", isAliBooksRemote(remote), "Origin must identify the AliBooks repository; HTTPS and SSH are supported.");
check("Doc has exact push flow", includesAll(doc, ["npm run check:release", "npm run check:git -- --strict", "git push", "npm run check:sync"]), "CI handoff should start from a clean local release.");
check("Doc lists CI jobs", includesAll(doc, ["Backend build and test", "Frontend release gate", "Docker build"]), "The user should know which GitHub jobs must be green.");
check("Doc lists CI artifacts and summaries", includesAll(doc, ["backend-surefire-reports", "frontend-dist", "GITHUB_STEP_SUMMARY"]), "The user should know where to find downloadable CI evidence and job summaries.");
check("Doc lists Dockerhub secrets", includesAll(doc, ["DOCKERHUB_USERNAME", "DOCKERHUB_TOKEN"]), "Dockerhub publish requires GitHub secrets.");
check("Doc lists Dockerhub image names", includesAll(doc, ["cloudshop-backend", "cloudshop-frontend"]), "Expected Dockerhub image names should be explicit.");
check("Doc covers known backend test failure", includesAll(doc, ["OrderController", "CreateOrderRequest"]), "Past constructor/record CI failures should be easy to triage.");
check("Doc covers npm audit failure", doc.includes("npm audit"), "Frontend dependency audit failures should be named.");
check("Doc covers release gate failure", doc.includes("npm run check:release"), "Frontend release gate failures should be named.");
check("Doc covers Docker build failure", doc.includes("Docker build"), "Docker build triage should be named.");
check("Doc covers Node 24", doc.includes("Node 24"), "Node runtime drift should stay visible.");
check("Doc covers PostgreSQL 16", doc.includes("PostgreSQL 16"), "CI database version should stay visible.");
check("Doc links go-live decision", doc.includes("npm run check:go-live-decision"), "CI proof should not be confused with production go-live.");
check("CI runs on push to main", includesAll(ci, ["push:", "main"]), "CI should start when main is pushed.");
check("CI supports workflow_dispatch", ci.includes("workflow_dispatch"), "CI should be runnable manually.");
check("CI backend uses Java 21 and PostgreSQL 16", includesAll(ci, ['java-version: "21"', "postgres:16"]), "Backend CI should match local/prod direction.");
check("CI backend runs Maven tests in batch mode", ci.includes("mvn -B test"), "Backend CI must run tests without interactive Maven output.");
check("CI frontend uses Node 24 and npm ci", includesAll(ci, ['node-version: "24"', "npm ci"]), "Frontend CI should be reproducible and current.");
check("CI frontend runs audit and release gate", includesAll(ci, ["npm run check:audit", "npm run check:release"]), "Frontend CI should run security audit and release gate.");
check("CI Docker waits for backend and frontend", includesAll(ci, ["needs:", "backend", "frontend"]), "Docker job should wait for test jobs.");
check("CI builds backend and frontend Docker images", includesAll(ci, ["cloudshop-backend:ci", "cloudshop-frontend:ci"]), "CI should prove both images can build.");
check("CI stores downloadable artifacts", includesAll(ci, ["backend-surefire-reports", "frontend-dist", "actions/upload-artifact@v4"]), "CI should preserve backend reports and frontend build output as artifacts.");
check("Dockerhub workflow can run manually and by tag", includesAll(dockerhub, ["workflow_dispatch", '"v*"']), "Dockerhub workflow should support manual and tagged releases.");
check("Dockerhub workflow uses secrets", includesAll(dockerhub, ["secrets.DOCKERHUB_USERNAME", "secrets.DOCKERHUB_TOKEN"]), "Dockerhub credentials must not be committed.");
check("Dockerhub workflow publishes traceable tags", includesAll(dockerhub, ["type=raw,value=latest", "type=sha,prefix=sha-", "type=ref,event=tag"]), "Published images should be traceable.");
check("Dockerhub workflow pushes both images", includesAll(dockerhub, ["Build and push backend", "Build and push frontend", "push: true"]), "Backend and frontend images should be pushed.");
check("Frontend exposes CI handoff check", frontendPackage.scripts?.["check:ci-handoff"] === "node ../scripts/ci-handoff-check.mjs", "frontend/package.json should expose npm run check:ci-handoff.");
check("Root exposes CI handoff check", rootPackage.scripts?.["check:ci-handoff"] === "npm --prefix frontend run check:ci-handoff --", "package.json should expose npm run check:ci-handoff.");
check("Release gate runs CI handoff", releaseGate.includes('"check:ci-handoff"'), "Release gate should include CI handoff proof.");
check("Readiness requires CI handoff", readiness.includes("scripts/ci-handoff-check.mjs") && readiness.includes("check:ci-handoff"), "Readiness should protect CI handoff.");
check("Evidence requires CI handoff", evidence.includes("scripts/ci-handoff-check.mjs") && evidence.includes("check:ci-handoff"), "Evidence should keep CI handoff visible.");
check("Release evidence mentions CI handoff", releaseEvidence.includes("check:ci-handoff"), "Release evidence should list CI handoff proof.");
check("Roadmap mentions CI handoff", roadmap.includes("npm run check:ci-handoff"), "Roadmap should show the CI handoff command.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks CI handoff check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} CI handoff check(s) failed.`);
  process.exit(1);
}
