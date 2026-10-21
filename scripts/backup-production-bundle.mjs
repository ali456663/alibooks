import { spawnSync } from "node:child_process";
import { mkdirSync, openSync, closeSync, readdirSync, lstatSync, realpathSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { docker, sha256, tableRowCounts, verifyBackup } from "./backup-restore-verify.mjs";

const CONTAINER_NAME = /^[a-zA-Z0-9][a-zA-Z0-9_.-]*$/;
const DB_NAME = /^[a-zA-Z0-9_]+$/;

export function receiptCopyDockerArgs(container, destination) {
  return ["run", "--rm", "--network", "none", "--read-only", "--volumes-from", `${container}:ro`,
    "-v", `${destination}:/bundle`, "--entrypoint", "sh", "postgres:16", "-ec",
    "test -d /app/uploads/receipts; cp -R --no-preserve=mode,ownership /app/uploads/receipts/. /bundle/receipts/"];
}

export function assessRestoredEvidence(result) {
  const counts = ["expensesWithoutReceipts", "unreferencedFiles"];
  for (const field of counts) {
    if (!Number.isSafeInteger(result?.[field]) || result[field] < 0) {
      throw new Error(`Restore verification did not report ${field}.`);
    }
  }
  const voucher = result.voucherEvidence;
  if (!voucher || !Number.isSafeInteger(voucher.vouchersMissingEvidence)
      || voucher.vouchersMissingEvidence < 0 || !["present", "legacy_absent"].includes(voucher.archiveSchema)) {
    throw new Error("Restore verification did not report voucher evidence coverage.");
  }
  const invoice = result.invoiceOriginals;
  if (!invoice || !["present", "legacy_absent"].includes(invoice.archiveSchema)
      || !Number.isSafeInteger(invoice.archivedInvoicesVerified) || invoice.archivedInvoicesVerified < 0
      || !Number.isSafeInteger(invoice.issuedWithoutOriginals) || invoice.issuedWithoutOriginals < 0) {
    throw new Error("Restore verification did not report invoice original coverage.");
  }
  const delivery = result.emailDeliveryEvidence;
  if (!delivery || !["present", "legacy_absent"].includes(delivery.ledgerSchema)
      || ["attemptsVerified", "unresolvedAttempts", "unlinkedInvoiceAttempts", "attachmentOriginalMismatches"]
        .some(field => !Number.isSafeInteger(delivery[field]) || delivery[field] < 0)) {
    throw new Error("Restore verification did not report email delivery evidence.");
  }
  const reasons = [];
  if (result.expensesWithoutReceipts > 0) reasons.push("expenses_without_receipts");
  if (result.unreferencedFiles > 0) reasons.push("unreferenced_receipt_files");
  if (voucher.archiveSchema !== "present") reasons.push("voucher_evidence_archive_absent");
  if (voucher.vouchersMissingEvidence > 0) reasons.push("vouchers_missing_evidence");
  if (invoice.archiveSchema !== "present") reasons.push("invoice_original_archive_absent");
  if (invoice.issuedWithoutOriginals > 0) reasons.push("issued_invoices_without_originals");
  if (delivery.ledgerSchema !== "present") reasons.push("email_delivery_ledger_absent");
  if (delivery.unresolvedAttempts > 0) reasons.push("unresolved_email_deliveries");
  if (delivery.unlinkedInvoiceAttempts > 0) reasons.push("email_attempts_without_invoice_or_original");
  if (delivery.attachmentOriginalMismatches > 0) reasons.push("email_attachments_differ_from_originals");
  return { reviewRequired: reasons.length > 0, reasons };
}

export async function createProductionBundle({ backendContainer, backupDirectory, writersStopped, env = process.env, operations = {} }) {
  if (writersStopped !== true) throw new Error("Stop every application instance and writer, then confirm --writers-stopped.");
  if (!CONTAINER_NAME.test(backendContainer ?? "")) throw new Error("Invalid backend container name.");
  const { PGHOST: host, PGDATABASE: database, PGUSER: user, PGPASSWORD: password } = env;
  if (!host || /^(localhost|127\.0\.0\.1|::1|host\.docker\.internal)$/i.test(host) || !/^[a-zA-Z0-9.-]+$/.test(host)) {
    throw new Error("Production backup requires a remote PostgreSQL DNS host.");
  }
  if (!DB_NAME.test(database ?? "") || !DB_NAME.test(user ?? "") || !password) throw new Error("PGDATABASE, PGUSER and PGPASSWORD are required.");
  const port = env.PGPORT ?? "5432";
  if (!/^\d{1,5}$/.test(port) || Number(port) < 1 || Number(port) > 65535) throw new Error("Invalid PGPORT.");
  if (env.PGSSLMODE && env.PGSSLMODE !== "verify-full") throw new Error("Production backup requires PGSSLMODE=verify-full.");
  if (!env.RDS_CA_CERT_HOST_PATH) throw new Error("RDS_CA_CERT_HOST_PATH is required.");
  const ca = realpathSync(env.RDS_CA_CERT_HOST_PATH);
  if (!lstatSync(ca).isFile()) throw new Error("RDS_CA_CERT_HOST_PATH must be a file.");

  const runDocker = operations.docker ?? docker;
  const countRows = operations.tableRowCounts ?? tableRowCounts;
  const sourceWalPosition = operations.sourceWalPosition
    ?? (query => query("SELECT pg_current_wal_insert_lsn()::text;"));
  const verify = operations.verifyBackup ?? verifyBackup;
  const hash = operations.sha256 ?? sha256;
  const dumpDatabase = operations.dumpDatabase ?? defaultDumpDatabase;
  const inspect = JSON.parse(runDocker(["inspect", "--type", "container", "--format", "{{json .}}", backendContainer]));
  if (inspect.State?.Running !== false) throw new Error("Backend container must be stopped before the backup.");
  const uploads = inspect.Mounts?.filter(mount => mount.Destination === "/app/uploads" && mount.Type === "volume");
  if (uploads?.length !== 1) throw new Error("Expected one named receipt volume at /app/uploads.");

  const destination = path.resolve(backupDirectory);
  const parent = realpathSync(path.dirname(destination));
  const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  if (parent === repository || parent.startsWith(repository + path.sep)) throw new Error("Backup directory must be outside the repository.");
  const pgArgs = ["run", "--rm", "-i", "-e", "PGPASSWORD", "-e", "PGSSLMODE=verify-full",
    "-e", "PGSSLROOTCERT=/run/certs/rds-ca-bundle.pem", "-v", `${ca}:/run/certs/rds-ca-bundle.pem:ro`, "postgres:16"];
  const query = sql => runDocker([...pgArgs, "psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1",
    "-h", host, "-p", port, "-U", user, "-d", database, "-c", sql]);
  const initialWalPosition = sourceWalPosition(query);
  if (typeof initialWalPosition !== "string" || !/^[0-9A-F]+\/[0-9A-F]+$/i.test(initialWalPosition)) {
    throw new Error("Cannot verify the source PostgreSQL WAL position.");
  }
  const before = countRows(query);
  // Never reuse a directory: an old success marker must not survive a failed backup.
  mkdirSync(destination, { recursive: false, mode: 0o700 });
  const receipts = path.join(destination, "receipts");
  mkdirSync(receipts, { mode: 0o700 });
  const dump = path.join(destination, "database.dump");
  runDocker(receiptCopyDockerArgs(backendContainer, destination), undefined, { timeoutMs: 1_800_000 });
  const files = [];
  for (const name of readdirSync(receipts)) {
    const file = path.join(receipts, name);
    if (!CONTAINER_NAME.test(name) || !lstatSync(file).isFile() || lstatSync(file).isSymbolicLink()
        || path.dirname(realpathSync(file)) !== receipts) throw new Error("Receipts must be regular flat files.");
    files.push({ name, sha256: await hash(file) });
  }
  await dumpDatabase(pgArgs, { host, port, user, database, dump });
  const result = await verify(dump, receipts);
  const after = countRows(query);
  const finalWalPosition = sourceWalPosition(query);
  if (typeof finalWalPosition !== "string" || !/^[0-9A-F]+\/[0-9A-F]+$/i.test(finalWalPosition)
      || finalWalPosition !== initialWalPosition) {
    throw new Error("Source PostgreSQL WAL changed during backup; writers may still be active. Bundle is not verified.");
  }
  if (result.relocatedPaths !== 0 || JSON.stringify(result.tableRowCounts) !== JSON.stringify(before)
      || JSON.stringify(after) !== JSON.stringify(before)) {
    throw new Error("Source and restored data differ, or receipt paths need relocation. Bundle is not verified.");
  }
  const accountingEvidence = assessRestoredEvidence(result);
  const manifest = { version: 1, createdAt: new Date().toISOString(), sourceContainer: backendContainer,
    database, writersStoppedConfirmed: true, sourceWalPosition: finalWalPosition,
    sourceTableRowCounts: before, databaseSha256: await hash(dump), files,
    verification: result, accountingEvidence };
  writeFileSync(path.join(destination, "verified-manifest.json"), JSON.stringify(manifest, null, 2) + "\n", { flag: "wx", mode: 0o600 });
  return { directory: destination, ...result, accountingEvidence };
}

function defaultDumpDatabase(pgArgs, { host, port, user, database, dump }) {
  const fd = openSync(dump, "wx", 0o600);
  try {
    const result = spawnSync(process.platform === "win32" ? "docker.exe" : "docker",
      [...pgArgs.filter(arg => arg !== "-i"), "pg_dump", "-h", host, "-p", port, "-U", user, "-d", database, "-Fc"],
      { stdio: ["ignore", fd, "pipe"], timeout: 1_800_000, maxBuffer: 1024 * 1024 });
    if (result.error || result.status !== 0) throw new Error("Production database dump failed; bundle is incomplete.");
  } finally { closeSync(fd); }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [backendContainer, backupDirectory, confirmation] = process.argv.slice(2);
    if (process.argv.length !== 5 || confirmation !== "--writers-stopped") {
      throw new Error("Usage: node scripts/backup-production-bundle.mjs <stopped-backend-container> <new-backup-directory> --writers-stopped");
    }
    console.log(JSON.stringify(await createProductionBundle({ backendContainer, backupDirectory, writersStopped: true }), null, 2));
  } catch (error) {
    console.error(`Production backup FAILED: ${error.message}`);
    process.exitCode = 1;
  }
}
