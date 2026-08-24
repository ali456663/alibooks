import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

const packageJson = JSON.parse(read("frontend/package.json"));
const rootPackageJson = JSON.parse(read("package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const protocol = read("docs/mvp-testprotokoll.md");
const goLiveChecklist = read("docs/go-live-checklista.md");
const riskRegister = read("docs/go-live-riskregister.md");
const allDocs = [protocol, goLiveChecklist, riskRegister, releaseEvidence].join("\n\n");
const goLiveChecklistLower = goLiveChecklist.toLowerCase();

check(
  "Manual evidence command exists in frontend",
  packageJson.scripts?.["check:manual-go-live"] === "node ../scripts/manual-go-live-evidence-check.mjs",
  "frontend/package.json should expose npm run check:manual-go-live."
);

check(
  "Manual evidence command exists in root",
  rootPackageJson.scripts?.["check:manual-go-live"]?.includes("--prefix frontend"),
  "package.json should expose npm run check:manual-go-live from the repo root."
);

check(
  "Release gate runs manual go-live evidence check",
  releaseGate.includes('"check:manual-go-live"'),
  "Release gate should fail if manual external go-live evidence requirements disappear."
);

check(
  "Release evidence mentions manual go-live check",
  releaseEvidence.includes("check:manual-go-live"),
  "Release evidence should show the current manual go-live evidence guard."
);

check(
  "Manual test protocol separates automated and manual proof",
  includesAll(protocol, ["Automatiskt bevis", "Manuellt bevis", "Ej testat"]),
  "MVP protocol should make it clear what still needs real browser/service proof."
);

check(
  "PDF visual proof is required",
  includesAll(protocol, ["PDF visuellt", "F-skatt", "OCR", "PlusGiro"]) &&
    goLiveChecklist.includes("Generera PDF"),
  "Invoices must be visually checked before real customer use."
);

check(
  "Stripe webhook proof is required",
  includesAll(allDocs, ["Stripe", "webhook", "checkout.session.completed"]),
  "Stripe payments should not be trusted until test webhook evidence exists."
);

check(
  "SMTP delivery proof is required",
  includesAll(allDocs, ["SMTP", "testmail"]) &&
    allDocs.includes("Faktura- och paminnelsemail"),
  "Invoice and reminder email delivery must be tested with real SMTP settings."
);

check(
  "Bank or payment reconciliation proof is required",
  includesAll(allDocs, ["bank", "betalningar", "bokforas korrekt"]),
  "Bank/card payment flow should be reviewed before production use."
);

check(
  "Receipt and attachment proof is required",
  includesAll(protocol, ["underlag", "kvitto/PDF", "uppladdning", "nedladdning", "export"]),
  "Receipt and attachment storage should be manually checked."
);

check(
  "Backup file verification is required",
  includesAll(allDocs, ["Kontrollera backupfil", "pg_restore -l"]),
  "Backups should be verified, not only downloaded."
);

check(
  "Restore drill proof is required",
  includesAll(allDocs, ["restore drill", "separat test", "test database"]),
  "A restore drill must be done away from production before real data."
);

check(
  "Public EC2/RDS smoke proof is required",
  includesAll(allDocs, ["publik", "EC2", "RDS", "/api/health", "/api/system/status"]),
  "Local MVP proof should not be confused with public cloud proof."
);

check(
  "GitHub and Dockerhub external proof is required",
  includesAll(allDocs, ["GitHub Actions", "Dockerhub", "Docker images"]),
  "CI and image publishing must be externally visible before cloud deployment."
);

check(
  "Accountant handoff proof is required",
  includesAll(allDocs, ["redovisningskonsult", "export"]),
  "The user should be able to export material for accounting review."
);

check(
  "Real customer data is blocked before external checks",
  includesAll(riskRegister, ["far inte anvandas med skarp kunddata", "BLOCKERAR SKARP DRIFT"]),
  "Docs should warn against real customer/bookkeeping data until production blockers are cleared."
);

check(
  "Manual proof appears before go-live claim",
  releaseEvidence.indexOf("Detta maste fortfarande verifieras") >= 0 &&
    releaseEvidence.indexOf("AliBooks lokal MVP") < releaseEvidence.indexOf("Detta maste fortfarande verifieras"),
  "Release evidence should separate local MVP status from external go-live proof."
);

check(
  "Go-live checklist includes browser flow",
  includesAll(goLiveChecklistLower, ["testa pa den publika url", "registrera", "kund", "faktura"]),
  "The public app should be clicked through before demo or production use."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`Manual go-live evidence check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} manual go-live evidence check(s) failed.`);
  process.exit(1);
}
