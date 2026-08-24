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

const docPath = "docs/periodstangning-och-bokslutskontroll.md";
const doc = exists(docPath) ? read(docPath) : "";
const service = read("backend/src/main/java/se/cloudshop/accounting/AccountingPeriodLockService.java");
const controller = read("backend/src/main/java/se/cloudshop/accounting/AccountingPeriodLockController.java");
const resultRecord = read("backend/src/main/java/se/cloudshop/accounting/PeriodCloseCheckResult.java");
const serviceTest = read("backend/src/test/java/se/cloudshop/accounting/AccountingPeriodLockServiceTest.java");
const controllerTest = read("backend/src/test/java/se/cloudshop/accounting/AccountingPeriodLockControllerTest.java");
const frontend = read("frontend/src/main.jsx");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidenceCheck = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");

check(
  "Period close document exists",
  includesAll(doc, ["Periodstangning", "periodlasning", "Stoppsignal", "check:period-close"]),
  "docs/periodstangning-och-bokslutskontroll.md should explain period close controls and stop signals."
);

check(
  "Period close command exists in root package",
  rootPackage.scripts?.["check:period-close"] === "npm --prefix frontend run check:period-close --",
  "Root package should expose npm run check:period-close."
);

check(
  "Period close command exists in frontend package",
  frontendPackage.scripts?.["check:period-close"] === "node ../scripts/period-close-readiness-check.mjs",
  "Frontend package should expose npm run check:period-close."
);

check(
  "Release gate runs period close check",
  releaseGate.includes('"check:period-close"'),
  "Release gate should fail if period close readiness proof disappears."
);

check(
  "Readiness requires period close artifacts",
  includesAll(readiness, [docPath, "scripts/period-close-readiness-check.mjs", "check:period-close"]),
  "Readiness should require period close docs, script and command."
);

check(
  "Evidence check counts period close output",
  includesAll(evidenceCheck, ["period-close-readiness-check.mjs", "AliBooks period close readiness check"]),
  "MVP evidence should verify the current period close check count."
);

check(
  "Release evidence documents period close proof",
  releaseEvidence.includes("check:period-close") && releaseEvidence.includes("periodstangning"),
  "Release evidence should keep period close proof visible."
);

check(
  "Roadmap documents period close command",
  roadmap.includes("npm run check:period-close") && roadmap.includes("periodstangning"),
  "Roadmap should tell the user to run the period close gate."
);

check(
  "Backend has period close endpoints",
  includesAll(controller, [
    '@GetMapping("/accounting-period/close-check")',
    '@GetMapping("/accounting-period/close-check/export")',
    '@PostMapping("/accounting-period/close")',
    "authHeader.requireValidToken(authorizationHeader)"
  ]),
  "Period close check, export and close endpoints should require JWT."
);

check(
  "Period close export includes proof values",
  includesAll(controller, ["Periodstampel", "Slutlig kedjekod", "SIE redo", "Sena verifikat", "period_close_check_exported"]),
  "Period close export should include integrity proof, SIE readiness, late vouchers and audit event."
);

check(
  "Service validates close date",
  includesAll(service, ["requireValidLockDate", "Locked-through date is required", "framtida datum"]),
  "Period close should reject missing or future lock dates."
);

check(
  "Service blocks unbalanced accounting",
  includesAll(service, ["unbalancedVoucherCount", "Balansrapporten har differens", "Saldobalansen har differens"]),
  "Period close should block unbalanced vouchers and report differences."
);

check(
  "Service blocks voucher and VAT critical issues",
  includesAll(service, ["createVoucherControlReport", "createVatControlReport", "kritiska verifikationspunkter", "kritiska momspunkter"]),
  "Period close should block critical voucher and VAT control issues."
);

check(
  "Service blocks incomplete VAT proof chain",
  includesAll(service, ["createVatFilingProofReportsThroughDate", "vatProofIncompleteCount", "saknar komplett beviskedja"]),
  "Period close should block VAT filings without complete settlement/payment proof."
);

check(
  "Service blocks journal integrity problems",
  includesAll(service, ["createJournalIntegrityReport", "journalIntegrityReport.difference", "missingEvidenceCount", "periodFingerprint", "finalChainHash"]),
  "Period close should include journal integrity difference, source links and hash proof."
);

