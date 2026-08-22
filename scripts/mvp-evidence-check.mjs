import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
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

const evidence = read("docs/release-evidence.md");
const riskRegister = read("docs/go-live-riskregister.md");
const packageJson = JSON.parse(read("frontend/package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const prePushGate = read("scripts/pre-push-gate.mjs");

const readinessResult = spawnSync(process.execPath, ["scripts/alibooks-readiness-check.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
  shell: false
});
const readinessOutput = `${readinessResult.stdout || ""}\n${readinessResult.stderr || ""}`;
const readinessActualMatch = readinessOutput.match(/AliBooks readiness check:\s*(\d+)\/(\d+)/);
const readinessActualTotal = readinessActualMatch ? Number(readinessActualMatch[2]) : 0;
const readinessEvidenceMatch = evidence.match(/`check:ready`:\s*(\d+)\/(\d+)/);
const readinessEvidenceTotal = readinessEvidenceMatch ? Number(readinessEvidenceMatch[2]) : 0;

function runNodeScript(relativePath) {
  const result = spawnSync(process.execPath, [relativePath], {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false
  });
  return {
    status: result.status,
    output: `${result.stdout || ""}\n${result.stderr || ""}`
  };
}

function countFromOutput(output, label) {
  const match = output.match(new RegExp(`${label}:\\s*(\\d+)\\/(\\d+)`));
  return match ? Number(match[2]) : 0;
}

function countFromEvidence(pattern) {
  const match = evidence.match(pattern);
  return match ? Number(match[2]) : 0;
}

const countedEvidenceChecks = [
  {
    name: "Data safety evidence count matches script",
    script: "scripts/data-safety-check.mjs",
    outputLabel: "AliBooks data safety check",
    evidencePattern: /`check:data-safety`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Production readiness evidence count matches script",
    script: "scripts/production-readiness-check.mjs",
    outputLabel: "Production readiness check",
    evidencePattern: /`check:prod`:\s*(\d+)\/(\d+)/
  },
  {
    name: "CI evidence count matches script",
    script: "scripts/ci-pipeline-check.mjs",
    outputLabel: "AliBooks CI pipeline check",
    evidencePattern: /`check:ci`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Schema policy evidence count matches script",
    script: "scripts/schema-policy-check.mjs",
    outputLabel: "Schema policy check",
    evidencePattern: /`check:schema`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Schema migration evidence count matches script",
    script: "scripts/schema-migration-check.mjs",
    outputLabel: "Schema migration check",
    evidencePattern: /`check:migrations`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Schema bootstrap evidence count matches script",
    script: "scripts/schema-bootstrap-check.mjs",
    outputLabel: "Schema bootstrap check",
    evidencePattern: /`check:schema-bootstrap`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Backup evidence count matches script",
    script: "scripts/backup-readiness-check.mjs",
    outputLabel: "AliBooks backup readiness check",
    evidencePattern: /`npm run check:backup`:\s*passed,\s*(\d+)\/(\d+)/
  },
  {
    name: "Go-live risk evidence count matches script",
    script: "scripts/go-live-risk-check.mjs",
    outputLabel: "Go-live risk check",
    evidencePattern: /`check:go-live-risks`:\s*(\d+)\/(\d+)/
  }
];

check(
  "Readiness script runs for evidence check",
  readinessResult.status === 0 && readinessActualTotal > 0,
  "MVP evidence should be checked against the current readiness script output."
);

check(
  "Release evidence documents full gate",
  evidence.includes("npm run check:release:full"),
  "The release evidence should name the strongest local verification command."
);

check(
  "Release evidence readiness count matches script",
  readinessEvidenceTotal === readinessActualTotal,
  `release-evidence has ${readinessEvidenceTotal || "missing"} readiness checks; current readiness output has ${readinessActualTotal || "missing"}.`
);

for (const countedCheck of countedEvidenceChecks) {
  const result = runNodeScript(countedCheck.script);
  const actualTotal = countFromOutput(result.output, countedCheck.outputLabel);
  const evidenceTotal = countFromEvidence(countedCheck.evidencePattern);
  check(
    countedCheck.name,
    result.status === 0 && actualTotal > 0 && evidenceTotal === actualTotal,
    `release-evidence has ${evidenceTotal || "missing"} checks; current script output has ${actualTotal || "missing"}.`
  );
}

const backendTestEvidence = evidence.match(/(\d+) tests,\s*0 failures,\s*0 errors/);
check(
  "Release evidence documents backend tests",
  backendTestEvidence && Number(backendTestEvidence[1]) >= 187,
  "Backend test proof should include at least 187 tests and zero failures/errors."
);

check(
  "Release evidence documents Docker image proof",
  includesAll(evidence, ["alibooks-backend:release-gate", "alibooks-frontend:release-gate"]),
  "Local Docker image build evidence should list backend and frontend images."
);

check(
  "Release evidence links go-live risk register",
  evidence.includes("go-live-riskregister.md") && evidence.includes("check:go-live-risks"),
  "Remaining go-live risk evidence should be connected to the automated risk check."
);

check(
  "Release evidence keeps external blockers visible",
  includesAll(evidence, ["GitHub Actions", "Dockerhub workflow", "EC2", "RDS", "restore drill"]),
  "The evidence file should not claim production readiness before external checks are proven."
);

check(
  "Risk register separates local MVP from production",
  includesAll(riskRegister, ["nara MVP-klar lokalt", "inte skarp produktionsklar", "externt bevis"]),
  "The risk register should make the local-vs-production boundary explicit."
);

check(
  "Package exposes evidence check",
  packageJson.scripts?.["check:evidence"] === "node ../scripts/mvp-evidence-check.mjs",
  "frontend/package.json should expose npm run check:evidence."
);

check(
  "Package exposes acceptance check",
  packageJson.scripts?.["check:acceptance"] === "node ../scripts/mvp-acceptance-check.mjs",
  "frontend/package.json should expose npm run check:acceptance."
);

check(
  "Package exposes pre-push gate",
  packageJson.scripts?.["check:prepush"] === "node ../scripts/pre-push-gate.mjs",
  "frontend/package.json should expose npm run check:prepush."
);

check(
  "Package exposes git parser check",
  packageJson.scripts?.["check:git-parser"] === "node ../scripts/git-release-status.mjs --self-test",
  "frontend/package.json should expose npm run check:git-parser."
);

check(
  "Package exposes dependency risk check",
  packageJson.scripts?.["check:dependencies"] === "node ../scripts/dependency-risk-check.mjs",
  "frontend/package.json should expose npm run check:dependencies."
);

check(
  "Package exposes release traceability check",
  packageJson.scripts?.["check:release-traceability"] === "node ../scripts/release-traceability-check.mjs",
  "frontend/package.json should expose npm run check:release-traceability."
);

check(
  "Package exposes full release gate",
  packageJson.scripts?.["check:release:full"] === "node ../scripts/release-gate.mjs --with-backend --with-docker-build",
  "frontend/package.json should expose npm run check:release:full."
);

check(
  "Package exposes schema policy check",
  packageJson.scripts?.["check:schema"] === "node ../scripts/schema-policy-check.mjs",
  "frontend/package.json should expose npm run check:schema."
);

check(
  "Package exposes schema migration check",
  packageJson.scripts?.["check:migrations"] === "node ../scripts/schema-migration-check.mjs",
  "frontend/package.json should expose npm run check:migrations."
);

check(
  "Package exposes schema bootstrap check",
  packageJson.scripts?.["check:schema-bootstrap"] === "node ../scripts/schema-bootstrap-check.mjs",
  "frontend/package.json should expose npm run check:schema-bootstrap."
);

check(
  "Release gate runs evidence check",
  releaseGate.includes('"check:evidence"'),
  "The release gate should fail when MVP evidence becomes stale."
);

check(
  "Release gate runs acceptance check",
  releaseGate.includes('"check:acceptance"'),
  "The release gate should fail when MVP acceptance coverage becomes stale."
);

check(
  "Release gate runs schema policy check",
  releaseGate.includes('"check:schema"'),
  "The release gate should fail when database schema policy becomes implicit."
);

check(
  "Release gate runs schema migration check",
  releaseGate.includes('"check:migrations"'),
  "The release gate should fail when production schema migration proof becomes stale."
);

check(
  "Release gate runs schema bootstrap check",
  releaseGate.includes('"check:schema-bootstrap"'),
  "The release gate should fail when first RDS schema bootstrap proof is undocumented."
);

check(
  "Release gate runs git parser check",
  releaseGate.includes('"check:git-parser"'),
  "The release gate should fail when git release status parser coverage is removed."
);

check(
  "Release gate runs dependency risk check",
  releaseGate.includes('"check:dependencies"'),
  "The release gate should fail when frontend dependency or lockfile safety becomes stale."
);

check(
  "Release gate runs release traceability check",
  releaseGate.includes('"check:release-traceability"'),
  "The release gate should fail when release traceability between git, Dockerhub and EC2 becomes stale."
);

check(
  "Pre-push gate runs release, git and sync checks",
  ["check:release", "check:git", "check:sync", "--with-backend", "--with-docker-build"].every((term) => prePushGate.includes(term)),
  "The pre-push gate should combine full local release evidence and GitHub sync protection."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`MVP evidence check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} MVP evidence check(s) failed.`);
  process.exit(1);
}
