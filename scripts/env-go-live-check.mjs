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

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

const docPath = "docs/miljovariabler-go-live.md";
const doc = exists(docPath) ? read(docPath) : "";
const applicationProperties = read("backend/src/main/resources/application.properties");
const productionCheck = read("scripts/production-readiness-check.mjs");
const secretCheck = read("scripts/secret-placeholder-check.mjs");
const riskRegister = read("docs/go-live-riskregister.md");
const goLiveDecision = read("docs/go-live-beslut.md");
const externalProof = read("docs/externa-go-live-bevis.md");
const firstRealData = read("docs/forsta-riktiga-data.md");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const mainSource = read("frontend/src/main.jsx");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");

const requiredEnv = [
  "APP_FRONTEND_URL",
  "VITE_API_URL",
  "APP_CORS_ALLOWED_ORIGINS",
  "APP_CORS_LOCAL_DEV_ENABLED",
  "SPRING_DATASOURCE_URL",
  "SPRING_DATASOURCE_USERNAME",
  "SPRING_DATASOURCE_PASSWORD",
  "SPRING_JPA_HIBERNATE_DDL_AUTO",
  "APP_SCHEMA_PATCH_ENABLED",
  "JWT_SECRET",
  "JWT_EXPIRATION_MINUTES",
  "APP_TEST_DATA_RESET_ENABLED",
  "APP_BANK_RECONCILIATION_RESET_ENABLED",
  "STRIPE_SECRET_KEY",
  "STRIPE_WEBHOOK_SECRET",
  "SPRING_MAIL_HOST",
  "SPRING_MAIL_PORT",
  "SPRING_MAIL_USERNAME",
  "SPRING_MAIL_PASSWORD",
  "SPRING_MAIL_SMTP_AUTH",
  "SPRING_MAIL_SMTP_STARTTLS_ENABLE",
  "GEMINI_API_KEY",
  "HF_TOKEN",
  "AI_OPENAI_API_KEY"
];

const productionRuntimeEnv = requiredEnv.filter((key) => ![
  "GEMINI_API_KEY",
  "HF_TOKEN",
  "AI_OPENAI_API_KEY",
  "SPRING_MAIL_SMTP_AUTH",
  "SPRING_MAIL_SMTP_STARTTLS_ENABLE"
].includes(key));

check("Environment go-live document exists", exists(docPath), docPath);
check("Document names all critical env vars", includesAll(doc, requiredEnv), "Go-live env docs should list all critical runtime knobs.");
check("Document separates local and production", includesAll(doc, ["Lokal utveckling", "Produktion eller publik demo"]), "The user should see different local and production rules.");
check("Document forbids secrets in frontend and GitHub", includesAll(doc, ["aldrig ligga i frontend", "GitHub", "secret manager"]), "Secret placement should be explicit.");
check("Document requires public production URL", includesAll(doc, ["publik frontend-URL", "APP_CORS_ALLOWED_ORIGINS", "APP_CORS_LOCAL_DEV_ENABLED=false"]), "Production origin rules should be explicit.");
check("Document requires RDS or managed PostgreSQL", includesAll(doc, ["RDS", "hanterad PostgreSQL", "localhost"]), "Database target should be explicit.");
check("Document requires safe schema mode", includesAll(doc, ["SPRING_JPA_HIBERNATE_DDL_AUTO=validate", "APP_SCHEMA_PATCH_ENABLED=false"]), "Production schema mutation should be blocked.");
check("Document requires strong JWT", includesAll(doc, ["JWT_SECRET", "minst 32 tecken"]), "JWT strength should be visible.");
check("Document covers Stripe formats", includesAll(doc, ["sk_test_", "sk_live_", "whsec_"]), "Stripe key and webhook formats should be visible.");
check("Document covers SMTP group", includesAll(doc, ["SPRING_MAIL_HOST", "SPRING_MAIL_PORT", "SPRING_MAIL_USERNAME", "SPRING_MAIL_PASSWORD"]), "SMTP settings should be configured together.");
check("Document covers AI safe mode", includesAll(doc, ["AI-sakert lage", "anonymiserad", "personnummer", "extern AI"]), "AI provider settings should be tied to privacy.");
check("Document has stop signals", includesAll(doc, ["Stoppa direkt", "change_me_in_production", "localhost", "update", "APP_SCHEMA_PATCH_ENABLED=true"]), "Unsafe env states should stop go-live.");
check("Document shows control commands", includesAll(doc, ["npm run check:env-go-live", "npm run check:prod -- --env-file", "--strict"]), "User should get copy-pasteable verification commands.");

