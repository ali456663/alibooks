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

const runbookPath = "docs/schema-bootstrap-runbook.md";
const runbook = exists(runbookPath) ? read(runbookPath) : "";
const goLiveChecklist = read("docs/go-live-checklista.md");
const riskRegister = read("docs/go-live-riskregister.md");
const releaseEvidence = read("docs/release-evidence.md");
const packageJson = JSON.parse(read("frontend/package.json"));
const releaseGate = read("scripts/release-gate.mjs");

check("Schema bootstrap runbook exists", exists(runbookPath), runbookPath);
check(
  "Runbook exports schema only",
  includesAll(runbook, ["pg_dump", "--schema-only", "--no-owner", "--no-privileges", "alibooks-schema.sql"]),
  "First RDS schema should come from a reviewed schema-only dump, not live personal or bookkeeping data."
);
check(
  "Runbook tests restore before RDS",
  includesAll(runbook, ["cloudshop_restore", "restore/staging", "psql", "001_startup_schema_patch.sql"]),
  "Schema bootstrap should be rehearsed on restore/staging before production."
);
check(
  "Runbook keeps production immutable on startup",
  includesAll(runbook, ["SPRING_JPA_HIBERNATE_DDL_AUTO=validate", "APP_SCHEMA_PATCH_ENABLED=false", "database.ok = true"]),
  "Production backend should validate schema and not run startup ALTER TABLE patches."
);
check(
  "Runbook has explicit stop conditions",
  includesAll(runbook, ["column does not exist", "relation does not exist", "APP_SCHEMA_PATCH_ENABLED=true", "RDS snapshot"]),
  "Go-live should stop on schema drift, unsafe flags or missing backup proof."
);
check(
  "Go-live checklist references schema bootstrap",
  includesAll(goLiveChecklist, ["schema-bootstrap-runbook.md", "npm run check:schema-bootstrap", "alibooks-schema.sql"]),
  "Operational go-live checklist should point to the schema bootstrap runbook."
);
check(
  "Risk register requires schema bootstrap proof",
  includesAll(riskRegister, ["check:schema-bootstrap", "schema-dump", "restore/staging"]),
  "Go-live risk register should require proof that full schema bootstrap was rehearsed."
);
check(
  "Release evidence documents schema bootstrap check",
  releaseEvidence.includes("check:schema-bootstrap"),
  "Release evidence should include the schema bootstrap check."
);
check(
  "Package exposes schema bootstrap check",
  packageJson.scripts?.["check:schema-bootstrap"] === "node ../scripts/schema-bootstrap-check.mjs",
  "frontend/package.json should expose npm run check:schema-bootstrap."
);
check(
  "Release gate runs schema bootstrap check",
  releaseGate.includes('"check:schema-bootstrap"'),
  "Release gate should fail when full schema bootstrap proof is undocumented."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`Schema bootstrap check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} schema bootstrap check(s) failed.`);
  process.exit(1);
}
