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
    name: "Frontend bundle budget evidence count matches script",
    script: "scripts/frontend-bundle-budget-check.mjs",
    outputLabel: "AliBooks frontend bundle budget check",
    evidencePattern: /`check:bundle`:\s*(\d+)\/(\d+)/
  },
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
    name: "Env go-live evidence count matches script",
    script: "scripts/env-go-live-check.mjs",
    outputLabel: "AliBooks env go-live check",
    evidencePattern: /`check:env-go-live`:\s*(\d+)\/(\d+)/
  },
  {
    name: "CI evidence count matches script",
    script: "scripts/ci-pipeline-check.mjs",
    outputLabel: "AliBooks CI pipeline check",
    evidencePattern: /`check:ci`:\s*(\d+)\/(\d+)/
  },
  {
    name: "CI handoff evidence count matches script",
    script: "scripts/ci-handoff-check.mjs",
    outputLabel: "AliBooks CI handoff check",
    evidencePattern: /`check:ci-handoff`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Post-push evidence count matches script",
    script: "scripts/post-push-verification-check.mjs",
    outputLabel: "AliBooks post-push verification check",
    evidencePattern: /`check:post-push`:\s*(\d+)\/(\d+)/
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
  },
  {
    name: "Go-live decision evidence count matches script",
    script: "scripts/go-live-decision-check.mjs",
    outputLabel: "AliBooks go-live decision check",
    evidencePattern: /`check:go-live-decision`:\s*(\d+)\/(\d+)/
  },
  {
    name: "External go-live evidence count matches script",
    script: "scripts/external-go-live-proof-check.mjs",
    outputLabel: "External go-live proof check",
    evidencePattern: /`check:external-go-live`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Manual go-live evidence count matches script",
    script: "scripts/manual-go-live-evidence-check.mjs",
    outputLabel: "Manual go-live evidence check",
    evidencePattern: /`check:manual-go-live`:\s*(\d+)\/(\d+)/
  },
  {
    name: "MVP use readiness evidence count matches script",
    script: "scripts/mvp-use-readiness-check.mjs",
    outputLabel: "AliBooks MVP use readiness check",
    evidencePattern: /`check:mvp-use`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Operations readiness evidence count matches script",
    script: "scripts/operations-readiness-check.mjs",
    outputLabel: "AliBooks operations readiness check",
    evidencePattern: /`check:operations`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Use-today evidence count matches script",
    script: "scripts/use-today-check.mjs",
    outputLabel: "AliBooks use-today check",
    evidencePattern: /`check:use-today`:\s*(\d+)\/(\d+)/
  },
  {
    name: "First real data evidence count matches script",
    script: "scripts/first-real-data-check.mjs",
    outputLabel: "AliBooks first real data check",
    evidencePattern: /`check:first-real-data`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Pilot readiness evidence count matches script",
    script: "scripts/pilot-readiness-check.mjs",
    outputLabel: "AliBooks pilot readiness check",
    evidencePattern: /`check:pilot`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Calculation integrity evidence count matches script",
    script: "scripts/calculation-integrity-check.mjs",
    outputLabel: "AliBooks calculation integrity check",
    evidencePattern: /`check:calculations`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Retention evidence count matches script",
    script: "scripts/retention-immutability-check.mjs",
    outputLabel: "AliBooks retention and immutability check",
    evidencePattern: /`check:retention`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Audit integrity evidence count matches script",
    script: "scripts/audit-integrity-check.mjs",
    outputLabel: "AliBooks audit integrity check",
    evidencePattern: /`check:audit-integrity`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Period close evidence count matches script",
    script: "scripts/period-close-readiness-check.mjs",
    outputLabel: "AliBooks period close readiness check",
    evidencePattern: /`check:period-close`:\s*(\d+)\/(\d+)/
  },
  {
    name: "Accountant handoff evidence count matches script",
    script: "scripts/accountant-handoff-check.mjs",
    outputLabel: "AliBooks accountant handoff check",
    evidencePattern: /`check:handoff`:\s*(\d+)\/(\d+)/
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
  "Release evidence documents frontend hygiene",
  includesAll(evidence, ["check:frontend-hygiene", "demo-filer", "alert()"]),
  "Frontend production hygiene should be visible in release evidence."
);

