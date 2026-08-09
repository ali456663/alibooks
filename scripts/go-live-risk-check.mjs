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

const riskRegisterPath = "docs/go-live-riskregister.md";
check("Risk register exists", exists(riskRegisterPath), riskRegisterPath);

const riskRegister = exists(riskRegisterPath) ? read(riskRegisterPath) : "";
const releaseEvidence = read("docs/release-evidence.md");
const goLiveChecklist = read("docs/go-live-checklista.md");
const packageJson = JSON.parse(read("frontend/package.json"));

const requiredRiskAreas = [
  "GitHub Actions CI",
  "GitHub sync",
  "Dockerhub images",
  "EC2 deploy",
  "RDS databas",
  "Backup och restore drill",
  "Frontend beroenden",
  "Stripe betalningar",
  "SMTP e-post",
  "AI och personuppgifter",
  "Kort, Swish och kassaregisterregler",
  "Bokforingsansvar"
];

for (const area of requiredRiskAreas) {
  check(`Risk area documented: ${area}`, riskRegister.includes(area), "Every production-blocking area should be visible before go-live.");
}

check(
  "Risk statuses are explicit",
  includesAll(riskRegister, ["KLAR LOKALT", "KRAVER EXTERN VERIFIERING", "BLOCKERAR SKARP DRIFT"]),
  "The register should separate local MVP readiness from external production blockers."
);

check(
  "Risk register demands evidence",
  includesAll(riskRegister, ["Bevis som kravs", "GitHub Actions", "Dockerhub", "database.ok = true", "pg_restore -l"]),
  "Go-live should require concrete evidence, not only intent."
);

check(
  "Risk register protects real data",
  riskRegister.includes("far inte anvandas med skarp kunddata") && riskRegister.includes("restore drill"),
  "The register should warn against using real customer/bookkeeping data before external checks."
);

check(
  "Go-live checklist links risk register",
  goLiveChecklist.includes("go-live-riskregister.md"),
  "The operational checklist should point to the risk register."
);

check(
  "Release evidence references risk register",
  releaseEvidence.includes("go-live-riskregister.md") || releaseEvidence.includes("riskregister"),
  "Release evidence should tell reviewers where the remaining go-live risks are tracked."
);

check(
  "Package exposes risk check",
  packageJson.scripts?.["check:go-live-risks"] === "node ../scripts/go-live-risk-check.mjs",
  "frontend/package.json should expose npm run check:go-live-risks."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`Go-live risk check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} go-live risk check(s) failed.`);
  process.exit(1);
}
