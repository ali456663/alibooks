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

function includesAll(source, terms) {
  return terms.every((term) => source.includes(term));
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

const docPath = "docs/revisionsspar-integritet.md";
const doc = exists(docPath) ? read(docPath) : "";
const auditService = read("backend/src/main/java/se/cloudshop/audit/AuditService.java");
const auditController = read("backend/src/main/java/se/cloudshop/audit/AuditController.java");
const auditEvent = read("backend/src/main/java/se/cloudshop/audit/AuditEvent.java");
const auditTest = read("backend/src/test/java/se/cloudshop/audit/AuditServiceTest.java");
const frontend = read("frontend/src/main.jsx");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidenceCheck = read("scripts/mvp-evidence-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const backupCheck = read("scripts/backup-readiness-check.mjs");
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");

check(
  "Audit integrity document exists",
  includesAll(doc, ["revisionsspar", "SHA-256", "auditstampel", "Stoppsignaler", "check:audit-integrity"]),
  "docs/revisionsspar-integritet.md should describe audit trail, chain hashes, exports and stop signals."
);

check(
  "Audit command exists in root package",
  rootPackage.scripts?.["check:audit-integrity"] === "npm --prefix frontend run check:audit-integrity --",
  "Root package should expose npm run check:audit-integrity."
);

check(
  "Audit command exists in frontend package",
  frontendPackage.scripts?.["check:audit-integrity"] === "node ../scripts/audit-integrity-check.mjs",
  "Frontend package should expose npm run check:audit-integrity."
);

check(
  "Release gate runs audit integrity check",
  releaseGate.includes('"check:audit-integrity"'),
  "Release gate should fail if audit integrity proof disappears."
);

check(
  "Readiness requires audit integrity artifacts",
  includesAll(readiness, [docPath, "scripts/audit-integrity-check.mjs", "check:audit-integrity"]),
  "Readiness should require audit integrity docs, script and command."
);

check(
  "Evidence check counts audit integrity output",
  includesAll(evidenceCheck, ["audit-integrity-check.mjs", "AliBooks audit integrity check"]),
  "MVP evidence should verify the current audit integrity check count."
);

check(
  "Release evidence documents audit proof",
  releaseEvidence.includes("check:audit-integrity") && releaseEvidence.includes("revisionsspar"),
  "Release evidence should keep audit integrity proof visible."
);

check(
  "Roadmap documents audit integrity command",
  roadmap.includes("npm run check:audit-integrity") && roadmap.includes("revisionsspar"),
  "Roadmap should tell the user to run the audit integrity gate."
);

check(
  "Audit service builds a SHA-256 row hash",
  includesAll(auditService, ["MessageDigest.getInstance(\"SHA-256\")", "rowHash", "value(event.getEventType())", "value(event.getActorEmail())"]),
  "Audit rows should produce deterministic SHA-256 row hashes from important fields."
);

check(
  "Audit service chains each event",
  includesAll(auditService, ["previousChainHash = \"START\"", "chainHash", "index + 1", "previousChainHash = chainHash"]),
  "Audit chain should bind each row to the previous chain hash."
);

check(
  "Audit service creates whole-report fingerprint",
  includesAll(auditService, ["auditFingerprint", "events.size()", "firstChainHash", "finalChainHash"]),
  "Audit report should produce a fingerprint over event count and chain boundaries."
);

check(
  "Audit event stores traceable fields",
  includesAll(auditEvent, ["eventType", "entityType", "entityId", "action", "reference", "message", "amount", "actorEmail", "createdAt"]),
  "Audit event should include actor, time, object, action, reference and amount."
);

check(
  "Audit endpoints require JWT",
  includesAll(auditController, [
    "@GetMapping(\"/audit-events\")",
    "@GetMapping(\"/audit-events/integrity\")",
    "@GetMapping(\"/audit-events/export\")",
    "@GetMapping(\"/audit-events/integrity/export\")",
    "authHeader.requireValidToken(authorizationHeader)"
  ]),
  "Audit list, integrity and exports should require authentication."
);

check(
  "Audit exports include control values",
  includesAll(auditController, ["revisionsspar.csv", "revisionsspar-integritet.csv", "Backend auditstampel", "Slutlig kedjekod", "Auditstampel"]),
  "Audit CSV exports should include fingerprint and final chain hash."
);

check(
  "Audit exports are themselves audited",
  includesAll(auditController, ["audit_integrity_exported", "audit_events_exported", "auditService.record("]),
  "Exporting audit data should create audit events too."
);

check(
  "Backend test covers chained audit report",
  includesAll(auditTest, ["createsAuditIntegrityChainForEvents", "previousChainHash", "auditFingerprint"]),
  "Backend tests should prove basic audit chain creation."
);

check(
  "Backend test covers tamper-sensitive fingerprint",
  auditTest.includes("changingAuditEventContentChangesFingerprint"),
  "Backend tests should prove that changed event content changes the fingerprint."
);

check(
  "Backend test covers actor from JWT",
  auditTest.includes("recordsActorEmailFromJwtSubject"),
  "Backend tests should prove audit actor is taken from JWT subject."
);

check(
  "Frontend loads audit trail and integrity",
  includesAll(frontend, ["/audit-events", "/audit-events/integrity", "auditIntegrityReport", "Auditstampel", "Audit slutlig kedjekod"]),
  "Frontend should expose audit trail and backend integrity values."
);

check(
  "Frontend exports audit evidence",
  includesAll(frontend, ["/audit-events/export", "/audit-events/integrity/export", "revisionsspar.csv", "revisionsspar-integritet.csv"]),
  "Frontend should allow audit trail and audit integrity exports."
);

check(
  "Backup includes audit integrity proof",
  includesAll(frontend, ["integrityProofs", "auditTrail", "auditFingerprint", "finalChainHash", "integrityManifest"]),
  "Local backup should include audit fingerprint, final chain hash and manifest data."
);

check(
  "Backup readiness checks audit proof",
  includesAll(backupCheck, ["auditFingerprint", "finalChainHash", "auditTrail", "integrityManifest"]),
  "Backup readiness should fail if audit integrity proof disappears."
);

const failed = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks audit integrity check: ${checks.length - failed.length}/${checks.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} audit integrity check(s) failed.`);
  process.exit(1);
}
