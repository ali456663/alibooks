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

check(
  "Readiness script runs for evidence check",
  readinessResult.status === 0 && readinessActualTotal > 0,
  "MVP evidence should be checked against the current readiness script output."
);

check(
  "Release evidence documents full gate",
  evidence.includes("npm run check:release -- --with-backend --with-docker-build"),
  "The release evidence should name the strongest local verification command."
);

check(
  "Release evidence readiness count matches script",
  readinessEvidenceTotal === readinessActualTotal,
  `release-evidence has ${readinessEvidenceTotal || "missing"} readiness checks; current readiness output has ${readinessActualTotal || "missing"}.`
);

check(
  "Release evidence documents backend tests",
  /184 tests,\s*0 failures,\s*0 errors/.test(evidence),
  "Backend test proof should include test count and zero failures/errors."
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
  "Package exposes schema policy check",
  packageJson.scripts?.["check:schema"] === "node ../scripts/schema-policy-check.mjs",
  "frontend/package.json should expose npm run check:schema."
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
  "Release gate runs git parser check",
  releaseGate.includes('"check:git-parser"'),
  "The release gate should fail when git release status parser coverage is removed."
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
