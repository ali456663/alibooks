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

const docPath = "docs/forsta-riktiga-data.md";
const doc = exists(docPath) ? read(docPath) : "";
const useToday = read("docs/anvanda-idag-beslut.md");
const backupRunbook = read("docs/backup-restore-runbook.md");
const calculationDoc = read("docs/berakningskontroll.md");
const retentionDoc = read("docs/arkiv-och-andringsspar.md");
const handoffDoc = read("docs/redovisningspaket-och-konsultexport.md");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");
const mainSource = read("frontend/src/main.jsx");
const appSettings = read("backend/src/main/java/se/cloudshop/settings/AppSettings.java");
const settingsService = read("backend/src/main/java/se/cloudshop/settings/SettingsService.java");
const testDataController = read("backend/src/main/java/se/cloudshop/admin/TestDataController.java");
const orderController = read("backend/src/main/java/se/cloudshop/order/OrderController.java");
const accountingService = read("backend/src/main/java/se/cloudshop/accounting/AccountingService.java");
const accountingExportController = read("backend/src/main/java/se/cloudshop/accounting/AccountingExportController.java");
const customerController = read("backend/src/main/java/se/cloudshop/customer/CustomerController.java");
const secretCheck = read("scripts/secret-placeholder-check.mjs");

check("First real data document exists", exists(docPath), docPath);
check("Document explains purpose", includesAll(doc, ["riktiga kunder", "fakturor", "kvitton", "bankrader", "bokforingsposter"]), "The checklist should cover the first real data boundary.");
check("Document has exact local commands", includesAll(doc, ["npm run check:release:full", "npm run doctor", "npm run check:use-today", "npm run check:first-real-data", "npm run check:git -- --strict"]), "The user should get copy-pasteable local checks.");
check("Document requires backup and restore", includesAll(doc, ["backup", "Restore drill", "separat testdatabas"]), "Real data should not start before recovery proof.");
check("Document separates testdata from real data", includesAll(doc, ["Testdata", "riktiga kunder"]), "The user should not mix demo/test rows with real customers.");
check("Document covers company settings", includesAll(doc, ["Foretagsform", "Bokforingsmetod", "Momsperiod", "rakenskapsar", "F-skatt", "PlusGiro", "OCR"]), "Company setup should be checked before first invoice.");
check("Document blocks company-type changes after bookkeeping", doc.includes("Byt inte foretagsform efter att bokforing har skapats"), "Sole trader to limited company should be handled deliberately.");
check("Document covers number series", includesAll(doc, ["Fakturanummer", "Verifikationsnummer", "Krediteringar", "Bokio"]), "Numbering should be protected before real use.");
check("Document covers personal data", includesAll(doc, ["personnummer", "adress", "telefon", "e-post", "extern AI", "anonymiserad"]), "Personal data handling should be explicit.");
check("Document covers payment reconciliation", includesAll(doc, ["Stripe", "1580 Fordran hos Stripe", "1930", "Delbetalningar", "Swish"]), "Payment channels should be reconciled.");
check("Document covers accounting exports", includesAll(doc, ["SIE-export", "huvudbok", "saldobalans", "resultatrapport", "balansrapport", "momsrapport", "redovisningspaket"]), "Data should be exportable before trust.");
check("Document has stop signals", includesAll(doc, ["vit sida", "PostgreSQL", "verifikat", "momsrapporten", "fakturanummer", "API-nycklar"]), "First-use stop conditions should be visible.");
check("Use-today doc points to first real data", useToday.includes("check:first-real-data") && useToday.includes("forsta-riktiga-data.md"), "Daily use decision should point to the first-real-data gate.");
check("Backup runbook supports real data boundary", includesAll(backupRunbook, ["pg_dump", "pg_restore", "restore drill"]), "Backup docs should support first real data.");
check("Calculation docs support first real data boundary", includesAll(calculationDoc, ["faktura", "moms", "delbetalning", "Stripe", "verifikat"]), "Calculation docs should support first real data.");
check("Retention docs support first real data boundary", includesAll(retentionDoc, ["raderas", "rattelse", "arkiv"]), "Retention docs should support first real data.");
check("Handoff docs support accountant review", includesAll(handoffDoc, ["SIE", "huvudbok", "saldobalans", "redovisningskonsult"]), "Accountant handoff should be ready before serious use.");
check("Frontend exposes settings and controls", includesAll(mainSource, ["settings", "companyType", "accountingMethod", "vatReportingPeriod", "numberControl", "backup"]), "The app should expose setup and control views.");
check("Frontend exposes first real data gate", includesAll(mainSource, ["firstRealDataGateRows", "Forsta riktiga data", "first-real-data-panel", "first-real-data-card", "riktiga kunder, fakturor, kvitton, bankrader"]), "Startklar should show the first real data decision inside the app.");
check("Backend defaults include real invoice settings", includesAll(appSettings, ["companyType = \"SOLE_TRADER\"", "accountingMethod = \"INVOICE_METHOD\"", "vatReportingPeriod = \"QUARTERLY\"", "paymentTermsDays = 30", "fTaxApproved = true"]), "Default settings should be explicit.");
check("Backend guards company type after bookkeeping", includesAll(settingsService, ["Company type cannot be changed after bookkeeping has been created", "journalEntryRepository.count() == 0"]), "Changing entity type after bookkeeping should be blocked.");
check("Backend applies invoice payment settings", includesAll(orderController, ["setPaymentTermsDays", "setOcrNumber", "setPlusGiro", "setPaymentRecipient"]), "Invoices should inherit payment settings.");
check("Test-data reset is protected", includesAll(testDataController, ["@DeleteMapping(\"/test-data\")", "X-AliBooks-Confirm-Reset", "authHeader.requireValidToken", "APP_TEST_DATA_RESET_ENABLED=true"]), "Bulk test-data deletion should require auth and confirmation.");
check("Customer data is validated before save", includesAll(customerController, ["personalNumber", "email", "validate"]), "Customer personal data should be validated.");
check("Accounting service supports Stripe and voucher controls", includesAll(accountingService, ["createStripeExternalSaleEntries", "createStripePayoutEntry", "VoucherApprovalStats", "AccountSignRule"]), "Real payment/accounting controls should exist.");
check("Exports include SIE and control reports", includesAll(accountingExportController, ["/sie/export", "/general-ledger/export", "/trial-balance/export", "/voucher-control/export", "/accountant-package/export"]), "Critical exports should exist.");
check("Secret scan protects real API keys", includesAll(secretCheck, ["OpenRouter API key", "Google/Gemini API key", "Hugging Face token", "Stripe secret key"]), "Real keys should be blocked before GitHub.");
check("Frontend exposes first-real-data check", frontendPackage.scripts?.["check:first-real-data"] === "node ../scripts/first-real-data-check.mjs", "frontend/package.json should expose npm run check:first-real-data.");
check("Root exposes first-real-data check", rootPackage.scripts?.["check:first-real-data"] === "npm --prefix frontend run check:first-real-data --", "package.json should expose npm run check:first-real-data.");
check("Release gate runs first-real-data check", releaseGate.includes('"check:first-real-data"'), "Release gate should fail if first-real-data proof disappears.");
check("Readiness requires first-real-data check", includesAll(readiness, [docPath, "scripts/first-real-data-check.mjs", "check:first-real-data"]), "Readiness should protect the first-real-data gate.");
check("Evidence requires first-real-data check", evidence.includes("scripts/first-real-data-check.mjs") && evidence.includes("check:first-real-data"), "Evidence should keep first-real-data proof fresh.");
check("Release evidence documents first-real-data check", releaseEvidence.includes("check:first-real-data"), "Release evidence should record first-real-data proof.");
check("Roadmap documents first-real-data check", roadmap.includes("npm run check:first-real-data"), "Roadmap should show the first-real-data command.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks first real data check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} first real data check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Forsta riktiga data: OK lokalt nar release gate, backup, restore drill och manuell klickkontroll ar grona.");
