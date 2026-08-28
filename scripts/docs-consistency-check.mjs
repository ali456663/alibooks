import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");

const docs = [
  "README.md",
  "docs/kom-igang-snabbt.md",
  "docs/go-live-checklista.md",
  "docs/microservices-jwt-https.md",
  "docs/mvp-testprotokoll.md",
  "docs/professionell-bokforing-loop.md",
  "docs/roadmap-kvar.md",
  "docs/backup-restore-runbook.md",
  "docs/release-evidence.md",
  "docs/go-live-riskregister.md"
];

const failures = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function check(name, ok, detail) {
  const marker = ok ? "OK" : "FAIL";
  console.log(`${marker} - ${name}: ${detail}`);
  if (!ok) {
    failures.push(name);
  }
}

const allDocs = docs.map((file) => `${file}\n${read(file)}`).join("\n\n");
const rootPackage = JSON.parse(read("package.json"));
const rootScripts = rootPackage.scripts || {};
const riskyGitStagePattern = /git\s+add\s+\./;
const mojibakeMarkers = ["Ã", "Â", "ï¿½", "�"];

check(
  "Local frontend port is 5157",
  allDocs.includes("localhost:5157") && !allDocs.includes("localhost:5173"),
  "Docs should point to the strict Vite port used by npm run dev."
);

check(
  "Local backend port is 3000",
  allDocs.includes("localhost:3000"),
  "Docs should show the Spring Boot backend port."
);

check(
  "Database startup command exists",
  allDocs.includes("docker compose up db"),
  "Docs should explain how to start PostgreSQL locally."
);

check(
  "Local backend test command exists",
  allDocs.includes("npm run test:backend"),
  "Docs should include the Docker/Maven backend test wrapper."
);

check(
  "Local doctor command exists",
  allDocs.includes("npm run doctor") && allDocs.includes("/system/status") && allDocs.includes("5432"),
  "Docs should include the local DB/backend/frontend diagnosis command."
);

check(
  "Root npm command center exists",
  ["dev", "doctor", "build", "check:release", "check:release:full", "test:backend"].every((script) =>
    rootScripts[script]?.includes("--prefix frontend")
  ),
  "The repo root should delegate common AliBooks commands to frontend so npm run is safe from the project root."
);

check(
  "Local release gate command exists",
  allDocs.includes("npm run check:release"),
  "Docs should include the one-command MVP release gate."
);

check(
  "Full release gate command exists",
  allDocs.includes("npm run check:release:full"),
  "Docs should include the full local release gate with backend tests and Docker image builds."
);

check(
  "MVP acceptance command exists",
  allDocs.includes("npm run check:acceptance"),
  "Docs should explain how automated MVP acceptance evidence is checked."
);

check(
  "MVP evidence command exists",
  allDocs.includes("npm run check:evidence"),
  "Docs should explain how release evidence is kept in sync with readiness and risk checks."
);

check(
  "Frontend bundle budget command exists",
  allDocs.includes("npm run check:bundle") && allDocs.includes("bundle"),
  "Docs should explain how the production bundle budget is checked."
);

check(
  "Dependency risk command exists",
  allDocs.includes("npm run check:dependencies") &&
    allDocs.includes("npm run check:audit") &&
    allDocs.includes("npm audit --omit=dev --audit-level=critical"),
  "Docs should include the local dependency lockfile check, check:audit wrapper and the external vulnerability audit command."
);

check(
  "Controlled schema migration command exists",
  allDocs.includes("npm run check:migrations") &&
    allDocs.includes("npm run check:schema-bootstrap") &&
    allDocs.includes("schema-bootstrap-runbook.md") &&
    allDocs.includes("alibooks-schema.sql") &&
    allDocs.includes("db/migrations/001_startup_schema_patch.sql") &&
    allDocs.includes("APP_SCHEMA_PATCH_ENABLED=false") &&
    allDocs.includes("psql"),
  "Docs should explain the controlled schema bootstrap and migration path before RDS production start."
);

check(
  "Release traceability command exists",
  allDocs.includes("npm run check:release-traceability") && allDocs.includes("IMAGE_TAG") && allDocs.includes("sha-"),
  "Docs should explain how commit/image/version traceability is checked before go-live."
);

check(
  "Git release checks are documented",
  allDocs.includes("npm run check:git") && allDocs.includes("npm run check:sync") && allDocs.includes("git status -sb"),
  "Docs should explain local git cleanliness, explicit status review and GitHub sync checks."
);

check(
  "Docs avoid broad git add",
  !riskyGitStagePattern.test(allDocs),
  "Go-live docs should not recommend git add . because it can stage local exports, evidence or environment mistakes."
);

check(
  "Production readiness command exists",
  allDocs.includes("npm run check:prod"),
  "Docs should include the production readiness check."
);

check(
  "Startklar command exists",
  allDocs.includes("npm run check:startklar"),
  "Docs should include the short local MVP readiness check."
);

check(
  "Backup verification is documented",
  allDocs.includes("Kontrollera backupfil") || allDocs.includes("Verify backup file") || allDocs.includes("pg_restore -l"),
  "Docs should remind the user to verify a backup before go-live."
);

check(
  "Restore drill is documented",
  allDocs.includes("restore drill") && allDocs.includes("test database"),
  "Docs should require a restore drill outside production."
);

check(
  "No mojibake in Swedish docs",
  !mojibakeMarkers.some((marker) => allDocs.includes(marker)),
  "Docs should not contain broken encoding characters."
);

check(
  "Go-live path is documented",
  ["GitHub Actions", "Dockerhub", "EC2", "RDS", "smoke test"].every((term) => allDocs.includes(term)),
  "Docs should cover the production/demo path."
);

if (failures.length > 0) {
  console.error("");
  console.error(`${failures.length} documentation consistency check(s) failed.`);
  process.exit(1);
}

console.log("");
console.log("Documentation consistency check passed.");
