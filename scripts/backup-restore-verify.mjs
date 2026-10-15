import { spawnSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { createReadStream, lstatSync, realpathSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout as sleep } from "node:timers/promises";

export function docker(args, input, { timeoutMs = 120_000 } = {}) {
  const result = spawnSync(process.platform === "win32" ? "docker.exe" : "docker", args, {
    input, encoding: "utf8", timeout: timeoutMs, maxBuffer: 32 * 1024 * 1024,
  });
  // Database errors can contain personal data. Do not print SQL or Docker output.
  if (result.error || result.status !== 0) throw new Error(`Docker ${args[0]} failed (exit ${result.status ?? "unknown"}).`);
  return result.stdout.trim();
}

let postgresImagePull;

async function ensurePostgresImage() {
  postgresImagePull ??= Promise.resolve().then(() => docker(["pull", "postgres:16"]));
  try {
    await postgresImagePull;
  } catch (error) {
    postgresImagePull = undefined;
    throw error;
  }
}

export async function isolatedDatabase(action) {
  const name = `alibooks-restore-${randomUUID()}`;
  let created = false;
  let actionError;
  try {
    await ensurePostgresImage();
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
  } catch (error) {
    actionError = error;
    throw error;
  } finally {
    if (created) {
      try {
        docker(["rm", "-f", name]);
      } catch (cleanupError) {
        if (!actionError) throw cleanupError;
        if (actionError instanceof Error) {
          actionError.message += " Isolated test container cleanup also failed; check Docker when it is available.";
        }
      }
    }
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
  for (const table of tables) {
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(table)) throw new Error("Unsupported table name in backup inventory.");
  }
  if (tables.length === 0) return {};
  const selects = tables.map(table => `SELECT '${table}' AS name, count(*) AS row_count FROM public."${table}"`);
  const rows = JSON.parse(query(`SELECT coalesce(json_agg(r), '[]'::json) FROM (${selects.join(" UNION ALL ")}) r;`));
  return Object.fromEntries(rows.map(row => {
    const count = Number(row.row_count);
    if (!Number.isSafeInteger(count) || count < 0) throw new Error("Unsupported row count in backup inventory.");
    return [row.name, count];
  }));
}

const MONEY_COLUMNS = [
  ["products", "price", "price_minor"],
  ["products", "discount_price", "discount_price_minor"],
  ["customer_orders", "ordinary_price", "ordinary_price_minor"],
  ["customer_orders", "discount_amount", "discount_amount_minor"],
  ["customer_orders", "net_amount", "net_amount_minor"],
  ["customer_orders", "vat_amount", "vat_amount_minor"],
  ["customer_orders", "total_amount", "total_amount_minor"],
  ["customer_orders", "paid_amount", "paid_amount_minor"],
  ["customer_orders", "refunded_amount", "refunded_amount_minor"],
  ["invoice_payments", "amount", "amount_minor"],
  ["supplier_invoices", "total_amount", "total_amount_minor"],
  ["supplier_invoices", "vat_amount", "vat_amount_minor"],
  ["supplier_invoices", "net_amount", "net_amount_minor"],
  ["supplier_invoices", "paid_amount", "paid_amount_minor"],
  ["supplier_invoice_payments", "amount", "amount_minor"],
  ["expenses", "net_amount", "net_amount_minor"],
  ["expenses", "vat_amount", "vat_amount_minor"],
  ["expenses", "total_amount", "total_amount_minor"],
  ["card_purchases", "net_amount", "net_amount_minor"],
  ["card_purchases", "vat_amount", "vat_amount_minor"],
  ["card_purchases", "total_amount", "total_amount_minor"],
  ["stripe_payouts", "gross_amount", "gross_amount_minor"],
  ["stripe_payouts", "fee_amount", "fee_amount_minor"],
  ["stripe_payouts", "net_amount", "net_amount_minor"],
  ["bank_reconciliation_entries", "amount", "amount_minor"],
  ["vat_filings", "output_vat", "output_vat_minor"],
  ["vat_filings", "input_vat", "input_vat_minor"],
  ["vat_filings", "vat_to_pay", "vat_to_pay_minor"],
  ["journal_entries", "debit", "debit_minor"],
  ["journal_entries", "credit", "credit_minor"],
  ["owner_transactions", "amount", "amount_minor"],
  ["audit_events", "amount", "amount_minor"],
];

const OPERATIONAL_MONEY_GUARDS = [
  "supplier_invoices", "expenses", "card_purchases", "stripe_payouts",
  "bank_reconciliation_entries", "vat_filings", "owner_transactions", "audit_events",
];

const SCHEMA_INVENTORIES = new WeakMap();

function schemaInventory(query) {
  if (!SCHEMA_INVENTORIES.has(query)) {
    const inventory = JSON.parse(query(`SELECT json_build_object(
      'tables', (SELECT coalesce(json_agg(tablename), '[]'::json) FROM pg_tables WHERE schemaname = 'public'),
      'columns', (SELECT coalesce(json_agg(table_name || '.' || column_name), '[]'::json)
        FROM information_schema.columns WHERE table_schema = 'public'));`));
    SCHEMA_INVENTORIES.set(query, {
      tables: new Set(inventory.tables),
      columns: new Set(inventory.columns),
    });
  }
  return SCHEMA_INVENTORIES.get(query);
}

function hasTable(query, table) {
  return schemaInventory(query).tables.has(table);
}

function hasColumn(query, table, column) {
  return schemaInventory(query).columns.has(`${table}.${column}`);
}

function verifyMoneyModel(query) {
  const requiredTables = [...new Set(MONEY_COLUMNS.map(([table]) => table)), "currencies", "money_migration_runs", "money_migration_ledger", "journal_voucher_registry"];
  const missingTables = requiredTables.filter(table => !hasTable(query, table));
  if (missingTables.length > 0) {
    throw new Error(`Restored database lacks the money migration schema: ${missingTables.join(", ")}.`);
  }

  const missingColumns = MONEY_COLUMNS.flatMap(([table, legacy, minor]) =>
    [legacy, minor].filter(column => !hasColumn(query, table, column)).map(column => `${table}.${column}`));
  const moneyTables = [...new Set(MONEY_COLUMNS.map(([table]) => table))];
  const missingCurrencyColumns = moneyTables
    .filter(table => !hasColumn(query, table, "currency_code"))
    .map(table => `${table}.currency_code`);
  if (missingColumns.length > 0 || missingCurrencyColumns.length > 0) {
    throw new Error(`Restored database lacks money columns: ${[...missingColumns, ...missingCurrencyColumns].join(", ")}.`);
  }
  const missingRegistryColumns = ["voucher_number", "posting_xid", "sealed"]
    .filter(column => !hasColumn(query, "journal_voucher_registry", column));
  if (missingRegistryColumns.length > 0) {
    throw new Error(`Restored database lacks voucher registry columns: ${missingRegistryColumns.join(", ")}.`);
  }

  const currency = JSON.parse(query(`SELECT row_to_json(r) FROM
    (SELECT currency_code, minor_unit_exponent FROM public.currencies WHERE currency_code = 'SEK') r;`));
  if (!currency || currency.minor_unit_exponent !== 2) {
    throw new Error("Restored database has no valid SEK minor-unit exponent of 2.");
  }

  const mismatchSelects = MONEY_COLUMNS.map(([table, legacy, minor]) =>
    `SELECT '${table}.${legacy}' AS field, count(*) AS mismatch_count FROM public."${table}"
      WHERE ("${legacy}" IS NULL) <> ("${minor}" IS NULL)
         OR ("${legacy}" IS NOT NULL AND
             (CAST("${legacy}" AS numeric) <> TRUNC(CAST("${legacy}" AS numeric))
              OR CAST("${legacy}" AS numeric) * 100 <> ROUND(CAST("${legacy}" AS numeric) * 100, 0)
              OR "${minor}" IS DISTINCT FROM CAST("${legacy}" AS numeric) * 100))`);
  const mismatchRows = JSON.parse(query(`SELECT coalesce(json_agg(m), '[]'::json)
    FROM (${mismatchSelects.join(" UNION ALL ")}) m;`));
  const mismatches = mismatchRows.filter(row => Number(row.mismatch_count) > 0)
    .map(row => `${row.field} has ${row.mismatch_count} legacy/minor mismatch(es)`);
  if (mismatches.length > 0) {
    throw new Error(`Restored money model is inconsistent: ${mismatches.join("; ")}.`);
  }

  const currencySelects = moneyTables.map(table =>
    `SELECT '${table}' AS name, count(*) AS wrong_count FROM public."${table}"
      WHERE currency_code IS NULL OR currency_code <> 'SEK'`);
  const wrongCurrency = JSON.parse(query(`SELECT coalesce(json_agg(c), '[]'::json)
    FROM (${currencySelects.join(" UNION ALL ")}) c;`))
    .map(row => [row.name, Number(row.wrong_count)])
    .filter(([, count]) => count > 0);
  if (wrongCurrency.length > 0) {
    throw new Error(`Restored database has invalid currency codes: ${wrongCurrency.map(([table, count]) => `${table}=${count}`).join(", ")}.`);
  }

  const verifiedRuns = Number(query(`SELECT count(*) FROM
    (SELECT state, issue_count, finished_at FROM public.money_migration_runs
     WHERE migration_key = 'core-minor-v2-verified' AND mode = 'full'
     ORDER BY started_at DESC LIMIT 1) latest_run
    JOIN public.money_migration_ledger guard ON guard.migration_key = 'journal-money-guard-v1'
    JOIN public.money_migration_ledger supplier_guard ON supplier_guard.migration_key = 'supplier-payment-exact-v1'
    JOIN public.money_migration_ledger customer_guard ON customer_guard.migration_key = 'customer-payment-exact-v1'
    JOIN public.money_migration_ledger balance_guard ON balance_guard.migration_key = 'journal-voucher-balance-v1'
    JOIN public.money_migration_ledger seal_guard ON seal_guard.migration_key = 'journal-voucher-seal-v1'
    WHERE latest_run.state = 'VERIFIED' AND latest_run.issue_count = 0
      AND latest_run.finished_at >= guard.applied_at
      AND latest_run.finished_at >= supplier_guard.applied_at
      AND latest_run.finished_at >= customer_guard.applied_at
      AND latest_run.finished_at >= balance_guard.applied_at
      AND latest_run.finished_at >= seal_guard.applied_at;`));
  if (verifiedRuns !== 1) {
    throw new Error("Restored database has no latest verified zero-issue full money check after the required guards.");
  }

  const expectedOperationalGuards = OPERATIONAL_MONEY_GUARDS.map(table => {
    const argumentsForTable = MONEY_COLUMNS.filter(([name]) => name === table)
      .flatMap(([, legacy, minor]) => [legacy, minor]);
    return `('${table}', 'a_${table}_money_integrity_guard', ${argumentsForTable.length}, '${Buffer.from(`${argumentsForTable.join("\0")}\0`).toString("hex")}')`;
  });
  const operationalGuards = Number(query(`SELECT count(*) FROM (VALUES ${expectedOperationalGuards.join(", ")})
      AS expected(table_name, trigger_name, argument_count, argument_hex)
    JOIN pg_class table_ref ON table_ref.relname = expected.table_name
    JOIN pg_namespace table_schema ON table_schema.oid = table_ref.relnamespace
    JOIN pg_trigger t ON t.tgrelid = table_ref.oid AND t.tgname = expected.trigger_name
    JOIN pg_proc guard ON guard.oid = t.tgfoid
    JOIN pg_namespace guard_schema ON guard_schema.oid = guard.pronamespace
    WHERE table_schema.nspname = 'public' AND guard_schema.nspname = 'public'
      AND guard.proname = 'guard_operational_money_integrity'
      AND t.tgenabled = 'O' AND NOT t.tgisinternal
      AND (t.tgtype & 23) = 23
      AND t.tgnargs = expected.argument_count
      AND encode(t.tgargs, 'hex') = expected.argument_hex;`));
  if (operationalGuards !== OPERATIONAL_MONEY_GUARDS.length) {
    throw new Error("Restored database is missing enabled operational money integrity guards with exact field arguments.");
  }
  try {
    query(`CREATE TEMP TABLE operational_money_guard_probe
        (amount integer, amount_minor bigint, currency_code text);
      CREATE TRIGGER probe_money_integrity_guard BEFORE INSERT ON operational_money_guard_probe
        FOR EACH ROW EXECUTE FUNCTION public.guard_operational_money_integrity('amount', 'amount_minor');
      DO $$ BEGIN
        BEGIN
          INSERT INTO operational_money_guard_probe VALUES (1, 100, 'EUR');
          RAISE EXCEPTION 'Invalid operational currency was allowed';
        EXCEPTION WHEN check_violation THEN
          IF SQLERRM <> 'Invalid operational money currency' THEN RAISE; END IF;
        END;
        BEGIN
          INSERT INTO operational_money_guard_probe VALUES (1, 101, 'SEK');
          RAISE EXCEPTION 'Invalid operational minor amount was allowed';
        EXCEPTION WHEN check_violation THEN
          IF SQLERRM <> 'Invalid operational money values: amount' THEN RAISE; END IF;
        END;
      END $$;`);
  } catch {
    throw new Error("Restored operational money guard does not reject invalid currency and minor amounts.");
  }

  const unbalancedVouchers = Number(query(`SELECT count(*) FROM
    (SELECT voucher_number FROM public.journal_entries GROUP BY voucher_number
     HAVING COALESCE(sum(debit_minor), 0) <> COALESCE(sum(credit_minor), 0)
        OR bool_or(debit_minor IS NULL OR credit_minor IS NULL OR debit_minor < 0 OR credit_minor < 0
           OR (debit_minor > 0 AND credit_minor > 0))
        OR voucher_number IS NULL OR btrim(voucher_number) = '') invalid;`));
  if (unbalancedVouchers !== 0) {
    throw new Error("Restored journal is invalid or unbalanced in minor units.");
  }

  const missingJournalReferences = Number(query(`SELECT count(*) FROM public.journal_entries
    WHERE voucher_date IS NULL OR account_number IS NULL OR btrim(account_number) = '';`));
  const mixedVoucherDates = Number(query(`SELECT count(*) FROM
    (SELECT voucher_number FROM public.journal_entries
     WHERE voucher_number IS NOT NULL AND btrim(voucher_number) <> ''
     GROUP BY voucher_number HAVING count(DISTINCT voucher_date) > 1) mixed_dates;`));
  if (missingJournalReferences || mixedVoucherDates) {
    throw new Error("Restored journal has missing account/date or inconsistent voucher dates.");
  }

  const missingRegistry = Number(query(`SELECT count(*) FROM
    (SELECT DISTINCT voucher_number FROM public.journal_entries) vouchers
    LEFT JOIN public.journal_voucher_registry registry USING (voucher_number)
    WHERE registry.voucher_number IS NULL;`));
  const orphanRegistry = Number(query(`SELECT count(*) FROM public.journal_voucher_registry registry
    WHERE NOT EXISTS (SELECT 1 FROM public.journal_entries j WHERE j.voucher_number = registry.voucher_number);`));
  const invalidRegistry = Number(query(`SELECT count(*) FROM public.journal_voucher_registry
    WHERE sealed IS DISTINCT FROM true OR posting_xid IS NULL
       OR voucher_number IS NULL OR btrim(voucher_number) = '';`));
  if (missingRegistry || orphanRegistry || invalidRegistry) {
    throw new Error("Restored journal voucher registry has missing, orphaned or unsealed entries.");
  }

  return {
    currency: currency.currency_code,
    minorUnitExponent: currency.minor_unit_exponent,
    moneyColumnsVerified: MONEY_COLUMNS.length,
    moneyMigrationRunsVerified: verifiedRuns,
    operationalMoneyGuardsVerified: operationalGuards,
    unbalancedVouchers,
    sealedVouchersVerified: Number(query("SELECT count(*) FROM public.journal_voucher_registry;")),
  };
}

export function verifyAuditTrailProtection(query) {
  const guards = Number(query(`SELECT count(*) FROM (VALUES
    ('audit_events_immutable', 27), ('audit_events_no_truncate', 34)
  ) AS expected(trigger_name, event_mask)
    JOIN pg_class relation ON relation.relname = 'audit_events'
    JOIN pg_namespace relation_schema ON relation_schema.oid = relation.relnamespace
    JOIN pg_trigger trigger_row ON trigger_row.tgrelid = relation.oid
      AND trigger_row.tgname = expected.trigger_name AND NOT trigger_row.tgisinternal
    JOIN pg_proc guard ON guard.oid = trigger_row.tgfoid
    JOIN pg_namespace guard_schema ON guard_schema.oid = guard.pronamespace
    WHERE relation_schema.nspname = 'public' AND guard_schema.nspname = 'public'
      AND guard.proname = 'protect_audit_event'
      AND trigger_row.tgenabled IN ('O', 'A')
      AND (trigger_row.tgtype & expected.event_mask) = expected.event_mask;`));
  if (guards !== 2) throw new Error("Restored audit history lacks enabled append-only guards.");
  try {
    query(`DO $$ BEGIN
      BEGIN
        TRUNCATE public.audit_events;
        RAISE EXCEPTION 'Audit TRUNCATE was allowed';
      EXCEPTION WHEN check_violation THEN
        IF SQLERRM <> 'Audit events are append-only' THEN RAISE; END IF;
      END;
    END $$;`);
  } catch {
    throw new Error("Restored audit history does not reject TRUNCATE with its append-only guard.");
  }
}

function verifyImmutableArchiveGuard(query, { table, trigger, guard, key, field, error, label }) {
  const installed = Number(query(`SELECT count(*) FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_proc p ON p.oid = t.tgfoid
    JOIN pg_namespace pn ON pn.oid = p.pronamespace
    WHERE n.nspname = 'public' AND c.relname = '${table}'
      AND pn.nspname = 'public' AND p.proname = '${guard}'
      AND t.tgname = '${trigger}' AND NOT t.tgisinternal
      AND t.tgenabled IN ('O', 'A') AND (t.tgtype & 27) = 27;`));
  if (installed !== 1) throw new Error(`Restored ${label} lacks its enabled immutable guard.`);
  if (query(`SELECT EXISTS (SELECT 1 FROM public.${table});`) !== "t") return;

  for (const operation of ["UPDATE", "DELETE"]) {
    const mutation = operation === "UPDATE"
      ? `UPDATE public.${table} SET ${field} = ${field} WHERE ${key} = (SELECT ${key} FROM public.${table} ORDER BY ${key} LIMIT 1);`
      : `DELETE FROM public.${table} WHERE ${key} = (SELECT ${key} FROM public.${table} ORDER BY ${key} LIMIT 1);`;
    try {
      query(`DO $$ BEGIN
        BEGIN
          ${mutation}
          RAISE EXCEPTION 'Immutable archive mutation was allowed';
        EXCEPTION WHEN check_violation THEN
          IF SQLERRM <> '${error}' THEN RAISE; END IF;
        END;
      END $$;`);
    } catch {
      throw new Error(`Restored ${label} does not reject ${operation} with its immutable guard.`);
    }
  }
}

export function verifyVoucherEvidence(query) {
  const hasMetadata = hasTable(query, "voucher_evidence");
  const hasContents = hasTable(query, "voucher_evidence_contents");
  const hasJournalLink = hasColumn(query, "journal_entries", "voucher_evidence_id");
  const missing = () => Number(query(`SELECT count(DISTINCT voucher_number) FROM public.journal_entries
    WHERE split_part(voucher_number, '-', 1) IN ('M', 'IB') AND voucher_evidence_id IS NULL;`));
  if (!hasMetadata && !hasContents && !hasJournalLink) {
    return {
      archiveSchema: "legacy_absent",
      archivedVouchersVerified: 0,
      vouchersMissingEvidence: Number(query(`SELECT count(DISTINCT voucher_number) FROM public.journal_entries
        WHERE split_part(voucher_number, '-', 1) IN ('M', 'IB');`)),
    };
  }
  for (const [table, columns] of [
    ["voucher_evidence", ["id", "voucher_number", "sha256", "file_size"]],
    ["voucher_evidence_contents", ["voucher_evidence_id", "content"]],
    ["journal_entries", ["voucher_number", "voucher_evidence_id"]],
  ]) {
    if (!hasTable(query, table) || columns.some(column => !hasColumn(query, table, column))) {
      throw new Error(`Restored database lacks voucher evidence schema: ${table}.`);
    }
  }

  verifyImmutableArchiveGuard(query, { table: "voucher_evidence", trigger: "voucher_evidence_immutable",
    guard: "protect_voucher_evidence", key: "id", field: "sha256",
    error: "Archived voucher evidence is immutable", label: "voucher evidence metadata" });
  verifyImmutableArchiveGuard(query, { table: "voucher_evidence_contents", trigger: "voucher_evidence_contents_immutable",
    guard: "protect_voucher_evidence", key: "voucher_evidence_id", field: "content",
    error: "Archived voucher evidence is immutable", label: "voucher evidence content" });

  // Only the isolated restored database is modified; the source dump remains untouched.
  query("CREATE EXTENSION IF NOT EXISTS pgcrypto;");
  const damaged = Number(query(`SELECT count(*) FROM public.voucher_evidence e
    LEFT JOIN public.voucher_evidence_contents c ON c.voucher_evidence_id = e.id
    WHERE c.voucher_evidence_id IS NULL OR e.file_size IS DISTINCT FROM octet_length(c.content)
       OR upper(e.sha256) IS DISTINCT FROM upper(encode(digest(c.content, 'sha256'), 'hex'));`));
  if (damaged !== 0) throw new Error(`Restored voucher evidence has ${damaged} missing or corrupted document(s).`);

  const orphanContents = Number(query(`SELECT count(*) FROM public.voucher_evidence_contents c
    LEFT JOIN public.voucher_evidence e ON e.id = c.voucher_evidence_id WHERE e.id IS NULL;`));
  const orphanMetadata = Number(query(`SELECT count(*) FROM public.voucher_evidence e
    WHERE NOT EXISTS (SELECT 1 FROM public.journal_entries j WHERE j.voucher_evidence_id = e.id);`));
  const wrongLinks = Number(query(`SELECT count(*) FROM public.journal_entries j
    LEFT JOIN public.voucher_evidence e ON e.id = j.voucher_evidence_id
    WHERE j.voucher_evidence_id IS NOT NULL
      AND (e.id IS NULL OR e.voucher_number IS DISTINCT FROM j.voucher_number);`));
  const partialLinks = Number(query(`SELECT count(*) FROM
    (SELECT voucher_number FROM public.journal_entries GROUP BY voucher_number
     HAVING count(DISTINCT voucher_evidence_id) > 1
        OR (bool_or(voucher_evidence_id IS NULL) AND bool_or(voucher_evidence_id IS NOT NULL))) inconsistent;`));
  if (orphanContents || orphanMetadata || wrongLinks || partialLinks) {
    throw new Error("Restored voucher evidence has orphaned, wrong or partial journal links.");
  }

  return {
    archiveSchema: "present",
    archivedVouchersVerified: Number(query("SELECT count(*) FROM public.voucher_evidence;")),
    vouchersMissingEvidence: missing(),
  };
}

export function verifyInvoiceOriginals(query) {
  if (!hasColumn(query, "customer_orders", "status")) {
    throw new Error("Restored database lacks invoice status schema.");
  }
  const hasArchive = hasTable(query, "invoice_originals");
  if (hasArchive && ["invoice_id", "pdf", "sha256", "archived_at"]
    .some(column => !hasColumn(query, "invoice_originals", column))) {
    throw new Error("Restored database lacks invoice original archive schema.");
  }
  if (hasArchive) {
    verifyImmutableArchiveGuard(query, { table: "invoice_originals", trigger: "invoice_originals_immutable",
      guard: "protect_invoice_original", key: "invoice_id", field: "pdf",
      error: "Issued invoice originals are immutable", label: "invoice original archive" });
    // The check runs on the isolated restore, never on the production source.
    query("CREATE EXTENSION IF NOT EXISTS pgcrypto;");
    const damaged = Number(query(`SELECT count(*) FROM public.invoice_originals i
      LEFT JOIN public.customer_orders o ON o.id = i.invoice_id
      WHERE o.id IS NULL OR o.status IS NULL OR o.status = 'DRAFT'
         OR i.pdf IS NULL OR octet_length(i.pdf) < 5 OR octet_length(i.pdf) > 10485760
         OR substring(i.pdf from 1 for 5) IS DISTINCT FROM decode('255044462d', 'hex')
         OR i.sha256 IS NULL
         OR upper(i.sha256) IS DISTINCT FROM upper(encode(digest(i.pdf, 'sha256'), 'hex'))
         OR i.archived_at IS NULL;`));
    if (damaged !== 0) throw new Error(`Restored invoice original archive has ${damaged} corrupted or invalid document(s).`);
  }
  const issuedWithoutOriginals = Number(query(hasArchive
    ? `SELECT count(*) FROM public.customer_orders o WHERE o.status IS DISTINCT FROM 'DRAFT'
       AND NOT EXISTS (SELECT 1 FROM public.invoice_originals i WHERE i.invoice_id = o.id);`
    : `SELECT count(*) FROM public.customer_orders WHERE status IS DISTINCT FROM 'DRAFT';`));
  return {
    archiveSchema: hasArchive ? "present" : "legacy_absent",
    archivedInvoicesVerified: hasArchive ? Number(query("SELECT count(*) FROM public.invoice_originals;")) : 0,
    issuedWithoutOriginals,
  };
}

export function verifyEmailDeliveryEvidence(query) {
  if (!hasTable(query, "email_delivery_attempts")) {
    return { ledgerSchema: "legacy_absent", attemptsVerified: 0, unresolvedAttempts: 0,
      unlinkedInvoiceAttempts: 0, attachmentOriginalMismatches: 0 };
  }
  const requiredColumns = ["id", "delivery_type", "invoice_id", "recipient_email", "subject", "body",
    "attachment_name", "attachment_content", "attachment_sha256", "status", "attempts",
    "created_at", "updated_at", "sent_at"];
  if (requiredColumns.some(column => !hasColumn(query, "email_delivery_attempts", column))) {
    throw new Error("Restored database lacks email delivery ledger schema.");
  }
  query("CREATE EXTENSION IF NOT EXISTS pgcrypto;");
  const damaged = Number(query(`SELECT count(*) FROM public.email_delivery_attempts a
    WHERE a.delivery_type IS NULL OR a.delivery_type NOT IN ('INVOICE_EMAIL', 'INVOICE_REMINDER', 'OVERDUE_REMINDER')
       OR a.status IS NULL OR a.status NOT IN ('PENDING', 'UNCERTAIN', 'SENT', 'NOT_DELIVERED')
       OR a.invoice_id IS NULL OR a.recipient_email IS NULL OR btrim(a.recipient_email) = ''
       OR a.subject IS NULL OR btrim(a.subject) = '' OR a.body IS NULL OR btrim(a.body) = ''
       OR a.attempts IS NULL OR a.attempts < 1 OR a.created_at IS NULL OR a.updated_at IS NULL
       OR (a.status = 'SENT' AND a.sent_at IS NULL)
       OR (a.status <> 'SENT' AND a.sent_at IS NOT NULL)
       OR (a.attachment_content IS NULL) <> (a.attachment_sha256 IS NULL)
       OR (a.attachment_content IS NOT NULL AND
           upper(a.attachment_sha256) IS DISTINCT FROM upper(encode(digest(a.attachment_content, 'sha256'), 'hex')))
       OR (a.delivery_type = 'INVOICE_EMAIL' AND
           (a.attachment_name IS NULL OR btrim(a.attachment_name) = ''
            OR a.attachment_content IS NULL OR octet_length(a.attachment_content) < 5
            OR octet_length(a.attachment_content) > 10485760
            OR substring(a.attachment_content from 1 for 5) IS DISTINCT FROM decode('255044462d', 'hex')));`));
  if (damaged !== 0) throw new Error(`Restored email delivery ledger has ${damaged} corrupted or invalid attempt(s).`);

  const reconciledNonDeliveries = Number(query("SELECT count(*) FROM public.email_delivery_attempts WHERE status = 'NOT_DELIVERED';"));
  if (reconciledNonDeliveries > 0) {
    const auditColumns = ["event_type", "entity_type", "entity_id", "event_action", "reference",
      "message", "actor_email", "created_at"];
    if (!hasTable(query, "audit_events") || auditColumns.some(column => !hasColumn(query, "audit_events", column))) {
      throw new Error("Restored email non-delivery decisions lack an audit trail schema.");
    }
    const missingAudit = Number(query(`SELECT count(*) FROM public.email_delivery_attempts a
      WHERE a.status = 'NOT_DELIVERED' AND NOT EXISTS (
        SELECT 1 FROM public.audit_events e
        WHERE e.event_type = 'email_delivery' AND e.entity_type = 'invoice'
          AND e.entity_id = a.invoice_id::text
          AND e.event_action = 'delivery_reconciled_not_delivered'
          AND e.reference = a.id::text
          AND e.message IS NOT NULL AND length(btrim(e.message)) BETWEEN 20 AND 500
          AND e.actor_email IS NOT NULL AND btrim(e.actor_email) <> ''
          AND e.actor_email NOT IN ('system', 'authenticated-user')
          AND e.created_at IS NOT NULL);`));
    if (missingAudit !== 0) {
      throw new Error(`Restored email delivery ledger has ${missingAudit} non-delivery decision(s) without matching audit evidence.`);
    }
  }

  const unlinkedInvoiceAttempts = Number(query(hasTable(query, "invoice_originals")
    ? `SELECT count(*) FROM public.email_delivery_attempts a
       LEFT JOIN public.customer_orders o ON o.id = a.invoice_id
       LEFT JOIN public.invoice_originals i ON i.invoice_id = a.invoice_id
       WHERE o.id IS NULL OR (a.delivery_type = 'INVOICE_EMAIL' AND i.invoice_id IS NULL);`
    : `SELECT count(*) FROM public.email_delivery_attempts a
       LEFT JOIN public.customer_orders o ON o.id = a.invoice_id
       WHERE o.id IS NULL OR a.delivery_type = 'INVOICE_EMAIL';`));
  const attachmentOriginalMismatches = hasTable(query, "invoice_originals")
    ? Number(query(`SELECT count(*) FROM public.email_delivery_attempts a
        JOIN public.invoice_originals i ON i.invoice_id = a.invoice_id
        WHERE a.delivery_type = 'INVOICE_EMAIL'
          AND upper(a.attachment_sha256) IS DISTINCT FROM upper(i.sha256);`)) : 0;
  return {
    ledgerSchema: "present",
    attemptsVerified: Number(query("SELECT count(*) FROM public.email_delivery_attempts;")),
    unresolvedAttempts: Number(query("SELECT count(*) FROM public.email_delivery_attempts WHERE status IN ('PENDING', 'UNCERTAIN');")),
    unlinkedInvoiceAttempts,
    attachmentOriginalMismatches,
  };
}

export async function verifyBackup(dumpFile, receiptsDirectory) {
  const dump = realpathSync(dumpFile);
  const receipts = realpathSync(receiptsDirectory);
  if (!lstatSync(dump).isFile() || !lstatSync(receipts).isDirectory()) {
    throw new Error("Supply a database dump and its matching receipts directory.");
  }
  return isolatedDatabase(async (name) => {
    docker(["cp", dump, `${name}:/tmp/database.dump`], undefined, { timeoutMs: 1_800_000 });
    docker(["exec", name, "pg_restore", "-U", "postgres", "-d", "alibooks_restore_test",
      "--single-transaction", "--exit-on-error", "--no-owner", "--no-privileges", "/tmp/database.dump"],
    undefined, { timeoutMs: 1_800_000 });
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
    const query = statement => sql(name, statement);
    const moneyModel = verifyMoneyModel(query);
    verifyAuditTrailProtection(query);
    const voucherEvidence = verifyVoucherEvidence(query);
    const invoiceOriginals = verifyInvoiceOriginals(query);
    const emailDeliveryEvidence = verifyEmailDeliveryEvidence(query);
    const journalRows = Number(sql(name, "SELECT count(*) FROM public.journal_entries;"));
    return { receiptsVerified: rows.length, journalRows, relocatedPaths, tableRowCounts: tableRowCounts(query),
      archiveFilesVerified: copied.size, unreferencedFiles: copied.size - seen.size, expensesWithoutReceipts,
      moneyModel,
      voucherEvidence,
      invoiceOriginals,
      emailDeliveryEvidence,
      scope: "Isolated database restore, receipt/voucher/invoice/email-attachment hashes and coverage, minor-unit integrity, voucher balance and sealed registry only; not application or legal approval." };
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
