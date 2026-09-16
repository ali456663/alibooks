import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { createReadStream, lstatSync, realpathSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as sleep } from "node:timers/promises";

export function docker(args, input) {
  const result = spawnSync(process.platform === "win32" ? "docker.exe" : "docker", args, {
    input, encoding: "utf8", timeout: 120_000, maxBuffer: 32 * 1024 * 1024,
  });
  // Database errors can contain personal data. Do not print SQL or Docker output.
  if (result.error || result.status !== 0) throw new Error(`Docker ${args[0]} failed (exit ${result.status ?? "unknown"}).`);
  return result.stdout.trim();
}

export async function isolatedDatabase(action) {
  const name = `alibooks-restore-${randomUUID()}`;
  let created = false;
  try {
    docker(["create", "--name", name, "--network", "none",
      "--label", "alibooks.purpose=isolated-restore-drill",
      "--tmpfs", "/var/lib/postgresql/data", "--tmpfs", "/app/uploads",
      "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "-e", "POSTGRES_DB=alibooks_restore_test", "postgres:16"]);
    created = true;
    docker(["start", name]);
    let ready = false;
    for (let attempt = 0; attempt < 60; attempt++) {
      try {
        // TCP readiness avoids the temporary socket-only initialization server.
        docker(["exec", name, "pg_isready", "-h", "127.0.0.1", "-U", "postgres", "-d", "alibooks_restore_test"]);
        ready = true;
        break;
      } catch { await sleep(500); }
    }
    if (!ready) throw new Error("Isolated PostgreSQL did not become ready.");
    return await action(name);
  } finally {
    if (created) docker(["rm", "-f", name]);
  }
}

export function sql(name, query) {
  return docker(["exec", "-i", name, "psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1",
    "-U", "postgres", "-d", "alibooks_restore_test"], query);
}

export async function sha256(filename) {
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(filename)) hash.update(chunk);
  return hash.digest("hex");
}

export function tableRowCounts(query) {
  const tables = JSON.parse(query("SELECT coalesce(json_agg(tablename ORDER BY tablename), '[]'::json) FROM pg_tables WHERE schemaname = 'public';"));
  return Object.fromEntries(tables.map(table => {
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(table)) throw new Error("Unsupported table name in backup inventory.");
    const count = Number(query(`SELECT count(*) FROM public."${table}";`));
    if (!Number.isSafeInteger(count) || count < 0) throw new Error("Unsupported row count in backup inventory.");
    return [table, count];
  }));
}

export async function verifyBackup(dumpFile, receiptsDirectory) {
  const dump = realpathSync(dumpFile);
  const receipts = realpathSync(receiptsDirectory);
  if (!lstatSync(dump).isFile() || !lstatSync(receipts).isDirectory()) {
    throw new Error("Supply a database dump and its matching receipts directory.");
  }
  return isolatedDatabase(async (name) => {
    docker(["cp", dump, `${name}:/tmp/database.dump`]);
    docker(["exec", name, "pg_restore", "-U", "postgres", "-d", "alibooks_restore_test",
      "--single-transaction", "--exit-on-error", "--no-owner", "--no-privileges", "/tmp/database.dump"]);
    const rows = JSON.parse(sql(name, `SELECT coalesce(json_agg(r), '[]'::json) FROM
      (SELECT receipt_storage_path AS location, receipt_sha256 AS hash FROM public.expenses
       WHERE receipt_storage_path IS NOT NULL AND btrim(receipt_storage_path) <> '') r;`));
    const orphanHashes = Number(sql(name, `SELECT count(*) FROM public.expenses
      WHERE (receipt_storage_path IS NULL OR btrim(receipt_storage_path) = '')
      AND receipt_sha256 IS NOT NULL AND btrim(receipt_sha256) <> '';`));
    if (orphanHashes) throw new Error("Receipt hash exists without a storage reference.");
    const expensesWithoutReceipts = Number(sql(name, `SELECT count(*) FROM public.expenses
      WHERE receipt_storage_path IS NULL OR btrim(receipt_storage_path) = '';`));
    docker(["exec", name, "mkdir", "-p", "/app/uploads/receipts"]);
    const copied = new Map();
    for (const basename of readdirSync(receipts)) {
      const file = path.join(receipts, basename);
      if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(basename) || !lstatSync(file).isFile()
          || lstatSync(file).isSymbolicLink() || path.dirname(realpathSync(file)) !== receipts) {
        throw new Error("Backup receipt directory must contain only regular flat files.");
      }
      const hash = await sha256(file);
      docker(["cp", file, `${name}:/tmp/receipt-copy`]);
      docker(["exec", name, "cp", "/tmp/receipt-copy", `/app/uploads/receipts/${basename}`]);
      const restoredHash = docker(["exec", name, "sha256sum", `/app/uploads/receipts/${basename}`]).split(/\s/)[0];
      if (restoredHash !== hash) throw new Error("Restored receipt checksum mismatch.");
      copied.set(basename, hash);
    }
    let relocatedPaths = 0;
    const seen = new Set();
    for (const row of rows) {
      // The app currently stores absolute Windows or Linux paths. Never use one
      // to read the live filesystem: resolve only flat files inside this backup.
      const basename = row.location.replaceAll("\\", "/").split("/").at(-1);
      if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(basename) || seen.has(basename)) {
        throw new Error("Invalid or duplicate receipt storage filename in restored database.");
      }
      seen.add(basename);
      if (!/^[a-f0-9]{64}$/i.test(row.hash ?? "")) throw new Error("Receipt is missing a valid stored SHA-256 hash.");
      const file = path.join(receipts, basename);
      let stat;
      try { stat = lstatSync(file); } catch { throw new Error("A referenced receipt is missing from the backup."); }
      if (!stat.isFile() || stat.isSymbolicLink() || path.dirname(realpathSync(file)) !== receipts) {
        throw new Error("Receipt must be a regular file inside the backup directory.");
      }
      if (await sha256(file) !== row.hash.toLowerCase()) throw new Error("Receipt backup checksum mismatch.");
      const restoredPath = `/app/uploads/receipts/${basename}`;
      if (copied.get(basename) !== row.hash.toLowerCase()) throw new Error("Restored receipt checksum mismatch.");
      if (row.location !== restoredPath) relocatedPaths++;
    }
    const invalidVouchers = Number(sql(name, `SELECT count(*) FROM
      (SELECT voucher_number FROM public.journal_entries GROUP BY voucher_number
       HAVING sum(debit::bigint) <> sum(credit::bigint)
          OR bool_or(debit IS NULL OR credit IS NULL OR debit < 0 OR credit < 0)
          OR voucher_number IS NULL OR btrim(voucher_number) = '') invalid;`));
    if (invalidVouchers !== 0) throw new Error("Restored journal contains invalid or unbalanced vouchers.");
    const journalRows = Number(sql(name, "SELECT count(*) FROM public.journal_entries;"));
    return { receiptsVerified: rows.length, journalRows, relocatedPaths, tableRowCounts: tableRowCounts(query => sql(name, query)),
      archiveFilesVerified: copied.size, unreferencedFiles: copied.size - seen.size, expensesWithoutReceipts,
      scope: "Isolated database restore, receipt hashes and voucher balance only; not application or legal approval." };
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length !== 4) throw new Error("Usage: node scripts/backup-restore-verify.mjs <database.dump> <backup-receipts-directory>");
    console.log(JSON.stringify(await verifyBackup(process.argv[2], process.argv[3]), null, 2));
  } catch (error) {
    console.error(`Restore verification FAILED: ${error.message}`);
    process.exitCode = 1;
  }
}
