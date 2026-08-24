import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const args = new Set(process.argv.slice(2));
const requirePushed = args.has("--require-pushed");
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

function git(commandArgs) {
  const result = spawnSync("git", commandArgs, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false
  });
  return {
    ok: result.status === 0,
    output: `${result.stdout || ""}\n${result.stderr || ""}`.trim()
  };
}

const doc = read("docs/post-push-verifiering.md");
const ciHandoff = read("docs/ci-handoff-efter-push.md");
const goLiveDecision = read("docs/go-live-beslut.md");
const externalProof = read("docs/externa-go-live-bevis.md");
const ciWorkflow = read(".github/workflows/ci.yml");
const dockerhubWorkflow = read(".github/workflows/dockerhub.yml");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");
const remote = git(["remote", "-v"]);
const status = git(["status", "-sb"]);
const aheadMatch = status.output.match(/\[ahead\s+(\d+)/);
const aheadCount = aheadMatch ? Number(aheadMatch[1]) : 0;

check("Post-push doc names purpose", includesAll(doc, ["Lokal kod ar gron", "GitHub har samma kod", "CI, Dockerhub"]), "The checklist should separate local, GitHub and external proof.");
check("Post-push doc has exact command order", includesAll(doc, ["npm run check:release:full", "npm run check:git -- --strict", "git push", "npm run check:sync", "npm run check:post-push -- --require-pushed"]), "The user should be able to follow copy-pasteable commands.");
check("Post-push doc names GitHub Actions URL", doc.includes("https://github.com/ali456663/alibooks/actions"), "GitHub Actions URL should be explicit.");
check("Post-push doc names CI jobs", includesAll(doc, ["Backend build and test", "Frontend release gate", "Docker build"]), "CI job names should be explicit.");
check("Post-push doc names Dockerhub secrets", includesAll(doc, ["DOCKERHUB_USERNAME", "DOCKERHUB_TOKEN"]), "Dockerhub secrets should be visible but not values.");
check("Post-push doc names Docker images", includesAll(doc, ["cloudshop-backend", "cloudshop-frontend"]), "Expected image names should be explicit.");
check("Post-push doc requires traceable image tag", includesAll(doc, ["sha-*", "v*", "IMAGE_TAG"]), "Production should not rely on an untraceable image.");
check("Post-push doc keeps production blockers visible", includesAll(doc, ["publik frontend URL", "RDS status", "restore drill", "Stripe test-webhook", "SMTP testmail"]), "External blockers should not disappear after push.");
check("Post-push doc explains local MVP boundary", includesAll(doc, ["lokal MVP", "inte skarp drift"]), "A green local push should not overclaim production readiness.");
check("Post-push doc lists common stops", includesAll(doc, ["check:sync", "Backendtest", "Frontend release gate", "Docker build", "Dockerhub"]), "The user should know what to do when post-push fails.");
check("CI handoff links post-push command", ciHandoff.includes("npm run check:post-push"), "CI handoff should route to the post-push verifier.");
check("Go-live decision links post-push command", goLiveDecision.includes("npm run check:post-push"), "Final go-live decision should include post-push verification.");
check("External proof links post-push command", externalProof.includes("npm run check:post-push"), "External proof checklist should include post-push verification.");
check("Roadmap links post-push command", roadmap.includes("npm run check:post-push"), "Roadmap should keep post-push verification visible.");
check("Release evidence links post-push check", releaseEvidence.includes("check:post-push"), "Release evidence should record the post-push verifier.");
check("CI workflow runs on push to main", includesAll(ciWorkflow, ["push:", "main"]), "CI should run when main is pushed.");
check("CI workflow supports manual run", ciWorkflow.includes("workflow_dispatch"), "CI should be manually rerunnable.");
check("CI workflow runs backend and frontend gates", includesAll(ciWorkflow, ["mvn test", "npm run check:release", "docker build -t cloudshop-backend:ci", "cloudshop-frontend:ci"]), "CI should prove backend, frontend and Docker image builds.");
check("Dockerhub workflow supports manual and tag release", includesAll(dockerhubWorkflow, ["workflow_dispatch", '"v*"']), "Dockerhub release should be manually runnable and taggable.");
check("Dockerhub workflow uses secrets", includesAll(dockerhubWorkflow, ["secrets.DOCKERHUB_USERNAME", "secrets.DOCKERHUB_TOKEN"]), "Dockerhub credentials should stay in GitHub secrets.");
check("Dockerhub workflow publishes traceable tags", includesAll(dockerhubWorkflow, ["type=sha,prefix=sha-", "type=ref,event=tag"]), "Docker images should be traceable to commit/tag.");
check("Git remote points to AliBooks", remote.ok && remote.output.includes("https://github.com/ali456663/alibooks.git"), "Post-push verifier should check the expected remote.");
check("Optional pushed-state enforcement is available", doc.includes("--require-pushed") && requirePushed !== undefined, "The verifier should support a strict after-push mode.");
check("Branch is pushed when strict mode is used", !requirePushed || aheadCount === 0, requirePushed ? `Commits ahead of origin/main: ${aheadCount}.` : "Strict pushed-state check is only enforced with --require-pushed.");
check("Frontend exposes post-push check", frontendPackage.scripts?.["check:post-push"] === "node ../scripts/post-push-verification-check.mjs", "frontend/package.json should expose npm run check:post-push.");
check("Root exposes post-push check", rootPackage.scripts?.["check:post-push"] === "npm --prefix frontend run check:post-push --", "package.json should expose npm run check:post-push.");
check("Release gate runs post-push checklist check", releaseGate.includes('"check:post-push"'), "Release gate should verify the post-push checklist exists.");
check("Readiness requires post-push verifier", readiness.includes("scripts/post-push-verification-check.mjs") && readiness.includes("check:post-push"), "Readiness should protect the post-push verifier.");
check("Evidence requires post-push verifier", evidence.includes("scripts/post-push-verification-check.mjs") && evidence.includes("check:post-push"), "Evidence should keep the post-push verifier visible.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks post-push verification check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} post-push verification check(s) failed.`);
  process.exit(1);
}

if (!requirePushed && aheadCount > 0) {
  console.log("");
  console.log(`Info: branch is ${aheadCount} commit(s) ahead of origin/main. Run with --require-pushed after git push.`);
}
