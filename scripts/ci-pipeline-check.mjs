import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const results = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function check(name, ok, detail) {
  results.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

const ci = read(".github/workflows/ci.yml");
const dockerhub = read(".github/workflows/dockerhub.yml");
const dependabot = read(".github/dependabot.yml");
const releaseGate = read("scripts/release-gate.mjs");

check("CI triggers on push to main", ci.includes("branches:") && ci.includes("- main"), "CI should run when main is updated.");
check("CI supports manual run", ci.includes("workflow_dispatch:"), "CI should be runnable manually before demos.");
check("CI cancels duplicate branch runs", ci.includes("cancel-in-progress: true"), "Repeated pushes should not leave stale CI runs active.");
check("CI has read-only contents permission", ci.includes("contents: read"), "CI should use least-privilege repository permissions.");
check("CI jobs have timeouts", includesAll(ci, ["timeout-minutes: 20", "timeout-minutes: 30"]), "Backend, frontend and Docker jobs should not hang indefinitely.");

check("Backend CI uses PostgreSQL 16 service", includesAll(ci, ["postgres:16", "POSTGRES_DB: cloudshop", "pg_isready"]), "Backend tests should run against PostgreSQL 16 like local/prod direction.");
check("Backend CI uses Java 21", includesAll(ci, ["actions/setup-java@v5", 'java-version: "21"']), "Backend CI should match the Spring Boot Java 21 build with the supported setup-java action.");
check("Backend CI runs Maven tests in batch mode", ci.includes("run: mvn -B test"), "Backend job must run Maven tests without interactive Maven output.");
check("Backend CI has JWT secret", ci.includes("JWT_SECRET: ci_test_secret_must_be_long_enough_for_demo"), "Backend tests should use a non-empty CI JWT secret.");
check("Backend CI has explicit schema mode", ci.includes("SPRING_JPA_HIBERNATE_DDL_AUTO: update"), "Backend CI should run with an explicit Hibernate ddl-auto mode.");
check("Backend CI has explicit schema patch flag", ci.includes('APP_SCHEMA_PATCH_ENABLED: "true"'), "Backend CI should declare whether startup schema patches run during tests.");
check("Backend CI uploads test reports", includesAll(ci, ["actions/upload-artifact@v4", "backend-surefire-reports", "backend/target/surefire-reports"]), "Backend CI should preserve Surefire reports as downloadable evidence.");

check("Frontend CI uses Node 24", includesAll(ci, ["actions/setup-node@v4", 'node-version: "24"']), "Frontend CI should avoid deprecated Node 20 warnings.");
check("Frontend CI caches npm", includesAll(ci, ["cache: npm", "frontend/package-lock.json"]), "Frontend CI should cache npm dependencies from the frontend lockfile.");
check("Frontend CI installs with npm ci", includesAll(ci, ["working-directory: frontend", "run: npm ci"]), "Frontend CI should install reproducibly.");
check("Frontend CI audits dependencies", ci.includes("run: npm run check:audit"), "Frontend CI should run a live npm audit before the release gate.");
check("Frontend CI runs release gate", ci.includes("run: npm run check:release"), "Frontend CI must run the same release gate used locally.");
check("Frontend CI uploads production bundle", includesAll(ci, ["frontend-dist", "frontend/dist", "retention-days: 14"]), "Frontend CI should preserve the built production bundle for demo and debugging evidence.");
check("CI writes job summaries", includesAll(ci, ["GITHUB_STEP_SUMMARY", "AliBooks backend evidence", "AliBooks frontend evidence", "AliBooks Docker evidence"]), "CI should write human-readable summaries for backend, frontend and Docker proof.");
check("Frontend CI release gate includes migration proof", releaseGate.includes('"check:migrations"'), "CI release gate should fail if controlled schema migration proof is stale.");
check("Frontend CI release gate includes schema bootstrap proof", releaseGate.includes('"check:schema-bootstrap"'), "CI release gate should fail if first RDS schema bootstrap proof is stale.");
check("Frontend CI release gate includes Startklar proof", releaseGate.includes('"check:startklar"'), "CI release gate should fail if local MVP start readiness proof is removed.");

check("Docker CI waits for backend/frontend", includesAll(ci, ["needs:", "- backend", "- frontend"]), "Docker build should only run after test jobs pass.");
check("Docker CI builds backend image", ci.includes("docker build -t cloudshop-backend:ci ./backend"), "CI should build backend Docker image.");
check("Docker CI builds frontend prod image", ci.includes("docker build -f ./frontend/Dockerfile.prod -t cloudshop-frontend:ci ./frontend"), "CI should build production frontend Docker image.");

check("Dockerhub workflow supports manual release", dockerhub.includes("workflow_dispatch:"), "Dockerhub workflow should be runnable manually.");
check("Dockerhub workflow runs on version tags", includesAll(dockerhub, ["push:", "tags:", '"v*"']), "Dockerhub workflow should publish tagged releases.");
check("Dockerhub uses read-only contents permission", dockerhub.includes("contents: read"), "Dockerhub workflow should use least-privilege repository permissions.");
check("Dockerhub workflow has timeout", dockerhub.includes("timeout-minutes: 45"), "Dockerhub image publishing should not hang indefinitely.");
check("Dockerhub login uses secrets", includesAll(dockerhub, ["docker/login-action@v3", "secrets.DOCKERHUB_USERNAME", "secrets.DOCKERHUB_TOKEN"]), "Dockerhub credentials must come from GitHub secrets.");
check("Dockerhub sets up Buildx", dockerhub.includes("docker/setup-buildx-action@v3"), "Dockerhub workflow should use Buildx.");
check("Dockerhub tags backend and frontend", includesAll(dockerhub, ["cloudshop-backend", "cloudshop-frontend", "type=sha,prefix=sha-", "type=ref,event=tag"]), "Dockerhub images should receive latest, sha and tag metadata.");
check("Dockerhub pushes backend image", includesAll(dockerhub, ["context: ./backend", "push: true", "steps.backend_meta.outputs.tags"]), "Backend Dockerhub image should be pushed.");
check("Dockerhub pushes frontend prod image", includesAll(dockerhub, ["context: ./frontend", "file: ./frontend/Dockerfile.prod", "steps.frontend_meta.outputs.tags"]), "Frontend Dockerhub image should be pushed from the production Dockerfile.");

check("Dependabot monitors frontend npm", includesAll(dependabot, ['package-ecosystem: "npm"', 'directory: "/frontend"']), "Frontend dependencies should be checked regularly.");
check("Dependabot monitors backend Maven", includesAll(dependabot, ['package-ecosystem: "maven"', 'directory: "/backend"']), "Backend Java dependencies should be checked regularly.");
check("Dependabot monitors GitHub Actions", includesAll(dependabot, ['package-ecosystem: "github-actions"', 'directory: "/"']), "Workflow action versions should be checked regularly.");
check("Dependabot runs on weekly schedule", includesAll(dependabot, ['interval: "weekly"', 'timezone: "Europe/Stockholm"']), "Dependency update cadence should be predictable.");
check("Dependabot limits update noise", includesAll(dependabot, ["open-pull-requests-limit: 5", "groups:"]), "Dependency update PRs should stay reviewable for a small MVP team.");

const failed = results.filter((result) => !result.ok);
for (const result of results) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks CI pipeline check: ${results.length - failed.length}/${results.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} CI pipeline check(s) failed.`);
  process.exit(1);
}
