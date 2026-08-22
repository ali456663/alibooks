import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const sourcePath = "backend/src/main/java/se/cloudshop/config/DatabaseSchemaPatch.java";
const migrationPath = "db/migrations/001_startup_schema_patch.sql";
const shouldWrite = process.argv.includes("--write");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function unescapeJavaString(value) {
  return value
    .replace(/\\n/g, "\n")
    .replace(/\\t/g, "\t")
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, "\\");
}

function normalizeSql(value) {
  return value
    .replace(/\r\n/g, "\n")
    .replace(/;\s*$/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

function extractStartupSql(source) {
  const statements = [];
  const pattern = /jdbcTemplate\.execute\("((?:\\.|[^"\\])*)"\);/g;
  let match;

  while ((match = pattern.exec(source)) !== null) {
    statements.push(unescapeJavaString(match[1]));
  }

  return statements;
}

function renderMigration(statements) {
  const renderedStatements = statements.map((statement, index) => {
    const prefix = String(index + 1).padStart(3, "0");
    return `-- ${prefix}\n${statement};`;
  });

  return [
    "-- AliBooks controlled schema patch migration",
    `-- Source: ${sourcePath}`,
    "--",
    "-- This file mirrors DatabaseSchemaPatch startup SQL so production schema changes",
    "-- are visible, reviewed and repeatable instead of being hidden in application startup.",
    "--",
    "-- Production policy:",
    "-- - Set SPRING_JPA_HIBERNATE_DDL_AUTO=validate or none.",
    "-- - Set APP_SCHEMA_PATCH_ENABLED=false on EC2/RDS.",
    "-- - Run this against a restored/staging database first and verify backup/restore.",
    "-- - For a brand new RDS database, bootstrap the full schema from a tested release",
    "--   schema dump first, then run this patch file for additive release changes.",
    "--",
    "-- Example:",
    "--   psql \"postgresql://USER:PASSWORD@HOST:5432/cloudshop\" -f db/migrations/001_startup_schema_patch.sql",
    "",
    ...renderedStatements,
    ""
  ].join("\n");
}

const source = read(sourcePath);
const statements = extractStartupSql(source);
const migrationAbsolutePath = path.join(repoRoot, migrationPath);

check("Startup schema SQL statements are extracted", statements.length > 0, `${statements.length} statement(s) found in ${sourcePath}.`);

if (shouldWrite) {
  mkdirSync(path.dirname(migrationAbsolutePath), { recursive: true });
  writeFileSync(migrationAbsolutePath, renderMigration(statements));
}

check("Migration SQL file exists", existsSync(migrationAbsolutePath), migrationPath);

const migration = existsSync(migrationAbsolutePath) ? read(migrationPath) : "";
const normalizedMigration = normalizeSql(migration);
const missingStatements = statements.filter((statement) => !normalizedMigration.includes(normalizeSql(statement)));

check(
  "Migration mirrors DatabaseSchemaPatch SQL",
  missingStatements.length === 0,
  `${statements.length - missingStatements.length}/${statements.length} startup statement(s) are present in ${migrationPath}.`
);

check(
  "Migration documents production schema policy",
  ["APP_SCHEMA_PATCH_ENABLED=false", "SPRING_JPA_HIBERNATE_DDL_AUTO=validate", "RDS", "restore", "psql"].every((term) => migration.includes(term)),
  "Migration file should explain validate mode, disabled startup patch, RDS, restore rehearsal and psql execution."
);

check(
  "Migration stays idempotent",
  !/\bDROP\s+TABLE\b|\bTRUNCATE\b|\bDELETE\s+FROM\b/i.test(migration),
  "Controlled schema patch must not contain destructive SQL."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`Schema migration check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} schema migration check(s) failed.`);
  process.exit(1);
}
