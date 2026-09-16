import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { docker, isolatedDatabase, sql, verifyBackup } from "./backup-restore-verify.mjs";
import { createLocalBundle } from "./backup-local-bundle.mjs";

// Synthetic data only. Never read production settings, dumps or uploads.
const root = mkdtempSync(path.join(os.tmpdir(), "alibooks-restore-test-"));
const receipts = path.join(root, "receipts");
mkdirSync(receipts);
const file = path.join(receipts, "1-test-receipt.pdf");
const content = Buffer.from("%PDF-1.4\nSynthetic AliBooks receipt, not a customer document.\n%%EOF\n");
const hash = createHash("sha256").update(content).digest("hex");
writeFileSync(file, content);
let passed = 0;
try {
  await isolatedDatabase(async (source) => {
    sql(source, `CREATE TABLE expenses (id bigint PRIMARY KEY, receipt_storage_path text, receipt_sha256 text);
      CREATE TABLE journal_entries (voucher_number text, debit integer, credit integer);
      INSERT INTO expenses VALUES (1, '/app/uploads/receipts/1-test-receipt.pdf', '${hash}');
      INSERT INTO journal_entries VALUES ('V-1', 125, 0), ('V-1', 0, 125);`);
    const dump = path.join(root, "fixture.dump");
    function backup() {
      docker(["exec", source, "pg_dump", "-U", "postgres", "-d", "alibooks_restore_test", "-Fc", "-f", "/tmp/fixture.dump"]);
      docker(["cp", `${source}:/tmp/fixture.dump`, dump]);
    }
    backup();
    const good = await verifyBackup(dump, receipts);
    assert.equal(good.receiptsVerified, 1);
    assert.equal(good.journalRows, 2);
    assert.equal(good.relocatedPaths, 0);
    console.log("PASS: real pg_dump/pg_restore, receipt copy and journal verification"); passed++;

    writeFileSync(path.join(receipts, "unlinked.pdf"), "Unlinked synthetic file");
    const bundleOptions = { container: source, database: "alibooks_restore_test", username: "postgres",
      receiptsDirectory: receipts, backupDirectory: path.join(root, "bundle"), writersStopped: true };
    await assert.rejects(createLocalBundle({ ...bundleOptions, writersStopped: false }), /Stop all app/);
    console.log("PASS: combined backup requires stopped-writer confirmation"); passed++;
    const bundle = await createLocalBundle(bundleOptions);
    assert.equal(bundle.archiveFilesVerified, 2);
    assert.equal(bundle.unreferencedFiles, 1);
    assert.equal(bundle.expensesWithoutReceipts, 0);
    const manifest = JSON.parse(readFileSync(path.join(bundle.directory, "verified-manifest.json"), "utf8"));
    assert.equal(manifest.files.length, 2);
    assert.equal(manifest.databaseSha256.length, 64);
    assert.equal(manifest.verification.journalRows, 2);
    assert.deepEqual(manifest.sourceTableRowCounts, { expenses: 1, journal_entries: 2 });
    assert.deepEqual(manifest.verification.tableRowCounts, manifest.sourceTableRowCounts);
    console.log("PASS: combined backup copies and restores every file, including unlinked documents"); passed++;
    await assert.rejects(createLocalBundle(bundleOptions), /EEXIST/);
    console.log("PASS: existing backup directory is never overwritten"); passed++;
    rmSync(path.join(receipts, "unlinked.pdf"));

    sql(source, "INSERT INTO expenses VALUES (2, NULL, NULL);"); backup();
    assert.equal((await verifyBackup(dump, receipts)).expensesWithoutReceipts, 1);
    console.log("PASS: missing expense evidence is explicitly reported"); passed++;
    sql(source, `UPDATE expenses SET receipt_sha256 = '${hash}' WHERE id = 2;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /without a storage reference/);
    console.log("PASS: orphaned receipt metadata cannot be silently ignored"); passed++;
    sql(source, "DELETE FROM expenses WHERE id = 2;"); backup();

    rmSync(file);
    await assert.rejects(verifyBackup(dump, receipts), /receipt is missing/);
    console.log("PASS: missing receipt blocks verification"); passed++;
    writeFileSync(file, "corrupted receipt");
    await assert.rejects(verifyBackup(dump, receipts), /checksum mismatch/);
    console.log("PASS: corrupted receipt blocks verification"); passed++;
    writeFileSync(file, content);

    sql(source, "UPDATE expenses SET receipt_sha256 = NULL;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /valid stored SHA-256/);
    console.log("PASS: unverified legacy receipt hash blocks verification"); passed++;
    sql(source, `UPDATE expenses SET receipt_sha256 = '${hash}', receipt_storage_path = 'C:/old-machine/uploads/receipts/1-test-receipt.pdf';`); backup();
    assert.equal((await verifyBackup(dump, receipts)).relocatedPaths, 1);
    console.log("PASS: relocated Windows path is reported, never followed"); passed++;

    sql(source, "UPDATE expenses SET receipt_storage_path = '/outside/../receipt.pdf';"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /receipt is missing/);
    console.log("PASS: database path cannot read outside backup directory"); passed++;
    sql(source, "UPDATE expenses SET receipt_storage_path = '/app/uploads/receipts/1-test-receipt.pdf'; UPDATE journal_entries SET credit = 124 WHERE credit = 125;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /unbalanced vouchers/);
    console.log("PASS: unbalanced journal blocks verification"); passed++;
    writeFileSync(dump, "not a PostgreSQL dump");
    await assert.rejects(verifyBackup(dump, receipts), /Docker exec failed/);
    console.log("PASS: invalid dump blocks verification"); passed++;

    const deploy = readFileSync(new URL("./ec2-deploy.sh", import.meta.url), "utf8").replaceAll("\r\n", "\n");
    function deployProbe(id, mount) {
      const stub = `COMPOSE_FILE=/etc/hosts
ENV_FILE=/etc/hosts
FRONTEND_URL=
BACKEND_URL=
docker() {
  case "$*" in
    *"ps -a -q backend") printf '%s\\n' '${id}' ;;
    inspect*) printf '%s\\n' '${mount}' ;;
    *" up -d") printf 'MOCK-UP\\n' ;;
    *" pull"|*" ps"|*" logs --tail=40 backend") : ;;
    *) return 99 ;;
  esac
}
`;
      return docker(["exec", "-i", source, "sh"], stub + deploy);
    }
    assert.throws(() => deployProbe("legacy-container", ""), /Docker exec failed/);
    console.log("PASS: deploy blocks legacy container without receipt volume"); passed++;
    assert.match(deployProbe("persistent-container", "/app/uploads"), /MOCK-UP/);
    console.log("PASS: deploy permits existing persistent receipt volume"); passed++;
    assert.match(deployProbe("", ""), /MOCK-UP/);
    console.log("PASS: first deployment permits new receipt volume"); passed++;
  });
  console.log(`Backup/restore integration: ${passed}/16 passed. Synthetic fixtures, not a production backup approval.`);
} finally {
  // root is the unique directory returned by mkdtempSync, never user input.
  assert.equal(path.dirname(path.resolve(root)), path.resolve(os.tmpdir()));
  assert.ok(path.basename(root).startsWith("alibooks-restore-test-"));
  rmSync(root, { recursive: true, force: true });
}