check(
  "Release evidence documents bundle budget",
  includesAll(evidence, ["check:bundle", "bundle budget", "visual/motion/animation chunks"]),
  "Frontend production bundle budget should be visible in release evidence."
);

check(
  "Release evidence links go-live risk register",
  evidence.includes("go-live-riskregister.md") && evidence.includes("check:go-live-risks"),
  "Remaining go-live risk evidence should be connected to the automated risk check."
);

check(
  "Release evidence links final go-live decision",
  evidence.includes("go-live-beslut.md") && evidence.includes("check:go-live-decision"),
  "The final production decision should be connected to release evidence."
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
  "Package exposes frontend hygiene check",
  packageJson.scripts?.["check:frontend-hygiene"] === "node ../scripts/frontend-production-hygiene-check.mjs",
  "frontend/package.json should expose npm run check:frontend-hygiene."
);

check(
  "Package exposes bundle budget check",
  packageJson.scripts?.["check:bundle"] === "node ../scripts/frontend-bundle-budget-check.mjs",
  "frontend/package.json should expose npm run check:bundle."
);

check(
  "Package exposes pre-push gate",
  packageJson.scripts?.["check:prepush"] === "node ../scripts/pre-push-gate.mjs",
  "frontend/package.json should expose npm run check:prepush."
);

check(
  "Package exposes CI handoff check",
  packageJson.scripts?.["check:ci-handoff"] === "node ../scripts/ci-handoff-check.mjs",
  "frontend/package.json should expose npm run check:ci-handoff."
);

check(
  "Package exposes post-push check",
  packageJson.scripts?.["check:post-push"] === "node ../scripts/post-push-verification-check.mjs",
  "frontend/package.json should expose npm run check:post-push."
);

check(
  "Package exposes MVP use readiness check",
  packageJson.scripts?.["check:mvp-use"] === "node ../scripts/mvp-use-readiness-check.mjs",
  "frontend/package.json should expose npm run check:mvp-use."
);

check(
  "Package exposes operations readiness check",
  packageJson.scripts?.["check:operations"] === "node ../scripts/operations-readiness-check.mjs",
  "frontend/package.json should expose npm run check:operations."
);

check(
  "Package exposes use-today check",
  packageJson.scripts?.["check:use-today"] === "node ../scripts/use-today-check.mjs",
  "frontend/package.json should expose npm run check:use-today."
);

check(
  "Package exposes first-real-data check",
  packageJson.scripts?.["check:first-real-data"] === "node ../scripts/first-real-data-check.mjs",
  "frontend/package.json should expose npm run check:first-real-data."
);

check(
  "Package exposes pilot check",
  packageJson.scripts?.["check:pilot"] === "node ../scripts/pilot-readiness-check.mjs",
  "frontend/package.json should expose npm run check:pilot."
);

check(
  "Package exposes go-live decision check",
  packageJson.scripts?.["check:go-live-decision"] === "node ../scripts/go-live-decision-check.mjs",
  "frontend/package.json should expose npm run check:go-live-decision."
);

check(
  "Package exposes external go-live proof check",
  packageJson.scripts?.["check:external-go-live"] === "node ../scripts/external-go-live-proof-check.mjs",
  "frontend/package.json should expose npm run check:external-go-live."
);

check(
  "Package exposes calculation integrity check",
  packageJson.scripts?.["check:calculations"] === "node ../scripts/calculation-integrity-check.mjs",
  "frontend/package.json should expose npm run check:calculations."
);

check(
  "Package exposes retention check",
  packageJson.scripts?.["check:retention"] === "node ../scripts/retention-immutability-check.mjs",
  "frontend/package.json should expose npm run check:retention."
);

