import { isDeepStrictEqual } from "node:util";
import { lstatSync, readFileSync, readdirSync, realpathSync } from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { assessRestoredEvidence } from "./backup-production-bundle.mjs";
import { sha256, verifyBackup } from "./backup-restore-verify.mjs";

const RECEIPT_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;
const SHA256 = /^[a-f0-9]{64}$/i;

function regularFileIn(parent, name) {
  const file = path.join(parent, name);
  if (!lstatSync(file).isFile() || lstatSync(file).isSymbolicLink()
      || path.dirname(realpathSync(file)) !== parent) {
    throw new Error("Backup package contains an unsafe file.");
  }
  return file;
}

export async function verifyBackupBundle(directory, { strict = false, restore = verifyBackup, hash = sha256 } = {}) {
  if (!lstatSync(directory).isDirectory() || lstatSync(directory).isSymbolicLink()) {
    throw new Error("Backup package must be a real directory.");
  }
  const root = realpathSync(directory);
  const receipts = path.join(root, "receipts");
  if (!lstatSync(receipts).isDirectory() || lstatSync(receipts).isSymbolicLink()
      || realpathSync(receipts) !== receipts) {
    throw new Error("Backup receipts must be a real directory inside the package.");
  }
  const dump = regularFileIn(root, "database.dump");
  const manifestFile = regularFileIn(root, "verified-manifest.json");
  if (!isDeepStrictEqual(readdirSync(root).sort(), ["database.dump", "receipts", "verified-manifest.json"].sort())) {
    throw new Error("Backup package contains unexpected or missing files.");
  }
  let manifest;
  const manifestText = readFileSync(manifestFile, "utf8");
  try {
    manifest = JSON.parse(manifestText);
  } catch {
    throw new Error("Backup manifest is not valid JSON.");
  }
  if (manifest?.version !== 1 || manifest.writersStoppedConfirmed !== true
      || !SHA256.test(manifest.databaseSha256 ?? "") || !Array.isArray(manifest.files)
      || !manifest.verification || !manifest.sourceTableRowCounts) {
    throw new Error("Backup manifest is incomplete.");
  }
  if ((await hash(dump)).toLowerCase() !== manifest.databaseSha256.toLowerCase()) {
    throw new Error("Database dump differs from the verified manifest.");
  }
  const expectedNames = new Set();
  for (const entry of manifest.files) {
    if (!RECEIPT_NAME.test(entry?.name ?? "") || !SHA256.test(entry?.sha256 ?? "")
        || expectedNames.has(entry.name)) {
      throw new Error("Backup manifest has an invalid or duplicate receipt entry.");
    }
    expectedNames.add(entry.name);
    const file = regularFileIn(receipts, entry.name);
    if ((await hash(file)).toLowerCase() !== entry.sha256.toLowerCase()) {
      throw new Error("Receipt differs from the verified manifest.");
    }
  }
  if (!isDeepStrictEqual(readdirSync(receipts).sort(), [...expectedNames].sort())) {
    throw new Error("Backup receipts differ from the manifest inventory.");
  }
  const restored = await restore(dump, receipts);
  if (readFileSync(regularFileIn(root, "verified-manifest.json"), "utf8") !== manifestText
      || (await hash(regularFileIn(root, "database.dump"))).toLowerCase() !== manifest.databaseSha256.toLowerCase()) {
    throw new Error("Backup package changed during restore verification.");
  }
  for (const entry of manifest.files) {
    if ((await hash(regularFileIn(receipts, entry.name))).toLowerCase() !== entry.sha256.toLowerCase()) {
      throw new Error("Backup package changed during restore verification.");
    }
  }
  if (!isDeepStrictEqual(restored, manifest.verification)
      || !isDeepStrictEqual(restored.tableRowCounts, manifest.sourceTableRowCounts)) {
    throw new Error("Restored data no longer matches the verified source and manifest.");
  }
  if (manifest.accountingEvidence) {
    const accountingEvidence = assessRestoredEvidence(restored);
    if (!isDeepStrictEqual(accountingEvidence, manifest.accountingEvidence)) {
      throw new Error("Accounting evidence no longer matches the restored data.");
    }
  }
  if (strict) {
    if (!/^[0-9A-F]+\/[0-9A-F]+$/i.test(manifest.sourceWalPosition ?? "")
        || !manifest.accountingEvidence || manifest.accountingEvidence.reviewRequired !== false
        || restored.relocatedPaths !== 0) {
      throw new Error("Backup is restorable but cannot be used as production go-live evidence.");
    }
  }
  return {
    restored: true,
    strict,
    tableCount: Object.keys(restored.tableRowCounts).length,
    receiptCount: expectedNames.size,
    accountingReviewRequired: manifest.accountingEvidence?.reviewRequired ?? true,
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const [directory, option] = process.argv.slice(2);
    if (!directory || process.argv.length > 4 || (option && option !== "--strict")) {
      throw new Error("Usage: node scripts/verify-backup-bundle.mjs <backup-directory> [--strict]");
    }
    console.log(JSON.stringify(await verifyBackupBundle(directory, { strict: option === "--strict" }), null, 2));
  } catch (error) {
    console.error(`Backup bundle verification FAILED: ${error.message}`);
    process.exitCode = 1;
  }
}
