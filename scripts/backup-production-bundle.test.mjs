import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, existsSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { assessRestoredEvidence, createProductionBundle } from "./backup-production-bundle.mjs";
import { docker } from "./backup-restore-verify.mjs";

function fixture(t) {
  const root = mkdtempSync(path.join(os.tmpdir(), "alibooks-production-backup-test-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const ca = path.join(root, "ca.pem");
  writeFileSync(ca, "synthetic CA");
  const env = { PGHOST: "db.example.test", PGPORT: "5432", PGDATABASE: "cloudshop", PGUSER: "cloudshop",
    PGPASSWORD: "synthetic-secret", RDS_CA_CERT_HOST_PATH: ca, PGSSLMODE: "verify-full" };
  const backupDirectory = path.join(root, "new-bundle");
  const calls = [];
  let running = false;
  let restoredCounts = { expenses: 1 };
  let sourceWalPosition = "0/ABCDEF";
  const operations = {
    docker(args) {
      calls.push(args);
      if (args[0] === "inspect") return JSON.stringify({ State: { Running: running },
        Mounts: [{ Type: "volume", Destination: "/app/uploads", Name: "alibooks_receipt_uploads" }] });
      if (args.includes("--volumes-from")) writeFileSync(path.join(backupDirectory, "receipts", "receipt.pdf"), "synthetic receipt");
      return "";
    },
    tableRowCounts: () => ({ expenses: 1 }),
    sourceWalPosition: () => sourceWalPosition,
    dumpDatabase: (_args, { dump }) => writeFileSync(dump, "synthetic dump"),
    verifyBackup: async () => ({ relocatedPaths: 0, tableRowCounts: restoredCounts, receiptsVerified: 1,
      expensesWithoutReceipts: 0, unreferencedFiles: 0,
      voucherEvidence: { archiveSchema: "present", vouchersMissingEvidence: 0 },
      invoiceOriginals: { archiveSchema: "present", archivedInvoicesVerified: 1, issuedWithoutOriginals: 0 },
      emailDeliveryEvidence: { ledgerSchema: "present", attemptsVerified: 1, unresolvedAttempts: 0,
        unlinkedInvoiceAttempts: 0, attachmentOriginalMismatches: 0 } }),
  };
  return { env, backupDirectory, operations, calls, setRunning: value => { running = value; },
    setRestoredCounts: value => { restoredCounts = value; },
    setSourceWalPosition: value => { sourceWalPosition = value; } };
}

test("requires stopped writers and a stopped backend container", async t => {
  const f = fixture(t);
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: false, env: f.env, operations: f.operations }), /writers-stopped/);
  f.setRunning(true);
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /must be stopped/);
  assert.equal(existsSync(f.backupDirectory), false);
});

test("requires verified TLS and a receipt volume", async t => {
  const f = fixture(t);
  f.env.PGSSLMODE = "require";
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /verify-full/);
  f.env.PGSSLMODE = "verify-full";
  f.operations.docker = () => JSON.stringify({ State: { Running: false }, Mounts: [] });
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /receipt volume/);
  assert.equal(existsSync(f.backupDirectory), false);
});

test("publishes a manifest only after counts and restore agree", async t => {
  const f = fixture(t);
  const result = await createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations });
  assert.equal(result.receiptsVerified, 1);
  const manifest = JSON.parse(readFileSync(path.join(f.backupDirectory, "verified-manifest.json"), "utf8"));
  assert.equal(manifest.files.length, 1);
  assert.equal(manifest.verification.relocatedPaths, 0);
  assert.equal(manifest.sourceWalPosition, "0/ABCDEF");
  assert.deepEqual(manifest.accountingEvidence, { reviewRequired: false, reasons: [] });
  assert.equal(JSON.stringify(f.calls).includes(f.env.PGPASSWORD), false);
  assert.equal(JSON.stringify(manifest).includes(f.env.PGPASSWORD), false);
  assert.ok(f.calls.some(args => args.includes("--volumes-from") && args.includes("backend:ro")));
});

test("missing original evidence is visible without mislabeling restore as go-live approval", () => {
  assert.deepEqual(assessRestoredEvidence({ expensesWithoutReceipts: 1, unreferencedFiles: 2,
    voucherEvidence: { archiveSchema: "legacy_absent", vouchersMissingEvidence: 3 },
    invoiceOriginals: { archiveSchema: "legacy_absent", archivedInvoicesVerified: 0, issuedWithoutOriginals: 2 },
    emailDeliveryEvidence: { ledgerSchema: "legacy_absent", attemptsVerified: 0, unresolvedAttempts: 0,
      unlinkedInvoiceAttempts: 0, attachmentOriginalMismatches: 0 } }), {
    reviewRequired: true,
    reasons: ["expenses_without_receipts", "unreferenced_receipt_files",
      "voucher_evidence_archive_absent", "vouchers_missing_evidence",
      "invoice_original_archive_absent", "issued_invoices_without_originals", "email_delivery_ledger_absent"]
  });
  assert.throws(() => assessRestoredEvidence({ expensesWithoutReceipts: 0, unreferencedFiles: 0 }),
    /voucher evidence coverage/);
  assert.throws(() => assessRestoredEvidence({ expensesWithoutReceipts: 0, unreferencedFiles: 0,
    voucherEvidence: { archiveSchema: "present", vouchersMissingEvidence: 0 } }), /invoice original coverage/);
  assert.throws(() => assessRestoredEvidence({ expensesWithoutReceipts: 0, unreferencedFiles: 0,
    voucherEvidence: { archiveSchema: "present", vouchersMissingEvidence: 0 },
    invoiceOriginals: { archiveSchema: "present", archivedInvoicesVerified: 1, issuedWithoutOriginals: 0 } }),
  /email delivery evidence/);
});

