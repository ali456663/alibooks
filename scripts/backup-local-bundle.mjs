import { spawnSync } from "node:child_process";
import { closeSync, copyFileSync, mkdirSync, openSync, readdirSync, realpathSync, lstatSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { docker, sha256, verifyBackup, tableRowCounts } from "./backup-restore-verify.mjs";

export async function createLocalBundle({ container, receiptsDirectory, backupDirectory, writersStopped, database = "cloudshop", username = "cloudshop" }) {
  if (writersStopped !== true) throw new Error("Stop all app instances and other writers, then explicitly confirm --writers-stopped.");
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_.-]*$/.test(container ?? "")) throw new Error("Invalid local database container name.");
  if (![database, username].every(value => /^[a-zA-Z0-9_]+$/.test(value))) throw new Error("Invalid local database identifier.");
  const source = realpathSync(receiptsDirectory);
  const sourceQuery = query => docker(["exec", "-i", container, "psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1",
    "-U", username, "-d", database], `BEGIN READ ONLY;\n${query}\nCOMMIT;`);
  const sourceCounts = tableRowCounts(sourceQuery);
  const destination = path.resolve(backupDirectory);
  if (destination === source || destination.startsWith(source + path.sep)) throw new Error("Backup must be outside the source receipt directory.");
  // A new directory prevents an old successful manifest surviving a failed run.
  mkdirSync(destination, { recursive: false, mode: 0o700 });
  const receipts = path.join(destination, "receipts");
  mkdirSync(receipts, { mode: 0o700 });
  const dump = path.join(destination, "database.dump");
  const fd = openSync(dump, "wx", 0o600);
  try {
    const result = spawnSync(process.platform === "win32" ? "docker.exe" : "docker",
      ["exec", container, "pg_dump", "-U", username, "-d", database, "-Fc"],
      { stdio: ["ignore", fd, "pipe"], timeout: 120_000, maxBuffer: 1024 * 1024 });
    if (result.error || result.status !== 0) throw new Error("Local database dump failed; bundle is incomplete.");
  } finally { closeSync(fd); }
  const files = [];
  for (const name of readdirSync(source)) {
    const original = path.join(source, name);
    const stat = lstatSync(original);
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(name) || !stat.isFile() || stat.isSymbolicLink()
        || path.dirname(realpathSync(original)) !== source) throw new Error("Source receipts must be regular flat files.");
    const before = await sha256(original);
    const copy = path.join(receipts, name);
    copyFileSync(original, copy);
    if (await sha256(copy) !== before || await sha256(original) !== before) throw new Error("Receipt changed during backup; bundle is incomplete.");
    files.push({ name, sha256: before });
  }
  const verification = await verifyBackup(dump, receipts);
  if (JSON.stringify(sourceCounts) !== JSON.stringify(verification.tableRowCounts)
      || JSON.stringify(sourceCounts) !== JSON.stringify(tableRowCounts(sourceQuery))) {
    throw new Error("Source and restored table counts differ; bundle is not verified. Check that all writers are stopped.");
  }
  const manifest = { version: 1, createdAt: new Date().toISOString(), sourceContainer: container, database,
    writersStoppedConfirmed: true, sourceTableRowCounts: sourceCounts, databaseSha256: await sha256(dump), files, verification };
  writeFileSync(path.join(destination, "verified-manifest.json"), JSON.stringify(manifest, null, 2) + "\n", { flag: "wx", mode: 0o600 });
  return { directory: destination, ...verification };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [container, receiptsDirectory, backupDirectory, confirmation] = process.argv.slice(2);
    if (process.argv.length !== 6 || confirmation !== "--writers-stopped") {
      throw new Error("Usage: node scripts/backup-local-bundle.mjs <local-db-container> <receipts-directory> <new-backup-directory> --writers-stopped");
    }
    console.log(JSON.stringify(await createLocalBundle({ container, receiptsDirectory, backupDirectory, writersStopped: true }), null, 2));
  } catch (error) {
    console.error(`Backup bundle FAILED: ${error.message}`);
    process.exitCode = 1;
  }
}
