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

const packageJson = JSON.parse(read("frontend/package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const testProtocol = read("docs/mvp-testprotokoll.md");
const releaseEvidence = read("docs/release-evidence.md");
const apiContract = read("scripts/api-contract-check.mjs");
const runtimeSmoke = read("scripts/frontend-runtime-smoke.mjs");
const mainSource = read("frontend/src/main.jsx");

const requiredProtocolSections = [
  "## 1. Start och systemstatus",
  "## 2. Konto och inloggning",
  "## 3. Kunder",
  "## 4. Fakturor",
  "## 5. Betalningar",
  "## 6. Bokforing",
  "## 7. Kostnader och underlag",
  "## 8. Moms och rapporter",
  "## 9. Stripe utan riktig webhook",
  "## 10. Redo for moln"
];

const requiredAcceptanceTerms = [
  "Automatiskt bevis",
  "Manuellt kvar fore go-live",
  "Ej testat betyder",
  "npm run check:release",
  "npm run test:backend",
  "smoke:runtime",
  "check:api-contract",
  "check:backup",
  "check:prod",
  "check:go-live-risks"
];

const requiredManualTerms = [
  "kunduppgifter",
  "PDF",
  "Stripe",
  "underlag",
  "restore drill",
  "publik URL"
];

const requiredBackendTests = [
  "backend/src/test/java/se/cloudshop/CloudShopApplicationTests.java",
  "backend/src/test/java/se/cloudshop/auth/UserServiceTest.java",
  "backend/src/test/java/se/cloudshop/auth/JwtServiceTest.java",
  "backend/src/test/java/se/cloudshop/order/OrderControllerTest.java",
  "backend/src/test/java/se/cloudshop/accounting/AccountingServiceTest.java",
  "backend/src/test/java/se/cloudshop/config/DatabaseSchemaPatchTest.java",
  "backend/src/test/java/se/cloudshop/payment/StripePaymentServiceTest.java",
  "backend/src/test/java/se/cloudshop/expense/ExpenseControllerTest.java",
  "backend/src/test/java/se/cloudshop/invoice/InvoicePdfControllerTest.java"
];

const criticalApiContracts = [
  "POST\", \"/auth/register\"",
  "POST\", \"/auth/login\"",
  "GET\", \"/system/status\"",
  "POST\", \"/customers\"",
  "POST\", \"/invoices\"",
  "GET\", \"/journal-entries\"",
  "GET\", \"/vat-report\"",
  "GET\", \"/profit-and-loss\"",
  "GET\", \"/balance-report\""
];

check(
  "MVP test protocol covers full flow",
  includesAll(testProtocol, requiredProtocolSections),
  "Protocol should cover startup, auth, customers, invoices, payments, bookkeeping, expenses, reports, Stripe and cloud readiness."
);

check(
  "MVP protocol separates automated and manual evidence",
  includesAll(testProtocol, requiredAcceptanceTerms),
  "Protocol should explain what release gates prove automatically and what still needs manual review."
);

check(
  "Manual go-live gaps are explicit",
  includesAll(testProtocol, requiredManualTerms),
  "Protocol should keep visual/customer/PDF/Stripe/evidence/restore/public URL checks visible before production."
);

for (const file of requiredBackendTests) {
  check(`Backend acceptance test exists: ${path.basename(file)}`, exists(file), file);
}

check(
  "Critical API contracts are covered",
  includesAll(apiContract, criticalApiContracts),
  "API contract should cover auth, system status, customer, invoice, bookkeeping and reports."
);

check(
  "Runtime smoke guards against blank page",
  includesAll(runtimeSmoke, ["bodyTextLength", "rootChildCount", "scrollHeight", "alibooks-active-view"]),
  "Frontend smoke should catch blank page and broken reset states."
);

check(
  "Frontend exposes manual MVP evidence",
  includesAll(mainSource, ["mvpManualChecklistRows", "alibooks-mvp-manual-checks", "Manuella MVP-bevis", "downloadTestFlowCsv", "downloadGoLiveCsv"]),
  "Test flow and go-live should keep manual evidence for PDF, email, Stripe/bank, receipts, backup and public demo visible."
);

check(
  "Package exposes MVP acceptance check",
  packageJson.scripts?.["check:acceptance"] === "node ../scripts/mvp-acceptance-check.mjs",
  "frontend/package.json should expose npm run check:acceptance."
);

check(
  "Release gate runs MVP acceptance check",
  releaseGate.includes('"check:acceptance"'),
  "Release gate should fail when MVP acceptance evidence becomes stale."
);

check(
  "Release evidence mentions acceptance check",
  releaseEvidence.includes("check:acceptance"),
  "Release evidence should mention MVP acceptance coverage."
);

const failures = checks.filter((result) => !result.ok);
for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`MVP acceptance check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} MVP acceptance check(s) failed.`);
  process.exit(1);
}