check(
  "Package exposes audit integrity check",
  packageJson.scripts?.["check:audit-integrity"] === "node ../scripts/audit-integrity-check.mjs",
  "frontend/package.json should expose npm run check:audit-integrity."
);

check(
  "Package exposes period close check",
  packageJson.scripts?.["check:period-close"] === "node ../scripts/period-close-readiness-check.mjs",
  "frontend/package.json should expose npm run check:period-close."
);

check(
  "Package exposes accountant handoff check",
  packageJson.scripts?.["check:handoff"] === "node ../scripts/accountant-handoff-check.mjs",
  "frontend/package.json should expose npm run check:handoff."
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
  "Package exposes env-go-live check",
  packageJson.scripts?.["check:env-go-live"] === "node ../scripts/env-go-live-check.mjs",
  "frontend/package.json should expose npm run check:env-go-live."
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
  "Release gate runs env-go-live check",
  releaseGate.includes('"check:env-go-live"'),
  "The release gate should fail when go-live env variable proof becomes stale."
);

check(
  "Release gate runs MVP use readiness check",
  releaseGate.includes('"check:mvp-use"'),
  "The release gate should fail when 20-step MVP use evidence becomes stale."
);

check(
  "Release gate runs CI handoff check",
  releaseGate.includes('"check:ci-handoff"'),
  "The release gate should fail when CI handoff proof becomes stale."
);

check(
  "Release gate runs post-push check",
  releaseGate.includes('"check:post-push"'),
  "The release gate should fail when post-push proof becomes stale."
);

check(
  "Release gate runs operations readiness check",
  releaseGate.includes('"check:operations"'),
  "The release gate should fail when operations readiness evidence becomes stale."
);

check(
  "Release gate runs use-today check",
  releaseGate.includes('"check:use-today"'),
  "The release gate should fail when the final local use decision becomes stale."
);

check(
  "Release gate runs first-real-data check",
  releaseGate.includes('"check:first-real-data"'),
  "The release gate should fail when first-real-data proof becomes stale."
);

check(
  "Release gate runs pilot readiness check",
  releaseGate.includes('"check:pilot"'),
  "The release gate should fail when pilot readiness proof becomes stale."
);

check(
  "Release gate runs go-live decision check",
  releaseGate.includes('"check:go-live-decision"'),
  "The release gate should fail when the final production decision becomes stale."
);

check(
  "Release gate runs external go-live proof check",
  releaseGate.includes('"check:external-go-live"'),
  "The release gate should fail when external production proof becomes stale."
);

check(
  "Release gate runs calculation integrity check",
  releaseGate.includes('"check:calculations"'),
  "The release gate should fail when calculation integrity proof becomes stale."
);

check(
  "Release gate runs retention check",
  releaseGate.includes('"check:retention"'),
  "The release gate should fail when retention and immutability proof becomes stale."
);

check(
  "Release gate runs audit integrity check",
  releaseGate.includes('"check:audit-integrity"'),
  "The release gate should fail when audit integrity proof becomes stale."
);

check(
  "Release gate runs period close readiness check",
  releaseGate.includes('"check:period-close"'),
  "The release gate should fail when period close readiness proof becomes stale."
);

check(
  "Release gate runs accountant handoff check",
  releaseGate.includes('"check:handoff"'),
  "The release gate should fail when accountant handoff proof becomes stale."
);

check(
  "Release gate runs acceptance check",
  releaseGate.includes('"check:acceptance"'),
  "The release gate should fail when MVP acceptance coverage becomes stale."
);

check(
  "Release gate runs frontend hygiene check",
  releaseGate.includes('"check:frontend-hygiene"'),
  "The release gate should fail when frontend production hygiene is removed."
);

check(
  "Release gate runs bundle budget check",
  releaseGate.includes('"check:bundle"'),
  "The release gate should fail when frontend production bundle budget protection is removed."
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
