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

const docPath = "docs/speedledger-paritet.md";
const doc = exists(docPath) ? read(docPath) : "";
const main = read("frontend/src/main.jsx");
const backend = [
  "backend/src/main/java/se/cloudshop/bank/BankReconciliationController.java",
  "backend/src/main/java/se/cloudshop/accounting/AccountingExportController.java",
  "backend/src/main/java/se/cloudshop/payroll/PayrollEmailController.java",
  "backend/src/main/java/se/cloudshop/payment/StripePaymentService.java",
  "backend/src/main/java/se/cloudshop/config/DatabaseSchemaPatch.java"
].map(read).join("\n");
const packageJson = JSON.parse(read("frontend/package.json"));
const rootPackageJson = JSON.parse(read("package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const roadmap = read("docs/roadmap-kvar.md");
const releaseEvidence = read("docs/release-evidence.md");

check("Parity document exists", exists(docPath), docPath);
check("Parity command exists in frontend", packageJson.scripts?.["check:speedledger-parity"] === "node ../scripts/speedledger-parity-check.mjs", "frontend/package.json should expose npm run check:speedledger-parity.");
check("Parity command exists in root", rootPackageJson.scripts?.["check:speedledger-parity"]?.includes("--prefix frontend"), "package.json should expose npm run check:speedledger-parity.");
check("Release gate runs parity check", releaseGate.includes('"check:speedledger-parity"'), "Release gate should fail if feature parity documentation becomes stale.");
check("Release evidence mentions parity check", releaseEvidence.includes("check:speedledger-parity"), "Release evidence should include the SpeedLedger-style parity guard.");

check("Accounting program support exists", includesAll(main, ["bookkeeping", "accounts", "journal"]) && backend.includes("SIE"), "AliBooks should expose bookkeeping, chart of accounts and export.");
check("Invoicing support exists", includesAll(main, ["invoices", "customers", "services", "quotes", "PDF"]), "AliBooks should expose customers, services, invoices, quotes and PDF.");
check("Autokontering MVP exists", includesAll(main, ["bankImportRules", "bankImportExpenseRule", "bank-reconciliations"]), "AliBooks should have bank CSV rules and reconciliation before real bank connection.");
check("Bank reconciliation backend exists", includesAll(backend, ["BankReconciliationController", "/bank-reconciliations", "bank_reconciliation_entries"]), "Bank reconciliation should persist and export reviewed bank rows.");
check("VAT report exists", includesAll(main, ["vatReport", "vat-report", "Momsrapport"]), "AliBooks should expose VAT report and VAT reconciliation.");
check("Digital evidence support exists", includesAll(main, ["uploaded", "underlag", "kvitto/PDF", "receipt"]), "AliBooks should expose uploaded evidence and receipt/PDF workflows.");
check("Balance report exists", includesAll(main, ["balance-report", "Balansrapport"]), "AliBooks should expose balance report.");
check("Profit report exists", includesAll(main, ["profit-and-loss", "Resultatrapport"]), "AliBooks should expose profit and loss report.");
check("Tax account support exists", includesAll(main, ["Skattekonto", "taxAccount", "1630"]), "AliBooks should expose tax account reconciliation/work papers.");
check("Payroll MVP exists", includesAll(main, ["payroll", "Arbetsgivardeklaration"]) && backend.includes("PayrollEmailController"), "AliBooks should expose payroll work papers and payslip email support.");
check("Closing support exists", includesAll(main, ["closingCenter", "Arsbokslut", "bokslut"]), "AliBooks should expose annual/monthly closing controls.");
check("Accountant handoff exists", includesAll(main, ["accountantHandoff", "Redovisningspaket"]) && backend.includes("AccountingExportController"), "AliBooks should support accountant export/handoff.");
check("NE work paper exists", includesAll(main, ["NE kontrollunderlag", "Preliminar resultatgrund"]), "AliBooks should expose NE work paper for sole trader mode.");
check("RUT ROT work paper exists", includesAll(main, ["RUT/ROT", "ansokningsunderlag"]), "AliBooks should expose RUT/ROT working evidence for service businesses.");

check("Real bank connection is not overclaimed", includesAll(doc, ["Bankkoppling", "Kraver extern integration", "open-banking"]) && roadmap.includes("riktig bankkoppling"), "Docs should say real bank connection requires external integration.");
check("PEPPOL e-invoice is not overclaimed", includesAll(doc, ["E-faktura / PEPPOL", "Kraver extern integration", "extern operator"]) && roadmap.includes("e-faktura"), "Docs should say e-invoice/PEPPOL requires an external operator.");
check("Annual report is not overclaimed", includesAll(doc, ["Arsredovisning", "Komplett juridisk arsredovisning", "Bolagsverket"]), "Docs should separate work papers from final legal annual report.");
check("NE filing is not overclaimed", includesAll(doc, ["NE-bilaga", "Lokal MVP", "Skattemassiga justeringar"]), "Docs should separate NE work paper from final tax filing.");
check("Daily closing is not overclaimed", includesAll(doc, ["E-dagsavslut", "Kraver extern integration", "kassa/POS"]), "Docs should separate daily routine from real POS daily close integration.");
check("Factoring is not overclaimed", includesAll(doc, ["Salj faktura", "factoring", "extern finanspartner"]), "Docs should separate receivables follow-up from invoice sale/factoring.");
check("Fullservice is not overclaimed", includesAll(doc, ["Fullservice", "redovisningskonsult", "ersatter inte"]), "Docs should say AliBooks does not replace fullservice advice.");
check("Feature status levels are explicit", includesAll(doc, ["Klar i AliBooks", "Lokal MVP", "Kraver extern integration", "Senare"]), "Docs should classify every large feature by readiness.");
check("MVP priority order exists", includesAll(doc, ["MVP-beslut", "Bank-CSV", "Stripe/SMTP", "Redovisningspaket"]), "Docs should guide what to build before external integrations.");

const failed = checks.filter((result) => !result.ok);
for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`SpeedLedger-style feature parity check: ${checks.length - failed.length}/${checks.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} feature parity check(s) failed.`);
  process.exit(1);
}
