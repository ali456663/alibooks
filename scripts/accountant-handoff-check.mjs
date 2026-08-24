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

function includesAll(source, terms) {
  return terms.every((term) => source.includes(term));
}

const docPath = "docs/redovisningspaket-och-konsultexport.md";
const doc = exists(docPath) ? read(docPath) : "";
const controller = read("backend/src/main/java/se/cloudshop/accounting/AccountingExportController.java");
const accountingController = read("backend/src/main/java/se/cloudshop/accounting/AccountingController.java");
const service = read("backend/src/main/java/se/cloudshop/accounting/AccountingService.java");
const report = read("backend/src/main/java/se/cloudshop/accounting/AccountantPackageReport.java");
const exportTest = read("backend/src/test/java/se/cloudshop/accounting/AccountingExportControllerTest.java");
const serviceTest = read("backend/src/test/java/se/cloudshop/accounting/AccountingServiceTest.java");
const frontend = read("frontend/src/main.jsx");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidenceCheck = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const riskRegister = read("docs/go-live-riskregister.md");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");

check(
  "Handoff document exists",
  includesAll(doc, ["Redovisningspaket", "SIE", "Saker delning", "check:handoff"]),
  "docs/redovisningspaket-och-konsultexport.md should describe accountant handoff, SIE and safe sharing."
);

check(
  "Handoff command exists in root package",
  rootPackage.scripts?.["check:handoff"] === "npm --prefix frontend run check:handoff --",
  "Root package should expose npm run check:handoff."
);

check(
  "Handoff command exists in frontend package",
  frontendPackage.scripts?.["check:handoff"] === "node ../scripts/accountant-handoff-check.mjs",
  "Frontend package should expose npm run check:handoff."
);

check(
  "Release gate runs handoff check",
  releaseGate.includes('"check:handoff"'),
  "Release gate should fail if accountant handoff proof disappears."
);

check(
  "Readiness requires handoff artifacts",
  includesAll(readiness, [docPath, "scripts/accountant-handoff-check.mjs", "check:handoff"]),
  "Readiness should require handoff docs, script and command."
);

check(
  "Evidence check counts handoff output",
  includesAll(evidenceCheck, ["accountant-handoff-check.mjs", "AliBooks accountant handoff check"]),
  "MVP evidence should verify the current handoff check count."
);

check(
  "Release evidence documents handoff proof",
  releaseEvidence.includes("check:handoff") && releaseEvidence.includes("redovisningspaket"),
  "Release evidence should keep accountant handoff proof visible."
);

check(
  "Roadmap documents handoff command",
  roadmap.includes("npm run check:handoff") && roadmap.includes("redovisningspaket"),
  "Roadmap should tell the user to run the handoff gate."
);

check(
  "Risk register requires accountant export proof",
  includesAll(riskRegister, ["redovisningskonsult", "export", "En export kan lamnas"]),
  "Go-live risks should keep accountant export as a manual proof."
);

check(
  "Backend exposes accountant package report endpoint",
  includesAll(accountingController, ['@GetMapping("/accountant-package")', "createAccountantPackageReport", "authHeader.requireValidToken"]),
  "Backend should expose authenticated accountant package report data."
);

check(
  "Backend exports accountant package",
  includesAll(controller, ['@GetMapping("/accountant-package/export")', "AliBooks redovisningspaket", "redovisningspaket-backend.csv", "accountant_package_exported"]),
  "Backend should export accountant package CSV and audit the export."
);

check(
  "Backend exports SIE and SIE receipt",
  includesAll(controller, ['@GetMapping("/sie/export")', '@GetMapping("/sie/receipt/export")', "alibooks-sie.se", "sie-export-kvittens.csv", "SIE file exported with control hash"]),
  "Backend should export SIE and a receipt with a control hash."
);

check(
  "Backend exports archive year control",
  includesAll(controller, ['@GetMapping("/archive-year/export")', "AliBooks arsarkivkontroll", "Periodstampel", "Slutlig kedjekod"]),
  "Backend should export annual archive control with hash proof."
);

check(
  "Backend exports system documentation",
  includesAll(controller, ['@GetMapping("/system-documentation/export")', "AliBooks systemdokumentation", "Automatiska floden", "Kontroller"]),
  "Backend should export system documentation for accountant review."
);

