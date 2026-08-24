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

const docPath = "docs/drift-runbook.md";
const doc = exists(docPath) ? read(docPath) : "";
const main = read("frontend/src/main.jsx");
const frontendPackage = JSON.parse(read("frontend/package.json"));
const rootPackage = JSON.parse(read("package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidenceCheck = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const localDoctor = read("scripts/local-doctor.mjs");
const runtimeSmoke = read("scripts/frontend-runtime-smoke.mjs");
const production = read("scripts/production-readiness-check.mjs");
const backup = read("scripts/backup-readiness-check.mjs");
const traceability = read("scripts/release-traceability-check.mjs");

check("Drift runbook exists", exists(docPath), docPath);
check("Runbook covers quick triage", includesAll(doc, ["npm run doctor", "npm run check:views", "npm run smoke:runtime", "vit sida"]), "Runbook should cover white page, local doctor and smoke checks.");
check("Runbook covers database recovery", includesAll(doc, ["pg_dump -Fc", "pg_restore -l", "restore drill", "APP_SCHEMA_PATCH_ENABLED=false"]), "Runbook should cover backup, restore and schema safety.");
check("Runbook covers incident handling", includesAll(doc, ["Incidentlogg", "severity", "paverkan", "verifierad"]), "Runbook should explain how to log and verify incidents.");
check("Runbook covers release and rollback", includesAll(doc, ["Releasejournal", "rollback", "IMAGE_TAG", "commit"]), "Runbook should require release evidence and rollback plan.");
check("Runbook blocks real production data", includesAll(doc, ["riktig kunddata", "GitHub Actions", "Dockerhub", "EC2/RDS", "Stripe", "SMTP"]), "Runbook should keep external production blockers visible.");

check("Operations center exists in UI", includesAll(main, ['operationsCenter: "Driftcenter"', 'activeView === "operationsCenter"', "operationsCenterRows"]), "Frontend should expose Driftcenter.");
check("Incident log persists locally", includesAll(main, ["alibooks-operations-incidents", "setOperationsIncidents", "downloadOperationsIncidentLogCsv"]), "Driftcenter should store and export incidents.");
check("Release journal persists locally", includesAll(main, ["alibooks-operations-releases", "setOperationsReleases", "downloadOperationsReleaseLogCsv"]), "Driftcenter should store and export release/rollback evidence.");
check("Release entries require rollback plan", includesAll(main, ["operationsReleaseRollbackPlan", "Skriv rollback-plan", "Enter a rollback plan"]), "Release journal should require rollback plan text.");
check("Release entries track backup and smoke", includesAll(main, ["operationsReleaseBackupChecked", "operationsReleaseSmokeTested", "backupChecked", "smokeTested"]), "Release journal should track backup and smoke proof.");
check("AI assistant can route to operations", includesAll(main, ["Ga till Driftcenter", "operationsCenterScore", "operationsIncidentOpenCount", "operationsReleaseUnsafeCount"]), "AI assistant should explain Driftcenter status.");

check("Local doctor checks health and database", includesAll(localDoctor, ["/health", "/system/status", "database.ok", "Docker Compose status"]), "Local doctor should diagnose backend, database and Docker.");
check("Runtime smoke catches white page", includesAll(runtimeSmoke, ["bodyTextLength", "rootChildCount", "render recovery", "alibooks-active-view"]), "Runtime smoke should catch blank render and broken local view state.");
check("Production smoke is checked", includesAll(production, ["prod-smoke-test.sh", "prod-smoke-test.ps1", "/health", "/system/status"]), "Production readiness should require smoke scripts.");
check("Backup readiness is checked", includesAll(backup, ["pg_dump", "pg_restore", "restore drill", "RDS snapshot"]), "Backup check should remain part of operations readiness.");
check("Release traceability is checked", includesAll(traceability, ["IMAGE_TAG", "Dockerhub", "Commits ahead of upstream", "sha-*"]), "Release traceability should keep rollback targets visible.");

check("Frontend exposes operations check", frontendPackage.scripts?.["check:operations"] === "node ../scripts/operations-readiness-check.mjs", "frontend/package.json should expose npm run check:operations.");
check("Root exposes operations check", rootPackage.scripts?.["check:operations"]?.includes("--prefix frontend"), "package.json should expose npm run check:operations.");
check("Release gate runs operations check", releaseGate.includes('"check:operations"'), "Release gate should fail if operations readiness is removed.");
check("Readiness includes operations check", readiness.includes("docs/drift-runbook.md") && readiness.includes('"check:operations"'), "Readiness should require the drift runbook and operations command.");
check("Evidence includes operations check", evidenceCheck.includes("scripts/operations-readiness-check.mjs") && releaseEvidence.includes("check:operations"), "Evidence should keep operations proof fresh.");
check("Roadmap points to operations check", roadmap.includes("npm run check:operations") && roadmap.includes("Driftcenter"), "Roadmap should explain the operations gate.");

const failures = checks.filter((result) => !result.ok);
for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks operations readiness check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} operations readiness check(s) failed.`);
  process.exit(1);
}

