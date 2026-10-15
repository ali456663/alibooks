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
const voucherContent = Buffer.from("%PDF-1.4\nSynthetic voucher document.\n%%EOF\n");
const voucherHash = createHash("sha256").update(voucherContent).digest("hex");
const invoiceContent = Buffer.from("%PDF-1.4\nSynthetic issued invoice original.\n%%EOF\n");
const invoiceHash = createHash("sha256").update(invoiceContent).digest("hex");
writeFileSync(file, content);
let passed = 0;
try {
  await isolatedDatabase(async (source) => {
    sql(source, `CREATE TABLE currencies (currency_code text PRIMARY KEY, minor_unit_exponent integer);
      INSERT INTO currencies VALUES ('SEK', 2);
      CREATE TABLE money_migration_ledger (migration_key text PRIMARY KEY, applied_at timestamptz NOT NULL);
      INSERT INTO money_migration_ledger VALUES ('journal-money-guard-v1', CURRENT_TIMESTAMP - INTERVAL '2 hours');
      INSERT INTO money_migration_ledger VALUES ('supplier-payment-exact-v1', CURRENT_TIMESTAMP - INTERVAL '2 hours');
      INSERT INTO money_migration_ledger VALUES ('customer-payment-exact-v1', CURRENT_TIMESTAMP - INTERVAL '2 hours');
      INSERT INTO money_migration_ledger VALUES ('journal-voucher-balance-v1', CURRENT_TIMESTAMP - INTERVAL '2 hours');
      INSERT INTO money_migration_ledger VALUES ('journal-voucher-seal-v1', CURRENT_TIMESTAMP - INTERVAL '2 hours');
      CREATE TABLE money_migration_runs (run_id uuid PRIMARY KEY, migration_key text, mode text, state text,
        started_at timestamptz, finished_at timestamptz, issue_count bigint);
      INSERT INTO money_migration_runs VALUES ('00000000-0000-0000-0000-000000000001', 'core-minor-v2-verified',
        'full', 'VERIFIED', CURRENT_TIMESTAMP - INTERVAL '90 minutes', CURRENT_TIMESTAMP - INTERVAL '1 hour', 0);
      CREATE TABLE expenses (id bigint PRIMARY KEY, receipt_storage_path text, receipt_sha256 text,
        net_amount integer, net_amount_minor bigint, vat_amount integer, vat_amount_minor bigint,
        total_amount integer, total_amount_minor bigint, currency_code text);
      CREATE TABLE journal_entries (id bigint, voucher_number text, debit integer, debit_minor bigint,
        credit integer, credit_minor bigint, currency_code text, voucher_evidence_id bigint,
        account_number text, voucher_date date);
      CREATE TABLE journal_voucher_registry (voucher_number text PRIMARY KEY, posting_xid bigint NOT NULL, sealed boolean NOT NULL);
      CREATE TABLE voucher_evidence (id bigint PRIMARY KEY, voucher_number text, file_name text,
        content_type text, sha256 text, uploaded_at timestamptz, file_size integer);
      CREATE TABLE voucher_evidence_contents (voucher_evidence_id bigint PRIMARY KEY, content bytea);
      CREATE FUNCTION protect_voucher_evidence() RETURNS trigger LANGUAGE plpgsql AS
        $$ BEGIN RAISE EXCEPTION 'Archived voucher evidence is immutable' USING ERRCODE = '23514'; END; $$;
      CREATE TRIGGER voucher_evidence_immutable BEFORE UPDATE OR DELETE ON voucher_evidence
        FOR EACH ROW EXECUTE FUNCTION protect_voucher_evidence();
      CREATE TRIGGER voucher_evidence_contents_immutable BEFORE UPDATE OR DELETE ON voucher_evidence_contents
        FOR EACH ROW EXECUTE FUNCTION protect_voucher_evidence();
      CREATE TABLE products (id bigint, price integer, price_minor bigint, discount_price integer, discount_price_minor bigint, currency_code text);
      CREATE TABLE customer_orders (id bigint PRIMARY KEY, ordinary_price integer, ordinary_price_minor bigint, discount_amount integer, discount_amount_minor bigint,
        net_amount integer, net_amount_minor bigint, vat_amount integer, vat_amount_minor bigint, total_amount integer, total_amount_minor bigint,
        paid_amount integer, paid_amount_minor bigint, refunded_amount integer, refunded_amount_minor bigint, currency_code text, status text);
      CREATE TABLE invoice_originals (invoice_id bigint PRIMARY KEY REFERENCES customer_orders(id), pdf bytea NOT NULL,
        sha256 varchar(64) NOT NULL, archived_at timestamptz NOT NULL);
      CREATE FUNCTION protect_invoice_original() RETURNS trigger LANGUAGE plpgsql AS
        $$ BEGIN RAISE EXCEPTION 'Issued invoice originals are immutable' USING ERRCODE = '23514'; END; $$;
      CREATE TRIGGER invoice_originals_immutable BEFORE UPDATE OR DELETE ON invoice_originals
        FOR EACH ROW EXECUTE FUNCTION protect_invoice_original();
      CREATE TABLE email_delivery_attempts (id bigint PRIMARY KEY, delivery_type text, invoice_id bigint,
        recipient_email text, subject text, body text, attachment_name text, attachment_content bytea,
        attachment_sha256 text, status text, attempts integer, created_at timestamptz,
        updated_at timestamptz, sent_at timestamptz);
      CREATE TABLE invoice_payments (id bigint, amount integer, amount_minor bigint, currency_code text);
      CREATE TABLE supplier_invoices (id bigint, total_amount integer, total_amount_minor bigint, vat_amount integer, vat_amount_minor bigint,
        net_amount integer, net_amount_minor bigint, paid_amount integer, paid_amount_minor bigint, currency_code text);
      CREATE TABLE supplier_invoice_payments (id bigint, amount numeric, amount_minor bigint, currency_code text);
      CREATE TABLE card_purchases (id bigint, net_amount integer, net_amount_minor bigint, vat_amount integer, vat_amount_minor bigint,
        total_amount integer, total_amount_minor bigint, currency_code text);
      CREATE TABLE stripe_payouts (id bigint, gross_amount integer, gross_amount_minor bigint, fee_amount integer, fee_amount_minor bigint,
        net_amount integer, net_amount_minor bigint, currency_code text);
      CREATE TABLE bank_reconciliation_entries (id bigint, amount integer, amount_minor bigint, currency_code text);
      CREATE TABLE vat_filings (id bigint, output_vat integer, output_vat_minor bigint, input_vat integer, input_vat_minor bigint,
        vat_to_pay integer, vat_to_pay_minor bigint, currency_code text);
      CREATE TABLE owner_transactions (id bigint, amount integer, amount_minor bigint, currency_code text);
      CREATE TABLE audit_events (id bigint, amount integer, amount_minor bigint, currency_code text,
        event_type text, entity_type text, entity_id text, event_action text, reference text,
        message text, actor_email text, created_at timestamp);
      CREATE FUNCTION protect_audit_event() RETURNS trigger LANGUAGE plpgsql AS
        $$ BEGIN RAISE EXCEPTION 'Audit events are append-only' USING ERRCODE = '23514'; END; $$;
      CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
        FOR EACH ROW EXECUTE FUNCTION protect_audit_event();
      CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit_events
        FOR EACH STATEMENT EXECUTE FUNCTION protect_audit_event();
      INSERT INTO expenses VALUES (1, '/app/uploads/receipts/1-test-receipt.pdf', '${hash}', 125, 12500, 0, 0, 125, 12500, 'SEK');
      INSERT INTO customer_orders (id, currency_code, status) VALUES (1, 'SEK', 'SENT');
      INSERT INTO invoice_originals VALUES (1, decode('${invoiceContent.toString("hex")}', 'hex'), '${invoiceHash}', CURRENT_TIMESTAMP);
      INSERT INTO email_delivery_attempts VALUES (1, 'INVOICE_EMAIL', 1, 'customer@example.test',
        'Invoice F-1', 'Your invoice is attached.', 'F-1.pdf',
        decode('${invoiceContent.toString("hex")}', 'hex'), '${invoiceHash}', 'SENT', 1,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
      INSERT INTO voucher_evidence VALUES (1, 'M-1', 'voucher.pdf', 'application/pdf', '${voucherHash}', CURRENT_TIMESTAMP, ${voucherContent.length});
      INSERT INTO voucher_evidence_contents VALUES (1, decode('${voucherContent.toString("hex")}', 'hex'));
      INSERT INTO journal_entries VALUES (1, 'V-1', 125, 12500, 0, 0, 'SEK', NULL, '1930', '2026-09-01'),
        (2, 'V-1', 0, 0, 125, 12500, 'SEK', NULL, '3001', '2026-09-01'),
        (3, 'M-1', 125, 12500, 0, 0, 'SEK', 1, '1930', '2026-09-02'),
        (4, 'M-1', 0, 0, 125, 12500, 'SEK', 1, '2018', '2026-09-02');
      INSERT INTO journal_voucher_registry VALUES ('V-1', 0, true), ('M-1', 0, true);`);
    const guardedTables = ["supplier_invoices", "expenses", "card_purchases", "stripe_payouts",
      "bank_reconciliation_entries", "vat_filings", "owner_transactions", "audit_events"];
    const migrationStatements = readFileSync(new URL("../db/migrations/001_startup_schema_patch.sql", import.meta.url), "utf8").split(/\r?\n/);
    const operationalGuardSql = migrationStatements.find(statement =>
      statement.startsWith("CREATE OR REPLACE FUNCTION guard_operational_money_integrity()"));
    assert.ok(operationalGuardSql, "Money guard migration statement must exist");
    const guardTriggers = Object.fromEntries(guardedTables.map(table => {
      const statement = migrationStatements.find(line => line.startsWith(`CREATE TRIGGER a_${table}_money_integrity_guard `));
      assert.ok(statement, `Money guard trigger migration must exist for ${table}`);
      return [table, statement];
    }));
    sql(source, `${operationalGuardSql}\n${Object.values(guardTriggers).join("\n")}`);
    const dump = path.join(root, "fixture.dump");
    function backup() {
      docker(["exec", source, "pg_dump", "-U", "postgres", "-d", "alibooks_restore_test", "-Fc", "-f", "/tmp/fixture.dump"]);
      docker(["cp", `${source}:/tmp/fixture.dump`, dump]);
    }
    backup();
    const good = await verifyBackup(dump, receipts);
    assert.equal(good.receiptsVerified, 1);
    assert.equal(good.journalRows, 4);
    assert.equal(good.relocatedPaths, 0);
    assert.equal(good.moneyModel.currency, "SEK");
    assert.equal(good.moneyModel.minorUnitExponent, 2);
    assert.equal(good.moneyModel.moneyColumnsVerified, 32);
    assert.equal(good.moneyModel.operationalMoneyGuardsVerified, 8);
    assert.equal(good.moneyModel.sealedVouchersVerified, 2);
    assert.deepEqual(good.voucherEvidence, { archiveSchema: "present", archivedVouchersVerified: 1, vouchersMissingEvidence: 0 });
    assert.deepEqual(good.invoiceOriginals, { archiveSchema: "present", archivedInvoicesVerified: 1, issuedWithoutOriginals: 0 });
    assert.deepEqual(good.emailDeliveryEvidence, { ledgerSchema: "present", attemptsVerified: 1,
      unresolvedAttempts: 0, unlinkedInvoiceAttempts: 0, attachmentOriginalMismatches: 0 });
    console.log("PASS: real pg_dump/pg_restore, receipt, voucher, invoice and delivery evidence"); passed++;

    sql(source, "ALTER TABLE invoice_originals DISABLE TRIGGER invoice_originals_immutable;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /invoice original archive lacks its enabled immutable guard/);
    sql(source, "ALTER TABLE invoice_originals ENABLE TRIGGER invoice_originals_immutable;");
    console.log("PASS: disabled invoice original guard blocks restore approval"); passed++;

    sql(source, `CREATE OR REPLACE FUNCTION protect_invoice_original() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RETURN OLD; END; $$;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /invoice original archive does not reject UPDATE/);
    sql(source, `CREATE OR REPLACE FUNCTION protect_invoice_original() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RAISE EXCEPTION 'Issued invoice originals are immutable' USING ERRCODE = '23514'; END; $$;`);
    console.log("PASS: no-op invoice original guard blocks restore approval"); passed++;

    sql(source, "ALTER TABLE voucher_evidence DISABLE TRIGGER voucher_evidence_immutable;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /voucher evidence metadata lacks its enabled immutable guard/);
    sql(source, "ALTER TABLE voucher_evidence ENABLE TRIGGER voucher_evidence_immutable;");
    console.log("PASS: disabled voucher metadata guard blocks restore approval"); passed++;

    sql(source, "ALTER TABLE voucher_evidence_contents DISABLE TRIGGER voucher_evidence_contents_immutable;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /voucher evidence content lacks its enabled immutable guard/);
    sql(source, "ALTER TABLE voucher_evidence_contents ENABLE TRIGGER voucher_evidence_contents_immutable;");
    console.log("PASS: disabled voucher content guard blocks restore approval"); passed++;

    sql(source, `CREATE OR REPLACE FUNCTION protect_voucher_evidence() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RETURN OLD; END; $$;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /voucher evidence metadata does not reject UPDATE/);
    sql(source, `CREATE OR REPLACE FUNCTION protect_voucher_evidence() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RAISE EXCEPTION 'Archived voucher evidence is immutable' USING ERRCODE = '23514'; END; $$;`);
    console.log("PASS: no-op voucher evidence guard blocks restore approval"); passed++;

    sql(source, "ALTER TABLE audit_events DISABLE TRIGGER audit_events_no_truncate;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /audit history lacks enabled append-only guards/);
    sql(source, "ALTER TABLE audit_events ENABLE TRIGGER audit_events_no_truncate;");
    console.log("PASS: disabled audit history guard blocks restore approval"); passed++;

    sql(source, `CREATE OR REPLACE FUNCTION protect_audit_event() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RETURN NULL; END; $$;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /does not reject TRUNCATE with its append-only guard/);
    sql(source, `CREATE OR REPLACE FUNCTION protect_audit_event() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RAISE EXCEPTION 'Audit events are append-only' USING ERRCODE = '23514'; END; $$;`);
    console.log("PASS: no-op audit history guard blocks restore approval"); passed++;

    sql(source, "ALTER TABLE expenses DISABLE TRIGGER a_expenses_money_integrity_guard;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /missing enabled operational money integrity guards/);
    sql(source, "ALTER TABLE expenses ENABLE TRIGGER a_expenses_money_integrity_guard;");
    console.log("PASS: disabled money guard blocks restore approval"); passed++;

    sql(source, "DROP TRIGGER a_card_purchases_money_integrity_guard ON card_purchases;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /missing enabled operational money integrity guards/);
    sql(source, guardTriggers.card_purchases);
    console.log("PASS: missing money guard blocks restore approval"); passed++;

    sql(source, `DROP TRIGGER a_card_purchases_money_integrity_guard ON card_purchases;
      CREATE TRIGGER a_card_purchases_money_integrity_guard BEFORE INSERT OR UPDATE ON card_purchases
      FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('net_amount_minor', 'net_amount',
        'vat_amount', 'vat_amount_minor', 'total_amount', 'total_amount_minor');`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /exact field arguments/);
    sql(source, `DROP TRIGGER a_card_purchases_money_integrity_guard ON card_purchases;
      ${guardTriggers.card_purchases}`);
    console.log("PASS: misordered money guard arguments block restore approval"); passed++;

    sql(source, `CREATE OR REPLACE FUNCTION guard_operational_money_integrity() RETURNS trigger LANGUAGE plpgsql AS
      $$ BEGIN RETURN NEW; END; $$;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /does not reject invalid currency and minor amounts/);
    sql(source, operationalGuardSql);
    console.log("PASS: no-op money guard blocks restore approval"); passed++;

    sql(source, "UPDATE email_delivery_attempts SET attachment_sha256 = repeat('0', 64) WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /email delivery ledger has 1 corrupted or invalid attempt/);
    sql(source, `UPDATE email_delivery_attempts SET attachment_sha256 = '${invoiceHash}' WHERE id = 1;`);
    console.log("PASS: corrupted delivery attachment blocks restore approval"); passed++;

    sql(source, "UPDATE email_delivery_attempts SET status = 'UNCERTAIN', sent_at = NULL WHERE id = 1;"); backup();
    assert.equal((await verifyBackup(dump, receipts)).emailDeliveryEvidence.unresolvedAttempts, 1);
    sql(source, "UPDATE email_delivery_attempts SET status = 'NOT_DELIVERED' WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /non-delivery decision\(s\) without matching audit evidence/);
    console.log("PASS: non-delivery without an operator audit trail blocks restore approval"); passed++;
    sql(source, `INSERT INTO audit_events (id, amount, amount_minor, currency_code, event_type,
      entity_type, entity_id, event_action, reference, message, actor_email, created_at)
      VALUES (1, 0, 0, 'SEK', 'email_delivery', 'invoice', '1',
        'delivery_reconciled_not_delivered', '1',
        'SMTP provider confirmed that this message was not accepted.',
        'operator@example.test', CURRENT_TIMESTAMP);`); backup();
    assert.equal((await verifyBackup(dump, receipts)).emailDeliveryEvidence.unresolvedAttempts, 0);
    console.log("PASS: reconciled non-delivery remains valid after restore"); passed++;

    sql(source, "UPDATE email_delivery_attempts SET sent_at = CURRENT_TIMESTAMP WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /email delivery ledger has 1 corrupted or invalid attempt/);
    sql(source, "UPDATE email_delivery_attempts SET sent_at = NULL, status = 'FORGED' WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /email delivery ledger has 1 corrupted or invalid attempt/);
    sql(source, "UPDATE email_delivery_attempts SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id = 1;");
    console.log("PASS: unresolved email and invalid delivery states are detected after restore"); passed++;

    sql(source, "UPDATE email_delivery_attempts SET invoice_id = 999 WHERE id = 1;"); backup();
    assert.equal((await verifyBackup(dump, receipts)).emailDeliveryEvidence.unlinkedInvoiceAttempts, 1);
    sql(source, "UPDATE email_delivery_attempts SET invoice_id = 1 WHERE id = 1;");
    console.log("PASS: delivery without invoice original is reported for production review"); passed++;

    sql(source, "UPDATE email_delivery_attempts SET delivery_type = 'INVOICE_REMINDER', invoice_id = 999 WHERE id = 1;"); backup();
    assert.equal((await verifyBackup(dump, receipts)).emailDeliveryEvidence.unlinkedInvoiceAttempts, 1);
    sql(source, "UPDATE email_delivery_attempts SET invoice_id = 1 WHERE id = 1;"); backup();
    assert.equal((await verifyBackup(dump, receipts)).emailDeliveryEvidence.unlinkedInvoiceAttempts, 0);
    sql(source, "UPDATE email_delivery_attempts SET delivery_type = 'INVOICE_EMAIL' WHERE id = 1;");
    console.log("PASS: reminder delivery without its invoice is reported for production review"); passed++;

    const otherAttachment = Buffer.from("%PDF-1.4\nDifferent stored delivery.\n%%EOF\n");
    const otherHash = createHash("sha256").update(otherAttachment).digest("hex");
    sql(source, `UPDATE email_delivery_attempts SET attachment_content = decode('${otherAttachment.toString("hex")}', 'hex'),
      attachment_sha256 = '${otherHash}' WHERE id = 1;`); backup();
    assert.equal((await verifyBackup(dump, receipts)).emailDeliveryEvidence.attachmentOriginalMismatches, 1);
    sql(source, `UPDATE email_delivery_attempts SET attachment_content = decode('${invoiceContent.toString("hex")}', 'hex'),
      attachment_sha256 = '${invoiceHash}' WHERE id = 1;`);
    console.log("PASS: sent attachment differing from original is reported for production review"); passed++;

    sql(source, "ALTER TABLE invoice_originals DISABLE TRIGGER invoice_originals_immutable; UPDATE invoice_originals SET sha256 = repeat('0', 64) WHERE invoice_id = 1; ALTER TABLE invoice_originals ENABLE TRIGGER invoice_originals_immutable;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /invoice original archive has 1 corrupted or invalid document/);
    sql(source, `ALTER TABLE invoice_originals DISABLE TRIGGER invoice_originals_immutable; UPDATE invoice_originals SET sha256 = '${invoiceHash}' WHERE invoice_id = 1; ALTER TABLE invoice_originals ENABLE TRIGGER invoice_originals_immutable;`);
    console.log("PASS: corrupted invoice original blocks restore approval"); passed++;

    sql(source, "ALTER TABLE invoice_originals DISABLE TRIGGER invoice_originals_immutable; DELETE FROM invoice_originals WHERE invoice_id = 1; ALTER TABLE invoice_originals ENABLE TRIGGER invoice_originals_immutable;"); backup();
    assert.deepEqual((await verifyBackup(dump, receipts)).invoiceOriginals,
      { archiveSchema: "present", archivedInvoicesVerified: 0, issuedWithoutOriginals: 1 });
    sql(source, `INSERT INTO invoice_originals VALUES (1, decode('${invoiceContent.toString("hex")}', 'hex'), '${invoiceHash}', CURRENT_TIMESTAMP);`);
    console.log("PASS: issued invoice without original is reported for production review"); passed++;

    sql(source, "ALTER TABLE invoice_originals DISABLE TRIGGER invoice_originals_immutable; UPDATE invoice_originals SET pdf = decode('6e6f74206120706466', 'hex') WHERE invoice_id = 1; ALTER TABLE invoice_originals ENABLE TRIGGER invoice_originals_immutable;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /invoice original archive has 1 corrupted or invalid document/);
    sql(source, `ALTER TABLE invoice_originals DISABLE TRIGGER invoice_originals_immutable; UPDATE invoice_originals SET pdf = decode('${invoiceContent.toString("hex")}', 'hex') WHERE invoice_id = 1; ALTER TABLE invoice_originals ENABLE TRIGGER invoice_originals_immutable;`);
    console.log("PASS: invalid archived invoice PDF blocks restore approval"); passed++;

    sql(source, "DROP TABLE invoice_originals;"); backup();
    assert.deepEqual((await verifyBackup(dump, receipts)).invoiceOriginals,
      { archiveSchema: "legacy_absent", archivedInvoicesVerified: 0, issuedWithoutOriginals: 1 });
    sql(source, `CREATE TABLE invoice_originals (invoice_id bigint PRIMARY KEY REFERENCES customer_orders(id), pdf bytea NOT NULL,
      sha256 varchar(64) NOT NULL, archived_at timestamptz NOT NULL);
      INSERT INTO invoice_originals VALUES (1, decode('${invoiceContent.toString("hex")}', 'hex'), '${invoiceHash}', CURRENT_TIMESTAMP);
      CREATE TRIGGER invoice_originals_immutable BEFORE UPDATE OR DELETE ON invoice_originals
        FOR EACH ROW EXECUTE FUNCTION protect_invoice_original();`);
    console.log("PASS: missing invoice original archive is reported for production review"); passed++;

    sql(source, "DELETE FROM journal_voucher_registry WHERE voucher_number = 'V-1';"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /voucher registry has missing/);
    sql(source, "INSERT INTO journal_voucher_registry VALUES ('V-1', 0, true);");
    console.log("PASS: missing posted voucher registry row blocks restore approval"); passed++;

    sql(source, "UPDATE journal_voucher_registry SET sealed = false WHERE voucher_number = 'V-1';"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /voucher registry has missing/);
    sql(source, "UPDATE journal_voucher_registry SET sealed = true WHERE voucher_number = 'V-1';");
    console.log("PASS: unsealed posted voucher blocks restore approval"); passed++;

    sql(source, "INSERT INTO journal_voucher_registry VALUES ('V-orphan', 0, true);"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /voucher registry has missing/);
    sql(source, "DELETE FROM journal_voucher_registry WHERE voucher_number = 'V-orphan';");
    console.log("PASS: orphaned voucher registry row blocks restore approval"); passed++;

    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP WHERE migration_key = 'journal-voucher-seal-v1';"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /after the required guards/);
    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE migration_key = 'journal-voucher-seal-v1';");
    console.log("PASS: full money check predating voucher seal blocks restore approval"); passed++;

    sql(source, `ALTER TABLE voucher_evidence_contents DISABLE TRIGGER voucher_evidence_contents_immutable;
      UPDATE voucher_evidence_contents SET content = set_byte(content, 10, 88) WHERE voucher_evidence_id = 1;
      ALTER TABLE voucher_evidence_contents ENABLE TRIGGER voucher_evidence_contents_immutable;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /missing or corrupted document/);
    sql(source, `ALTER TABLE voucher_evidence_contents DISABLE TRIGGER voucher_evidence_contents_immutable;
      UPDATE voucher_evidence_contents SET content = decode('${voucherContent.toString("hex")}', 'hex') WHERE voucher_evidence_id = 1;
      ALTER TABLE voucher_evidence_contents ENABLE TRIGGER voucher_evidence_contents_immutable;`);
    console.log("PASS: corrupted archived voucher document blocks restore approval"); passed++;

    sql(source, `ALTER TABLE voucher_evidence_contents DISABLE TRIGGER voucher_evidence_contents_immutable;
      DELETE FROM voucher_evidence_contents WHERE voucher_evidence_id = 1;
      ALTER TABLE voucher_evidence_contents ENABLE TRIGGER voucher_evidence_contents_immutable;`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /missing or corrupted document/);
    sql(source, `INSERT INTO voucher_evidence_contents VALUES (1, decode('${voucherContent.toString("hex")}', 'hex'));`);
    console.log("PASS: missing archived voucher document blocks restore approval"); passed++;

    sql(source, "UPDATE journal_entries SET voucher_evidence_id = 1 WHERE voucher_number = 'V-1';"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /wrong or partial journal links/);
    sql(source, "UPDATE journal_entries SET voucher_evidence_id = NULL WHERE voucher_number = 'V-1';");
    console.log("PASS: document linked to a different voucher blocks restore approval"); passed++;

    sql(source, "UPDATE journal_entries SET voucher_evidence_id = NULL WHERE id = 4;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /wrong or partial journal links/);
    sql(source, "UPDATE journal_entries SET voucher_evidence_id = 1 WHERE id = 4;");
    console.log("PASS: partially linked voucher blocks restore approval"); passed++;

    sql(source, `INSERT INTO journal_entries VALUES (5, 'M-legacy', 1, 100, 0, 0, 'SEK', NULL, '1930', '2026-09-03'),
      (6, 'M-legacy', 0, 0, 1, 100, 'SEK', NULL, '2018', '2026-09-03');
      INSERT INTO journal_voucher_registry VALUES ('M-legacy', 0, true);`); backup();
    assert.equal((await verifyBackup(dump, receipts)).voucherEvidence.vouchersMissingEvidence, 1);
    sql(source, "DELETE FROM journal_entries WHERE voucher_number = 'M-legacy'; DELETE FROM journal_voucher_registry WHERE voucher_number = 'M-legacy';"); backup();
    console.log("PASS: historical manual voucher without evidence is reported separately"); passed++;

    const deploy = readFileSync(new URL("./ec2-deploy.sh", import.meta.url), "utf8").replaceAll("\r\n", "\n");
    function deployProbe(id, mount) {
      const stub = `COMPOSE_FILE=/etc/hosts
ENV_FILE=/tmp/alibooks-test.env
FRONTEND_URL=https://books.example.test
BACKEND_URL=https://books.example.test/api
ALLOW_UNVERIFIED_BOOTSTRAP=true
cat > "$ENV_FILE" <<'TEST_ENV'
DOCKERHUB_USERNAME=alibooks-test
IMAGE_TAG=sha-abcdef0
APP_FRONTEND_URL=https://books.example.test
APP_CORS_ALLOWED_ORIGINS=https://books.example.test
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/alibooks
SPRING_DATASOURCE_USERNAME=alibooks
SPRING_DATASOURCE_PASSWORD=synthetic-test-password
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
APP_SCHEMA_PATCH_ENABLED=false
APP_CORS_LOCAL_DEV_ENABLED=false
APP_TEST_DATA_RESET_ENABLED=false
APP_BANK_RECONCILIATION_RESET_ENABLED=false
JWT_SECRET=synthetic-test-jwt-secret-with-more-than-32-chars
APP_AUTH_REGISTRATION_BOOTSTRAP_KEY=synthetic-test-bootstrap-key-different-and-long
APP_AUTH_MFA_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
APP_AUTH_REQUIRE_MFA=true
TEST_ENV
docker() {
  case "$*" in
    *"ps -a -q backend") printf '%s\\n' '${id}' ;;
    inspect*) printf '%s\\n' '${mount}' ;;
    *" up -d"*) printf 'MOCK-UP\\n' ;;
    *" config --quiet"|*" pull"|*" ps"|*" logs --tail=40 backend") : ;;
    *) return 99 ;;
  esac
}
sh() {
  [ "$1" = "./scripts/prod-smoke-test.sh" ] || return 99
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
    assert.equal(manifest.verification.journalRows, 4);
    assert.equal(manifest.verification.moneyModel.moneyColumnsVerified, 32);
    assert.equal(manifest.verification.moneyModel.sealedVouchersVerified, 2);
    assert.equal(manifest.verification.voucherEvidence.archivedVouchersVerified, 1);
    assert.deepEqual(manifest.verification.tableRowCounts, manifest.sourceTableRowCounts);
    console.log("PASS: combined backup copies and restores every file, including unlinked documents"); passed++;
    await assert.rejects(createLocalBundle(bundleOptions), /EEXIST/);
    console.log("PASS: existing backup directory is never overwritten"); passed++;
    rmSync(path.join(receipts, "unlinked.pdf"));

    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP WHERE migration_key = 'journal-money-guard-v1';");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /after the required guards/);
    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE migration_key = 'journal-money-guard-v1';");
    console.log("PASS: full money check predating journal guard blocks restore approval"); passed++;

    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP WHERE migration_key = 'supplier-payment-exact-v1';");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /after the required guards/);
    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE migration_key = 'supplier-payment-exact-v1';");
    console.log("PASS: full money check predating supplier payment guard blocks restore approval"); passed++;

    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP WHERE migration_key = 'customer-payment-exact-v1';");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /after the required guards/);
    sql(source, "UPDATE money_migration_ledger SET applied_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE migration_key = 'customer-payment-exact-v1';");
    console.log("PASS: full money check predating customer payment guard blocks restore approval"); passed++;

    sql(source, `INSERT INTO money_migration_runs VALUES ('00000000-0000-0000-0000-000000000002',
      'core-minor-v2-verified', 'full', 'FAILED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1);`);
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /latest verified zero-issue/);
    sql(source, "DELETE FROM money_migration_runs WHERE run_id = '00000000-0000-0000-0000-000000000002';");
    console.log("PASS: later failed money check cannot be hidden by older verified run"); passed++;

    sql(source, "INSERT INTO supplier_invoice_payments VALUES (1, 125, 12501, 'SEK');");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /supplier_invoice_payments.amount/);
    sql(source, "UPDATE supplier_invoice_payments SET amount_minor = 12500 WHERE id = 1;");
    console.log("PASS: supplier payment minor-unit mismatch blocks restore approval"); passed++;

    sql(source, "UPDATE supplier_invoice_payments SET amount = 125.001 WHERE id = 1;");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /supplier_invoice_payments.amount/);
    sql(source, "DELETE FROM supplier_invoice_payments WHERE id = 1;");
    console.log("PASS: amount requiring rounding blocks restore approval"); passed++;

    sql(source, "INSERT INTO supplier_invoice_payments VALUES (2, 125.50, 12550, 'SEK');");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /supplier_invoice_payments.amount/);
    sql(source, "DELETE FROM supplier_invoice_payments WHERE id = 2;");
    console.log("PASS: exact-cent fractional supplier payment blocks restore approval"); passed++;

    sql(source, "ALTER TABLE invoice_payments ALTER COLUMN amount TYPE numeric;");
    sql(source, "INSERT INTO invoice_payments VALUES (1, 125.50, 12550, 'SEK');");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /invoice_payments.amount/);
    sql(source, "DELETE FROM invoice_payments WHERE id = 1;");
    console.log("PASS: historical fractional customer payment blocks restore approval"); passed++;

    sql(source, "ALTER TABLE expenses ALTER COLUMN net_amount TYPE numeric; "
      + "ALTER TABLE expenses ALTER COLUMN total_amount TYPE numeric; "
      + "ALTER TABLE expenses DISABLE TRIGGER a_expenses_money_integrity_guard; "
      + "UPDATE expenses SET net_amount = 125.50, net_amount_minor = 12550, "
      + "total_amount = 125.50, total_amount_minor = 12550 WHERE id = 1; "
      + "ALTER TABLE expenses ENABLE TRIGGER a_expenses_money_integrity_guard;");
    backup();
    await assert.rejects(verifyBackup(dump, receipts), /expenses.net_amount/);
    sql(source, "ALTER TABLE expenses DISABLE TRIGGER a_expenses_money_integrity_guard; "
      + "UPDATE expenses SET net_amount = 125, net_amount_minor = 12500, "
      + "total_amount = 125, total_amount_minor = 12500 WHERE id = 1; "
      + "ALTER TABLE expenses ENABLE TRIGGER a_expenses_money_integrity_guard;");
    console.log("PASS: exact-cent fractional expense blocks restore approval while legacy APIs use kronor"); passed++;

    sql(source, "INSERT INTO expenses (id, receipt_storage_path, receipt_sha256, net_amount, net_amount_minor, vat_amount, vat_amount_minor, total_amount, total_amount_minor, currency_code) VALUES (2, NULL, NULL, 0, 0, 0, 0, 0, 0, 'SEK');"); backup();
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
    sql(source, "UPDATE expenses SET receipt_storage_path = '/app/uploads/receipts/1-test-receipt.pdf'; UPDATE journal_entries SET account_number = NULL WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /missing account\/date/);
    sql(source, "UPDATE journal_entries SET account_number = '1930' WHERE id = 1;");
    console.log("PASS: journal line without account blocks restore approval"); passed++;
    sql(source, "UPDATE journal_entries SET voucher_date = NULL WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /missing account\/date/);
    sql(source, "UPDATE journal_entries SET voucher_date = '2026-09-01' WHERE id = 1;");
    console.log("PASS: journal line without date blocks restore approval"); passed++;
    sql(source, "UPDATE journal_entries SET voucher_date = '2026-09-04' WHERE id = 1;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /inconsistent voucher dates/);
    sql(source, "UPDATE journal_entries SET voucher_date = '2026-09-01' WHERE id = 1;");
    console.log("PASS: voucher with mixed dates blocks restore approval"); passed++;
    sql(source, "UPDATE expenses SET receipt_storage_path = '/app/uploads/receipts/1-test-receipt.pdf'; UPDATE journal_entries SET credit = 124, credit_minor = 12400 WHERE credit = 125;"); backup();
    await assert.rejects(verifyBackup(dump, receipts), /invalid or unbalanced/);
    console.log("PASS: unbalanced journal blocks verification"); passed++;
    sql(source, `UPDATE journal_entries SET credit = 125, credit_minor = 12500 WHERE id IN (2, 4);
      UPDATE journal_entries SET debit = 125, debit_minor = 12500, credit = 125, credit_minor = 12500
      WHERE voucher_number = 'V-1';`); backup();
    await assert.rejects(verifyBackup(dump, receipts), /invalid or unbalanced/);
    sql(source, "UPDATE journal_entries SET credit = 0, credit_minor = 0 WHERE id = 1; UPDATE journal_entries SET debit = 0, debit_minor = 0 WHERE id = 2;");
    console.log("PASS: balanced voucher with both sides positive on each row blocks verification"); passed++;
    sql(source, `UPDATE journal_entries SET credit = 125, credit_minor = 12500 WHERE id IN (2, 4);
      ALTER TABLE journal_entries DROP COLUMN voucher_evidence_id;
      DROP TABLE voucher_evidence_contents;
      DROP TABLE voucher_evidence;`); backup();
    assert.deepEqual((await verifyBackup(dump, receipts)).voucherEvidence,
      { archiveSchema: "legacy_absent", archivedVouchersVerified: 0, vouchersMissingEvidence: 1 });
    console.log("PASS: a pre-migration backup remains verifiable and reports legacy missing evidence"); passed++;
    writeFileSync(dump, "not a PostgreSQL dump");
    await assert.rejects(verifyBackup(dump, receipts), /Docker exec failed/);
    console.log("PASS: invalid dump blocks verification"); passed++;

  });
  assert.equal(passed, 61);
  console.log(`Backup/restore integration: ${passed}/61 passed. Synthetic fixtures, not a production backup approval.`);
} finally {
  // root is the unique directory returned by mkdtempSync, never user input.
  assert.equal(path.dirname(path.resolve(root)), path.resolve(os.tmpdir()));
  assert.ok(path.basename(root).startsWith("alibooks-restore-test-"));
  rmSync(root, { recursive: true, force: true });
}
