import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function exists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");
const releaseGate = read("scripts/release-gate.mjs");
const prePushGate = read("scripts/pre-push-gate.mjs");
const ci = read(".github/workflows/ci.yml");
const dockerhub = read(".github/workflows/dockerhub.yml");
const riskRegister = read("docs/go-live-riskregister.md");
const roadmap = read("docs/roadmap-kvar.md");
const evidence = read("docs/release-evidence.md");
const mvpProtocol = read("docs/mvp-testprotokoll.md");
const goLiveChecklist = read("docs/go-live-checklista.md");
const docs = `${roadmap}\n${evidence}\n${mvpProtocol}\n${goLiveChecklist}`;
const normalizedMvpProtocol = mvpProtocol.toLowerCase();
const mainSource = read("frontend/src/main.jsx");

const rootScripts = rootPackage.scripts || {};
const frontendScripts = frontendPackage.scripts || {};

check("Startklar command exists in frontend", frontendScripts["check:startklar"] === "node ../scripts/startklar-check.mjs", "frontend/package.json should expose npm run check:startklar.");
check("Startklar command exists in root", rootScripts["check:startklar"] === "npm --prefix frontend run check:startklar --", "package.json should expose npm run check:startklar from the repo root.");
check("Release gate runs Startklar", releaseGate.includes('"check:startklar"'), "The release gate should include the user-facing readiness summary.");
check("Pre-push gate runs full local proof", includesAll(prePushGate, ["--with-backend", "--with-docker-build", "check:git", "check:sync"]), "Pre-push should combine release, backend, Docker, git and sync checks.");
check("CI tests backend with PostgreSQL", includesAll(ci, ["postgres:16", "mvn -B test", "SPRING_DATASOURCE_URL"]), "GitHub Actions should test backend against PostgreSQL in Maven batch mode.");
check("CI runs frontend release gate", includesAll(ci, ["npm ci", "npm run check:audit", "npm run check:release"]), "GitHub Actions should install, audit and run the frontend release gate.");
check("CI builds Docker images", includesAll(ci, ["docker build -t cloudshop-backend:ci", "cloudshop-frontend:ci"]), "CI should build backend and frontend Docker images.");
check("Dockerhub workflow can publish images", includesAll(dockerhub, ["workflow_dispatch", "docker/login-action", "docker/build-push-action", "type=sha,prefix=sha-"]), "Dockerhub release should be manual and traceable by sha tag.");
check("Runtime smoke protects against blank page", includesAll(releaseGate, ["smoke:runtime", "Check frontend view routes"]), "Release should catch white page and broken navigation before use.");
check("Backend tests are part of full release", frontendScripts["check:release:full"]?.includes("--with-backend"), "Full local release should include backend tests.");
check("Docker builds are part of full release", frontendScripts["check:release:full"]?.includes("--with-docker-build"), "Full local release should include Docker image builds.");
check("Professional accounting controls are visible", includesAll(mainSource, ["periodlasning", "verifikationskontroll", "redovisningskontroll", "resultat- och balansdiagnos"]), "The app should expose period lock, voucher control, accounting control and report diagnosis.");
check("MVP flow is documented", includesAll(normalizedMvpProtocol, ["registrera", "logga in", "skapa kund", "skapa faktura", "pdf", "momsrapport"]), "The manual protocol should cover the core daily flow.");
check("GitHub sync blocker is explicit", includesAll(riskRegister, ["GitHub sync", "BLOCKERAR SKARP DRIFT", "check:sync"]), "The risk register should stop production trust when local commits are not pushed.");
check("External production blockers are explicit", includesAll(riskRegister, ["Dockerhub images", "EC2 deploy", "RDS databas", "Stripe betalningar", "SMTP e-post"]), "External go-live proof should stay visible.");
check("Real data warning is explicit", includesAll(riskRegister, ["skarp kunddata", "restore drill", "Bokforingsansvar"]), "The user should not put real production data in until backup/restore and responsibility checks are clear.");
check("Release evidence mentions latest local proof", includesAll(evidence, ["npm run check:prepush -- --allow-ahead", "AliBooks pre-push gate passed", "alibooks-backend:release-gate", "alibooks-frontend:release-gate"]), "Release evidence should show the strongest local proof.");
check("Roadmap points to Startklar", roadmap.includes("npm run check:startklar"), "The remaining roadmap should tell the user how to get the short readiness answer.");
check("Go-live checklist points to Startklar", goLiveChecklist.includes("npm run check:startklar"), "The go-live checklist should start with the short readiness answer.");
check("Docs separate local MVP from production", includesAll(docs, ["lokalt", "GitHub Actions", "Dockerhub", "EC2", "RDS"]), "Docs should distinguish local use from cloud production proof.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks startklar check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} startklar check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Startklar lokalt: ja, nar release gate och backend/databas ar grona.");
console.log("Skarp produktion: vanta tills GitHub sync, Dockerhub, EC2/RDS, backup restore drill, Stripe och SMTP ar externt verifierade.");
