import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const backendSrc = path.join(repoRoot, "backend", "src", "main", "java", "se", "cloudshop");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, terms) {
  return terms.every((term) => source.includes(term));
}

function javaFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      return javaFiles(fullPath);
    }
    return entry.isFile() && entry.name.endsWith(".java") ? [fullPath] : [];
  });
}

function findMatchingBrace(source, openIndex) {
  let depth = 0;
  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === "{") {
      depth += 1;
    }
    if (source[index] === "}") {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }
  return -1;
}

function deleteMethods(filePath) {
  const source = readFileSync(filePath, "utf8");
  const methods = [];
  const pattern = /@DeleteMapping\(([^)]*)\)/g;
  let match;
  while ((match = pattern.exec(source)) !== null) {
    const bodyStart = source.indexOf("{", pattern.lastIndex);
    const bodyEnd = bodyStart < 0 ? -1 : findMatchingBrace(source, bodyStart);
    methods.push({
      filePath,
      route: match[1].replaceAll('"', "").trim(),
      source,
      body: bodyStart >= 0 && bodyEnd >= 0 ? source.slice(bodyStart, bodyEnd + 1) : ""
    });
  }
  return methods;
}

const allDeletes = javaFiles(backendSrc).flatMap(deleteMethods);
const deletesByRoute = new Map(allDeletes.map((method) => [method.route, method]));

function method(route) {
  return deletesByRoute.get(route);
}

function methodHas(route, terms) {
  const target = method(route);
  return Boolean(target && includesAll(target.body, terms));
}

const policy = read("docs/arkiv-och-andringsspar.md");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidenceCheck = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const rootPackage = JSON.parse(read("package.json"));
const frontendPackage = JSON.parse(read("frontend/package.json"));
const orderController = read("backend/src/main/java/se/cloudshop/order/OrderController.java");
const customerController = read("backend/src/main/java/se/cloudshop/customer/CustomerController.java");
const supplierController = read("backend/src/main/java/se/cloudshop/supplier/SupplierController.java");
const expenseController = read("backend/src/main/java/se/cloudshop/expense/ExpenseController.java");
const settingsService = read("backend/src/main/java/se/cloudshop/settings/SettingsService.java");
const accountingService = read("backend/src/main/java/se/cloudshop/accounting/AccountingService.java");
const mainSource = read("frontend/src/main.jsx");

check(
  "Retention policy exists",
  includesAll(policy, ["Fakturor", "verifikat", "Hard delete", "revisionsspar", "Periodlasta"]),
  "docs/arkiv-och-andringsspar.md should explain accounting retention and correction rules."
);

check(
  "Retention command exists in root package",
  rootPackage.scripts?.["check:retention"] === "npm --prefix frontend run check:retention --",
  "Root package should expose npm run check:retention."
);

check(
  "Retention command exists in frontend package",
  frontendPackage.scripts?.["check:retention"] === "node ../scripts/retention-immutability-check.mjs",
  "Frontend package should expose npm run check:retention."
);

check(
  "Release gate runs retention check",
  releaseGate.includes('"check:retention"'),
  "Release gate should fail if immutability and archive controls disappear."
);

check(
  "Readiness requires retention artifacts",
  includesAll(readiness, ["docs/arkiv-och-andringsspar.md", "scripts/retention-immutability-check.mjs", "check:retention"]),
  "Readiness should require retention policy, script and command."
);

check(
  "Evidence check counts retention output",
  includesAll(evidenceCheck, ["retention-immutability-check.mjs", "AliBooks retention and immutability check"]),
  "MVP evidence should verify the current retention check count."
);

check(
  "Release evidence documents retention proof",
  releaseEvidence.includes("check:retention") && releaseEvidence.includes("arkiv") && releaseEvidence.includes("hard delete"),
  "Release evidence should keep immutability proof visible."
);

check(
  "Roadmap documents retention command",
  roadmap.includes("npm run check:retention") && roadmap.includes("arkiv"),
  "Roadmap should tell the user to run the retention gate."
);