check(
  "Service checks bank reconciliation",
  includesAll(service, ["bankReconciliationService.createReport", "criticalIssueCount", "bankavstamningspunkter", "bankavstamningsvarningar"]),
  "Period close should include bank reconciliation blockers and warnings."
);

check(
  "Service checks invoice and receipt readiness",
  includesAll(service, ["DRAFT", "fakturautkast", "hasReceipt", "kostnader saknar kvitto"]),
  "Period close should block draft invoices and expenses without receipts."
);

check(
  "Service checks voucher approval",
  includesAll(service, ["voucherApprovalRepository.findAll", "voucherMissingApprovalCount", "voucherPendingApprovalCount", "voucherBlockedApprovalCount"]),
  "Period close should block missing, pending and blocked voucher approvals."
);

check(
  "Service checks open receivables and payables",
  includesAll(service, ["receivablesReportService.createAgingReport", "payablesReportService.createAgingReport", "oppna kundfakturor", "oppna leverantorsfakturor"]),
  "Period close should warn about open customer and supplier balances."
);

check(
  "Service warns about late bookings",
  includesAll(service, ["calculateLateBookingStats", "ChronoUnit.DAYS.between", "mer an 35 dagar"]),
  "Period close should warn when vouchers are booked late."
);

check(
  "Closing writes audit event and lock date",
  includesAll(service, ["settingsService.lockAccountingThroughDate", "auditService.record", "period_locked", "Accounting period locked"]),
  "Actual period close should lock settings and record audit proof."
);

check(
  "Result record exposes close controls",
  includesAll(resultRecord, [
    "readyToLock",
    "blockers",
    "warnings",
    "periodFingerprint",
    "finalChainHash",
    "bankReconciliationDifference",
    "voucherMissingApprovalCount",
    "lateBookedVouchers"
  ]),
  "PeriodCloseCheckResult should expose readiness, blockers, hashes, bank, approval and late booking data."
);

check(
  "Frontend loads backend period check",
  includesAll(frontend, ["loadBackendPeriodCloseCheck", "/accounting-period/close-check", "backendPeriodCloseCheck"]),
  "Frontend should load backend period close checks."
);

check(
  "Frontend exports backend period proof",
  includesAll(frontend, ["/accounting-period/close-check/export", "periodlasningskontroll-backend", "backendPeriodCloseCheck"]),
  "Frontend should export backend period close proof."
);

check(
  "Frontend shows period close risks",
  includesAll(frontend, ["backendPeriodCloseBlockers", "backendPeriodCloseWarnings", "voucherMissingApprovalCount", "bankReconciliationDifference", "periodFingerprint"]),
  "Frontend should show blockers, warnings, approvals, bank difference and hash proof."
);

check(
  "Service tests cover critical control blockers",
  includesAll(serviceTest, ["checkPeriodBlocksLockWhenProfessionalControlsHaveCriticalIssues", "checkPeriodBlocksLockWhenVatFilingProofChainIsIncomplete", "checkPeriodBlocksLockWhenJournalIntegrityHasProblems"]),
  "Service tests should cover core close blockers."
);

check(
  "Service tests cover bank and approval blockers",
  includesAll(serviceTest, ["checkPeriodBlocksLockWhenBankReconciliationHasCriticalDifference", "checkPeriodBlocksLockWhenVouchersAreNotApproved"]),
  "Service tests should cover bank reconciliation and voucher approval."
);

check(
  "Service tests cover receivables and payables warnings",
  includesAll(serviceTest, ["checkPeriodWarnsWhenReceivablesRemainOpen", "checkPeriodWarnsWhenPayablesRemainOpen"]),
  "Service tests should cover open receivables and payables warnings."
);

check(
  "Service tests cover late booking warnings",
  serviceTest.includes("checkPeriodWarnsWhenVoucherIsBookedLongAfterVoucherDate"),
  "Service tests should cover late booking warnings."
);

check(
  "Controller test covers export audit evidence",
  includesAll(controllerTest, ["closeCheckExportRecordsAuditEventWithRiskCounts", "period_close_check_exported", "AliBooks periodlasningskontroll"]),
  "Controller tests should prove export evidence and audit event."
);

const failed = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks period close readiness check: ${checks.length - failed.length}/${checks.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} period close readiness check(s) failed.`);
  process.exit(1);
}
