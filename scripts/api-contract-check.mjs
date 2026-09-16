import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const backendRoot = path.join(repoRoot, "backend", "src", "main", "java");
const frontendSource = ["main.jsx", "components/ui/BankJournalLink.jsx"]
  .map(file => readFileSync(path.join(repoRoot, "frontend", "src", file), "utf8")).join("\n");

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const fullPath = path.join(dir, name);
    return statSync(fullPath).isDirectory() ? walk(fullPath) : [fullPath];
  });
}

function normalizeEndpoint(endpoint) {
  return endpoint.replace(/\{[^}]+}/g, ":param");
}

const backendSource = walk(backendRoot)
  .filter((file) => file.endsWith(".java"))
  .map((file) => readFileSync(file, "utf8"))
  .join("\n");

const backendEndpoints = new Set();
for (const match of backendSource.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/g)) {
  backendEndpoints.add(`${match[1].toUpperCase()} ${normalizeEndpoint(match[2])}`);
}

const criticalContracts = [
  ["POST", "/auth/register", "registration", "/auth/${authMode}"],
  ["POST", "/auth/login", "login", "/auth/${authMode}"],
  ["GET", "/system/status", "system status"],
  ["GET", "/customers", "customer list"],
  ["POST", "/customers", "create customer"],
  ["GET", "/services", "service list"],
  ["GET", "/invoices", "invoice list"],
  ["POST", "/invoices", "create invoice"],
  ["GET", "/journal-entries", "bookkeeping list"],
  ["GET", "/vat-report", "VAT report"],
  ["GET", "/profit-and-loss", "profit and loss report"],
  ["GET", "/balance-report", "balance report"],
  ["GET", "/accounting-period/close-check", "period close check"],
  ["POST", "/accounting-period/close", "period lock"],
  ["GET", "/bank-reconciliations", "bank import/reconciliation"],
  ["POST", "/bank-import/invoices/:param/paid", "atomic bank payment", "/bank-import/invoices/${invoiceItem.id}/paid"],
  ["POST", "/bank-import/expenses", "atomic bank expense"],
  ["GET", "/bank-reconciliations/:param/journal-candidates", "journal link candidates", '"journal-candidates"'],
  ["POST", "/bank-reconciliations/:param/journal-link", "explicit journal link", '"journal-link"'],
  ["GET", "/accountant-package", "accountant handoff"],
  ["GET", "/archive-year", "year archive control"],
  ["POST", "/ai/assistant", "AI assistant"],
  ["GET", "/settings", "settings"],
  ["PUT", "/settings", "save settings"],
  ["GET", "/payroll/snapshot", "payroll snapshot"],
  ["PUT", "/payroll/snapshot", "save payroll snapshot"]
];

const failures = [];

for (const [method, endpoint, label, frontendNeedle = endpoint] of criticalContracts) {
  const backendKey = `${method} ${endpoint}`;
  const frontendUsesEndpoint = frontendSource.includes(frontendNeedle);
  const backendOk = backendEndpoints.has(backendKey);
  const frontendOk = frontendUsesEndpoint;
  const ok = backendOk && frontendOk;
  const marker = ok ? "OK" : "FAIL";
  console.log(`${marker} - ${label}: ${backendKey}`);
  if (!backendOk) {
    failures.push(`Backend endpoint missing: ${backendKey}`);
  }
  if (!frontendOk) {
    failures.push(`Frontend does not reference endpoint: ${endpoint}`);
  }
}

if (failures.length > 0) {
  console.error("");
  console.error(`API contract check failed:\n- ${failures.join("\n- ")}`);
  process.exit(1);
}

console.log("");
console.log(`API contract check passed: ${criticalContracts.length} critical frontend/backend contracts.`);
