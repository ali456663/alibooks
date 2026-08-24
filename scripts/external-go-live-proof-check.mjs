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

const proofPath = "docs/externa-go-live-bevis.md";
check("External go-live proof document exists", exists(proofPath), proofPath);

const proof = exists(proofPath) ? read(proofPath) : "";
const riskRegister = read("docs/go-live-riskregister.md");
const goLiveDecision = read("docs/go-live-beslut.md");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const ciHandoff = read("docs/ci-handoff-efter-push.md");
const prodCheck = read("scripts/production-readiness-check.mjs");
const releaseGate = read("scripts/release-gate.mjs");
const rootPackage = JSON.parse(read("package.json"));
const frontendPackage = JSON.parse(read("frontend/package.json"));

const requiredProofAreas = [
  "GitHub sync",
  "GitHub Actions",
  "Dockerhub",
  "EC2 publik app",
  "RDS PostgreSQL",
  "Schema",
  "Backup",
  "Stripe",
  "SMTP",
  "Bank/Swish/kort",
  "AI och data",
  "Redovisningspaket"
];

for (const area of requiredProofAreas) {
  check(`External proof area documented: ${area}`, proof.includes(area), "Every production blocker should have an external proof row.");
}

check(
  "Proof document separates local MVP from production",
  includesAll(proof, ["Lokal MVP", "Skarp drift", "far inte blandas ihop"]),
  "The document should prevent confusing localhost success with production readiness."
);

check(
  "Proof document has concrete command order",
  includesAll(proof, ["npm run check:release:full", "npm run check:external-go-live", "git push", "npm run check:sync", "npm run check:prod -- --env-file ../.env --strict"]),
  "Go-live proof should be runnable and reviewable."
);

check(
  "Proof document protects RDS schema",
  includesAll(proof, ["SPRING_JPA_HIBERNATE_DDL_AUTO=validate", "APP_SCHEMA_PATCH_ENABLED=false", "granskad schemafil"]),
  "Production database schema must be controlled before real data."
);

check(
  "Proof document requires backup restore drill",
  includesAll(proof, ["pg_dump", "pg_restore -l", "restore drill"]),
  "Backups are not real proof until restore has been rehearsed."
);

check(
  "Proof document requires Stripe webhook proof",
  includesAll(proof, ["checkout.session.completed", "bokforingen balanserar"]),
  "Stripe should not be considered done until webhook accounting is proven."
);

check(
  "Proof document requires SMTP proof",
  includesAll(proof, ["Testmail", "faktura", "paminnelsemail", "audit"]),
  "Email should not be considered done until real delivery and history are proven."
);

check(
  "Proof document protects AI personal data",
  includesAll(proof, ["AI-sakert lage", "minimerad", "anonymiserad", "Personnummer"]),
  "External AI use must not leak direct personal identifiers."
);

check(
  "Risk register links external blockers",
  includesAll(riskRegister, ["BLOCKERAR SKARP DRIFT", "GitHub sync", "Dockerhub images", "RDS databas", "Stripe betalningar", "SMTP e-post"]),
  "Risk register should agree with the external proof gate."
);

check(
  "Go-live decision references external proof",
  goLiveDecision.includes("GitHub Actions") && goLiveDecision.includes("Dockerhub") && goLiveDecision.includes("RDS") && goLiveDecision.includes("Stripe") && goLiveDecision.includes("SMTP"),
  "Final decision should still require external services before production."
);

check(
  "Release evidence references external blockers",
  includesAll(releaseEvidence, ["GitHub Actions", "Dockerhub workflow", "EC2", "RDS", "restore drill", "check:external-go-live"]),
  "Release evidence should show that external production blockers remain visible."
);

check(
  "Roadmap keeps external integrations after local MVP",
  includesAll(roadmap, ["MVP-flode", "Stripe-flode", "Moln och publik URL", "GitHub Actions", "RDS"]),
  "Roadmap should keep the local-to-cloud order explicit."
);

check(
  "CI handoff explains after-push review",
  includesAll(ciHandoff, ["git push", "GitHub Actions", "Dockerhub", "check:sync"]),
  "After push, the user needs an explicit CI review routine."
);

check(
  "Production readiness can run strict env check",
  includesAll(prodCheck, ["--env-file", "--strict", "APP_CORS_ALLOWED_ORIGINS", "APP_FRONTEND_URL", "SPRING_DATASOURCE_URL"]),
  "Production env should be checked before EC2/RDS use."
);

check(
  "Root package exposes external go-live check",
  rootPackage.scripts?.["check:external-go-live"] === "npm --prefix frontend run check:external-go-live --",
  "Root package should expose npm run check:external-go-live."
);

check(
  "Frontend package exposes external go-live check",
  frontendPackage.scripts?.["check:external-go-live"] === "node ../scripts/external-go-live-proof-check.mjs",
  "Frontend package should expose npm run check:external-go-live."
);

check(
  "Release gate runs external go-live proof check",
  releaseGate.includes('"check:external-go-live"'),
  "Release gate should fail if external proof documentation disappears."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`External go-live proof check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} external go-live proof check(s) failed.`);
  process.exit(1);
}
