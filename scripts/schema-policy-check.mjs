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

const allowedDdlModes = ["none", "validate", "update", "create", "create-drop"];
const localDdl = envExample.get("SPRING_JPA_HIBERNATE_DDL_AUTO") || "";
const prodDdl = prodEnvExample.get("SPRING_JPA_HIBERNATE_DDL_AUTO") || "";

check(
  "Hibernate ddl-auto is environment controlled",
  applicationProperties.includes("spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:update}"),
  "The app should not hide schema policy inside hard-coded production code."
);

check(
  "Local env declares ddl-auto",
  allowedDdlModes.includes(localDdl),
  "Local .env example should choose an explicit Hibernate ddl-auto mode."
);

check(
  "Production env declares ddl-auto",
  allowedDdlModes.includes(prodDdl),
  "Production .env example should choose an explicit Hibernate ddl-auto mode."
);

check(
  "Docker compose passes ddl-auto",
  compose.includes("SPRING_JPA_HIBERNATE_DDL_AUTO=${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}"),
  "Local Docker Compose should pass the explicit schema policy to Spring."
);

check(
  "Production compose passes ddl-auto",
  composeProd.includes("SPRING_JPA_HIBERNATE_DDL_AUTO=${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}"),
  "Production Docker Compose should pass the explicit schema policy to Spring."
);

check(
  "Production readiness checks ddl-auto",
  includesAll(prodCheck, ["SPRING_JPA_HIBERNATE_DDL_AUTO", "ddl-auto", "none", "validate", "update"]),
  "Production readiness should validate the configured schema mode."
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
