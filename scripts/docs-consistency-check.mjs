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
  "docs/release-evidence.md"
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
  "Local release gate command exists",
  allDocs.includes("npm run check:release"),
  "Docs should include the one-command MVP release gate."
);

check(
  "Git release checks are documented",
  allDocs.includes("npm run check:git") && allDocs.includes("npm run check:sync"),
  "Docs should explain local git cleanliness and GitHub sync checks."
);

check(
  "Production readiness command exists",
  allDocs.includes("npm run check:prod"),
  "Docs should include the production readiness check."
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
