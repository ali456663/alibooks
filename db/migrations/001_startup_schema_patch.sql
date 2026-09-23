-- AliBooks controlled schema patch migration
-- Source: backend/src/main/java/se/cloudshop/config/DatabaseSchemaPatch.java
--
-- This file mirrors DatabaseSchemaPatch startup SQL so production schema changes
-- are visible, reviewed and repeatable instead of being hidden in application startup.
--
-- Production policy:
-- - Set SPRING_JPA_HIBERNATE_DDL_AUTO=validate or none.
-- - Set APP_SCHEMA_PATCH_ENABLED=false on EC2/RDS.
-- - Run this against a restored/staging database first and verify backup/restore.
-- - For a brand new RDS database, bootstrap the full schema from a tested release
--   schema dump first, then run this patch file for additive release changes.
--
-- Example:
--   psql "postgresql://USER:PASSWORD@HOST:5432/cloudshop" -f db/migrations/001_startup_schema_patch.sql

-- 001
CREATE TABLE IF NOT EXISTS currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer NOT NULL CHECK (minor_unit_exponent BETWEEN 0 AND 9));
-- 002
INSERT INTO currencies (currency_code, minor_unit_exponent) VALUES ('SEK', 2), ('JPY', 0), ('KWD', 3), ('TND', 3) ON CONFLICT (currency_code) DO NOTHING;
-- 003
ALTER TABLE products ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 004
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_price integer DEFAULT 0;
-- 005
ALTER TABLE products ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 006
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 007
UPDATE products SET active = true WHERE active IS NULL;
-- 008
UPDATE products SET discount_price = 0 WHERE discount_price IS NULL;
-- 009
ALTER TABLE products ADD COLUMN IF NOT EXISTS price_minor bigint;
-- 010
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_price_minor bigint;
-- 011
ALTER TABLE products ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 012
UPDATE products SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 013
UPDATE products SET price_minor = CAST(ROUND(CAST(price AS numeric) * 100, 0) AS bigint) WHERE price_minor IS NULL AND price IS NOT NULL;
-- 014
UPDATE products SET discount_price_minor = CAST(ROUND(CAST(discount_price AS numeric) * 100, 0) AS bigint) WHERE discount_price_minor IS NULL AND discount_price IS NOT NULL;
-- 015
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 016
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 017
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 018
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 019
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 020
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price integer DEFAULT 0;
-- 021
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount integer DEFAULT 0;
-- 022
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 023
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS status varchar(255) DEFAULT 'DRAFT';
-- 024
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS stripe_checkout_session_id varchar(255);
-- 025
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_number varchar(255);
-- 026
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_date date DEFAULT CURRENT_DATE;
-- 027
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS due_date date;
-- 028
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 029
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 030
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ocr_number varchar(255);
-- 031
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 032
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 033
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_date date;
-- 034
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 035
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 036
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_date date;
-- 037
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount integer DEFAULT 0;
-- 038
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_reference varchar(255);
-- 039
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS reminder_sent_date date;
-- 040
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credit_invoice boolean DEFAULT false;
-- 041
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credited_invoice_id bigint;
-- 042
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price_minor bigint;
-- 043
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount_minor bigint;
-- 044
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 045
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 046
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 047
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount_minor bigint;
-- 048
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount_minor bigint;
-- 049
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 050
UPDATE customer_orders SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 051
UPDATE customer_orders SET ordinary_price_minor = CAST(ROUND(CAST(ordinary_price AS numeric) * 100, 0) AS bigint) WHERE ordinary_price_minor IS NULL AND ordinary_price IS NOT NULL;
-- 052
UPDATE customer_orders SET discount_amount_minor = CAST(ROUND(CAST(discount_amount AS numeric) * 100, 0) AS bigint) WHERE discount_amount_minor IS NULL AND discount_amount IS NOT NULL;
-- 053
UPDATE customer_orders SET net_amount_minor = CAST(ROUND(CAST(net_amount AS numeric) * 100, 0) AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 054
UPDATE customer_orders SET vat_amount_minor = CAST(ROUND(CAST(vat_amount AS numeric) * 100, 0) AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 055
UPDATE customer_orders SET total_amount_minor = CAST(ROUND(CAST(total_amount AS numeric) * 100, 0) AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 056
UPDATE customer_orders SET paid_amount_minor = CAST(ROUND(CAST(paid_amount AS numeric) * 100, 0) AS bigint) WHERE paid_amount_minor IS NULL AND paid_amount IS NOT NULL;
-- 057
UPDATE customer_orders SET refunded_amount_minor = CAST(ROUND(CAST(refunded_amount AS numeric) * 100, 0) AS bigint) WHERE refunded_amount_minor IS NULL AND refunded_amount IS NOT NULL;
-- 058
CREATE INDEX IF NOT EXISTS customer_orders_invoice_number_idx ON customer_orders(invoice_number) WHERE invoice_number IS NOT NULL AND invoice_number <> '';
-- 059
CREATE INDEX IF NOT EXISTS customer_orders_ocr_number_idx ON customer_orders(ocr_number) WHERE ocr_number IS NOT NULL AND ocr_number <> '';
-- 060
CREATE TABLE IF NOT EXISTS invoice_payments (id bigserial PRIMARY KEY);
-- 061
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 062
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS payment_date date;
-- 063
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 064
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 065
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 066
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 067
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 068
UPDATE invoice_payments SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 069
UPDATE invoice_payments SET amount_minor = CAST(ROUND(CAST(amount AS numeric) * 100, 0) AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 070
CREATE UNIQUE INDEX IF NOT EXISTS invoice_payments_identity_unique ON invoice_payments (invoice_id, payment_date, amount, lower(reference)) WHERE reference IS NOT NULL AND reference <> '';
-- 071
CREATE TABLE IF NOT EXISTS money_migration_ledger (migration_key varchar(128) PRIMARY KEY, source_unit varchar(32) NOT NULL, target_unit varchar(32) NOT NULL, applied_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- 072
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('core-invoice-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 073
CREATE TABLE IF NOT EXISTS invoice_reminders (id bigserial PRIMARY KEY);
-- 074
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 075
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 076
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS method varchar(255);
-- 077
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS status varchar(255);
-- 078
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS recipient_email varchar(255);
-- 079
CREATE TABLE IF NOT EXISTS stripe_webhook_events (event_id varchar(255) PRIMARY KEY);
-- 080
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 081
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS processed_at timestamp;
-- 082
CREATE TABLE IF NOT EXISTS stripe_payouts (id bigserial PRIMARY KEY);
-- 083
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS payout_date date;
-- 084
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount integer DEFAULT 0;
-- 085
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount integer DEFAULT 0;
-- 086
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 087
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount_minor bigint;
-- 088
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount_minor bigint;
-- 089
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 090
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 091
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 092
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 093
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 094
UPDATE stripe_payouts SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 095
CREATE UNIQUE INDEX IF NOT EXISTS stripe_payouts_reference_unique ON stripe_payouts(reference) WHERE reference IS NOT NULL AND reference <> '';
-- 096
CREATE TABLE IF NOT EXISTS bank_reconciliation_entries (id bigserial PRIMARY KEY);
-- 097
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_row_id varchar(255);
-- 098
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_date date;
-- 099
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS description varchar(255);
-- 100
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 101
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 102
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 103
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS entry_type varchar(255);
-- 104
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS status varchar(255);
-- 105
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS match_label varchar(255);
-- 106
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS booked_at timestamp;
-- 107
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 108
UPDATE bank_reconciliation_entries SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 109
CREATE INDEX IF NOT EXISTS bank_reconciliation_entries_bank_row_id_idx ON bank_reconciliation_entries(bank_row_id);
-- 110
CREATE TABLE IF NOT EXISTS vat_filings (id bigserial PRIMARY KEY);
-- 111
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_from date;
-- 112
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_to date;
-- 113
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat integer DEFAULT 0;
-- 114
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat integer DEFAULT 0;
-- 115
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay integer DEFAULT 0;
-- 116
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat_minor bigint;
-- 117
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat_minor bigint;
-- 118
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay_minor bigint;
-- 119
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS status varchar(255);
-- 120
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submission_reference varchar(255);
-- 121
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 122
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS note varchar(1000);
-- 123
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submitted_at timestamp;
-- 124
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS paid_at timestamp;
-- 125
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 126
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 127
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 128
UPDATE vat_filings SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 129
CREATE UNIQUE INDEX IF NOT EXISTS vat_filings_period_unique ON vat_filings(period_from, period_to);
-- 130
CREATE TABLE IF NOT EXISTS audit_events (id bigserial PRIMARY KEY);
-- 131
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 132
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_type varchar(255);
-- 133
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_id varchar(255);
-- 134
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_action varchar(255);
-- 135
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 136
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS message varchar(1000);
-- 137
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 138
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 139
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 140
UPDATE audit_events SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 141
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS actor_email varchar(255);
-- 142
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 143
CREATE INDEX IF NOT EXISTS audit_events_created_at_idx ON audit_events(created_at);
-- 144
CREATE INDEX IF NOT EXISTS audit_events_entity_idx ON audit_events(entity_type, entity_id);
-- 145
CREATE TABLE IF NOT EXISTS payroll_snapshots (id bigint PRIMARY KEY);
-- 146
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS content text;
-- 147
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 148
CREATE TABLE IF NOT EXISTS payroll_snapshot_revisions (id bigserial PRIMARY KEY);
-- 149
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS content text;
-- 150
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 151
ALTER TABLE customers ADD COLUMN IF NOT EXISTS personal_number varchar(255);
-- 152
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address varchar(255);
-- 153
ALTER TABLE customers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 154
ALTER TABLE customers ADD COLUMN IF NOT EXISTS postal_code varchar(255);
-- 155
ALTER TABLE customers ADD COLUMN IF NOT EXISTS city varchar(255);
-- 156
ALTER TABLE customers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 157
CREATE TABLE IF NOT EXISTS expenses (id bigserial PRIMARY KEY);
-- 158
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS expense_date date;
-- 159
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS description varchar(255);
-- 160
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 161
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 162
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 163
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 164
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 165
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 166
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS category varchar(255);
-- 167
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS paid_from varchar(255);
-- 168
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_file_name varchar(255);
-- 169
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_content_type varchar(255);
-- 170
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_storage_path varchar(1000);
-- 171
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_sha256 varchar(64);
-- 172
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_uploaded_at timestamp;
-- 173
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 174
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 175
UPDATE expenses SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 176
CREATE TABLE IF NOT EXISTS card_purchases (id bigserial PRIMARY KEY);
-- 177
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS purchase_date date;
-- 178
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS merchant_name varchar(255);
-- 179
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_holder varchar(255);
-- 180
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_last4 varchar(16);
-- 181
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 182
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 183
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 184
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 185
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 186
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 187
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 188
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS category varchar(255);
-- 189
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS clearing_account varchar(255);
-- 190
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'review';
-- 191
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS booked_expense_id bigint;
-- 192
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 193
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 194
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 195
UPDATE card_purchases SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 196
CREATE INDEX IF NOT EXISTS card_purchases_purchase_date_idx ON card_purchases(purchase_date);
-- 197
CREATE TABLE IF NOT EXISTS suppliers (id bigserial PRIMARY KEY);
-- 198
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS name varchar(255);
-- 199
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS email varchar(255);
-- 200
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS org_number varchar(255);
-- 201
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 202
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS payment_info varchar(512);
-- 203
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 204
CREATE INDEX IF NOT EXISTS suppliers_name_idx ON suppliers(name);
-- 205
CREATE TABLE IF NOT EXISTS supplier_invoices (id bigserial PRIMARY KEY);
-- 206
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_id bigint;
-- 207
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_name varchar(255);
-- 208
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_email varchar(255);
-- 209
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_org_number varchar(255);
-- 210
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS invoice_date date;
-- 211
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS due_date date;
-- 212
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS description varchar(512);
-- 213
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 214
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 215
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 216
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 217
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 218
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 219
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 220
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS category varchar(64);
-- 221
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'unpaid';
-- 222
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_at date;
-- 223
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 224
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount_minor bigint;
-- 225
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 226
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_history text;
-- 227
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancelled_at date;
-- 228
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancellation_voucher_number varchar(255);
-- 229
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS self_billing boolean DEFAULT false;
-- 230
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_name varchar(255);
-- 231
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_reference varchar(255);
-- 232
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS approval_reference varchar(255);
-- 233
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 234
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 235
UPDATE supplier_invoices SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 236
CREATE INDEX IF NOT EXISTS supplier_invoices_supplier_id_idx ON supplier_invoices(supplier_id);
-- 237
CREATE INDEX IF NOT EXISTS supplier_invoices_due_date_idx ON supplier_invoices(due_date);
-- 238
CREATE TABLE IF NOT EXISTS supplier_invoice_payments (id bigserial PRIMARY KEY);
-- 239
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 240
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS payment_date date;
-- 241
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 242
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 243
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 244
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 245
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 246
UPDATE supplier_invoice_payments SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 247
UPDATE supplier_invoice_payments SET amount_minor = CAST(ROUND(CAST(amount AS numeric) * 100, 0) AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 248
CREATE INDEX IF NOT EXISTS supplier_invoice_payments_invoice_date_idx ON supplier_invoice_payments(supplier_invoice_id, payment_date, id);
-- 249
CREATE UNIQUE INDEX IF NOT EXISTS supplier_invoice_payments_identity_unique ON supplier_invoice_payments (supplier_invoice_id, payment_date, amount, lower(reference)) WHERE reference IS NOT NULL AND reference <> '';
-- 250
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_supplier_invoice_payments_invoice' AND conrelid = 'supplier_invoice_payments'::regclass) THEN ALTER TABLE supplier_invoice_payments ADD CONSTRAINT fk_supplier_invoice_payments_invoice FOREIGN KEY (supplier_invoice_id) REFERENCES supplier_invoices(id); END IF; END $$;
-- 251
CREATE TABLE IF NOT EXISTS recurring_contracts (id bigserial PRIMARY KEY);
-- 252
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_id bigint;
-- 253
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_name varchar(255);
-- 254
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_id bigint;
-- 255
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_name varchar(255);
-- 256
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 257
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS contract_interval varchar(255);
-- 258
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS next_invoice_date date;
-- 259
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 260
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 261
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS last_invoice_number varchar(255);
-- 262
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 263
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived_at timestamp;
-- 264
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 265
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_date date DEFAULT CURRENT_DATE;
-- 266
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS correction_of_voucher_number varchar(255);
-- 267
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS expense_id bigint;
-- 268
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 269
CREATE INDEX IF NOT EXISTS journal_entries_voucher_number_idx ON journal_entries(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 270
CREATE INDEX IF NOT EXISTS journal_entries_expense_id_idx ON journal_entries(expense_id) WHERE expense_id IS NOT NULL;
-- 271
CREATE INDEX IF NOT EXISTS journal_entries_supplier_invoice_id_idx ON journal_entries(supplier_invoice_id) WHERE supplier_invoice_id IS NOT NULL;
-- 272
CREATE TABLE IF NOT EXISTS voucher_approvals (id bigserial PRIMARY KEY);
-- 273
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 274
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS status varchar(255);
-- 275
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS note text;
-- 276
ALTER TABLE voucher_approvals ALTER COLUMN note TYPE text;
-- 277
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewer varchar(255);
-- 278
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewed_at timestamp;
-- 279
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 280
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 281
CREATE UNIQUE INDEX IF NOT EXISTS voucher_approvals_voucher_number_unique ON voucher_approvals(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 282
CREATE INDEX IF NOT EXISTS voucher_approvals_reviewed_at_idx ON voucher_approvals(reviewed_at);
-- 283
CREATE TABLE IF NOT EXISTS owner_transactions (id bigserial PRIMARY KEY);
-- 284
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_type varchar(64);
-- 285
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_date date;
-- 286
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 287
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 288
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 289
UPDATE owner_transactions SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 290
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS description varchar(512);
-- 291
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 292
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS debit_account varchar(64);
-- 293
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS credit_account varchar(64);
-- 294
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'draft';
-- 295
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 296
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 297
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 298
CREATE INDEX IF NOT EXISTS owner_transactions_date_idx ON owner_transactions(transaction_date);
-- 299
CREATE TABLE IF NOT EXISTS app_settings (id bigint PRIMARY KEY);
-- 300
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_name varchar(255);
-- 301
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_address varchar(1000);
-- 302
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_postal_code varchar(255);
-- 303
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_city varchar(255);
-- 304
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_organization_number varchar(255);
-- 305
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_registration_number varchar(255);
-- 306
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS contact_email varchar(255);
-- 307
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 308
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS default_ocr varchar(255);
-- 309
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 310
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'SOLE_TRADER';
-- 311
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_method varchar(255) DEFAULT 'INVOICE_METHOD';
-- 312
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_reporting_period varchar(255) DEFAULT 'QUARTERLY';
-- 313
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_start_month integer DEFAULT 1;
-- 314
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_end_month integer DEFAULT 12;
-- 315
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 316
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 317
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 318
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_email_template text;
-- 319
ALTER TABLE app_settings ALTER COLUMN invoice_email_template TYPE text;
-- 320
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS automatic_invoice_reminders_enabled boolean DEFAULT true;
-- 321
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_days_before_due integer DEFAULT 5;
-- 322
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_template text;
-- 323
ALTER TABLE app_settings ALTER COLUMN invoice_reminder_template TYPE text;
-- 324
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminders_enabled boolean DEFAULT true;
-- 325
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_days_after_due integer DEFAULT 3;
-- 326
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_template text;
-- 327
ALTER TABLE app_settings ALTER COLUMN overdue_invoice_reminder_template TYPE text;
-- 328
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_locked_through_date date;
-- 329
UPDATE customer_orders SET status = 'DRAFT' WHERE status IS NULL;
-- 330
UPDATE customer_orders SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 331
UPDATE customer_orders SET ordinary_price = net_amount WHERE ordinary_price IS NULL OR ordinary_price = 0;
-- 332
UPDATE customer_orders SET discount_amount = 0 WHERE discount_amount IS NULL;
-- 333
UPDATE customer_orders SET invoice_date = CURRENT_DATE WHERE invoice_date IS NULL;
-- 334
UPDATE customer_orders SET payment_terms_days = 30 WHERE payment_terms_days IS NULL OR payment_terms_days = 0;
-- 335
UPDATE customer_orders SET due_date = invoice_date + payment_terms_days WHERE due_date IS NULL;
-- 336
UPDATE customer_orders SET f_tax_approved = true WHERE f_tax_approved IS NULL;
-- 337
UPDATE customer_orders SET refunded_amount = 0 WHERE refunded_amount IS NULL;
-- 338
UPDATE customers SET archived = false WHERE archived IS NULL;
-- 339
UPDATE suppliers SET archived = false WHERE archived IS NULL;
-- 340
UPDATE supplier_invoices SET status = 'unpaid' WHERE status IS NULL OR status = '';
-- 341
UPDATE supplier_invoices SET net_amount = GREATEST(total_amount - vat_amount, 0) WHERE net_amount IS NULL OR net_amount = 0;
-- 342
UPDATE supplier_invoices SET paid_amount = 0 WHERE paid_amount IS NULL;
-- 343
UPDATE supplier_invoices SET paid_amount = total_amount WHERE (status = 'paid' OR paid_at IS NOT NULL) AND paid_amount = 0;
-- 344
UPDATE supplier_invoices SET total_amount_minor = CAST(ROUND(CAST(total_amount AS numeric) * 100, 0) AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 345
UPDATE supplier_invoices SET vat_amount_minor = CAST(ROUND(CAST(vat_amount AS numeric) * 100, 0) AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 346
UPDATE supplier_invoices SET net_amount_minor = CAST(ROUND(CAST(net_amount AS numeric) * 100, 0) AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 347
UPDATE supplier_invoices SET paid_amount_minor = CAST(ROUND(CAST(paid_amount AS numeric) * 100, 0) AS bigint) WHERE paid_amount_minor IS NULL AND paid_amount IS NOT NULL;
-- 348
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('supplier-invoice-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 349
UPDATE supplier_invoices SET payment_reference = '' WHERE payment_reference IS NULL;
-- 350
UPDATE supplier_invoices SET payment_history = '' WHERE payment_history IS NULL;
-- 351
UPDATE supplier_invoices SET self_billing = false WHERE self_billing IS NULL;
-- 352
UPDATE supplier_invoices SET buyer_name = '' WHERE buyer_name IS NULL;
-- 353
UPDATE supplier_invoices SET buyer_reference = '' WHERE buyer_reference IS NULL;
-- 354
UPDATE supplier_invoices SET approval_reference = '' WHERE approval_reference IS NULL;
-- 355
UPDATE supplier_invoices SET status = 'cancelled' WHERE cancellation_voucher_number IS NOT NULL AND cancellation_voucher_number <> '';
-- 356
UPDATE expenses SET net_amount_minor = CAST(ROUND(CAST(net_amount AS numeric) * 100, 0) AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 357
UPDATE expenses SET vat_amount_minor = CAST(ROUND(CAST(vat_amount AS numeric) * 100, 0) AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 358
UPDATE expenses SET total_amount_minor = CAST(ROUND(CAST(total_amount AS numeric) * 100, 0) AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 359
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('expense-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 360
UPDATE stripe_payouts SET gross_amount_minor = CAST(ROUND(CAST(gross_amount AS numeric) * 100, 0) AS bigint) WHERE gross_amount_minor IS NULL AND gross_amount IS NOT NULL;
-- 361
UPDATE stripe_payouts SET fee_amount_minor = CAST(ROUND(CAST(fee_amount AS numeric) * 100, 0) AS bigint) WHERE fee_amount_minor IS NULL AND fee_amount IS NOT NULL;
-- 362
UPDATE stripe_payouts SET net_amount_minor = CAST(ROUND(CAST(net_amount AS numeric) * 100, 0) AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 363
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('stripe-payout-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 364
UPDATE vat_filings SET output_vat_minor = CAST(ROUND(CAST(output_vat AS numeric) * 100, 0) AS bigint) WHERE output_vat_minor IS NULL AND output_vat IS NOT NULL;
-- 365
UPDATE vat_filings SET input_vat_minor = CAST(ROUND(CAST(input_vat AS numeric) * 100, 0) AS bigint) WHERE input_vat_minor IS NULL AND input_vat IS NOT NULL;
-- 366
UPDATE vat_filings SET vat_to_pay_minor = CAST(ROUND(CAST(vat_to_pay AS numeric) * 100, 0) AS bigint) WHERE vat_to_pay_minor IS NULL AND vat_to_pay IS NOT NULL;
-- 367
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('vat-filing-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 368
UPDATE card_purchases SET net_amount_minor = CAST(ROUND(CAST(net_amount AS numeric) * 100, 0) AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 369
UPDATE card_purchases SET vat_amount_minor = CAST(ROUND(CAST(vat_amount AS numeric) * 100, 0) AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 370
UPDATE card_purchases SET total_amount_minor = CAST(ROUND(CAST(total_amount AS numeric) * 100, 0) AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 371
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('card-purchase-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 372
UPDATE bank_reconciliation_entries SET amount_minor = CAST(ROUND(CAST(amount AS numeric) * 100, 0) AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 373
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('bank-reconciliation-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 374
UPDATE recurring_contracts SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 375
UPDATE recurring_contracts SET contract_interval = 'monthly' WHERE contract_interval IS NULL OR contract_interval = '';
-- 376
UPDATE recurring_contracts SET next_invoice_date = CURRENT_DATE WHERE next_invoice_date IS NULL;
-- 377
UPDATE recurring_contracts SET active = true WHERE active IS NULL;
-- 378
UPDATE recurring_contracts SET archived = false WHERE archived IS NULL;
-- 379
UPDATE owner_transactions SET status = 'draft' WHERE status IS NULL OR status = '';
-- 380
UPDATE owner_transactions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
-- 381
UPDATE owner_transactions SET updated_at = created_at WHERE updated_at IS NULL;
-- 382
UPDATE owner_transactions SET status = 'booked' WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 383
UPDATE app_settings SET company_type = 'SOLE_TRADER' WHERE company_type IS NULL;
-- 384
UPDATE app_settings SET accounting_method = 'INVOICE_METHOD' WHERE accounting_method IS NULL OR accounting_method = '';
-- 385
UPDATE app_settings SET vat_reporting_period = 'QUARTERLY' WHERE vat_reporting_period IS NULL OR vat_reporting_period = '';
-- 386
UPDATE app_settings SET fiscal_year_start_month = 1 WHERE fiscal_year_start_month IS NULL OR fiscal_year_start_month < 1 OR fiscal_year_start_month > 12;
-- 387
UPDATE app_settings SET fiscal_year_end_month = 12 WHERE fiscal_year_end_month IS NULL OR fiscal_year_end_month < 1 OR fiscal_year_end_month > 12;
-- 388
UPDATE app_settings SET automatic_invoice_reminders_enabled = true WHERE automatic_invoice_reminders_enabled IS NULL;
-- 389
UPDATE app_settings SET invoice_email_template = 'Hej {kundnamn},

Bifogat finns faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_email_template IS NULL OR invoice_email_template = '';
-- 390
UPDATE app_settings SET invoice_reminder_days_before_due = 5 WHERE invoice_reminder_days_before_due IS NULL OR invoice_reminder_days_before_due = 0;
-- 391
UPDATE app_settings SET invoice_reminder_template = 'Hej {kundnamn},

Vi vill paminna om faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Kvar att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_reminder_template IS NULL OR invoice_reminder_template = '';
-- 392
UPDATE app_settings SET overdue_invoice_reminders_enabled = true WHERE overdue_invoice_reminders_enabled IS NULL;
-- 393
UPDATE app_settings SET overdue_invoice_reminder_days_after_due = 3 WHERE overdue_invoice_reminder_days_after_due IS NULL OR overdue_invoice_reminder_days_after_due = 0;
-- 394
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'BOTH';
-- 395
UPDATE accounts SET company_type = 'BOTH' WHERE company_type IS NULL;
-- 396
UPDATE journal_entries SET voucher_date = CURRENT_DATE WHERE voucher_date IS NULL;
-- 397
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS journal_entry_id bigint;
-- 398
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS document_snapshot text;
-- 399
CREATE TABLE IF NOT EXISTS invoice_originals (invoice_id bigint PRIMARY KEY REFERENCES customer_orders(id), pdf bytea NOT NULL, sha256 varchar(64) NOT NULL, archived_at timestamp with time zone NOT NULL);
-- 400
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoice_originals_invoice') THEN ALTER TABLE invoice_originals ADD CONSTRAINT fk_invoice_originals_invoice FOREIGN KEY (invoice_id) REFERENCES customer_orders(id); END IF; END $$;
-- 401
CREATE TABLE IF NOT EXISTS email_delivery_attempts (id bigserial PRIMARY KEY);
-- 402
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS delivery_type varchar(64) NOT NULL DEFAULT 'INVOICE_EMAIL';
-- 403
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 404
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS recipient_email varchar(255) NOT NULL DEFAULT '';
-- 405
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS subject varchar(512) NOT NULL DEFAULT '';
-- 406
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS body text NOT NULL DEFAULT '';
-- 407
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attachment_name varchar(255);
-- 408
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attachment_content bytea;
-- 409
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attachment_sha256 varchar(64);
-- 410
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'PENDING';
-- 411
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 1;
-- 412
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS last_error varchar(2000);
-- 413
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- 414
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- 415
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS sent_at timestamp with time zone;
-- 416
CREATE INDEX IF NOT EXISTS email_delivery_attempts_status_idx ON email_delivery_attempts(status, updated_at);
-- 417
CREATE INDEX IF NOT EXISTS email_delivery_attempts_invoice_idx ON email_delivery_attempts(invoice_id, created_at);
-- 418
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_reconciliation_journal ON bank_reconciliation_entries (journal_entry_id);
-- 419
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_reconciliation_journal' AND conrelid = 'bank_reconciliation_entries'::regclass) THEN ALTER TABLE bank_reconciliation_entries ADD CONSTRAINT fk_bank_reconciliation_journal FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id); END IF; END $$;
-- 420
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS debit_minor bigint;
-- 421
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS credit_minor bigint;
-- 422
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 423
UPDATE journal_entries SET currency_code = 'SEK' WHERE currency_code IS NULL OR currency_code = '';
-- 424
UPDATE journal_entries SET debit_minor = CAST(ROUND(CAST(debit AS numeric) * 100, 0) AS bigint) WHERE debit_minor IS NULL AND debit IS NOT NULL;
-- 425
UPDATE journal_entries SET credit_minor = CAST(ROUND(CAST(credit AS numeric) * 100, 0) AS bigint) WHERE credit_minor IS NULL AND credit IS NOT NULL;
-- 426
UPDATE owner_transactions SET amount_minor = CAST(ROUND(CAST(amount AS numeric) * 100, 0) AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 427
UPDATE audit_events SET amount_minor = CAST(ROUND(CAST(amount AS numeric) * 100, 0) AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 428
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-entry-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 429
CREATE OR REPLACE FUNCTION sync_money_shadow_fields() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_TABLE_NAME = 'products' THEN NEW.price_minor = CASE WHEN NEW.price IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.price AS numeric) * 100, 0) AS bigint) END; NEW.discount_price_minor = CASE WHEN NEW.discount_price IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.discount_price AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'customer_orders' THEN NEW.ordinary_price_minor = CASE WHEN NEW.ordinary_price IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.ordinary_price AS numeric) * 100, 0) AS bigint) END; NEW.discount_amount_minor = CASE WHEN NEW.discount_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.discount_amount AS numeric) * 100, 0) AS bigint) END; NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.paid_amount_minor = CASE WHEN NEW.paid_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.paid_amount AS numeric) * 100, 0) AS bigint) END; NEW.refunded_amount_minor = CASE WHEN NEW.refunded_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.refunded_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'invoice_payments' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'supplier_invoices' THEN NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.paid_amount_minor = CASE WHEN NEW.paid_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.paid_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'expenses' THEN NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'card_purchases' THEN NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'stripe_payouts' THEN NEW.gross_amount_minor = CASE WHEN NEW.gross_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.gross_amount AS numeric) * 100, 0) AS bigint) END; NEW.fee_amount_minor = CASE WHEN NEW.fee_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.fee_amount AS numeric) * 100, 0) AS bigint) END; NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'bank_reconciliation_entries' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'vat_filings' THEN NEW.output_vat_minor = CASE WHEN NEW.output_vat IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.output_vat AS numeric) * 100, 0) AS bigint) END; NEW.input_vat_minor = CASE WHEN NEW.input_vat IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.input_vat AS numeric) * 100, 0) AS bigint) END; NEW.vat_to_pay_minor = CASE WHEN NEW.vat_to_pay IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_to_pay AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'journal_entries' THEN NEW.debit_minor = CASE WHEN NEW.debit IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.debit AS numeric) * 100, 0) AS bigint) END; NEW.credit_minor = CASE WHEN NEW.credit IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.credit AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'owner_transactions' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'audit_events' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; END IF; RETURN NEW; END; $$;
-- 430
DROP TRIGGER IF EXISTS products_money_shadow_sync ON products;
-- 431
CREATE TRIGGER products_money_shadow_sync BEFORE INSERT OR UPDATE ON products FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 432
DROP TRIGGER IF EXISTS customer_orders_money_shadow_sync ON customer_orders;
-- 433
CREATE TRIGGER customer_orders_money_shadow_sync BEFORE INSERT OR UPDATE ON customer_orders FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 434
DROP TRIGGER IF EXISTS invoice_payments_money_shadow_sync ON invoice_payments;
-- 435
CREATE TRIGGER invoice_payments_money_shadow_sync BEFORE INSERT OR UPDATE ON invoice_payments FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 436
DROP TRIGGER IF EXISTS supplier_invoices_money_shadow_sync ON supplier_invoices;
-- 437
CREATE TRIGGER supplier_invoices_money_shadow_sync BEFORE INSERT OR UPDATE ON supplier_invoices FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 438
DROP TRIGGER IF EXISTS expenses_money_shadow_sync ON expenses;
-- 439
CREATE TRIGGER expenses_money_shadow_sync BEFORE INSERT OR UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 440
DROP TRIGGER IF EXISTS card_purchases_money_shadow_sync ON card_purchases;
-- 441
CREATE TRIGGER card_purchases_money_shadow_sync BEFORE INSERT OR UPDATE ON card_purchases FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 442
DROP TRIGGER IF EXISTS stripe_payouts_money_shadow_sync ON stripe_payouts;
-- 443
CREATE TRIGGER stripe_payouts_money_shadow_sync BEFORE INSERT OR UPDATE ON stripe_payouts FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 444
DROP TRIGGER IF EXISTS bank_reconciliation_entries_money_shadow_sync ON bank_reconciliation_entries;
-- 445
CREATE TRIGGER bank_reconciliation_entries_money_shadow_sync BEFORE INSERT OR UPDATE ON bank_reconciliation_entries FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 446
DROP TRIGGER IF EXISTS vat_filings_money_shadow_sync ON vat_filings;
-- 447
CREATE TRIGGER vat_filings_money_shadow_sync BEFORE INSERT OR UPDATE ON vat_filings FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 448
DROP TRIGGER IF EXISTS journal_entries_money_shadow_sync ON journal_entries;
-- 449
CREATE TRIGGER journal_entries_money_shadow_sync BEFORE INSERT OR UPDATE ON journal_entries FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 450
DROP TRIGGER IF EXISTS owner_transactions_money_shadow_sync ON owner_transactions;
-- 451
CREATE TRIGGER owner_transactions_money_shadow_sync BEFORE INSERT OR UPDATE ON owner_transactions FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 452
DROP TRIGGER IF EXISTS audit_events_money_shadow_sync ON audit_events;
-- 453
CREATE TRIGGER audit_events_money_shadow_sync BEFORE INSERT OR UPDATE ON audit_events FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 454
CREATE OR REPLACE FUNCTION sync_supplier_payment_shadow_fields() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; RETURN NEW; END; $$;
-- 455
DROP TRIGGER IF EXISTS supplier_invoice_payments_money_shadow_sync ON supplier_invoice_payments;
-- 456
CREATE TRIGGER supplier_invoice_payments_money_shadow_sync BEFORE INSERT OR UPDATE ON supplier_invoice_payments FOR EACH ROW EXECUTE FUNCTION sync_supplier_payment_shadow_fields();
