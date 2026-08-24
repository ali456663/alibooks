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

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

const docPath = "docs/pilotdrift-mvp.md";
const doc = exists(docPath) ? read(docPath) : "";
const mvpUseDoc = read("docs/anvandningsklar-mvp.md");
const useTodayDoc = read("docs/anvanda-idag-beslut.md");
const firstRealDataDoc = read("docs/forsta-riktiga-data.md");
const envDoc = read("docs/miljovariabler-go-live.md");
const backupRunbook = read("docs/backup-restore-runbook.md");
const handoffDoc = read("docs/redovisningspaket-och-konsultexport.md");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const mainSource = read("frontend/src/main.jsx");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const frontendPackage = json("frontend/package.json");
const rootPackage = json("package.json");

const pilotCommands = [
  "npm run check:release",
  "npm run check:first-real-data",
  "npm run check:env-go-live",
  "npm run check:calculations",
  "npm run check:retention",
  "npm run check:audit-integrity",
  "npm run check:git -- --strict"
];

check("Pilot document exists", exists(docPath), docPath);
check("Pilot document defines pilot boundary", includesAll(doc, ["Pilotdrift", "begransad riktig arbetsdata", "inte samma sak som skarp molndrift"]), "Pilot should not overclaim production.");
check("Pilot document lists required commands", includesAll(doc, pilotCommands), "Pilot should depend on existing local gates.");
check("Pilot document requires backup and restore drill", includesAll(doc, ["Backup ar skapad", "Restore drill", "separat testdatabas"]), "Pilot should require recovery proof.");
check("Pilot document requires manual click proof", includesAll(doc, ["Faktura-PDF", "e-post", "betalning", "underlag", "export"]), "Pilot should require manual flow proof.");
check("Pilot document limits first week", includesAll(doc, ["max 1-3 riktiga kunder", "fa fakturor", "bank-CSV", "daglig backup"]), "Pilot should limit blast radius.");
check("Pilot document has daily routine", includesAll(doc, ["npm run doctor", "Startklar", "Redovisningskontroll", "Momsrapport", "Underlag"]), "Daily pilot routine should be concrete.");
check("Pilot document has stop signals", includesAll(doc, ["vit sida", "PostgreSQL", "fakturanummer", "debet", "kredit", "anonym AI-export", "Git"]), "Pilot stop signs should be explicit.");
check("Pilot document has after-week review", includesAll(doc, ["resultatrapport", "balansrapport", "huvudbok", "momsrapport", "redovisningspaket"]), "Pilot should end with review and exports.");
check("Pilot document has command", doc.includes("npm run check:pilot"), "Pilot command should be visible.");
check("MVP use doc points to pilot", mvpUseDoc.includes("check:pilot") && mvpUseDoc.includes("pilotdrift"), "MVP decision should mention pilot before broader use.");
check("Use-today doc points to pilot", useTodayDoc.includes("check:pilot"), "Daily use decision should include pilot check.");
check("First real data doc supports pilot", includesAll(firstRealDataDoc, ["check:first-real-data", "backup", "restore drill"]), "First real data gate should support pilot.");
check("Env doc supports pilot", includesAll(envDoc, ["check:env-go-live", "JWT_SECRET", "APP_CORS_ALLOWED_ORIGINS"]), "Env gate should support pilot.");
check("Backup runbook supports pilot", includesAll(backupRunbook, ["pg_dump", "pg_restore", "restore drill"]), "Backup runbook should support pilot.");
check("Handoff doc supports pilot review", includesAll(handoffDoc, ["SIE", "huvudbok", "redovisningskonsult"]), "Pilot review should be exportable to accountant.");
check("Frontend shows pilot in Startklar", includesAll(mainSource, ["Pilotvecka", "Pilotdrift", "pilot"]), "Startklar should show pilot readiness.");
check("Frontend pilot uses backup and calculation signals", includesAll(mainSource, ["backupValidation?.ok", "calculationCriticalCount", "unbalancedJournalGroups"]), "Pilot UI should rely on real safety signals.");
check("Frontend exposes pilot check", frontendPackage.scripts?.["check:pilot"] === "node ../scripts/pilot-readiness-check.mjs", "frontend/package.json should expose npm run check:pilot.");
check("Root exposes pilot check", rootPackage.scripts?.["check:pilot"] === "npm --prefix frontend run check:pilot --", "package.json should expose npm run check:pilot.");
check("Release gate runs pilot check", releaseGate.includes('"check:pilot"'), "Release gate should fail if pilot proof disappears.");
check("Readiness requires pilot check", includesAll(readiness, [docPath, "scripts/pilot-readiness-check.mjs", "check:pilot"]), "Readiness should protect pilot docs and script.");
check("Evidence requires pilot check", includesAll(evidence, ["scripts/pilot-readiness-check.mjs", "check:pilot"]), "Evidence should keep pilot proof fresh.");
check("Release evidence documents pilot check", releaseEvidence.includes("check:pilot"), "Release evidence should record pilot proof.");
check("Roadmap documents pilot check", roadmap.includes("npm run check:pilot"), "Roadmap should show pilot command.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks pilot readiness check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} pilot readiness check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Pilotdrift: OK lokalt nar release gate, backup, restore drill och manuell klickkontroll ar grona.");
