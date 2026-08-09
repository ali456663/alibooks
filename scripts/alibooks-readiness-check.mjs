import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const results = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function exists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function check(name, ok, detail, severity = "fail") {
  results.push({ name, ok: Boolean(ok), detail, severity });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

const requiredFiles = [
  "backend/pom.xml",
  "frontend/package.json",
  "docker-compose.yml",
  "docker-compose.prod.yml",
  ".github/workflows/ci.yml",
  ".github/workflows/dockerhub.yml",
  ".env.example",
  ".env.production.example",
  "docs/mvp-testprotokoll.md",
  "docs/roadmap-kvar.md",
  "docs/professionell-bokforing-loop.md",
  "docs/release-evidence.md",
  "docs/backup-restore-runbook.md",
  "docs/go-live-riskregister.md"
];

for (const file of requiredFiles) {
  check(`Required file: ${file}`, exists(file), file);
}

const frontendPackage = json("frontend/package.json");
check(
  "Frontend scripts",
  includesAll(Object.keys(frontendPackage.scripts || {}).join("\n"), [
    "build",
    "check:api-contract",
    "check:release",
    "check:ready",
    "check:backend-wiring",
    "check:backup",
    "check:ci",
    "check:docs",
    "check:docker",
    "check:data-safety",
    "check:git",
    "check:go-live-risks",
    "check:prod",
    "smoke:runtime",
    "check:professional-loop",
    "check:secrets",
    "check:sync",
    "check:views",
    "test:backend"
  ]),
  "build, check:api-contract, check:release, check:ready, check:backend-wiring, check:backup, check:ci, check:docs, check:docker, check:data-safety, check:git, check:go-live-risks, check:prod, smoke:runtime, check:professional-loop, check:secrets, check:sync, check:views and test:backend should exist"
);

const ci = read(".github/workflows/ci.yml");
const releaseGate = read("scripts/release-gate.mjs");
check("CI runs backend tests", ci.includes("mvn test"), "GitHub Actions should run mvn test");
check("CI runs frontend release gate", ci.includes("npm run check:release"), "GitHub Actions should run the same frontend release gate as local verification");
check("Release gate builds frontend", releaseGate.includes('"build"'), "Release gate should run frontend build");
check("Release gate runs frontend smoke", releaseGate.includes('"smoke:runtime"'), "Release gate should run smoke:runtime");
check("Release gate checks professional loop", releaseGate.includes('"check:professional-loop"'), "Release gate should run professional-loop check");
check("Release gate checks API contract", releaseGate.includes('"check:api-contract"'), "Release gate should run API contract check");
check("Release gate checks AliBooks readiness", releaseGate.includes('"check:ready"'), "Release gate should run check:ready");
check("Release gate checks backend wiring", releaseGate.includes('"check:backend-wiring"'), "Release gate should run backend wiring check");
check("Release gate checks backup readiness", releaseGate.includes('"check:backup"'), "Release gate should run backup readiness check");
check("Release gate checks CI pipeline", releaseGate.includes('"check:ci"'), "Release gate should run GitHub Actions pipeline check");
check("Release gate checks docs consistency", releaseGate.includes('"check:docs"'), "Release gate should run documentation consistency check");
check("Release gate checks committed secrets", releaseGate.includes('"check:secrets"'), "Release gate should run secret placeholder check");
check("Release gate checks destructive data safety", releaseGate.includes('"check:data-safety"'), "Release gate should run destructive data safety check");
check("Release gate checks Docker config", releaseGate.includes('"check:docker"'), "Release gate should run Docker config check");
check("Release gate checks production readiness", releaseGate.includes('"check:prod"'), "Release gate should run production readiness check");
check("Release gate checks go-live risks", releaseGate.includes('"check:go-live-risks"'), "Release gate should run go-live risk register check");
check("Release gate checks frontend views", releaseGate.includes('"check:views"'), "Release gate should run view route check");
check("CI builds Docker images", includesAll(ci, ["docker build -t cloudshop-backend:ci", "cloudshop-frontend:ci"]), "CI should build backend and frontend Docker images");

const dockerhub = read(".github/workflows/dockerhub.yml");
check("Dockerhub workflow pushes images", includesAll(dockerhub, ["docker/login-action", "docker/build-push-action", "cloudshop-backend", "cloudshop-frontend"]), "Dockerhub workflow should login, build and push both images");

const envExample = read(".env.example");
const prodEnvExample = read(".env.production.example");
const requiredEnvVars = [
  "JWT_SECRET",
  "JWT_EXPIRATION_MINUTES",
  "SPRING_DATASOURCE_URL",
  "APP_FRONTEND_URL",
  "APP_CORS_ALLOWED_ORIGINS",
  "STRIPE_SECRET_KEY",
  "STRIPE_WEBHOOK_SECRET",
  "SPRING_MAIL_HOST",
  "SPRING_MAIL_USERNAME",
  "SPRING_MAIL_PASSWORD",
  "VITE_API_URL"
];
check("Local env template has required variables", includesAll(envExample, requiredEnvVars), requiredEnvVars.join(", "));
check("Production env template has required variables", includesAll(prodEnvExample, requiredEnvVars.filter((item) => item !== "VITE_API_URL")), requiredEnvVars.join(", "));

const possibleSecretPatterns = [
  /sk_live_[A-Za-z0-9]{12,}/,
  /sk_test_[A-Za-z0-9]{12,}/,
  /whsec_[A-Za-z0-9]{12,}/,
  /hf_[A-Za-z0-9]{20,}/,
  /AIzaSy[A-Za-z0-9_-]{20,}/
];
const envText = `${envExample}\n${prodEnvExample}`;
check(
  "Env templates do not contain real-looking secrets",
  !possibleSecretPatterns.some((pattern) => pattern.test(envText)),
  "Use placeholders in .env.example files, not real keys"
);

const mainSource = read("frontend/src/main.jsx");
check("Professional 20-step plan exists", mainSource.includes("const professionalActionPlanRows = ["), "Frontend should expose the professional action plan");
check("Go-live view exists", mainSource.includes('activeView === "goLive"'), "Frontend should expose Startklar/Go-live");
check("Compliance view exists", mainSource.includes('activeView === "compliance"'), "Frontend should expose Regelkontroll/Compliance");
check("Payment compliance warning exists", mainSource.includes("electronicPaymentNeedsCashRegisterReview"), "Stripe/Swish/card rule should be visible before production use");
check("AI assistant exists", mainSource.includes("aiAssistantMessages"), "AI assistant state should exist");
check("Runtime crash fallback exists", mainSource.includes("AliBooks kunde inte visa sidan"), "Frontend should have a render recovery fallback");

const backendFiles = [
  "backend/src/main/java/se/cloudshop/system/HealthController.java",
  "backend/src/main/java/se/cloudshop/config/SecurityHeadersFilter.java",
  "backend/src/main/java/se/cloudshop/accounting/AccountingService.java",
  "backend/src/main/java/se/cloudshop/accounting/AccountingPeriodLockService.java",
  "backend/src/main/java/se/cloudshop/audit/AuditService.java",
  "backend/src/main/java/se/cloudshop/payment/StripePaymentService.java"
];
for (const file of backendFiles) {
  check(`Backend capability: ${path.basename(file)}`, exists(file), file);
}

const releaseEvidence = read("docs/release-evidence.md");
const backupRunbook = read("docs/backup-restore-runbook.md");
const riskRegister = read("docs/go-live-riskregister.md");
const docs = `${read("docs/mvp-testprotokoll.md")}\n${read("docs/roadmap-kvar.md")}\n${read("docs/professionell-bokforing-loop.md")}\n${releaseEvidence}\n${backupRunbook}\n${riskRegister}`;
check("Docs include MVP flow", includesAll(docs, ["registrera", "logga in", "faktura", "betalning", "momsrapport"]), "Docs should cover the main MVP flow");
check("Docs include cloud/deployment path", includesAll(docs, ["EC2", "RDS", "GitHub Actions", "Dockerhub"]), "Docs should cover public cloud demo and CI/CD");
check("Docs include cash/card payment review", includesAll(docs, ["kort", "Swish", "Stripe"]), "Docs should remind about electronic payment review");
check("Release evidence includes full local gate", releaseEvidence.includes("npm run check:release -- --with-backend --with-docker-build"), "Release evidence should document the full local verification command");
check("Docs include backup and restore drill", includesAll(docs, ["pg_dump", "pg_restore", "restore drill", "RDS snapshot"]), "Docs should cover backup creation, verification and restore drill");
check("Docs include go-live risk register", includesAll(riskRegister, ["GitHub Actions CI", "Dockerhub images", "RDS databas", "BLOCKERAR SKARP DRIFT"]), "Docs should track production blockers explicitly");

const failed = results.filter((result) => !result.ok && result.severity === "fail");
const warnings = results.filter((result) => !result.ok && result.severity === "warn");

for (const result of results) {
  const marker = result.ok ? "OK" : result.severity === "warn" ? "WARN" : "FAIL";
  console.log(`${marker} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks readiness check: ${results.length - failed.length - warnings.length}/${results.length} required checks passed.`);

if (warnings.length > 0) {
  console.log(`${warnings.length} warning(s) need review.`);
}

if (failed.length > 0) {
  console.error(`${failed.length} required readiness check(s) failed.`);
  process.exit(1);
}
