import { readdirSync, readFileSync } from "node:fs";
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

function listJavaFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return listJavaFiles(fullPath);
    }
    return entry.isFile() && entry.name.endsWith(".java") ? [fullPath] : [];
  });
}

function entityBooleanDefaultIssues() {
  const sourceRoot = path.join(repoRoot, "backend", "src", "main", "java", "se", "cloudshop");
  const issues = [];

  for (const filePath of listJavaFiles(sourceRoot)) {
    const source = readFileSync(filePath, "utf8");
    if (!source.includes("@Entity")) {
      continue;
    }

    const lines = source.split(/\r?\n/);
    lines.forEach((line, index) => {
      const match = line.match(/\bprivate\s+boolean\s+([a-zA-Z0-9_]+)\b/);
      if (!match) {
        return;
      }

      const previousLines = lines.slice(Math.max(index - 3, 0), index).join("\n").toLowerCase();
      const hasBooleanDefaultColumn = previousLines.includes("@column")
        && previousLines.includes("columnDefinition".toLowerCase())
        && previousLines.includes("boolean default");
      if (!hasBooleanDefaultColumn) {
        const relativePath = path.relative(repoRoot, filePath).replace(/\\/g, "/");
        issues.push(`${relativePath}:${index + 1} ${match[1]}`);
      }
    });
  }

  return issues;
}

function parseEnv(source) {
  const values = new Map();
  for (const rawLine of source.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const equalsIndex = line.indexOf("=");
    if (equalsIndex < 1) {
      continue;
    }
    values.set(line.slice(0, equalsIndex).trim(), line.slice(equalsIndex + 1).trim());
  }
  return values;
}

const applicationProperties = read("backend/src/main/resources/application.properties");
const envExample = parseEnv(read(".env.example"));
const prodEnvExample = parseEnv(read(".env.production.example"));
const compose = read("docker-compose.yml");
const composeProd = read("docker-compose.prod.yml");
const ci = read(".github/workflows/ci.yml");
const prodCheck = read("scripts/production-readiness-check.mjs");
const riskRegister = read("docs/go-live-riskregister.md");
const releaseGate = read("scripts/release-gate.mjs");
const packageJson = JSON.parse(read("frontend/package.json"));
const databaseSchemaPatch = read("backend/src/main/java/se/cloudshop/config/DatabaseSchemaPatch.java");

const allowedDdlModes = ["none", "validate", "update", "create", "create-drop"];
const localDdl = envExample.get("SPRING_JPA_HIBERNATE_DDL_AUTO") || "";
const prodDdl = prodEnvExample.get("SPRING_JPA_HIBERNATE_DDL_AUTO") || "";
const localSchemaPatch = envExample.get("APP_SCHEMA_PATCH_ENABLED") || "";
const prodSchemaPatch = prodEnvExample.get("APP_SCHEMA_PATCH_ENABLED") || "";

check(
  "Hibernate ddl-auto is environment controlled",
  applicationProperties.includes("spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:update}"),
  "The app should not hide schema policy inside hard-coded production code."
);

check(
  "Startup schema patch is environment controlled",
  applicationProperties.includes("app.schema-patch.enabled=${APP_SCHEMA_PATCH_ENABLED:true}"),
  "The app should expose a feature flag for startup ALTER TABLE patches."
);

check(
  "Local env declares ddl-auto",
  allowedDdlModes.includes(localDdl),
  "Local .env example should choose an explicit Hibernate ddl-auto mode."
);

check(
  "Production env declares ddl-auto",
  prodDdl === "validate",
  "Production .env example should default to validate so Hibernate does not mutate RDS schema automatically."
);

check(
  "Local env enables schema patch",
  localSchemaPatch === "true",
  "Local .env example may keep schema patch enabled so development remains easy."
);

check(
  "Production env disables schema patch",
  prodSchemaPatch === "false",
  "Production .env example should disable startup schema patching for RDS."
);

check(
  "Docker compose passes ddl-auto",
  compose.includes("SPRING_JPA_HIBERNATE_DDL_AUTO=${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}"),
  "Local Docker Compose should pass the explicit schema policy to Spring."
);

check(
  "Docker compose passes schema patch flag",
  compose.includes("APP_SCHEMA_PATCH_ENABLED=${APP_SCHEMA_PATCH_ENABLED:-true}"),
  "Local Docker Compose should pass the startup schema patch flag."
);

check(
  "Production compose passes ddl-auto",
  composeProd.includes("SPRING_JPA_HIBERNATE_DDL_AUTO=${SPRING_JPA_HIBERNATE_DDL_AUTO:-validate}"),
  "Production Docker Compose should default to validate and pass the explicit schema policy to Spring."
);

check(
  "Production compose disables schema patch by default",
  composeProd.includes("APP_SCHEMA_PATCH_ENABLED=${APP_SCHEMA_PATCH_ENABLED:-false}"),
  "Production Docker Compose should disable startup schema patching by default."
);

check(
  "Production readiness checks ddl-auto",
  includesAll(prodCheck, ["SPRING_JPA_HIBERNATE_DDL_AUTO", "ddl-auto", "none", "validate", "update"]),
  "Production readiness should validate the configured schema mode."
);

check(
  "Production readiness checks schema patch flag",
  includesAll(prodCheck, ["APP_SCHEMA_PATCH_ENABLED", "Production disables startup schema patch", "false"]),
  "Production readiness should fail if startup schema patching is enabled."
);

check(
  "DatabaseSchemaPatch has production guard",
  includesAll(databaseSchemaPatch, ["app.schema-patch.enabled", "schemaPatchEnabled", "Database schema patch is disabled"]),
  "Startup schema patching should stop immediately when the feature flag is false."
);

check(
  "CI declares ddl-auto",
  ci.includes("SPRING_JPA_HIBERNATE_DDL_AUTO: update"),
  "GitHub Actions should run backend tests with the same explicit schema policy."
);

check(
  "Risk register documents schema drift",
  includesAll(riskRegister, ["Databas-schema och Hibernate", "schema", "ddl-auto", "column does not exist"]),
  "Go-live risks should mention schema drift before RDS is used with real data."
);

check(
  "Package exposes schema check",
  packageJson.scripts?.["check:schema"] === "node ../scripts/schema-policy-check.mjs",
  "frontend/package.json should expose npm run check:schema."
);

check(
  "Release gate runs schema check",
  releaseGate.includes('"check:schema"'),
  "The release gate should fail when schema policy becomes implicit or undocumented."
);

const booleanDefaultIssues = entityBooleanDefaultIssues();
check(
  "Entity boolean fields define database defaults",
  booleanDefaultIssues.length === 0,
  booleanDefaultIssues.length === 0
    ? "Primitive boolean fields in JPA entities should have explicit database defaults so Hibernate can update existing tables safely."
    : `Missing explicit boolean defaults: ${booleanDefaultIssues.join(", ")}`
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`Schema policy check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} schema policy check(s) failed.`);
  process.exit(1);
}
