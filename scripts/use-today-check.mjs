import { existsSync, readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
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

function check(name, ok, detail, severity = "fail") {
  checks.push({ name, ok: Boolean(ok), detail, severity });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

function gitStatus() {
  const result = spawnSync("git", ["status", "--porcelain"], {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false
  });

  return result.status === 0 ? result.stdout.trimEnd() : "";
}

const doc = read("docs/anvanda-idag-beslut.md");
const mvpUseDoc = read("docs/anvandningsklar-mvp.md");
const driftRunbook = read("docs/drift-runbook.md");
const riskRegister = read("docs/go-live-riskregister.md");
const roadmap = read("docs/roadmap-kvar.md");
const releaseEvidence = read("docs/release-evidence.md");
const mainSource = read("frontend/src/main.jsx");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const frontendPackage = json("frontend/package.json");
const rootPackage = json("package.json");
const status = gitStatus();

check("Use-today document exists", exists("docs/anvanda-idag-beslut.md"), "docs/anvanda-idag-beslut.md should exist.");
check("Use-today script exists", exists("scripts/use-today-check.mjs"), "scripts/use-today-check.mjs should exist.");
check("Decision separates local MVP and production", includesAll(doc, ["Gront for lokal MVP", "Skarp produktion", "externa bevis"]), "The decision should not overclaim production readiness.");
check("Decision covers blank-page stop", includesAll(doc, ["vit sida", "render recovery"]), "White-page recovery should stop real work until fixed.");
check("Decision covers database stop", includesAll(doc, ["Connection to localhost:5432 refused", "schema drift"]), "Database startup and schema drift should be visible stop signs.");
check("Decision covers bookkeeping stop", includesAll(doc, ["debet", "kredit", "Fakturanummer", "verifikationsnummer"]), "Accounting numbering and balance errors should stop use.");
check("Decision covers period lock", doc.includes("Periodlasning"), "Period locking should be part of the safety decision.");
check("Decision covers backup and restore", includesAll(doc, ["Backup", "restore drill"]), "Backup and restore proof should be required.");
check("Decision covers secrets", includesAll(doc, ["API-nycklar", "GitHub"]), "Secrets should not be committed or exposed.");
check("Decision has a daily routine", includesAll(doc, ["Daglig rutin", "npm run doctor", "Startklar", "Driftcenter", "Sakerhet"]), "The user should have a short daily operating routine.");
check("Decision points to review views", includesAll(doc, ["Regelkontroll", "Redovisningskontroll", "Momsrapport"]), "Daily use should route to the professional accounting control views.");
check("Decision points to accountant handoff", includesAll(doc, ["Exportera", "redovisningskonsult"]), "Use-today should keep accountant review/export visible.");
check("Decision mentions payment compliance", includesAll(doc, ["kort", "Apple Pay", "Swish", "kassaregisterkrav"]), "Electronic payments should stay blocked for review before production.");
check("Decision command is documented", doc.includes("npm run check:use-today"), "The short command should be visible in the doc.");
check("Decision links supporting docs", includesAll(doc, ["anvandningsklar-mvp.md", "drift-runbook.md", "go-live-riskregister.md"]), "The use-today decision should link to the deeper docs.");
check("MVP use doc includes use-today decision", mvpUseDoc.includes("check:use-today"), "MVP use checklist should point to the final daily use decision.");
check("Drift runbook includes use-today decision", driftRunbook.includes("check:use-today"), "Operations runbook should point to the daily use decision.");
check("Risk register includes use-today boundary", riskRegister.includes("check:use-today"), "Risk register should keep the local-use boundary explicit.");
check("Roadmap includes use-today command", roadmap.includes("npm run check:use-today"), "Roadmap should tell the user how to get the final use decision.");
check("Release evidence includes use-today proof", releaseEvidence.includes("check:use-today"), "Release evidence should include the use-today proof.");
check("Frontend exposes Startklar", mainSource.includes('activeView === "goLive"') && mainSource.includes("Startklar"), "The app should expose Startklar.");
check("Frontend exposes Driftcenter", mainSource.includes("operationsCenter") && mainSource.includes("Driftcenter"), "The app should expose Driftcenter.");
check("Frontend exposes safety views", includesAll(mainSource, ["Sakerhet", "Regelkontroll", "Redovisningskontroll", "Momsrapport"]), "The app should expose the views named in the use-today routine.");
check("Frontend exposes use-today script", frontendPackage.scripts?.["check:use-today"] === "node ../scripts/use-today-check.mjs", "frontend/package.json should expose npm run check:use-today.");
check("Root exposes use-today script", rootPackage.scripts?.["check:use-today"] === "npm --prefix frontend run check:use-today --", "package.json should expose npm run check:use-today.");
check("Release gate runs use-today check", releaseGate.includes('"check:use-today"'), "Release gate should fail if the use-today decision disappears.");
check("Readiness includes use-today check", readiness.includes("scripts/use-today-check.mjs") && readiness.includes("check:use-today"), "Readiness should require the use-today doc, script and command.");
check("Evidence includes use-today check", evidence.includes("scripts/use-today-check.mjs") && evidence.includes("check:use-today"), "Evidence should keep the use-today proof fresh.");
check("Working tree status is visible", true, status ? `Local changes present while checking: ${status.split("\n").length} file(s).` : "Working tree is clean.", status ? "warn" : "fail");

const failed = checks.filter((result) => !result.ok && result.severity === "fail");
const warnings = checks.filter((result) => !result.ok && result.severity === "warn");

for (const result of checks) {
  const marker = result.ok ? "OK" : result.severity === "warn" ? "WARN" : "FAIL";
  console.log(`${marker} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks use-today check: ${checks.length - failed.length - warnings.length}/${checks.length} required checks passed.`);

if (warnings.length > 0) {
  console.log(`${warnings.length} warning(s) need review.`);
}

if (failed.length > 0) {
  console.error(`${failed.length} use-today check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Lokal MVP: ja, om release gate, backend/databas, backup och manuell klickkontroll ar grona.");
console.log("Skarp produktion: nej, vanta pa GitHub/Dockerhub/EC2/RDS/Stripe/SMTP/restore-bevis.");
