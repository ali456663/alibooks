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

function check(name, ok, detail) {
  results.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

const shellBackup = exists("scripts/backup-postgres.sh") ? read("scripts/backup-postgres.sh") : "";
const psBackup = exists("scripts/backup-postgres.ps1") ? read("scripts/backup-postgres.ps1") : "";
const shellRestore = exists("scripts/restore-postgres.sh") ? read("scripts/restore-postgres.sh") : "";
const psRestore = exists("scripts/restore-postgres.ps1") ? read("scripts/restore-postgres.ps1") : "";
const runbook = exists("docs/backup-restore-runbook.md") ? read("docs/backup-restore-runbook.md") : "";
const gitignore = read(".gitignore");
const releaseEvidence = read("docs/release-evidence.md");

check("Shell backup script exists", exists("scripts/backup-postgres.sh"), "Linux/EC2 backup script should exist.");
check("PowerShell backup script exists", exists("scripts/backup-postgres.ps1"), "Windows backup script should exist.");
check("Shell restore script exists", exists("scripts/restore-postgres.sh"), "Linux/EC2 restore drill script should exist.");
check("PowerShell restore script exists", exists("scripts/restore-postgres.ps1"), "Windows restore drill script should exist.");
check("Backup runbook exists", exists("docs/backup-restore-runbook.md"), "Backup and restore runbook should exist.");

check(
  "Shell backup uses pg_dump custom format",
  includesAll(shellBackup, ["postgres:16", "pg_dump", "-Fc", "PGHOST", "PGDATABASE", "PGUSER", "PGPASSWORD"]),
  "Shell backup should use Postgres 16 pg_dump with required connection variables."
);
check(
  "Shell backup verifies pg_restore catalog",
  includesAll(shellBackup, ["pg_restore", "-l", "Backup verified"]),
  "Shell backup should verify the archive catalog after creating it."
);
check(
  "PowerShell backup uses pg_dump custom format",
  includesAll(psBackup, ["postgres:16", "pg_dump", "-Fc", "PgHost", "PgDatabase", "PgUser", "PgPassword"]),
  "PowerShell backup should use Postgres 16 pg_dump with required connection variables."
);
check(
  "PowerShell backup verifies pg_restore catalog",
  includesAll(psBackup, ["pg_restore", "-l", "Backup verified"]),
  "PowerShell backup should verify the archive catalog after creating it."
);
check(
  "Shell restore verifies catalog before restore",
  includesAll(shellRestore, ["RESTORE_FILE", "RESTORE_CONFIRM", "pg_restore", "-l", "--clean", "--if-exists"]),
  "Shell restore should require a dump file, explicit confirmation and catalog verification."
);
check(
  "Shell restore blocks production-looking targets",
  includesAll(shellRestore, ["Do not restore into production first", "*test*|*restore*|*drill*", "Refusing restore"]),
  "Shell restore should default to separate test/restore/drill databases."
);
check(
  "PowerShell restore verifies catalog before restore",
  includesAll(psRestore, ["RestoreFile", "RestoreConfirm", "pg_restore", "-l", "--clean", "--if-exists"]),
  "PowerShell restore should require a dump file, explicit confirmation and catalog verification."
);
check(
  "PowerShell restore blocks production-looking targets",
  includesAll(psRestore, ["test|restore|drill", "Refusing restore", "separate database"]),
  "PowerShell restore should default to separate test/restore/drill databases."
);
check(
  "Backup files are ignored by git",
  includesAll(gitignore, ["backups/", "*.dump"]),
  "Database backup files must not be committed."
);
check(
  "Runbook covers restore drill",
  includesAll(runbook, ["restore drill", "restore-postgres.sh", "restore-postgres.ps1", "test database", "Do not restore into production first"]),
  "Runbook should explain how to test restore away from production."
);
check(
  "Runbook covers RDS snapshot and retention",
  includesAll(runbook, ["RDS snapshot", "7 ar", "minst 7 ar", "innan go-live"]),
  "Runbook should cover RDS snapshot and Swedish bookkeeping retention expectations."
);
check(
  "Release evidence mentions backup gate",
  releaseEvidence.includes("npm run check:backup"),
  "Release evidence should mention the backup readiness check."
);

const failed = results.filter((result) => !result.ok);
for (const result of results) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks backup readiness check: ${results.length - failed.length}/${results.length} required checks passed.`);

if (failed.length > 0) {
  console.error(`${failed.length} backup readiness check(s) failed.`);
  process.exit(1);
}