test("unresolved or mismatched email evidence requires review", () => {
  assert.deepEqual(assessRestoredEvidence({ expensesWithoutReceipts: 0, unreferencedFiles: 0,
    voucherEvidence: { archiveSchema: "present", vouchersMissingEvidence: 0 },
    invoiceOriginals: { archiveSchema: "present", archivedInvoicesVerified: 1, issuedWithoutOriginals: 0 },
    emailDeliveryEvidence: { ledgerSchema: "present", attemptsVerified: 3, unresolvedAttempts: 1,
      unlinkedInvoiceAttempts: 1, attachmentOriginalMismatches: 1 } }), {
    reviewRequired: true,
    reasons: ["unresolved_email_deliveries", "email_attempts_without_invoice_or_original",
      "email_attachments_differ_from_originals"]
  });
});

test("production manifest preserves restore proof but marks missing vouchers for review", async t => {
  const f = fixture(t);
  f.operations.verifyBackup = async () => ({ relocatedPaths: 0, tableRowCounts: { expenses: 1 },
    receiptsVerified: 1, expensesWithoutReceipts: 0, unreferencedFiles: 0,
    voucherEvidence: { archiveSchema: "present", vouchersMissingEvidence: 1 },
    invoiceOriginals: { archiveSchema: "present", archivedInvoicesVerified: 1, issuedWithoutOriginals: 0 },
    emailDeliveryEvidence: { ledgerSchema: "present", attemptsVerified: 1, unresolvedAttempts: 0,
      unlinkedInvoiceAttempts: 0, attachmentOriginalMismatches: 0 } });
  const result = await createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations });
  assert.equal(result.accountingEvidence.reviewRequired, true);
  const manifest = JSON.parse(readFileSync(path.join(f.backupDirectory, "verified-manifest.json"), "utf8"));
  assert.deepEqual(manifest.accountingEvidence.reasons, ["vouchers_missing_evidence"]);
});

test("incomplete restore evidence cannot create a verified production manifest", async t => {
  const f = fixture(t);
  f.operations.verifyBackup = async () => ({ relocatedPaths: 0, tableRowCounts: { expenses: 1 },
    receiptsVerified: 1, expensesWithoutReceipts: 0, unreferencedFiles: 0 });
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /voucher evidence coverage/);
  assert.equal(existsSync(path.join(f.backupDirectory, "verified-manifest.json")), false);
});

test("a mismatched restore leaves no success manifest", async t => {
  const f = fixture(t);
  f.setRestoredCounts({ expenses: 2 });
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /not verified/);
  assert.equal(existsSync(path.join(f.backupDirectory, "verified-manifest.json")), false);
});

test("source writes block the manifest even when row counts stay unchanged", async t => {
  const f = fixture(t);
  f.operations.dumpDatabase = (_args, { dump }) => {
    writeFileSync(dump, "synthetic dump");
    f.setSourceWalPosition("0/ABCF00");
  };
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /WAL changed/);
  assert.equal(existsSync(path.join(f.backupDirectory, "verified-manifest.json")), false);
});

test("unavailable source WAL position fails before writing a backup", async t => {
  const f = fixture(t);
  f.setSourceWalPosition("");
  await assert.rejects(createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations }), /Cannot verify the source PostgreSQL WAL position/);
  assert.equal(existsSync(f.backupDirectory), false);
});

test("default WAL reader checks PostgreSQL before and after restore", async t => {
  const f = fixture(t);
  delete f.operations.sourceWalPosition;
  const originalDocker = f.operations.docker;
  let walQueries = 0;
  f.operations.docker = args => {
    if (args.includes("SELECT pg_current_wal_insert_lsn()::text;")) {
      walQueries++;
      return "0/ABCDEF";
    }
    return originalDocker(args);
  };

  await createProductionBundle({ backendContainer: "backend", backupDirectory: f.backupDirectory,
    writersStopped: true, env: f.env, operations: f.operations });

  assert.equal(walQueries, 2);
  assert.equal(JSON.parse(readFileSync(path.join(f.backupDirectory, "verified-manifest.json"), "utf8"))
    .sourceWalPosition, "0/ABCDEF");
});

test("copies receipts from a stopped container's named volume read-only", t => {
  const name = `alibooks-backup-volume-test-${randomUUID()}`;
  const root = mkdtempSync(path.join(os.tmpdir(), "alibooks-volume-copy-test-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  let volumeCreated = false;
  let containerCreated = false;
  try {
    docker(["volume", "create", name]);
    volumeCreated = true;
    docker(["create", "--name", name, "--mount", `type=volume,source=${name},target=/app/uploads`,
      "--entrypoint", "sh", "postgres:16", "-c", "true"]);
    containerCreated = true;
    docker(["run", "--rm", "--volumes-from", name, "--entrypoint", "sh", "postgres:16", "-ec",
      "mkdir -p /app/uploads/receipts; printf 'synthetic receipt' > /app/uploads/receipts/sample.pdf"]);
    mkdirSync(path.join(root, "receipts"));
    docker(["run", "--rm", "--network", "none", "--read-only", "--volumes-from", `${name}:ro`,
      "-v", `${root}:/bundle`, "--entrypoint", "sh", "postgres:16", "-ec",
      "test -d /app/uploads/receipts; cp -a /app/uploads/receipts/. /bundle/receipts/"],
    undefined, { timeoutMs: 1_800_000 });
    assert.equal(readFileSync(path.join(root, "receipts", "sample.pdf"), "utf8"), "synthetic receipt");
  } finally {
    if (containerCreated) docker(["rm", "-v", name]);
    if (volumeCreated) docker(["volume", "rm", name]);
  }
});