check(
  "DELETE endpoints are known and authenticated",
  allDeletes.length > 0 && allDeletes.every((entry) => entry.body.includes("authHeader.requireValidToken(authorizationHeader)")),
  `Found ${allDeletes.length} DELETE endpoint(s); each must require JWT.`
);

check(
  "Invoices are immutable after draft",
  methodHas("/invoices/{id}", ["Only draft invoices can be deleted", "Use a credit invoice or correction", "requireUnlockedAccountingDate(order.getInvoiceDate())", "draft_deleted"]),
  "Sent, paid or booked invoices should be corrected or credited, not hard-deleted."
);

check(
  "Credit invoice flow exists for booked invoices",
  includesAll(orderController, ["@PostMapping(\"/invoices/{id}/credit\")", "createCreditInvoiceEntries", "setCreditInvoice(true)", "CREDITED"]),
  "Booked customer invoices should have a credit invoice path."
);

check(
  "Customers with invoices are archived instead of deleted",
  methodHas("/customers/{id}", ["orderRepository.findByCustomer(customer)", "should be archived instead of deleted", "customer_deleted"])
    && includesAll(customerController, ["@PostMapping(\"/customers/{id}/archive\")", "customer_archived", "@PostMapping(\"/customers/{id}/restore\")"]),
  "Customers connected to invoices should keep history through archive/restore."
);

check(
  "Supplier invoices are immutable after booking or payment",
  methodHas("/supplier-invoices/{id}", ["hasSupplierInvoiceEntries(invoice)", "invoice.getPaidAmount() > 0", "Create a correction or cancellation instead of deleting it", "requireUnlockedAccountingDate(invoice.getInvoiceDate())"]),
  "Booked or paid supplier invoices should not be hard-deleted."
);

check(
  "Supplier invoice cancellation creates correction path",
  includesAll(supplierController, ["@PostMapping(\"/supplier-invoices/{id}/cancel\")", "createSupplierInvoiceCancellationEntries", "correctionVoucherNumber", "cancelled"]),
  "Supplier invoices need a controlled cancellation/correction route."
);

check(
  "Receipts are append-only evidence",
  includesAll(expenseController, ["Receipt already exists", "instead of replacing archived evidence", "SHA-256", "Receipt file can be max 10 MB", "PDF, JPG, PNG or WebP"]),
  "Uploaded receipt evidence should not be silently replaced."
);

check(
  "Journal entries have no DELETE endpoint",
  !allDeletes.some((entry) => entry.route.toLowerCase().includes("journal")),
  "Bookkeeping rows should be corrected with vouchers, not deleted through an API route."
);

check(
  "Accounting service supports corrections instead of rewriting",
  includesAll(accountingService, ["createCorrectionEntry", "Correction of", "createCreditInvoiceEntries", "createSupplierInvoiceCancellationEntries"]),
  "Accounting service should expose correction flows for material mistakes."
);

check(
  "Period lock protects historical edits",
  includesAll(settingsService, ["Accounting lock date cannot be moved backwards", "Company type cannot be changed after bookkeeping has been created", "Accounting method cannot be changed after bookkeeping has been created", "journalEntryRepository.count()"]),
  "Locked periods and started bookkeeping should block backwards policy changes."
);

check(
  "Bank bulk reset is disabled by default and confirmed",
  methodHas("/bank-reconciliations", ["bankReconciliationResetEnabled", "HttpStatus.FORBIDDEN", "DELETE_ALIBOOKS_BANK_RECONCILIATION_HISTORY", "bank_reconciliations_cleared"]),
  "Bulk reset of bank reconciliation data must stay local/test-only."
);

check(
  "Frontend warns against direct deletion",
  mainSource.includes("Do not directly delete invoices/vouchers") && mainSource.includes("archiveCenter"),
  "The UI should guide the user toward archive/correction instead of silent deletion."
);

const failed = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks retention and immutability check: ${checks.length - failed.length}/${checks.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} retention and immutability check(s) failed.`);
  process.exit(1);
}
