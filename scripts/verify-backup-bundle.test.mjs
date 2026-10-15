import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { verifyBackupBundle } from "./verify-backup-bundle.mjs";

const digest = value => createHash("sha256").update(value).digest("hex");

function fixture(t, { missingEvidence = 0 } = {}) {
  const root = mkdtempSync(path.join(os.tmpdir(), "alibooks-bundle-check-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(path.join(root, "receipts"));
  writeFileSync(path.join(root, "database.dump"), "database");
  writeFileSync(path.join(root, "receipts", "receipt.pdf"), "receipt");
  const verification = {
    tableRowCounts: { journal_entries: 2 }, relocatedPaths: 0,
    expensesWithoutReceipts: 0, unreferencedFiles: 0,
    voucherEvidence: { archiveSchema: "present", vouchersMissingEvidence: missingEvidence },
    invoiceOriginals: { archiveSchema: "present", archivedInvoicesVerified: 1, issuedWithoutOriginals: 0 },
    emailDeliveryEvidence: { ledgerSchema: "present", attemptsVerified: 1, unresolvedAttempts: 0,
      unlinkedInvoiceAttempts: 0, attachmentOriginalMismatches: 0 },
  };
  const manifest = {
    version: 1, writersStoppedConfirmed: true, sourceWalPosition: "0/1234",
    sourceTableRowCounts: { journal_entries: 2 }, databaseSha256: digest("database"),
    files: [{ name: "receipt.pdf", sha256: digest("receipt") }], verification,
    accountingEvidence: { reviewRequired: missingEvidence > 0,
      reasons: missingEvidence > 0 ? ["vouchers_missing_evidence"] : [] },
  };
  const manifestFile = path.join(root, "verified-manifest.json");
  writeFileSync(manifestFile, JSON.stringify(manifest));
  return { root, manifestFile, verification };
}

test("rechecks hashes, restore and clean production evidence", async t => {
  const { root, verification } = fixture(t);
  let restores = 0;
  const result = await verifyBackupBundle(root, { strict: true, restore: async () => {
    restores++;
    return verification;
  } });
  assert.equal(restores, 1);
  assert.deepEqual(result, { restored: true, strict: true, tableCount: 1,
    receiptCount: 1, accountingReviewRequired: false });
});

test("rejects a changed dump or receipt before restore", async t => {
  const { root, verification } = fixture(t);
  const restore = async () => verification;
  writeFileSync(path.join(root, "database.dump"), "changed");
  await assert.rejects(verifyBackupBundle(root, { restore }), /Database dump differs/);
  writeFileSync(path.join(root, "database.dump"), "database");
  writeFileSync(path.join(root, "receipts", "receipt.pdf"), "changed");
  await assert.rejects(verifyBackupBundle(root, { restore }), /Receipt differs/);
});

test("rejects changed manifest data and extra files", async t => {
  const { root, manifestFile, verification } = fixture(t);
  const original = JSON.parse(readFileSync(manifestFile, "utf8"));
  writeFileSync(manifestFile, JSON.stringify({ ...original,
    verification: { ...verification, tableRowCounts: { journal_entries: 3 } } }));
  await assert.rejects(verifyBackupBundle(root, { restore: async () => verification }), /Restored data no longer matches/);
  writeFileSync(manifestFile, JSON.stringify(original));
  writeFileSync(path.join(root, "receipts", "extra.pdf"), "extra");
  await assert.rejects(verifyBackupBundle(root, { restore: async () => verification }), /inventory/);
});

test("rejects a package modified while the isolated restore is running", async t => {
  const { root, verification } = fixture(t);
  await assert.rejects(verifyBackupBundle(root, { restore: async () => {
    writeFileSync(path.join(root, "database.dump"), "modified during restore");
    return verification;
  } }), /changed during restore verification/);
});

test("strict mode rejects missing voucher evidence without rejecting a technically valid restore", async t => {
  const { root, verification } = fixture(t, { missingEvidence: 1 });
  assert.equal((await verifyBackupBundle(root, { restore: async () => verification })).accountingReviewRequired, true);
  await assert.rejects(verifyBackupBundle(root, { strict: true, restore: async () => verification }),
    /cannot be used as production go-live evidence/);
});

test("strict mode rejects issued invoices without archived originals", async t => {
  const { root, manifestFile, verification } = fixture(t);
  verification.invoiceOriginals = { archiveSchema: "present", archivedInvoicesVerified: 0, issuedWithoutOriginals: 1 };
  const manifest = JSON.parse(readFileSync(manifestFile, "utf8"));
  manifest.verification = verification;
  manifest.accountingEvidence = { reviewRequired: true, reasons: ["issued_invoices_without_originals"] };
  writeFileSync(manifestFile, JSON.stringify(manifest));
  assert.equal((await verifyBackupBundle(root, { restore: async () => verification })).accountingReviewRequired, true);
  await assert.rejects(verifyBackupBundle(root, { strict: true, restore: async () => verification }),
    /cannot be used as production go-live evidence/);
});

test("strict mode rejects unresolved email delivery despite a valid restore", async t => {
  const { root, manifestFile, verification } = fixture(t);
  verification.emailDeliveryEvidence.unresolvedAttempts = 1;
  const manifest = JSON.parse(readFileSync(manifestFile, "utf8"));
  manifest.verification = verification;
  manifest.accountingEvidence = { reviewRequired: true, reasons: ["unresolved_email_deliveries"] };
  writeFileSync(manifestFile, JSON.stringify(manifest));
  assert.equal((await verifyBackupBundle(root, { restore: async () => verification })).accountingReviewRequired, true);
  await assert.rejects(verifyBackupBundle(root, { strict: true, restore: async () => verification }),
    /cannot be used as production go-live evidence/);
});
