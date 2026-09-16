import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const backendSrc = path.join(repoRoot, "backend", "src", "main", "java", "se", "cloudshop");
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok: Boolean(ok), detail });
}

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
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
    const character = source[index];
    if (character === "{") {
      depth += 1;
    } else if (character === "}") {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }
  return -1;
}

function findDeleteMethods(filePath, source) {
  const methods = [];
  const mappingPattern = /@DeleteMapping\(([^)]*)\)/g;
  let match;
  while ((match = mappingPattern.exec(source)) !== null) {
    const bodyStart = source.indexOf("{", mappingPattern.lastIndex);
    const bodyEnd = bodyStart < 0 ? -1 : findMatchingBrace(source, bodyStart);
    const signature = source.slice(match.index, bodyStart < 0 ? match.index : bodyStart);
    const methodNameMatch = signature.match(/public\s+\w+\s+(\w+)\s*\(/);
    methods.push({
      filePath,
      route: match[1].replace(/"/g, "").trim(),
      methodName: methodNameMatch ? methodNameMatch[1] : "unknown",
      body: bodyStart >= 0 && bodyEnd >= 0 ? source.slice(bodyStart, bodyEnd + 1) : "",
      source
    });
  }
  return methods;
}

function relative(filePath) {
  return path.relative(repoRoot, filePath).replaceAll("\\", "/");
}

const deleteMethods = javaFiles(backendSrc)
  .flatMap((filePath) => findDeleteMethods(filePath, readFileSync(filePath, "utf8")));

check(
  "DELETE endpoints discovered",
  deleteMethods.length >= 1,
  `Found ${deleteMethods.length} DELETE endpoint(s).`
);

for (const method of deleteMethods) {
  const label = `${method.route} in ${relative(method.filePath)}`;
  check(
    `JWT required for ${label}`,
    method.body.includes("authHeader.requireValidToken(authorizationHeader)"),
    "Every DELETE endpoint must require a valid JWT before changing data."
  );

  if (method.body.includes(".deleteAll(")) {
    check(
      `Bulk delete disabled by feature flag for ${label}`,
      method.source.includes("enabled:false")
        && method.body.includes("HttpStatus.FORBIDDEN")
        && method.body.includes("DELETE_ALIBOOKS_"),
      "Bulk deletes must be disabled by default and require an explicit confirmation header."
    );
  }

  if (method.body.includes(".delete(") || method.body.includes(".deleteAll(") || method.body.includes(".deleteBy")) {
    check(
      `Audit trail for destructive action ${label}`,
      method.body.includes("auditService.record") || method.body.includes("auditCustomer(") || method.route === "/test-data",
      "Destructive actions should be traceable in the audit trail, except local-only test data reset."
    );
  }
}

const byRoute = new Map(deleteMethods.map((method) => [method.route, method]));

const expectations = [
  {
    route: "/test-data",
    details: [
      ["test reset flag", "testDataResetEnabled"],
      ["test reset confirmation", "DELETE_ALIBOOKS_TEST_DATA"],
      ["test reset forbidden by default", "HttpStatus.FORBIDDEN"]
    ]
  },
  {
    route: "/bank-reconciliations",
    details: [
      ["bank reset flag", "bankReconciliationResetEnabled"],
      ["bank reset confirmation", "DELETE_ALIBOOKS_BANK_RECONCILIATION_HISTORY"],
      ["bank reset audit", "bank_reconciliations_cleared"]
    ]
  },
  {
    route: "/bank-reconciliations/skipped/{bankRowId}",
    details: [
      ["skipped row removal uses guarded service", "bankImport.removeSkipped(bankRowId)"],
      ["skipped row removal audit", "skipped_bank_reconciliation_removed"]
    ]
  },
  {
    route: "/invoices/{id}",
    details: [
      ["sent/paid invoices cannot be deleted", "Only draft invoices can be deleted"],
      ["locked invoice periods protected", "requireUnlockedAccountingDate(order.getInvoiceDate())"],
      ["invoice bookkeeping rows removed through service", "deleteEntriesForInvoice(order)"],
      ["invoice deletion audit", "draft_deleted"]
    ]
  },
  {
    route: "/supplier-invoices/{id}",
    details: [
      ["booked supplier invoices cannot be deleted", "hasSupplierInvoiceEntries(invoice)"],
      ["paid supplier invoices cannot be deleted", "invoice.getPaidAmount() > 0"],
      ["supplier deletion rejected regardless of period", "Registered supplier invoices must be retained for dated balances"],
      ["supplier deletion responds with conflict", "HttpStatus.CONFLICT"]
    ]
  },
  {
    route: "/owner-transactions/{id}",
    details: [
      ["locked owner transaction periods protected", "requireUnlockedAccountingDate(transaction.getDate())"],
      ["booked owner transactions cannot be deleted", "Booked owner transactions cannot be deleted"],
      ["owner transaction deletion audit", "Owner transaction deleted"]
    ]
  },
  {
    route: "/customers/{id}",
    details: [
      ["customers with invoices are archived instead", "orderRepository.findByCustomer(customer)"],
      ["customer deletion audit", "customer_deleted"]
    ]
  },
  {
    route: "/contracts/{id}",
    details: [
      ["contracts are archived instead of hard-deleted", "contract.archive()"],
      ["contract archive audit", "Recurring contract archived instead of deleted"]
    ]
  },
  {
    route: "/voucher-approvals/{voucherNumber}",
    details: [
      ["voucher approval reset uses service", "voucherApprovalService.delete(voucherNumber)"],
      ["voucher approval reset audit", "voucher_approval_reset"]
    ]
  }
];

for (const expectation of expectations) {
  const method = byRoute.get(expectation.route);
  check(
    `Expected DELETE route ${expectation.route}`,
    Boolean(method),
    "Critical destructive routes should be known and checked."
  );
  if (!method) {
    continue;
  }

  for (const [name, requiredText] of expectation.details) {
    check(
      `${name} for ${expectation.route}`,
      method.body.includes(requiredText),
      `Expected '${requiredText}' in ${relative(method.filePath)}.`
    );
  }
}

check(
  "Supplier deletion has no destructive call and has database coverage",
  Boolean(byRoute.get("/supplier-invoices/{id}"))
    && !/\.delete\w*\s*\(/.test(byRoute.get("/supplier-invoices/{id}").body)
    && read("backend/src/test/java/se/cloudshop/CloudShopApplicationIT.java").includes("cashSupplierInvoiceCannotBeDeletedOrCancelledWithoutADate"),
  "Registered cash-method invoices must also be retained; the integration test verifies the row remains."
);

const voucherApprovalService = read("backend/src/main/java/se/cloudshop/accounting/VoucherApprovalService.java");
const bankImportService = read("backend/src/main/java/se/cloudshop/bank/BankImportBookingService.java");
check(
  "Skipped bank removal serializes and respects closed periods",
  bankImportService.includes('deleteByBankRowIdAndStatus(bankRowId, "skipped")')
    && bankImportService.includes("rows.lockBankRow(bankRowId)")
    && bankImportService.includes("accounting.requireUnlockedAccountingDate(row.getBankDate())")
    && bankImportService.includes("Propagation.MANDATORY")
    && read("backend/src/test/java/se/cloudshop/CloudShopApplicationIT.java").includes("skippedBankRowMustBeRestoredBeforeBookingAndCannotBeRemovedInClosedPeriod"),
  "Only skipped rows may be removed, inside a transaction with row serialization, period guard and integration coverage."
);
check(
  "Voucher approval reset respects locked periods",
  voucherApprovalService.includes("requireVoucherApprovalEditable(cleanVoucherNumber)")
    && voucherApprovalService.includes("accountingService::requireUnlockedAccountingDate"),
  "Voucher approval resets must not change review state for locked accounting periods."
);

const healthController = read("backend/src/main/java/se/cloudshop/system/HealthController.java");
check(
  "System status exposes destructive reset flags",
  healthController.includes('"maintenance"')
    && healthController.includes("testDataResetEnabled")
    && healthController.includes("bankReconciliationResetEnabled")
    && healthController.includes("safeForProduction"),
  "Settings and Security views should not guess whether destructive reset endpoints are active."
);

const frontend = read("frontend/src/main.jsx");
check(
  "Frontend disables test-data reset unless backend flag is enabled",
  frontend.includes("maintenanceTestDataResetEnabled")
    && frontend.includes("disabled={!maintenanceTestDataResetEnabled}")
    && frontend.includes("APP_TEST_DATA_RESET_ENABLED=true bara i lokal testmiljo"),
  "The test-data reset button should not look available when the backend reset feature flag is off."
);

const failed = results.filter((result) => !result.ok);
for (const result of results) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks data safety check: ${results.length - failed.length}/${results.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} data safety check(s) failed.`);
  process.exit(1);
}
