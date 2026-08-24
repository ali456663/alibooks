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

const docPath = "docs/anvandningsklar-mvp.md";
const doc = exists(docPath) ? read(docPath) : "";
const frontendPackage = JSON.parse(read("frontend/package.json"));
const rootPackage = JSON.parse(read("package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidenceCheck = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const riskRegister = read("docs/go-live-riskregister.md");
const roadmap = read("docs/roadmap-kvar.md");
const main = read("frontend/src/main.jsx");
const apiContract = read("scripts/api-contract-check.mjs");
const dataSafety = read("scripts/data-safety-check.mjs");
const manualGoLive = read("scripts/manual-go-live-evidence-check.mjs");
const speedledgerParity = read("scripts/speedledger-parity-check.mjs");
const backup = read("scripts/backup-readiness-check.mjs");
const ci = read("scripts/ci-pipeline-check.mjs");
const traceability = read("scripts/release-traceability-check.mjs");
const runtimeSmoke = read("scripts/frontend-runtime-smoke.mjs");

const stepRows = Array.from(doc.matchAll(/^\|\s*(\d{2})\s*\|/gm)).map((match) => match[1]);
const expectedSteps = Array.from({ length: 20 }, (_, index) => String(index + 1).padStart(2, "0"));

check("MVP use document exists", exists(docPath), docPath);
check("MVP use document has exactly 20 numbered rows", stepRows.length === 20 && expectedSteps.every((step) => stepRows.includes(step)), "Docs should keep the 20 professional MVP steps visible.");
check("MVP status levels are explicit", includesAll(doc, ["KLAR LOKALT", "MANUELLT BEVIS", "EXTERN BLOCKER"]), "The document should separate local proof, manual proof and external blockers.");
check("Local startup proof is covered", includesAll(doc, ["npm run doctor", "npm run smoke:runtime", "npm run check:views"]) && includesAll(runtimeSmoke, ["bodyTextLength", "rootChildCount"]), "Startup and blank-page protection should be part of MVP readiness.");
check("Authentication proof is covered", includesAll(doc, ["JWT", "npm run check:api-contract"]) && includesAll(apiContract, ["/auth/register", "/auth/login"]), "Auth and JWT API contracts should stay checked.");
check("Customer and invoice flow is covered", includesAll(doc, ["Kundregister", "Fakturering", "PDF", "Betalning"]) && includesAll(apiContract, ["/customers", "/invoices"]), "Core customer, invoice and payment flow should stay visible.");
check("Bookkeeping and destructive safety are covered", includesAll(doc, ["Verifikat balanserar", "destruktiva"]) && includesAll(dataSafety, ["DELETE endpoints discovered", "destructive action", "requireUnlockedAccountingDate"]), "Bookkeeping changes must remain protected.");
check("Bank CSV before real bank connection is covered", includesAll(doc, ["Bank-CSV", "riktig bankkoppling"]) && speedledgerParity.includes("Real bank connection is not overclaimed"), "MVP should use CSV/rules before claiming open banking.");
check("Evidence, backup and restore are covered", includesAll(doc, ["Underlag", "Backup", "Restore", "restore drill"]) && includesAll(backup, ["pg_dump", "pg_restore"]), "Receipts and backups must be recoverable before real data.");
check("VAT, reports and closing are covered", includesAll(doc, ["Moms", "Rapporter", "Bokslut", "NE/INK2/K2"]) && includesAll(main, ["vat-report", "balance-report", "profit-and-loss", "closingCenter"]), "Tax/report/closing work papers should stay visible.");
check("Payroll MVP is covered without overclaim", includesAll(doc, ["Lon MVP", "arbetsgivardeklarationsunderlag"]) && speedledgerParity.includes("Payroll MVP exists"), "Payroll should be work-paper MVP, not full payroll overclaim.");
check("Security and AI-safe mode are covered", includesAll(doc, ["Secrets", "JWT", "CORS", "AI-sakert lage", "personuppgifter"]) && includesAll(main, ["security", "AI-sakert lage"]), "Security and AI privacy rules should remain part of MVP.");
check("CI and traceability are covered", includesAll(doc, ["GitHub Actions", "Dockerhub", "IMAGE_TAG"]) && includesAll(ci, ["Maven tests", "release gate"]) && traceability.includes("Commits ahead of upstream"), "CI/CD and release traceability should remain visible.");
check("External blockers are not hidden", includesAll(doc, ["inte skarp produktionsklar", "extern leverantor", "PEPPOL", "arsredovisning"]) && includesAll(riskRegister, ["BLOCKERAR SKARP DRIFT", "skarp kunddata"]), "The MVP must not invite real production data before external proof.");
check("Startklar and risk checks are the final decision", includesAll(doc, ["npm run check:startklar", "npm run check:go-live-risks"]) && includesAll(main, ["goLiveUsageLevelText", "goLiveProductionRows"]), "The app should expose the local-vs-production decision.");
check("Frontend exposes MVP use command", frontendPackage.scripts?.["check:mvp-use"] === "node ../scripts/mvp-use-readiness-check.mjs", "frontend/package.json should expose npm run check:mvp-use.");
check("Root exposes MVP use command", rootPackage.scripts?.["check:mvp-use"]?.includes("--prefix frontend"), "package.json should expose npm run check:mvp-use.");
check("Release gate runs MVP use check", releaseGate.includes('"check:mvp-use"'), "Release gate should fail if the 20-step MVP use contract is removed.");
check("Readiness includes MVP use check", readiness.includes("docs/anvandningsklar-mvp.md") && readiness.includes('"check:mvp-use"'), "Readiness should require the MVP use document and command.");
check("Evidence keeps MVP use proof fresh", evidenceCheck.includes("scripts/mvp-use-readiness-check.mjs") && releaseEvidence.includes("check:mvp-use") && roadmap.includes("npm run check:mvp-use"), "Evidence and roadmap should mention the MVP use gate.");

const failures = checks.filter((result) => !result.ok);
for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks MVP use readiness check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} MVP use readiness check(s) failed.`);
  process.exit(1);
}
