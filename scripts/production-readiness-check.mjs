import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const cliArgs = process.argv.slice(2);
const strict = cliArgs.includes("--strict");
const envFileIndex = cliArgs.indexOf("--env-file");
const envFile = envFileIndex >= 0
  ? cliArgs[envFileIndex + 1]
  : ".env.production.example";

if (envFileIndex >= 0 && !envFile) {
  console.error("Production readiness check failed: --env-file needs a file path.");
  process.exit(1);
}

const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function readMaybe(relativePath) {
  const resolved = path.isAbsolute(relativePath)
    ? relativePath
    : path.join(repoRoot, relativePath);
  if (!existsSync(resolved)) {
    return null;
  }
  return readFileSync(resolved, "utf8");
}

function check(name, ok, detail, severity = "fail") {
  checks.push({ name, ok: Boolean(ok), detail, severity });
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
    const key = line.slice(0, equalsIndex).trim();
    const value = line.slice(equalsIndex + 1).trim().replace(/^["']|["']$/g, "");
    values.set(key, value);
  }
  return values;
}

function hasValue(values, key) {
  return values.has(key) && values.get(key).trim() !== "";
}

function value(values, key) {
  return values.get(key) || "";
}

function isPlaceholder(text) {
  return /your-|replace_|<|example|placeholder|sk_test_or_live_key|whsec_from_stripe|AIza\.\.\.|hf_\.\.\./i.test(text);
}

function isHttpsUrl(text) {
  try {
    const parsed = new URL(text);
    return parsed.protocol === "https:" && Boolean(parsed.hostname);
  } catch {
    return false;
  }
}

function hasRequiredPostgresTls(text) {
  try {
    const parsed = new URL(text.slice("jdbc:".length));
    const modes = parsed.searchParams.getAll("sslmode");
    return text.startsWith("jdbc:postgresql://")
      && modes.length === 1
      && modes[0].toLowerCase() === "verify-full"
      && parsed.searchParams.getAll("sslrootcert").length === 1
      && parsed.searchParams.get("sslrootcert") === "/run/certs/rds-ca-bundle.pem";
  } catch {
    return false;
  }
}

function originOf(text) {
  try {
    return new URL(text).origin;
  } catch {
    return "";
  }
}

const composeProd = read("docker-compose.prod.yml");
const nginx = read("frontend/nginx.conf");
const smokeSh = read("scripts/prod-smoke-test.sh");
const smokePs = read("scripts/prod-smoke-test.ps1");
const backupSh = read("scripts/backup-postgres.sh");
const backupPs = read("scripts/backup-postgres.ps1");
const restoreSh = read("scripts/restore-postgres.sh");
const restorePs = read("scripts/restore-postgres.ps1");
const envSource = readMaybe(envFile);

check("Production env file exists", envSource !== null, envFile);

const env = parseEnv(envSource || "");
const requiredVars = [
  "DOCKERHUB_USERNAME",
  "IMAGE_TAG",
  "VITE_API_URL",
  "APP_FRONTEND_URL",
  "TLS_CERT_HOST_PATH",
  "TLS_KEY_HOST_PATH",
  "APP_CORS_ALLOWED_ORIGINS",
  "APP_CORS_LOCAL_DEV_ENABLED",
  "APP_TEST_DATA_RESET_ENABLED",
  "APP_BANK_RECONCILIATION_RESET_ENABLED",
  "SPRING_DATASOURCE_URL",
  "SPRING_DATASOURCE_USERNAME",
  "SPRING_DATASOURCE_PASSWORD",
  "SPRING_JPA_HIBERNATE_DDL_AUTO",
  "APP_SCHEMA_PATCH_ENABLED",
  "JWT_SECRET",
  "JWT_EXPIRATION_MINUTES",
  "APP_AUTH_REGISTRATION_BOOTSTRAP_KEY",
  "APP_AUTH_MFA_ENCRYPTION_KEY",
  "APP_AUTH_REQUIRE_MFA"
];

for (const key of requiredVars) {
  check(`Production env has ${key}`, hasValue(env, key), `${key} should be set`);
}

check("Production compose uses Dockerhub images", composeProd.includes("${DOCKERHUB_USERNAME}/cloudshop-frontend") && composeProd.includes("${DOCKERHUB_USERNAME}/cloudshop-backend"), "frontend/backend images should come from Dockerhub variables");
check("Production compose does not start local Postgres", !composeProd.includes("postgres:") && !/^\s+db:/m.test(composeProd), "Production should use RDS, not a local db service");
check("Production compose does not publish backend port", !composeProd.includes('"3000:3000"') && !composeProd.includes("- 3000:3000"), "Backend should be exposed only inside Docker network");
check("Production compose publishes HTTP redirect and HTTPS", composeProd.includes('"80:80"') && composeProd.includes('"443:443"'), "Frontend/Nginx should expose HTTPS and use port 80 only for redirect");
check("Production compose mounts TLS certificate read-only", composeProd.includes("${TLS_CERT_HOST_PATH}:/etc/nginx/certs/fullchain.pem:ro"), "TLS certificate chain must be mounted read-only into Nginx");
check("Production compose mounts TLS key read-only", composeProd.includes("${TLS_KEY_HOST_PATH}:/etc/nginx/certs/privkey.pem:ro"), "TLS private key must be mounted read-only into Nginx");
check("Production compose keeps local CORS disabled by default", composeProd.includes("APP_CORS_LOCAL_DEV_ENABLED=${APP_CORS_LOCAL_DEV_ENABLED:-false}"), "Local development CORS fallback should default to false");
check("Nginx redirects HTTP and serves HTTPS", nginx.includes("return 301 https://$host$request_uri") && nginx.includes("listen 443 ssl") && nginx.includes("ssl_protocols TLSv1.2 TLSv1.3"), "Nginx should redirect HTTP, negotiate TLS 1.2+, and serve the app over HTTPS");
check("Nginx proxies API internally", nginx.includes("location /api/") && nginx.includes("proxy_pass http://backend:3000/;"), "/api should proxy to backend:3000 internally after HTTPS termination");
check("Nginx serves runtime config without cache", nginx.includes("location = /config.js") && nginx.includes("no-store"), "config.js should not be cached");
check("Shell smoke checks frontend/backend/database", ["FRONTEND_URL", "BACKEND_URL", "/health", "/system/status", '"database":{"ok":true}'].every((item) => smokeSh.includes(item)), "prod-smoke-test.sh should verify frontend, backend and DB");
check("PowerShell smoke checks frontend/backend/database", ["FrontendUrl", "BackendUrl", "/health", "/system/status", "database.ok"].every((item) => smokePs.includes(item)), "prod-smoke-test.ps1 should verify frontend, backend and DB");
check("Shell backup creates and verifies PostgreSQL dump", ["pg_dump", "-Fc", "pg_restore", "-l", "postgres:16"].every((item) => backupSh.includes(item)), "backup-postgres.sh should create and verify a PostgreSQL backup");
check("PowerShell backup creates and verifies PostgreSQL dump", ["pg_dump", "-Fc", "pg_restore", "-l", "postgres:16"].every((item) => backupPs.includes(item)), "backup-postgres.ps1 should create and verify a PostgreSQL backup");
check("Shell restore drill restores only with explicit confirmation", ["RESTORE_FILE", "RESTORE_CONFIRM", "RESTORE_TO_TEST_DATABASE", "pg_restore", "--single-transaction", "--exit-on-error", "postgres:16"].every((item) => restoreSh.includes(item)) && !restoreSh.includes("--clean"), "restore-postgres.sh should restore atomically without deleting existing tables, after explicit test-database confirmation");
check("PowerShell restore drill restores only with explicit confirmation", ["RestoreFile", "RestoreConfirm", "RESTORE_TO_TEST_DATABASE", "pg_restore", "--single-transaction", "--exit-on-error", "postgres:16"].every((item) => restorePs.includes(item)) && !restorePs.includes("--clean"), "restore-postgres.ps1 should restore atomically without deleting existing tables, after explicit test-database confirmation");

const frontendUrl = value(env, "APP_FRONTEND_URL");
const corsOrigins = value(env, "APP_CORS_ALLOWED_ORIGINS");
const datasourceUrl = value(env, "SPRING_DATASOURCE_URL");
const jwtSecret = value(env, "JWT_SECRET");
const registrationBootstrapKey = value(env, "APP_AUTH_REGISTRATION_BOOTSTRAP_KEY");
const mfaEncryptionKey = value(env, "APP_AUTH_MFA_ENCRYPTION_KEY");
const ddlAuto = value(env, "SPRING_JPA_HIBERNATE_DDL_AUTO");
const schemaPatchEnabled = value(env, "APP_SCHEMA_PATCH_ENABLED");
const allowedDdlModes = ["none", "validate", "update", "create", "create-drop"];

check("VITE_API_URL uses same-origin /api", value(env, "VITE_API_URL") === "/api", "For EC2/Nginx deployment, frontend should call /api");
check("APP_FRONTEND_URL uses HTTPS", isHttpsUrl(frontendUrl), "APP_FRONTEND_URL should be a public HTTPS URL");
check("CORS origins use HTTPS", corsOrigins.split(",").every((item) => isHttpsUrl(item.trim())), "Every APP_CORS_ALLOWED_ORIGINS value should use HTTPS");
check("CORS includes frontend origin", corsOrigins.split(",").map((item) => item.trim()).includes(frontendUrl) || corsOrigins.split(",").map((item) => originOf(item.trim())).includes(originOf(frontendUrl)), "APP_CORS_ALLOWED_ORIGINS should include APP_FRONTEND_URL");
check("Production disables test data reset", value(env, "APP_TEST_DATA_RESET_ENABLED") === "false", "APP_TEST_DATA_RESET_ENABLED should be false");
check("Production disables bank reset", value(env, "APP_BANK_RECONCILIATION_RESET_ENABLED") === "false", "APP_BANK_RECONCILIATION_RESET_ENABLED should be false");
check("Production disables local CORS fallback", value(env, "APP_CORS_LOCAL_DEV_ENABLED") === "false", "APP_CORS_LOCAL_DEV_ENABLED should be false");
check("Datasource uses PostgreSQL JDBC", datasourceUrl.startsWith("jdbc:postgresql://"), "SPRING_DATASOURCE_URL should be PostgreSQL JDBC");
check("Datasource verifies PostgreSQL server identity", hasRequiredPostgresTls(datasourceUrl), "SPRING_DATASOURCE_URL must use sslmode=verify-full and sslrootcert=/run/certs/rds-ca-bundle.pem exactly once");
check("RDS CA bundle is mounted read-only", composeProd.includes("${RDS_CA_CERT_HOST_PATH}:/run/certs/rds-ca-bundle.pem:ro"), "Production compose must mount the current AWS RDS CA bundle read-only");
check("RDS CA bundle path is configured", hasValue(env, "RDS_CA_CERT_HOST_PATH"), "RDS_CA_CERT_HOST_PATH must point to the verified AWS RDS CA bundle on the host");
check("Hibernate ddl-auto mode is explicit", allowedDdlModes.includes(ddlAuto), "SPRING_JPA_HIBERNATE_DDL_AUTO should be one of none, validate, update, create or create-drop");
check("Production avoids automatic schema mutation", ["validate", "none"].includes(ddlAuto), "For production/RDS, prefer validate or none. Use update only during controlled migration rehearsal.");
check("Production disables startup schema patch", schemaPatchEnabled === "false", "APP_SCHEMA_PATCH_ENABLED should be false for production/RDS so startup does not run ALTER TABLE patches.");
check("JWT expiration is reasonable", Number(value(env, "JWT_EXPIRATION_MINUTES")) >= 15 && Number(value(env, "JWT_EXPIRATION_MINUTES")) <= 1440, "JWT_EXPIRATION_MINUTES should be between 15 and 1440");
check("Owner setup key is strong", registrationBootstrapKey.length >= 32 && !isPlaceholder(registrationBootstrapKey) && new Set(registrationBootstrapKey).size >= 12 && registrationBootstrapKey !== jwtSecret, "APP_AUTH_REGISTRATION_BOOTSTRAP_KEY should be a unique, non-placeholder secret with at least 32 characters", strict ? "fail" : "warn");
let decodedMfaKey;
try { decodedMfaKey = Buffer.from(mfaEncryptionKey, "base64"); } catch { decodedMfaKey = Buffer.alloc(0); }
check("MFA encryption key is configured", decodedMfaKey.length === 32 && new Set(decodedMfaKey).size >= 12 && !isPlaceholder(mfaEncryptionKey), "APP_AUTH_MFA_ENCRYPTION_KEY must decode from base64 to 32 random bytes", strict ? "fail" : "warn");
check("Production requires MFA for application data", value(env, "APP_AUTH_REQUIRE_MFA") === "true", "APP_AUTH_REQUIRE_MFA must be true for protected AliBooks API routes", strict ? "fail" : "warn");

const stripeKey = value(env, "STRIPE_SECRET_KEY");
const stripeWebhook = value(env, "STRIPE_WEBHOOK_SECRET");
const smtpHost = value(env, "SPRING_MAIL_HOST");
const smtpUser = value(env, "SPRING_MAIL_USERNAME");
const smtpPassword = value(env, "SPRING_MAIL_PASSWORD");

check("Stripe key format is recognizable when configured", !stripeKey || isPlaceholder(stripeKey) || /^sk_(test|live)_/.test(stripeKey), "Stripe key should start with sk_test_ or sk_live_", "warn");
check("Stripe webhook format is recognizable when configured", !stripeWebhook || isPlaceholder(stripeWebhook) || stripeWebhook.startsWith("whsec_"), "Stripe webhook secret should start with whsec_", "warn");
check("SMTP config is complete when enabled", !smtpHost || (hasValue(env, "SPRING_MAIL_PORT") && smtpUser && smtpPassword), "SMTP host, port, username and password should be configured together", "warn");

if (strict) {
  check("Strict env does not use placeholders", [...env.entries()].every(([, current]) => !isPlaceholder(current)), "Replace all placeholder values before production");
  check("Strict APP_FRONTEND_URL is not localhost", !/localhost|127\.0\.0\.1/i.test(frontendUrl), "Production frontend URL should be public");
  check("Strict CORS is not localhost", !/localhost|127\.0\.0\.1/i.test(corsOrigins), "Production CORS should be public frontend origin only");
  check("Strict datasource is not local Docker db", !/localhost|127\.0\.0\.1|\/\/db:/i.test(datasourceUrl), "Production datasource should point to RDS or managed PostgreSQL");
  check("Strict JWT secret is strong", jwtSecret.length >= 32 && !isPlaceholder(jwtSecret), "JWT_SECRET should be a unique secret with at least 32 characters");
  check("Strict Dockerhub username is set", !isPlaceholder(value(env, "DOCKERHUB_USERNAME")), "DOCKERHUB_USERNAME should be the real Dockerhub account");
} else {
  check("Template JWT secret explains length", jwtSecret.includes("32") || jwtSecret.length >= 32, "Template should remind about a 32+ character JWT secret");
}

for (const result of checks) {
  const marker = result.ok ? "OK" : result.severity === "warn" ? "WARN" : "FAIL";
  console.log(`${marker} - ${result.name}: ${result.detail}`);
}

const failures = checks.filter((result) => !result.ok && result.severity === "fail");
const warnings = checks.filter((result) => !result.ok && result.severity === "warn");

console.log("");
console.log(`Production readiness check: ${checks.length - failures.length - warnings.length}/${checks.length} required checks passed.`);

if (warnings.length > 0) {
  console.log(`${warnings.length} warning(s) need review.`);
}

if (failures.length > 0) {
  console.error(`${failures.length} required production readiness check(s) failed.`);
  process.exit(1);
}