check(
  "Backend exports core accounting reports",
  includesAll(controller, [
    '@GetMapping("/journal-entries/export")',
    '@GetMapping("/profit-and-loss/export")',
    '@GetMapping("/balance-report/export")',
    '@GetMapping("/trial-balance/export")',
    '@GetMapping("/general-ledger/export")'
  ]),
  "Backend should export journal, profit/loss, balance, trial balance and general ledger."
);

check(
  "Backend exports control reports",
  includesAll(controller, [
    '@GetMapping("/vat-control/export")',
    '@GetMapping("/voucher-control/export")',
    '@GetMapping("/journal-integrity/export")',
    '@GetMapping("/account-sign-control/export")'
  ]),
  "Backend should export VAT, voucher, journal integrity and account sign controls."
);

check(
  "Exports require JWT",
  (controller.match(/authHeader\.requireValidToken\(authorizationHeader\)/g) || []).length >= 12,
  "Accounting exports should require a valid JWT."
);

check(
  "Exports are audited",
  includesAll(controller, ["handoff_export", "auditService.record", "journal_entries_exported", "balance_report_exported", "journal_integrity_exported"]),
  "Key handoff exports should write audit events."
);

check(
  "Accountant package report includes professional risk totals",
  includesAll(report, [
    "balanceDifference",
    "trialBalanceDifference",
    "voucherCriticalIssues",
    "vatCriticalIssues",
    "bankReconciliationDifference",
    "journalIntegrityPeriodFingerprint",
    "voucherMissingApprovalCount",
    "sieExportReady"
  ]),
  "AccountantPackageReport should include balance, VAT, bank, integrity, approval and SIE readiness."
);

check(
  "Service builds accountant package from reports",
  includesAll(service, [
    "createAccountantPackageReport",
    "createProfitAndLossReport",
    "createBalanceReport",
    "createTrialBalanceReport",
    "createVoucherControlReport",
    "createVatControlReport",
    "createJournalIntegrityReport",
    "createAccountSignControlReport"
  ]),
  "AccountingService should compose handoff from core reports and controls."
);

check(
  "Service includes bank and reskontra reports",
  includesAll(service, ["bankReconciliationService.createReport", "receivablesReportService.createAgingReport", "payablesReportService.createAgingReport"]),
  "Accountant package should include bank, customer receivables and supplier payables."
);

check(
  "Service includes SIE readiness and recommended exports",
  includesAll(service, ["SIE kan exporteras", "/sie/export och /sie/receipt/export", "/accountant-package/export"]),
  "Accountant package should explain SIE readiness and recommended exports."
);

check(
  "Frontend exposes accountant handoff view",
  includesAll(frontend, ["accountantHandoff", "Redovisningspaket", "downloadBackendAccountantPackageCsv", "accountantHandoffItems"]),
  "Frontend should expose the accountant handoff page and backend export."
);

check(
  "Frontend exposes safe-share list",
  includesAll(frontend, ["accountantSafeShareItems", "accountantSafeShareSensitiveCount", "Skicka bara till redovisningskonsult/revisor"]),
  "Frontend should show sensitive/personal-data sharing guidance."
);

check(
  "Frontend AI assistant can explain handoff",
  includesAll(frontend, ["redovisningskonsult", "overlamning", "accountantHandoffStatusText"]),
  "AI assistant should route accountant handoff questions to the right view."
);

check(
  "Backend tests cover core exports",
  includesAll(exportTest, ["journalEntriesExportRecordsAuditEventWithEntryCount", "profitAndLossExportRecordsAuditEventWithResult", "balanceReportExportRecordsAuditEventWithDifference"]),
  "Backend tests should cover main report exports and audit events."
);

check(
  "Backend tests cover SIE export receipt",
  includesAll(exportTest, ["sieExportRecordsAuditEventWithControlHash", "CONTROLHASH"]),
  "Backend tests should prove SIE export audit uses a control hash."
);

check(
  "Backend tests cover archive approval counts",
  includesAll(exportTest, ["archiveYearExportIncludesVoucherApprovalCounts", "Verifikat utan attest"]),
  "Backend tests should prove archive export includes voucher approval counts."
);

check(
  "Backend service tests cover accountant package",
  includesAll(serviceTest, ["createAccountantPackageReport", "SIE", "Bankavstamning", "Kundreskontra", "Leverantorsreskontra"]),
  "Backend service tests should cover accountant package content."
);

const failed = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks accountant handoff check: ${checks.length - failed.length}/${checks.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} accountant handoff check(s) failed.`);
  process.exit(1);
}
