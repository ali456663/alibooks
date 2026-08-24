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

const decisionDocPath = "docs/go-live-beslut.md";
const decisionDoc = exists(decisionDocPath) ? read(decisionDocPath) : "";
const riskRegister = read("docs/go-live-riskregister.md");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const startklar = read("docs/anvanda-idag-beslut.md");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");

const externalProofs = [
  "GitHub Actions",
  "Dockerhub",
  "EC2",
  "RDS",
  "backup",
  "restore drill",
  "Stripe",
  "SMTP",
  "check:sync",
  "check:external-go-live",
  "check:release-traceability"
];

check("Go-live decision doc exists", exists(decisionDocPath), decisionDocPath);
check("Decision separates local MVP from production", includesAll(decisionDoc, ["Lokal MVP", "Skarp drift", "externa bevis"]), "The decision must not overclaim production readiness.");
check("Decision requires all external proofs", includesAll(decisionDoc, externalProofs), "The decision should name every external blocker before real data.");
check("Decision blocks real data until proof", includesAll(decisionDoc, ["ska inte anvandas med riktig kunddata", "BLOCKERAR"]), "Real customers/bookkeeping data should stay blocked until proof exists.");
check("Decision has a go/no-go matrix", includesAll(decisionDoc, ["GO lokalt", "NO-GO skarp drift", "Beslut"]), "The user needs a simple decision table.");
check("Decision has exact command order", includesAll(decisionDoc, ["npm run check:release:full", "npm run check:external-go-live", "npm run check:git -- --strict", "npm run check:sync", "npm run check:prepush -- --allow-ahead"]), "Go-live should have runnable commands in order.");
check("Decision includes rollback and restore", includesAll(decisionDoc, ["rollback", "restore", "separat testdatabas"]), "Production use needs recovery proof, not only deploy proof.");
check("Decision includes accountant review", includesAll(decisionDoc, ["redovisningskonsult", "SIE", "huvudbok", "saldobalans"]), "Accounting review/export should be visible before real use.");
check("Risk register points to decision", riskRegister.includes("go-live-beslut.md") && riskRegister.includes("check:go-live-decision"), "Risk register should route to the final decision gate.");
check("Use-today decision points to go-live decision", startklar.includes("go-live-beslut.md") && startklar.includes("check:go-live-decision"), "Daily local-use doc should distinguish local use from go-live.");
check("Roadmap includes go-live decision", roadmap.includes("npm run check:go-live-decision"), "Roadmap should show the final go-live decision command.");
check("Release evidence includes go-live decision", releaseEvidence.includes("check:go-live-decision"), "Release evidence should record the final decision gate.");
check("Frontend exposes go-live decision script", frontendPackage.scripts?.["check:go-live-decision"] === "node ../scripts/go-live-decision-check.mjs", "frontend/package.json should expose npm run check:go-live-decision.");
check("Root exposes go-live decision script", rootPackage.scripts?.["check:go-live-decision"] === "npm --prefix frontend run check:go-live-decision --", "package.json should expose npm run check:go-live-decision.");
check("Release gate runs go-live decision", releaseGate.includes('"check:go-live-decision"'), "Release gate should include the final go-live decision.");
check("Readiness requires go-live decision", readiness.includes("scripts/go-live-decision-check.mjs") && readiness.includes("check:go-live-decision"), "Readiness should protect the decision gate.");
check("Evidence requires go-live decision", evidence.includes("scripts/go-live-decision-check.mjs") && evidence.includes("check:go-live-decision"), "Evidence should keep the decision gate visible.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks go-live decision check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} go-live decision check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Beslut: lokal MVP kan anvandas forsiktigt nar lokala gates ar grona.");
console.log("Beslut: skarp drift vantar tills externa bevis ar verifierade.");