check("Backend exposes env placeholders", includesAll(applicationProperties, requiredEnv.filter((key) => key !== "VITE_API_URL")), "Backend properties should be driven by env vars.");
check("Production check validates required vars", includesAll(productionCheck, productionRuntimeEnv), "Production check should validate runtime env.");
check("Production check validates same-origin API", includesAll(productionCheck, ["VITE_API_URL", "/api"]), "Frontend API routing should be checked.");
check("Production check validates CORS hardening", includesAll(productionCheck, ["APP_CORS_ALLOWED_ORIGINS", "APP_CORS_LOCAL_DEV_ENABLED", "Strict CORS is not localhost"]), "CORS hardening should be checked.");
check("Production check validates JWT strength", includesAll(productionCheck, ["Strict JWT secret is strong", "32"]), "JWT strength should be checked.");
check("Production check validates schema safety", includesAll(productionCheck, ["validate", "none", "APP_SCHEMA_PATCH_ENABLED should be false"]), "Schema safety should be checked.");
check("Production check validates Stripe and SMTP", includesAll(productionCheck, ["sk_test_", "sk_live_", "whsec_", "SMTP config is complete"]), "Integration config should be checked.");
check("Secret scanner knows external provider keys", includesAll(secretCheck, ["OpenRouter API key", "Google/Gemini API key", "Hugging Face token", "Stripe secret key"]), "Secret scanner should block real provider keys.");
check("Security view surfaces env risks", includesAll(mainSource, ["JWT_SECRET", "APP_CORS_ALLOWED_ORIGINS", "APP_CORS_LOCAL_DEV_ENABLED", "Stripe", "SMTP", "AI-sakert lage"]), "Frontend security view should explain env warnings.");
check("Risk register includes env blockers", includesAll(riskRegister, ["CORS", "RDS", "Stripe", "SMTP", "AI", "APP_SCHEMA_PATCH_ENABLED=false"]), "Go-live risk register should include env-sensitive blockers.");
check("Go-live decision includes external blockers", includesAll(goLiveDecision, ["Stripe", "SMTP", "RDS", "AI", "GitHub Actions"]), "Final go-live decision should match env blockers.");
check("External proof requires integration evidence", includesAll(externalProof, ["Stripe", "SMTP", "RDS", "GitHub", "Dockerhub"]), "External proof should require integration evidence.");
check("First real data doc points to env safety", includesAll(firstRealData, ["API-nycklar", "GitHub", "check:first-real-data"]), "First real data gate should stop leaked or unsafe keys.");
check("Frontend exposes env-go-live check", frontendPackage.scripts?.["check:env-go-live"] === "node ../scripts/env-go-live-check.mjs", "frontend/package.json should expose npm run check:env-go-live.");
check("Root exposes env-go-live check", rootPackage.scripts?.["check:env-go-live"] === "npm --prefix frontend run check:env-go-live --", "package.json should expose npm run check:env-go-live.");
check("Release gate runs env-go-live check", releaseGate.includes('"check:env-go-live"'), "Release gate should fail if env-go-live proof disappears.");
check("Readiness requires env-go-live check", includesAll(readiness, [docPath, "scripts/env-go-live-check.mjs", "check:env-go-live"]), "Readiness should protect env-go-live docs and script.");
check("Evidence requires env-go-live check", includesAll(evidence, ["scripts/env-go-live-check.mjs", "check:env-go-live"]), "Evidence should keep env-go-live proof fresh.");
check("Release evidence documents env-go-live check", releaseEvidence.includes("check:env-go-live"), "Release evidence should record env-go-live proof.");
check("Roadmap documents env-go-live check", roadmap.includes("npm run check:env-go-live"), "Roadmap should show the env-go-live command.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks env go-live check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} env go-live check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Miljo variabler: OK lokalt. Skarp drift kraver riktig env-fil, strict production check och externa bevis.");
