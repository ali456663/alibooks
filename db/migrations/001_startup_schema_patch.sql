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
--   psql -v ON_ERROR_STOP=1 --single-transaction "postgresql://USER:PASSWORD@HOST:5432/cloudshop" -f db/migrations/001_startup_schema_patch.sql

-- 001
CREATE TABLE IF NOT EXISTS currencies (currency_code varchar(3) PRIMARY KEY, minor_unit_exponent integer NOT NULL CHECK (minor_unit_exponent BETWEEN 0 AND 9));
-- 002
INSERT INTO currencies (currency_code, minor_unit_exponent) VALUES ('SEK', 2), ('JPY', 0), ('KWD', 3), ('TND', 3) ON CONFLICT (currency_code) DO NOTHING;
-- 003
CREATE TABLE IF NOT EXISTS money_migration_ledger (migration_key varchar(128) PRIMARY KEY, source_unit varchar(32) NOT NULL, target_unit varchar(32) NOT NULL, applied_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- 004
ALTER TABLE products ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 005
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_price integer DEFAULT 0;
-- 006
ALTER TABLE products ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 007
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 008
UPDATE products SET active = true WHERE active IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 009
UPDATE products SET discount_price = 0 WHERE discount_price IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 010
ALTER TABLE products ADD COLUMN IF NOT EXISTS price_minor bigint;
-- 011
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_price_minor bigint;
-- 012
ALTER TABLE products ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 013
UPDATE products SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 014
UPDATE products SET price_minor = CAST(CAST(price AS numeric) * 100 AS bigint) WHERE price_minor IS NULL AND price IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 015
UPDATE products SET discount_price_minor = CAST(CAST(discount_price AS numeric) * 100 AS bigint) WHERE discount_price_minor IS NULL AND discount_price IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 016
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 017
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 018
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 019
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 020
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 021
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price integer DEFAULT 0;
-- 022
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount integer DEFAULT 0;
-- 023
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 024
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS status varchar(255) DEFAULT 'DRAFT';
-- 025
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS stripe_checkout_session_id varchar(255);
-- 026
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_number varchar(255);
-- 027
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_date date DEFAULT CURRENT_DATE;
-- 028
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS due_date date;
-- 029
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 030
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 031
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ocr_number varchar(255);
-- 032
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 033
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 034
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_date date;
-- 035
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 036
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 037
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_date date;
-- 038
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount integer DEFAULT 0;
-- 039
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_reference varchar(255);
-- 040
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS reminder_sent_date date;
-- 041
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credit_invoice boolean DEFAULT false;
-- 042
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credited_invoice_id bigint;
-- 043
UPDATE customer_orders SET status = 'DRAFT' WHERE status IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 044
UPDATE customer_orders SET quantity = 1 WHERE (quantity IS NULL OR quantity = 0) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 045
UPDATE customer_orders SET ordinary_price = net_amount WHERE (ordinary_price IS NULL OR ordinary_price = 0) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 046
UPDATE customer_orders SET discount_amount = 0 WHERE discount_amount IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 047
UPDATE customer_orders SET invoice_date = CURRENT_DATE WHERE invoice_date IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 048
UPDATE customer_orders SET payment_terms_days = 30 WHERE (payment_terms_days IS NULL OR payment_terms_days = 0) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 049
UPDATE customer_orders SET due_date = invoice_date + payment_terms_days WHERE due_date IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 050
UPDATE customer_orders SET f_tax_approved = true WHERE f_tax_approved IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 051
UPDATE customer_orders SET refunded_amount = 0 WHERE refunded_amount IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 052
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price_minor bigint;
-- 053
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount_minor bigint;
-- 054
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 055
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 056
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 057
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount_minor bigint;
-- 058
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount_minor bigint;
-- 059
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 060
UPDATE customer_orders SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 061
UPDATE customer_orders SET ordinary_price_minor = CAST(CAST(ordinary_price AS numeric) * 100 AS bigint) WHERE ordinary_price_minor IS NULL AND ordinary_price IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 062
UPDATE customer_orders SET discount_amount_minor = CAST(CAST(discount_amount AS numeric) * 100 AS bigint) WHERE discount_amount_minor IS NULL AND discount_amount IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 063
UPDATE customer_orders SET net_amount_minor = CAST(CAST(net_amount AS numeric) * 100 AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 064
UPDATE customer_orders SET vat_amount_minor = CAST(CAST(vat_amount AS numeric) * 100 AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 065
UPDATE customer_orders SET total_amount_minor = CAST(CAST(total_amount AS numeric) * 100 AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 066
UPDATE customer_orders SET paid_amount_minor = CAST(CAST(paid_amount AS numeric) * 100 AS bigint) WHERE paid_amount_minor IS NULL AND paid_amount IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 067
UPDATE customer_orders SET refunded_amount_minor = CAST(CAST(refunded_amount AS numeric) * 100 AS bigint) WHERE refunded_amount_minor IS NULL AND refunded_amount IS NOT NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'core-invoice-shadow-v1');
-- 068
CREATE INDEX IF NOT EXISTS customer_orders_invoice_number_idx ON customer_orders(invoice_number) WHERE invoice_number IS NOT NULL AND invoice_number <> '';
-- 069
CREATE INDEX IF NOT EXISTS customer_orders_ocr_number_idx ON customer_orders(ocr_number) WHERE ocr_number IS NOT NULL AND ocr_number <> '';
-- 070
CREATE TABLE IF NOT EXISTS invoice_payments (id bigserial PRIMARY KEY);
-- 071
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 072
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS payment_date date;
-- 073
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 074
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 075
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 076
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 077
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 078
CREATE UNIQUE INDEX IF NOT EXISTS invoice_payments_identity_unique ON invoice_payments (invoice_id, payment_date, amount, lower(reference)) WHERE reference IS NOT NULL AND reference <> '';
-- 079
UPDATE invoice_payments SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'customer-payment-exact-v1');
-- 080
UPDATE invoice_payments SET amount_minor = CAST(CAST(amount AS numeric) * 100 AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL AND CAST(amount AS numeric) = TRUNC(CAST(amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'customer-payment-exact-v1');
-- 081
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('core-invoice-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 082
CREATE TABLE IF NOT EXISTS invoice_reminders (id bigserial PRIMARY KEY);
-- 083
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 084
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 085
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS method varchar(255);
-- 086
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS status varchar(255);
-- 087
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS recipient_email varchar(255);
-- 088
CREATE TABLE IF NOT EXISTS stripe_webhook_events (event_id varchar(255) PRIMARY KEY);
-- 089
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 090
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS processed_at timestamp;
-- 091
CREATE TABLE IF NOT EXISTS stripe_payouts (id bigserial PRIMARY KEY);
-- 092
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS payout_date date;
-- 093
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount integer DEFAULT 0;
-- 094
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount integer DEFAULT 0;
-- 095
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 096
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount_minor bigint;
-- 097
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount_minor bigint;
-- 098
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 099
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 100
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 101
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 102
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 103
UPDATE stripe_payouts SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'stripe-payout-shadow-v1');
-- 104
CREATE UNIQUE INDEX IF NOT EXISTS stripe_payouts_reference_unique ON stripe_payouts(reference) WHERE reference IS NOT NULL AND reference <> '';
-- 105
CREATE TABLE IF NOT EXISTS bank_reconciliation_entries (id bigserial PRIMARY KEY);
-- 106
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_row_id varchar(255);
-- 107
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_date date;
-- 108
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS description varchar(255);
-- 109
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 110
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 111
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 112
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS entry_type varchar(255);
-- 113
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS status varchar(255);
-- 114
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS match_label varchar(255);
-- 115
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS booked_at timestamp;
-- 116
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 117
UPDATE bank_reconciliation_entries SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'bank-reconciliation-shadow-v1');
-- 118
CREATE INDEX IF NOT EXISTS bank_reconciliation_entries_bank_row_id_idx ON bank_reconciliation_entries(bank_row_id);
-- 119
CREATE TABLE IF NOT EXISTS vat_filings (id bigserial PRIMARY KEY);
-- 120
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_from date;
-- 121
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_to date;
-- 122
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat integer DEFAULT 0;
-- 123
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat integer DEFAULT 0;
-- 124
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay integer DEFAULT 0;
-- 125
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat_minor bigint;
-- 126
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat_minor bigint;
-- 127
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay_minor bigint;
-- 128
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS status varchar(255);
-- 129
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submission_reference varchar(255);
-- 130
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 131
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS note varchar(1000);
-- 132
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submitted_at timestamp;
-- 133
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS paid_at timestamp;
-- 134
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 135
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 136
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 137
UPDATE vat_filings SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'vat-filing-shadow-v1');
-- 138
CREATE UNIQUE INDEX IF NOT EXISTS vat_filings_period_unique ON vat_filings(period_from, period_to);
-- 139
CREATE TABLE IF NOT EXISTS audit_events (id bigserial PRIMARY KEY);
-- 140
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 141
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_type varchar(255);
-- 142
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_id varchar(255);
-- 143
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_action varchar(255);
-- 144
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 145
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS message varchar(1000);
-- 146
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 147
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 148
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 149
UPDATE audit_events SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 150
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS actor_email varchar(255);
-- 151
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 152
CREATE INDEX IF NOT EXISTS audit_events_created_at_idx ON audit_events(created_at);
-- 153
CREATE INDEX IF NOT EXISTS audit_events_entity_idx ON audit_events(entity_type, entity_id);
-- 154
CREATE TABLE IF NOT EXISTS payroll_snapshots (id bigint PRIMARY KEY);
-- 155
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS content text;
-- 156
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 157
CREATE TABLE IF NOT EXISTS payroll_snapshot_revisions (id bigserial PRIMARY KEY);
-- 158
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS content text;
-- 159
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 160
ALTER TABLE customers ADD COLUMN IF NOT EXISTS personal_number varchar(255);
-- 161
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address varchar(255);
-- 162
ALTER TABLE customers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 163
ALTER TABLE customers ADD COLUMN IF NOT EXISTS postal_code varchar(255);
-- 164
ALTER TABLE customers ADD COLUMN IF NOT EXISTS city varchar(255);
-- 165
ALTER TABLE customers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 166
CREATE TABLE IF NOT EXISTS expenses (id bigserial PRIMARY KEY);
-- 167
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS expense_date date;
-- 168
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS description varchar(255);
-- 169
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 170
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 171
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 172
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 173
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 174
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 175
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS category varchar(255);
-- 176
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS paid_from varchar(255);
-- 177
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_file_name varchar(255);
-- 178
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_content_type varchar(255);
-- 179
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_storage_path varchar(1000);
-- 180
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_sha256 varchar(64);
-- 181
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_uploaded_at timestamp;
-- 182
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 183
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 184
UPDATE expenses SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'expense-shadow-v1');
-- 185
CREATE TABLE IF NOT EXISTS card_purchases (id bigserial PRIMARY KEY);
-- 186
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS purchase_date date;
-- 187
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS merchant_name varchar(255);
-- 188
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_holder varchar(255);
-- 189
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_last4 varchar(16);
-- 190
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 191
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 192
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 193
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 194
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 195
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 196
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 197
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS category varchar(255);
-- 198
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS clearing_account varchar(255);
-- 199
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'review';
-- 200
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS booked_expense_id bigint;
-- 201
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 202
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 203
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 204
UPDATE card_purchases SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'card-purchase-shadow-v1');
-- 205
CREATE INDEX IF NOT EXISTS card_purchases_purchase_date_idx ON card_purchases(purchase_date);
-- 206
CREATE TABLE IF NOT EXISTS suppliers (id bigserial PRIMARY KEY);
-- 207
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS name varchar(255);
-- 208
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS email varchar(255);
-- 209
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS org_number varchar(255);
-- 210
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 211
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS payment_info varchar(512);
-- 212
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 213
CREATE INDEX IF NOT EXISTS suppliers_name_idx ON suppliers(name);
-- 214
CREATE TABLE IF NOT EXISTS supplier_invoices (id bigserial PRIMARY KEY);
-- 215
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_id bigint;
-- 216
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_name varchar(255);
-- 217
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_email varchar(255);
-- 218
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_org_number varchar(255);
-- 219
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS invoice_date date;
-- 220
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS due_date date;
-- 221
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS description varchar(512);
-- 222
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 223
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 224
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 225
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 226
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 227
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 228
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 229
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS category varchar(64);
-- 230
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'unpaid';
-- 231
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_at date;
-- 232
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 233
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount_minor bigint;
-- 234
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 235
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_history text;
-- 236
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancelled_at date;
-- 237
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancellation_voucher_number varchar(255);
-- 238
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS self_billing boolean DEFAULT false;
-- 239
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_name varchar(255);
-- 240
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_reference varchar(255);
-- 241
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS approval_reference varchar(255);
-- 242
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 243
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 244
UPDATE supplier_invoices SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 245
CREATE INDEX IF NOT EXISTS supplier_invoices_supplier_id_idx ON supplier_invoices(supplier_id);
-- 246
CREATE INDEX IF NOT EXISTS supplier_invoices_due_date_idx ON supplier_invoices(due_date);
-- 247
CREATE TABLE IF NOT EXISTS supplier_invoice_payments (id bigserial PRIMARY KEY);
-- 248
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 249
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS payment_date date;
-- 250
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 251
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 252
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 253
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 254
ALTER TABLE supplier_invoice_payments ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 255
UPDATE supplier_invoice_payments SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 256
UPDATE supplier_invoice_payments SET amount_minor = CAST(CAST(amount AS numeric) * 100 AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL AND CAST(amount AS numeric) = TRUNC(CAST(amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 257
CREATE INDEX IF NOT EXISTS supplier_invoice_payments_invoice_date_idx ON supplier_invoice_payments(supplier_invoice_id, payment_date, id);
-- 258
CREATE UNIQUE INDEX IF NOT EXISTS supplier_invoice_payments_identity_unique ON supplier_invoice_payments (supplier_invoice_id, payment_date, amount, lower(reference)) WHERE reference IS NOT NULL AND reference <> '';
-- 259
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_supplier_invoice_payments_invoice' AND conrelid = 'supplier_invoice_payments'::regclass) THEN ALTER TABLE supplier_invoice_payments ADD CONSTRAINT fk_supplier_invoice_payments_invoice FOREIGN KEY (supplier_invoice_id) REFERENCES supplier_invoices(id); END IF; END $$;
-- 260
CREATE TABLE IF NOT EXISTS recurring_contracts (id bigserial PRIMARY KEY);
-- 261
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_id bigint;
-- 262
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_name varchar(255);
-- 263
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_id bigint;
-- 264
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_name varchar(255);
-- 265
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 266
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS contract_interval varchar(255);
-- 267
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS next_invoice_date date;
-- 268
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 269
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 270
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS last_invoice_number varchar(255);
-- 271
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 272
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived_at timestamp;
-- 273
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 274
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_date date DEFAULT CURRENT_DATE;
-- 275
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS correction_of_voucher_number varchar(255);
-- 276
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS expense_id bigint;
-- 277
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 278
CREATE INDEX IF NOT EXISTS journal_entries_voucher_number_idx ON journal_entries(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 279
CREATE INDEX IF NOT EXISTS journal_entries_expense_id_idx ON journal_entries(expense_id) WHERE expense_id IS NOT NULL;
-- 280
CREATE INDEX IF NOT EXISTS journal_entries_supplier_invoice_id_idx ON journal_entries(supplier_invoice_id) WHERE supplier_invoice_id IS NOT NULL;
-- 281
CREATE TABLE IF NOT EXISTS voucher_approvals (id bigserial PRIMARY KEY);
-- 282
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 283
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS status varchar(255);
-- 284
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS note text;
-- 285
ALTER TABLE voucher_approvals ALTER COLUMN note TYPE text;
-- 286
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewer varchar(255);
-- 287
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewed_at timestamp;
-- 288
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 289
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 290
CREATE UNIQUE INDEX IF NOT EXISTS voucher_approvals_voucher_number_unique ON voucher_approvals(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 291
CREATE INDEX IF NOT EXISTS voucher_approvals_reviewed_at_idx ON voucher_approvals(reviewed_at);
-- 292
CREATE TABLE IF NOT EXISTS owner_transactions (id bigserial PRIMARY KEY);
-- 293
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_type varchar(64);
-- 294
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_date date;
-- 295
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 296
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 297
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 298
UPDATE owner_transactions SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 299
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS description varchar(512);
-- 300
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 301
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS debit_account varchar(64);
-- 302
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS credit_account varchar(64);
-- 303
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'draft';
-- 304
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 305
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 306
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 307
CREATE INDEX IF NOT EXISTS owner_transactions_date_idx ON owner_transactions(transaction_date);
-- 308
CREATE TABLE IF NOT EXISTS app_settings (id bigint PRIMARY KEY);
-- 309
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_name varchar(255);
-- 310
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_address varchar(1000);
-- 311
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_postal_code varchar(255);
-- 312
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_city varchar(255);
-- 313
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_organization_number varchar(255);
-- 314
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_registration_number varchar(255);
-- 315
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS contact_email varchar(255);
-- 316
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 317
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS default_ocr varchar(255);
-- 318
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 319
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'SOLE_TRADER';
-- 320
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_method varchar(255) DEFAULT 'INVOICE_METHOD';
-- 321
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_reporting_period varchar(255) DEFAULT 'QUARTERLY';
-- 322
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_start_month integer DEFAULT 1;
-- 323
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_end_month integer DEFAULT 12;
-- 324
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 325
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 326
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 327
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_email_template text;
-- 328
ALTER TABLE app_settings ALTER COLUMN invoice_email_template TYPE text;
-- 329
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS automatic_invoice_reminders_enabled boolean DEFAULT true;
-- 330
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_days_before_due integer DEFAULT 5;
-- 331
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_template text;
-- 332
ALTER TABLE app_settings ALTER COLUMN invoice_reminder_template TYPE text;
-- 333
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminders_enabled boolean DEFAULT true;
-- 334
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_days_after_due integer DEFAULT 3;
-- 335
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_template text;
-- 336
ALTER TABLE app_settings ALTER COLUMN overdue_invoice_reminder_template TYPE text;
-- 337
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_locked_through_date date;
-- 338
UPDATE customers SET archived = false WHERE archived IS NULL;
-- 339
UPDATE suppliers SET archived = false WHERE archived IS NULL;
-- 340
UPDATE supplier_invoices SET status = 'unpaid' WHERE (status IS NULL OR status = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 341
UPDATE supplier_invoices SET net_amount = GREATEST(total_amount - vat_amount, 0) WHERE (net_amount IS NULL OR net_amount = 0) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 342
UPDATE supplier_invoices SET paid_amount = 0 WHERE paid_amount IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 343
UPDATE supplier_invoices SET paid_amount = total_amount WHERE (status = 'paid' OR paid_at IS NOT NULL) AND paid_amount = 0 AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 344
UPDATE supplier_invoices SET total_amount_minor = CAST(CAST(total_amount AS numeric) * 100 AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL AND CAST(total_amount AS numeric) = TRUNC(CAST(total_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 345
UPDATE supplier_invoices SET vat_amount_minor = CAST(CAST(vat_amount AS numeric) * 100 AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL AND CAST(vat_amount AS numeric) = TRUNC(CAST(vat_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 346
UPDATE supplier_invoices SET net_amount_minor = CAST(CAST(net_amount AS numeric) * 100 AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL AND CAST(net_amount AS numeric) = TRUNC(CAST(net_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 347
UPDATE supplier_invoices SET paid_amount_minor = CAST(CAST(paid_amount AS numeric) * 100 AS bigint) WHERE paid_amount_minor IS NULL AND paid_amount IS NOT NULL AND CAST(paid_amount AS numeric) = TRUNC(CAST(paid_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 348
UPDATE supplier_invoices SET payment_reference = '' WHERE payment_reference IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 349
UPDATE supplier_invoices SET payment_history = '' WHERE payment_history IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 350
UPDATE supplier_invoices SET self_billing = false WHERE self_billing IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 351
UPDATE supplier_invoices SET buyer_name = '' WHERE buyer_name IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 352
UPDATE supplier_invoices SET buyer_reference = '' WHERE buyer_reference IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 353
UPDATE supplier_invoices SET approval_reference = '' WHERE approval_reference IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 354
UPDATE supplier_invoices SET status = 'cancelled' WHERE cancellation_voucher_number IS NOT NULL AND cancellation_voucher_number <> '' AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'supplier-invoice-shadow-v1');
-- 355
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('supplier-invoice-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 356
UPDATE expenses SET net_amount_minor = CAST(CAST(net_amount AS numeric) * 100 AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL AND CAST(net_amount AS numeric) = TRUNC(CAST(net_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'expense-shadow-v1');
-- 357
UPDATE expenses SET vat_amount_minor = CAST(CAST(vat_amount AS numeric) * 100 AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL AND CAST(vat_amount AS numeric) = TRUNC(CAST(vat_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'expense-shadow-v1');
-- 358
UPDATE expenses SET total_amount_minor = CAST(CAST(total_amount AS numeric) * 100 AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL AND CAST(total_amount AS numeric) = TRUNC(CAST(total_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'expense-shadow-v1');
-- 359
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('expense-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 360
UPDATE stripe_payouts SET gross_amount_minor = CAST(CAST(gross_amount AS numeric) * 100 AS bigint) WHERE gross_amount_minor IS NULL AND gross_amount IS NOT NULL AND CAST(gross_amount AS numeric) = TRUNC(CAST(gross_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'stripe-payout-shadow-v1');
-- 361
UPDATE stripe_payouts SET fee_amount_minor = CAST(CAST(fee_amount AS numeric) * 100 AS bigint) WHERE fee_amount_minor IS NULL AND fee_amount IS NOT NULL AND CAST(fee_amount AS numeric) = TRUNC(CAST(fee_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'stripe-payout-shadow-v1');
-- 362
UPDATE stripe_payouts SET net_amount_minor = CAST(CAST(net_amount AS numeric) * 100 AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL AND CAST(net_amount AS numeric) = TRUNC(CAST(net_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'stripe-payout-shadow-v1');
-- 363
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('stripe-payout-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 364
UPDATE vat_filings SET output_vat_minor = CAST(CAST(output_vat AS numeric) * 100 AS bigint) WHERE output_vat_minor IS NULL AND output_vat IS NOT NULL AND CAST(output_vat AS numeric) = TRUNC(CAST(output_vat AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'vat-filing-shadow-v1');
-- 365
UPDATE vat_filings SET input_vat_minor = CAST(CAST(input_vat AS numeric) * 100 AS bigint) WHERE input_vat_minor IS NULL AND input_vat IS NOT NULL AND CAST(input_vat AS numeric) = TRUNC(CAST(input_vat AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'vat-filing-shadow-v1');
-- 366
UPDATE vat_filings SET vat_to_pay_minor = CAST(CAST(vat_to_pay AS numeric) * 100 AS bigint) WHERE vat_to_pay_minor IS NULL AND vat_to_pay IS NOT NULL AND CAST(vat_to_pay AS numeric) = TRUNC(CAST(vat_to_pay AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'vat-filing-shadow-v1');
-- 367
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('vat-filing-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 368
UPDATE card_purchases SET net_amount_minor = CAST(CAST(net_amount AS numeric) * 100 AS bigint) WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL AND CAST(net_amount AS numeric) = TRUNC(CAST(net_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'card-purchase-shadow-v1');
-- 369
UPDATE card_purchases SET vat_amount_minor = CAST(CAST(vat_amount AS numeric) * 100 AS bigint) WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL AND CAST(vat_amount AS numeric) = TRUNC(CAST(vat_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'card-purchase-shadow-v1');
-- 370
UPDATE card_purchases SET total_amount_minor = CAST(CAST(total_amount AS numeric) * 100 AS bigint) WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL AND CAST(total_amount AS numeric) = TRUNC(CAST(total_amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'card-purchase-shadow-v1');
-- 371
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('card-purchase-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 372
UPDATE bank_reconciliation_entries SET amount_minor = CAST(CAST(amount AS numeric) * 100 AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL AND CAST(amount AS numeric) = TRUNC(CAST(amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'bank-reconciliation-shadow-v1');
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
UPDATE owner_transactions SET status = 'draft' WHERE (status IS NULL OR status = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 380
UPDATE owner_transactions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 381
UPDATE owner_transactions SET updated_at = created_at WHERE updated_at IS NULL AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 382
UPDATE owner_transactions SET status = 'booked' WHERE voucher_number IS NOT NULL AND voucher_number <> '' AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
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
ALTER TABLE accounts ALTER COLUMN number SET NOT NULL;
-- 397
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'accounts_number_nonblank' AND conrelid = 'accounts'::regclass) THEN ALTER TABLE accounts ADD CONSTRAINT accounts_number_nonblank CHECK (btrim(number) <> ''); END IF; END $$;
-- 398
CREATE UNIQUE INDEX IF NOT EXISTS accounts_number_unique ON accounts (number);
-- 399
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS journal_entry_id bigint;
-- 400
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS document_snapshot text;
-- 401
CREATE TABLE IF NOT EXISTS invoice_originals (invoice_id bigint PRIMARY KEY REFERENCES customer_orders(id), pdf bytea NOT NULL, sha256 varchar(64) NOT NULL, archived_at timestamp with time zone NOT NULL);
-- 402
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoice_originals_invoice') THEN ALTER TABLE invoice_originals ADD CONSTRAINT fk_invoice_originals_invoice FOREIGN KEY (invoice_id) REFERENCES customer_orders(id); END IF; END $$;
-- 403
CREATE OR REPLACE FUNCTION protect_invoice_original() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Issued invoice originals are immutable' USING ERRCODE = '23514'; END; $$;
-- 404
DROP TRIGGER IF EXISTS invoice_originals_immutable ON invoice_originals;
-- 405
CREATE TRIGGER invoice_originals_immutable BEFORE UPDATE OR DELETE ON invoice_originals FOR EACH ROW EXECUTE FUNCTION protect_invoice_original();
-- 406
CREATE TABLE IF NOT EXISTS email_delivery_attempts (id bigserial PRIMARY KEY);
-- 407
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS delivery_type varchar(64) NOT NULL DEFAULT 'INVOICE_EMAIL';
-- 408
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 409
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS recipient_email varchar(255) NOT NULL DEFAULT '';
-- 410
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS subject varchar(512) NOT NULL DEFAULT '';
-- 411
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS body text NOT NULL DEFAULT '';
-- 412
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attachment_name varchar(255);
-- 413
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attachment_content bytea;
-- 414
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attachment_sha256 varchar(64);
-- 415
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'PENDING';
-- 416
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS attempts integer NOT NULL DEFAULT 1;
-- 417
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS last_error varchar(2000);
-- 418
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- 419
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- 420
ALTER TABLE email_delivery_attempts ADD COLUMN IF NOT EXISTS sent_at timestamp with time zone;
-- 421
CREATE INDEX IF NOT EXISTS email_delivery_attempts_status_idx ON email_delivery_attempts(status, updated_at);
-- 422
CREATE INDEX IF NOT EXISTS email_delivery_attempts_invoice_idx ON email_delivery_attempts(invoice_id, created_at);
-- 423
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_reconciliation_journal ON bank_reconciliation_entries (journal_entry_id);
-- 424
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_reconciliation_journal' AND conrelid = 'bank_reconciliation_entries'::regclass) THEN ALTER TABLE bank_reconciliation_entries ADD CONSTRAINT fk_bank_reconciliation_journal FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id); END IF; END $$;
-- 425
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS debit_minor bigint;
-- 426
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS credit_minor bigint;
-- 427
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS currency_code varchar(3) DEFAULT 'SEK';
-- 428
UPDATE journal_entries SET currency_code = 'SEK' WHERE (currency_code IS NULL OR currency_code = '') AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 429
UPDATE journal_entries SET debit_minor = CAST(CAST(debit AS numeric) * 100 AS bigint) WHERE debit_minor IS NULL AND debit IS NOT NULL AND CAST(debit AS numeric) = TRUNC(CAST(debit AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 430
UPDATE journal_entries SET credit_minor = CAST(CAST(credit AS numeric) * 100 AS bigint) WHERE credit_minor IS NULL AND credit IS NOT NULL AND CAST(credit AS numeric) = TRUNC(CAST(credit AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 431
UPDATE owner_transactions SET amount_minor = CAST(CAST(amount AS numeric) * 100 AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL AND CAST(amount AS numeric) = TRUNC(CAST(amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 432
UPDATE audit_events SET amount_minor = CAST(CAST(amount AS numeric) * 100 AS bigint) WHERE amount_minor IS NULL AND amount IS NOT NULL AND CAST(amount AS numeric) = TRUNC(CAST(amount AS numeric)) AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-entry-shadow-v1');
-- 433
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-entry-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 434
CREATE OR REPLACE FUNCTION sync_money_shadow_fields() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_TABLE_NAME = 'products' THEN NEW.price_minor = CASE WHEN NEW.price IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.price AS numeric) * 100, 0) AS bigint) END; NEW.discount_price_minor = CASE WHEN NEW.discount_price IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.discount_price AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'customer_orders' THEN NEW.ordinary_price_minor = CASE WHEN NEW.ordinary_price IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.ordinary_price AS numeric) * 100, 0) AS bigint) END; NEW.discount_amount_minor = CASE WHEN NEW.discount_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.discount_amount AS numeric) * 100, 0) AS bigint) END; NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.paid_amount_minor = CASE WHEN NEW.paid_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.paid_amount AS numeric) * 100, 0) AS bigint) END; NEW.refunded_amount_minor = CASE WHEN NEW.refunded_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.refunded_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'invoice_payments' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'supplier_invoices' THEN NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.paid_amount_minor = CASE WHEN NEW.paid_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.paid_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'expenses' THEN NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'card_purchases' THEN NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.vat_amount_minor = CASE WHEN NEW.vat_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_amount AS numeric) * 100, 0) AS bigint) END; NEW.total_amount_minor = CASE WHEN NEW.total_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.total_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'stripe_payouts' THEN NEW.gross_amount_minor = CASE WHEN NEW.gross_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.gross_amount AS numeric) * 100, 0) AS bigint) END; NEW.fee_amount_minor = CASE WHEN NEW.fee_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.fee_amount AS numeric) * 100, 0) AS bigint) END; NEW.net_amount_minor = CASE WHEN NEW.net_amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.net_amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'bank_reconciliation_entries' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'vat_filings' THEN NEW.output_vat_minor = CASE WHEN NEW.output_vat IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.output_vat AS numeric) * 100, 0) AS bigint) END; NEW.input_vat_minor = CASE WHEN NEW.input_vat IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.input_vat AS numeric) * 100, 0) AS bigint) END; NEW.vat_to_pay_minor = CASE WHEN NEW.vat_to_pay IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.vat_to_pay AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'journal_entries' THEN NEW.debit_minor = CASE WHEN NEW.debit IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.debit AS numeric) * 100, 0) AS bigint) END; NEW.credit_minor = CASE WHEN NEW.credit IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.credit AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'owner_transactions' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; ELSIF TG_TABLE_NAME = 'audit_events' THEN NEW.amount_minor = CASE WHEN NEW.amount IS NULL THEN NULL ELSE CAST(ROUND(CAST(NEW.amount AS numeric) * 100, 0) AS bigint) END; NEW.currency_code = 'SEK'; END IF; RETURN NEW; END; $$;
-- 435
DROP TRIGGER IF EXISTS products_money_shadow_sync ON products;
-- 436
CREATE TRIGGER products_money_shadow_sync BEFORE INSERT OR UPDATE ON products FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 437
CREATE OR REPLACE FUNCTION guard_product_money_integrity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.currency_code IS DISTINCT FROM 'SEK' OR NEW.price IS NULL OR NEW.discount_price IS NULL THEN RAISE EXCEPTION 'Invalid product currency or amount' USING ERRCODE = '23514'; END IF; IF TG_OP = 'INSERT' THEN IF (NEW.price_minor IS NOT NULL AND NEW.price_minor IS DISTINCT FROM CAST(NEW.price AS numeric) * 100) OR (NEW.discount_price_minor IS NOT NULL AND NEW.discount_price_minor IS DISTINCT FROM CAST(NEW.discount_price AS numeric) * 100) THEN RAISE EXCEPTION 'Invalid product money shadow' USING ERRCODE = '23514'; END IF; ELSE IF OLD.currency_code IS DISTINCT FROM 'SEK' OR OLD.price_minor IS DISTINCT FROM CAST(OLD.price AS numeric) * 100 OR OLD.discount_price_minor IS DISTINCT FROM CAST(OLD.discount_price AS numeric) * 100 THEN RAISE EXCEPTION 'Stored product money integrity violation' USING ERRCODE = '23514'; END IF; IF NEW.price_minor IS NULL OR NEW.discount_price_minor IS NULL OR (NEW.price_minor IS DISTINCT FROM CAST(NEW.price AS numeric) * 100 AND (NEW.price_minor IS DISTINCT FROM OLD.price_minor OR NEW.price IS NOT DISTINCT FROM OLD.price)) OR (NEW.discount_price_minor IS DISTINCT FROM CAST(NEW.discount_price AS numeric) * 100 AND (NEW.discount_price_minor IS DISTINCT FROM OLD.discount_price_minor OR NEW.discount_price IS NOT DISTINCT FROM OLD.discount_price)) THEN RAISE EXCEPTION 'Invalid product money shadow' USING ERRCODE = '23514'; END IF; END IF; RETURN NEW; END; $$;
-- 438
DROP TRIGGER IF EXISTS a_products_money_integrity_guard ON products;
-- 439
CREATE TRIGGER a_products_money_integrity_guard BEFORE INSERT OR UPDATE ON products FOR EACH ROW EXECUTE FUNCTION guard_product_money_integrity();
-- 440
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('product-money-guard-v1', 'whole-krona', 'guarded-shadow') ON CONFLICT (migration_key) DO NOTHING;
-- 441
DROP TRIGGER IF EXISTS customer_orders_money_shadow_sync ON customer_orders;
-- 442
CREATE TRIGGER customer_orders_money_shadow_sync BEFORE INSERT OR UPDATE ON customer_orders FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 443
CREATE OR REPLACE FUNCTION guard_customer_order_money_integrity() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE field_index integer; legacy_value numeric; minor_value numeric; old_legacy numeric; old_minor numeric; new_row jsonb; old_row jsonb; BEGIN IF NEW.currency_code IS DISTINCT FROM 'SEK' THEN RAISE EXCEPTION 'Invalid customer order currency' USING ERRCODE = '23514'; END IF; new_row := to_jsonb(NEW); IF TG_OP = 'UPDATE' THEN IF OLD.currency_code IS DISTINCT FROM 'SEK' THEN RAISE EXCEPTION 'Stored customer order currency integrity violation' USING ERRCODE = '23514'; END IF; old_row := to_jsonb(OLD); END IF; FOR field_index IN 0..(TG_NARGS / 2 - 1) LOOP legacy_value := (new_row ->> TG_ARGV[field_index * 2])::numeric; minor_value := (new_row ->> TG_ARGV[field_index * 2 + 1])::numeric; IF legacy_value IS NOT NULL AND legacy_value <> TRUNC(legacy_value) THEN RAISE EXCEPTION 'Fractional legacy customer order amount: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; IF TG_OP = 'UPDATE' THEN old_legacy := (old_row ->> TG_ARGV[field_index * 2])::numeric; old_minor := (old_row ->> TG_ARGV[field_index * 2 + 1])::numeric; IF old_minor IS DISTINCT FROM old_legacy * 100 OR (old_legacy IS NOT NULL AND old_legacy <> TRUNC(old_legacy)) THEN RAISE EXCEPTION 'Stored customer order money integrity violation: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; END IF; IF legacy_value IS NULL AND minor_value IS NOT NULL OR TG_OP = 'UPDATE' AND legacy_value IS NOT NULL AND minor_value IS NULL THEN RAISE EXCEPTION 'Missing customer order money counterpart: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; IF minor_value IS NOT NULL AND minor_value IS DISTINCT FROM legacy_value * 100 THEN IF TG_OP = 'INSERT' THEN RAISE EXCEPTION 'Invalid customer order money values: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; IF minor_value IS DISTINCT FROM old_minor OR legacy_value IS NOT DISTINCT FROM old_legacy THEN RAISE EXCEPTION 'Invalid customer order money values: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; END IF; END LOOP; RETURN NEW; END; $$;
-- 444
DROP TRIGGER IF EXISTS a_customer_orders_money_integrity_guard ON customer_orders;
-- 445
CREATE TRIGGER a_customer_orders_money_integrity_guard BEFORE INSERT OR UPDATE ON customer_orders FOR EACH ROW EXECUTE FUNCTION guard_customer_order_money_integrity('ordinary_price', 'ordinary_price_minor', 'discount_amount', 'discount_amount_minor', 'net_amount', 'net_amount_minor', 'vat_amount', 'vat_amount_minor', 'total_amount', 'total_amount_minor', 'paid_amount', 'paid_amount_minor', 'refunded_amount', 'refunded_amount_minor');
-- 446
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('customer-order-exact-v1', 'whole-krona', 'guarded-shadow') ON CONFLICT (migration_key) DO NOTHING;
-- 447
DROP TRIGGER IF EXISTS invoice_payments_money_shadow_sync ON invoice_payments;
-- 448
CREATE TRIGGER invoice_payments_money_shadow_sync BEFORE INSERT OR UPDATE ON invoice_payments FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 449
CREATE OR REPLACE FUNCTION guard_customer_payment_money_integrity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'UPDATE' AND (OLD.amount IS NULL OR OLD.amount <> TRUNC(CAST(OLD.amount AS numeric)) OR OLD.amount_minor IS DISTINCT FROM CAST(OLD.amount AS numeric) * 100 OR OLD.currency_code IS DISTINCT FROM 'SEK') THEN RAISE EXCEPTION 'Stored customer payment money integrity violation' USING ERRCODE = '23514'; END IF; IF NEW.amount IS NULL OR NEW.amount <> TRUNC(CAST(NEW.amount AS numeric)) OR NEW.currency_code IS DISTINCT FROM 'SEK' OR (NEW.amount_minor IS NOT NULL AND NEW.amount_minor IS DISTINCT FROM CAST(NEW.amount AS numeric) * 100) OR (TG_OP = 'UPDATE' AND NEW.amount_minor IS NULL) THEN RAISE EXCEPTION 'Invalid customer payment money values' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$;
-- 450
DROP TRIGGER IF EXISTS a_invoice_payment_money_integrity_guard ON invoice_payments;
-- 451
CREATE TRIGGER a_invoice_payment_money_integrity_guard BEFORE INSERT OR UPDATE ON invoice_payments FOR EACH ROW EXECUTE FUNCTION guard_customer_payment_money_integrity();
-- 452
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('customer-payment-exact-v1', 'whole-krona', 'guarded-shadow') ON CONFLICT (migration_key) DO NOTHING;
-- 453
CREATE OR REPLACE FUNCTION guard_operational_money_integrity() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE field_index integer; legacy_value numeric; minor_value numeric; old_legacy numeric; old_minor numeric; new_row jsonb; old_row jsonb; BEGIN IF TG_NARGS = 0 OR MOD(TG_NARGS, 2) <> 0 THEN RAISE EXCEPTION 'Invalid operational money guard configuration' USING ERRCODE = '23514'; END IF; IF NEW.currency_code IS DISTINCT FROM 'SEK' THEN RAISE EXCEPTION 'Invalid operational money currency' USING ERRCODE = '23514'; END IF; new_row := to_jsonb(NEW); IF TG_OP = 'UPDATE' THEN IF OLD.currency_code IS DISTINCT FROM 'SEK' THEN RAISE EXCEPTION 'Stored operational money currency integrity violation' USING ERRCODE = '23514'; END IF; old_row := to_jsonb(OLD); END IF; FOR field_index IN 0..(TG_NARGS / 2 - 1) LOOP legacy_value := (new_row ->> TG_ARGV[field_index * 2])::numeric; minor_value := (new_row ->> TG_ARGV[field_index * 2 + 1])::numeric; IF legacy_value IS NOT NULL AND legacy_value <> TRUNC(legacy_value) THEN RAISE EXCEPTION 'Fractional legacy operational amount: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; IF TG_OP = 'UPDATE' THEN old_legacy := (old_row ->> TG_ARGV[field_index * 2])::numeric; old_minor := (old_row ->> TG_ARGV[field_index * 2 + 1])::numeric; IF old_minor IS DISTINCT FROM old_legacy * 100 OR (old_legacy IS NOT NULL AND old_legacy <> TRUNC(old_legacy)) THEN RAISE EXCEPTION 'Stored operational money integrity violation: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; END IF; IF (legacy_value IS NULL AND minor_value IS NOT NULL) OR (TG_OP = 'UPDATE' AND legacy_value IS NOT NULL AND minor_value IS NULL) THEN RAISE EXCEPTION 'Missing operational money counterpart: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; IF minor_value IS NOT NULL AND minor_value IS DISTINCT FROM legacy_value * 100 THEN IF TG_OP = 'INSERT' OR minor_value IS DISTINCT FROM old_minor OR legacy_value IS NOT DISTINCT FROM old_legacy THEN RAISE EXCEPTION 'Invalid operational money values: %', TG_ARGV[field_index * 2] USING ERRCODE = '23514'; END IF; END IF; END LOOP; RETURN NEW; END; $$;
-- 454
DROP TRIGGER IF EXISTS supplier_invoices_money_shadow_sync ON supplier_invoices;
-- 455
CREATE TRIGGER supplier_invoices_money_shadow_sync BEFORE INSERT OR UPDATE ON supplier_invoices FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 456
DROP TRIGGER IF EXISTS a_supplier_invoices_money_integrity_guard ON supplier_invoices;
-- 457
CREATE TRIGGER a_supplier_invoices_money_integrity_guard BEFORE INSERT OR UPDATE ON supplier_invoices FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('total_amount', 'total_amount_minor', 'vat_amount', 'vat_amount_minor', 'net_amount', 'net_amount_minor', 'paid_amount', 'paid_amount_minor');
-- 458
DROP TRIGGER IF EXISTS expenses_money_shadow_sync ON expenses;
-- 459
CREATE TRIGGER expenses_money_shadow_sync BEFORE INSERT OR UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 460
DROP TRIGGER IF EXISTS a_expenses_money_integrity_guard ON expenses;
-- 461
CREATE TRIGGER a_expenses_money_integrity_guard BEFORE INSERT OR UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('net_amount', 'net_amount_minor', 'vat_amount', 'vat_amount_minor', 'total_amount', 'total_amount_minor');
-- 462
DROP TRIGGER IF EXISTS card_purchases_money_shadow_sync ON card_purchases;
-- 463
CREATE TRIGGER card_purchases_money_shadow_sync BEFORE INSERT OR UPDATE ON card_purchases FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 464
DROP TRIGGER IF EXISTS a_card_purchases_money_integrity_guard ON card_purchases;
-- 465
CREATE TRIGGER a_card_purchases_money_integrity_guard BEFORE INSERT OR UPDATE ON card_purchases FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('net_amount', 'net_amount_minor', 'vat_amount', 'vat_amount_minor', 'total_amount', 'total_amount_minor');
-- 466
DROP TRIGGER IF EXISTS stripe_payouts_money_shadow_sync ON stripe_payouts;
-- 467
CREATE TRIGGER stripe_payouts_money_shadow_sync BEFORE INSERT OR UPDATE ON stripe_payouts FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 468
DROP TRIGGER IF EXISTS a_stripe_payouts_money_integrity_guard ON stripe_payouts;
-- 469
CREATE TRIGGER a_stripe_payouts_money_integrity_guard BEFORE INSERT OR UPDATE ON stripe_payouts FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('gross_amount', 'gross_amount_minor', 'fee_amount', 'fee_amount_minor', 'net_amount', 'net_amount_minor');
-- 470
DROP TRIGGER IF EXISTS bank_reconciliation_entries_money_shadow_sync ON bank_reconciliation_entries;
-- 471
CREATE TRIGGER bank_reconciliation_entries_money_shadow_sync BEFORE INSERT OR UPDATE ON bank_reconciliation_entries FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 472
DROP TRIGGER IF EXISTS a_bank_reconciliation_entries_money_integrity_guard ON bank_reconciliation_entries;
-- 473
CREATE TRIGGER a_bank_reconciliation_entries_money_integrity_guard BEFORE INSERT OR UPDATE ON bank_reconciliation_entries FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('amount', 'amount_minor');
-- 474
DROP TRIGGER IF EXISTS vat_filings_money_shadow_sync ON vat_filings;
-- 475
CREATE TRIGGER vat_filings_money_shadow_sync BEFORE INSERT OR UPDATE ON vat_filings FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 476
DROP TRIGGER IF EXISTS a_vat_filings_money_integrity_guard ON vat_filings;
-- 477
CREATE TRIGGER a_vat_filings_money_integrity_guard BEFORE INSERT OR UPDATE ON vat_filings FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('output_vat', 'output_vat_minor', 'input_vat', 'input_vat_minor', 'vat_to_pay', 'vat_to_pay_minor');
-- 478
DROP TRIGGER IF EXISTS journal_entries_money_shadow_sync ON journal_entries;
-- 479
CREATE TRIGGER journal_entries_money_shadow_sync BEFORE INSERT OR UPDATE ON journal_entries FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 480
CREATE OR REPLACE FUNCTION guard_journal_money_integrity() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'UPDATE' AND (OLD.debit IS NULL OR OLD.credit IS NULL OR OLD.debit_minor IS DISTINCT FROM OLD.debit::numeric * 100 OR OLD.credit_minor IS DISTINCT FROM OLD.credit::numeric * 100 OR OLD.currency_code IS DISTINCT FROM 'SEK' OR OLD.debit < 0 OR OLD.credit < 0 OR (OLD.debit > 0 AND OLD.credit > 0)) THEN RAISE EXCEPTION 'Stored journal money integrity violation' USING ERRCODE = '23514'; END IF; IF NEW.debit IS NULL OR NEW.credit IS NULL OR NEW.currency_code IS DISTINCT FROM 'SEK' OR NEW.debit < 0 OR NEW.credit < 0 OR (NEW.debit > 0 AND NEW.credit > 0) OR (NEW.debit_minor IS NOT NULL AND NEW.debit_minor IS DISTINCT FROM NEW.debit::numeric * 100) OR (NEW.credit_minor IS NOT NULL AND NEW.credit_minor IS DISTINCT FROM NEW.credit::numeric * 100) OR (TG_OP = 'UPDATE' AND (NEW.debit_minor IS NULL OR NEW.credit_minor IS NULL)) THEN RAISE EXCEPTION 'Invalid journal money values' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$;
-- 481
DROP TRIGGER IF EXISTS a_journal_money_integrity_guard ON journal_entries;
-- 482
CREATE TRIGGER a_journal_money_integrity_guard BEFORE INSERT OR UPDATE ON journal_entries FOR EACH ROW EXECUTE FUNCTION guard_journal_money_integrity();
-- 483
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-money-guard-v1', 'whole-krona', 'guarded-shadow') ON CONFLICT (migration_key) DO NOTHING;
-- 484
DROP TRIGGER IF EXISTS owner_transactions_money_shadow_sync ON owner_transactions;
-- 485
CREATE TRIGGER owner_transactions_money_shadow_sync BEFORE INSERT OR UPDATE ON owner_transactions FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 486
DROP TRIGGER IF EXISTS a_owner_transactions_money_integrity_guard ON owner_transactions;
-- 487
CREATE TRIGGER a_owner_transactions_money_integrity_guard BEFORE INSERT OR UPDATE ON owner_transactions FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('amount', 'amount_minor');
-- 488
DROP TRIGGER IF EXISTS audit_events_money_shadow_sync ON audit_events;
-- 489
CREATE TRIGGER audit_events_money_shadow_sync BEFORE INSERT OR UPDATE ON audit_events FOR EACH ROW EXECUTE FUNCTION sync_money_shadow_fields();
-- 490
DROP TRIGGER IF EXISTS a_audit_events_money_integrity_guard ON audit_events;
-- 491
CREATE TRIGGER a_audit_events_money_integrity_guard BEFORE INSERT OR UPDATE ON audit_events FOR EACH ROW EXECUTE FUNCTION guard_operational_money_integrity('amount', 'amount_minor');
-- 492
CREATE OR REPLACE FUNCTION sync_supplier_payment_shadow_fields() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'UPDATE' THEN IF OLD.amount IS NULL OR OLD.amount <> TRUNC(CAST(OLD.amount AS numeric)) OR OLD.amount_minor IS DISTINCT FROM CAST(OLD.amount AS numeric) * 100 OR OLD.currency_code IS DISTINCT FROM 'SEK' THEN RAISE EXCEPTION 'Stored supplier payment money integrity violation' USING ERRCODE = '23514'; END IF; END IF; IF NEW.amount IS NULL OR NEW.amount <> TRUNC(CAST(NEW.amount AS numeric)) OR NEW.currency_code IS DISTINCT FROM 'SEK' OR (NEW.amount_minor IS NOT NULL AND NEW.amount_minor IS DISTINCT FROM CAST(NEW.amount AS numeric) * 100) OR (TG_OP = 'UPDATE' AND NEW.amount_minor IS NULL) THEN RAISE EXCEPTION 'Invalid supplier payment money values' USING ERRCODE = '23514'; END IF; NEW.amount_minor = CAST(CAST(NEW.amount AS numeric) * 100 AS bigint); RETURN NEW; END; $$;
-- 493
DROP TRIGGER IF EXISTS supplier_invoice_payments_money_shadow_sync ON supplier_invoice_payments;
-- 494
CREATE TRIGGER supplier_invoice_payments_money_shadow_sync BEFORE INSERT OR UPDATE ON supplier_invoice_payments FOR EACH ROW EXECUTE FUNCTION sync_supplier_payment_shadow_fields();
-- 495
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('supplier-payment-exact-v1', 'whole-krona', 'guarded-shadow') ON CONFLICT (migration_key) DO NOTHING;
-- 496
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS mfa_secret_enc text;
-- 497
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS mfa_pending_secret_enc text;
-- 498
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS mfa_enabled boolean NOT NULL DEFAULT false;
-- 499
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS mfa_last_counter bigint NOT NULL DEFAULT -1;
-- 500
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS mfa_recovery_hashes text;
-- 501
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS auth_version bigint NOT NULL DEFAULT 0;
-- 502
CREATE TABLE IF NOT EXISTS mfa_login_challenges (challenge_hash varchar(64) PRIMARY KEY, user_email varchar(255) NOT NULL, expires_at timestamp with time zone NOT NULL, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- 503
CREATE INDEX IF NOT EXISTS mfa_login_challenges_expiry_idx ON mfa_login_challenges (expires_at);
-- 504
CREATE TABLE IF NOT EXISTS voucher_evidence (id bigserial PRIMARY KEY, voucher_number varchar(255) NOT NULL UNIQUE, file_name varchar(255) NOT NULL, content_type varchar(100) NOT NULL, sha256 varchar(64) NOT NULL, uploaded_at timestamp with time zone NOT NULL, file_size integer NOT NULL);
-- 505
CREATE TABLE IF NOT EXISTS voucher_evidence_contents (voucher_evidence_id bigint PRIMARY KEY REFERENCES voucher_evidence(id), content bytea NOT NULL);
-- 506
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_evidence_id bigint;
-- 507
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_journal_voucher_evidence' AND conrelid = 'journal_entries'::regclass) THEN ALTER TABLE journal_entries ADD CONSTRAINT fk_journal_voucher_evidence FOREIGN KEY (voucher_evidence_id) REFERENCES voucher_evidence(id); END IF; END $$;
-- 508
CREATE INDEX IF NOT EXISTS journal_entries_voucher_evidence_idx ON journal_entries(voucher_evidence_id) WHERE voucher_evidence_id IS NOT NULL;
-- 509
CREATE OR REPLACE FUNCTION protect_voucher_evidence() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Archived voucher evidence is immutable' USING ERRCODE = '23514'; END; $$;
-- 510
DROP TRIGGER IF EXISTS voucher_evidence_immutable ON voucher_evidence;
-- 511
CREATE TRIGGER voucher_evidence_immutable BEFORE UPDATE OR DELETE ON voucher_evidence FOR EACH ROW EXECUTE FUNCTION protect_voucher_evidence();
-- 512
DROP TRIGGER IF EXISTS voucher_evidence_contents_immutable ON voucher_evidence_contents;
-- 513
CREATE TRIGGER voucher_evidence_contents_immutable BEFORE UPDATE OR DELETE ON voucher_evidence_contents FOR EACH ROW EXECUTE FUNCTION protect_voucher_evidence();
-- 514
CREATE OR REPLACE FUNCTION protect_journal_voucher_evidence_link() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'UPDATE' AND OLD.voucher_evidence_id IS NOT NULL AND NEW.voucher_evidence_id IS DISTINCT FROM OLD.voucher_evidence_id THEN RAISE EXCEPTION 'Archived voucher evidence link is immutable' USING ERRCODE = '23514'; END IF; IF NEW.voucher_evidence_id IS NOT NULL AND (SELECT voucher_number FROM voucher_evidence WHERE id = NEW.voucher_evidence_id) IS DISTINCT FROM NEW.voucher_number THEN RAISE EXCEPTION 'Voucher evidence belongs to another voucher' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$;
-- 515
DROP TRIGGER IF EXISTS journal_voucher_evidence_link_guard ON journal_entries;
-- 516
CREATE TRIGGER journal_voucher_evidence_link_guard BEFORE INSERT OR UPDATE OF voucher_evidence_id, voucher_number ON journal_entries FOR EACH ROW EXECUTE FUNCTION protect_journal_voucher_evidence_link();
-- 517
CREATE OR REPLACE FUNCTION protect_posted_journal_entry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Posted journal entries cannot be deleted; use a correction voucher' USING ERRCODE = '23514'; END IF; IF TG_OP = 'UPDATE' AND ROW(OLD.id, OLD.invoice_id, OLD.expense_id, OLD.supplier_invoice_id, OLD.account_number, OLD.account_name, OLD.voucher_number, OLD.voucher_date, OLD.debit, OLD.credit, OLD.debit_minor, OLD.credit_minor, OLD.currency_code, OLD.description, OLD.correction_of_voucher_number, OLD.created_at) IS DISTINCT FROM ROW(NEW.id, NEW.invoice_id, NEW.expense_id, NEW.supplier_invoice_id, NEW.account_number, NEW.account_name, NEW.voucher_number, NEW.voucher_date, NEW.debit, NEW.credit, NEW.debit_minor, NEW.credit_minor, NEW.currency_code, NEW.description, NEW.correction_of_voucher_number, NEW.created_at) THEN RAISE EXCEPTION 'Posted journal entries are immutable; use a correction voucher' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$;
-- 518
DROP TRIGGER IF EXISTS b_journal_posted_immutable_guard ON journal_entries;
-- 519
CREATE TRIGGER b_journal_posted_immutable_guard BEFORE INSERT OR UPDATE OR DELETE ON journal_entries FOR EACH ROW EXECUTE FUNCTION protect_posted_journal_entry();
-- 520
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-posted-immutable-v1', 'mutable-journal', 'corrective-journal') ON CONFLICT (migration_key) DO NOTHING;
-- 521
CREATE TABLE IF NOT EXISTS journal_voucher_registry (voucher_number varchar(255) PRIMARY KEY, posting_xid bigint NOT NULL, sealed boolean NOT NULL);
-- 522
INSERT INTO journal_voucher_registry (voucher_number, posting_xid, sealed) SELECT DISTINCT voucher_number, 0, true FROM journal_entries WHERE voucher_number IS NOT NULL AND btrim(voucher_number) <> '' AND NOT EXISTS (SELECT 1 FROM money_migration_ledger WHERE migration_key = 'journal-voucher-seal-v1') ON CONFLICT (voucher_number) DO NOTHING;
-- 523
CREATE OR REPLACE FUNCTION protect_journal_voucher_registry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Posted voucher registry cannot be deleted' USING ERRCODE = '23514'; END IF; IF OLD.sealed OR NEW.voucher_number IS DISTINCT FROM OLD.voucher_number OR NEW.posting_xid IS DISTINCT FROM OLD.posting_xid OR NEW.sealed IS DISTINCT FROM true OR OLD.posting_xid <> txid_current() THEN RAISE EXCEPTION 'Posted voucher registry is immutable' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$;
-- 524
DROP TRIGGER IF EXISTS journal_voucher_registry_immutable ON journal_voucher_registry;
-- 525
CREATE TRIGGER journal_voucher_registry_immutable BEFORE UPDATE OR DELETE ON journal_voucher_registry FOR EACH ROW EXECUTE FUNCTION protect_journal_voucher_registry();
-- 526
CREATE OR REPLACE FUNCTION protect_audit_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Audit events are append-only' USING ERRCODE = '23514'; END; $$;
-- 527
DROP TRIGGER IF EXISTS audit_events_immutable ON audit_events;
-- 528
CREATE TRIGGER audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION protect_audit_event();
-- 529
DROP TRIGGER IF EXISTS audit_events_no_truncate ON audit_events;
-- 530
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit_events FOR EACH STATEMENT EXECUTE FUNCTION protect_audit_event();
-- 531
CREATE OR REPLACE FUNCTION reserve_journal_voucher() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE registered_xid bigint; already_sealed boolean; BEGIN IF NEW.voucher_number IS NULL OR btrim(NEW.voucher_number) = '' THEN RAISE EXCEPTION 'Journal voucher number is required' USING ERRCODE = '23514'; END IF; INSERT INTO journal_voucher_registry (voucher_number, posting_xid, sealed) VALUES (NEW.voucher_number, txid_current(), false) ON CONFLICT (voucher_number) DO NOTHING; SELECT posting_xid, sealed INTO registered_xid, already_sealed FROM journal_voucher_registry WHERE voucher_number = NEW.voucher_number FOR UPDATE; IF NOT FOUND OR already_sealed OR registered_xid <> txid_current() THEN RAISE EXCEPTION 'Posted voucher number cannot be reused; use a correction voucher' USING ERRCODE = '23514'; END IF; RETURN NEW; END; $$;
-- 532
DROP TRIGGER IF EXISTS journal_voucher_reservation_guard ON journal_entries;
-- 533
CREATE TRIGGER journal_voucher_reservation_guard BEFORE INSERT ON journal_entries FOR EACH ROW EXECUTE FUNCTION reserve_journal_voucher();
-- 534
CREATE OR REPLACE FUNCTION require_balanced_journal_voucher() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE debit_total numeric; credit_total numeric; registered_xid bigint; BEGIN SELECT posting_xid INTO registered_xid FROM journal_voucher_registry WHERE voucher_number = NEW.voucher_number FOR UPDATE; IF registered_xid IS DISTINCT FROM txid_current() THEN RAISE EXCEPTION 'Journal voucher reservation is missing' USING ERRCODE = '23514'; END IF; SELECT COALESCE(SUM(debit_minor), 0), COALESCE(SUM(credit_minor), 0) INTO debit_total, credit_total FROM journal_entries WHERE voucher_number = NEW.voucher_number; IF debit_total IS DISTINCT FROM credit_total THEN RAISE EXCEPTION 'Journal voucher is not balanced in minor units' USING ERRCODE = '23514'; END IF; UPDATE journal_voucher_registry SET sealed = true WHERE voucher_number = NEW.voucher_number AND NOT sealed; RETURN NULL; END; $$;
-- 535
DROP TRIGGER IF EXISTS journal_voucher_balance_guard ON journal_entries;
-- 536
CREATE CONSTRAINT TRIGGER journal_voucher_balance_guard AFTER INSERT ON journal_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_balanced_journal_voucher();
-- 537
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-voucher-balance-v1', 'application-checked-voucher', 'commit-checked-voucher') ON CONFLICT (migration_key) DO NOTHING;
-- 538
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-voucher-seal-v1', 'reusable-voucher-number', 'sealed-voucher-number') ON CONFLICT (migration_key) DO NOTHING;
-- 539
ALTER TABLE journal_entries ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
-- 540
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'journal_entry_created_at_present' AND conrelid = 'journal_entries'::regclass) THEN ALTER TABLE journal_entries ADD CONSTRAINT journal_entry_created_at_present CHECK (created_at IS NOT NULL) NOT VALID; END IF; END $$;
-- 541
CREATE TABLE IF NOT EXISTS money_migration_issues (id bigserial PRIMARY KEY, run_id uuid, migration_key varchar(128) NOT NULL, entity_table varchar(128) NOT NULL, entity_id varchar(128) NOT NULL, field_name varchar(128) NOT NULL, issue_code varchar(64) NOT NULL, source_value text, target_value text, details text, first_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- 542
ALTER TABLE money_migration_issues ADD COLUMN IF NOT EXISTS run_id uuid;
-- 543
ALTER TABLE money_migration_issues DROP CONSTRAINT IF EXISTS money_migration_issues_migration_key_entity_table_entity_id_field_name_issue_code_key;
-- 544
CREATE INDEX IF NOT EXISTS money_migration_issues_key_idx ON money_migration_issues(migration_key, last_seen_at);
-- 545
CREATE INDEX IF NOT EXISTS money_migration_issues_run_idx ON money_migration_issues(run_id, id);
-- 546
CREATE TABLE IF NOT EXISTS money_migration_runs (run_id uuid PRIMARY KEY, migration_key varchar(128) NOT NULL, mode varchar(32) NOT NULL, state varchar(32) NOT NULL, started_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_at timestamp with time zone, checked_rows bigint NOT NULL DEFAULT 0, issue_count bigint NOT NULL DEFAULT 0, error_details text);
-- 547
CREATE TABLE IF NOT EXISTS money_migration_checks (migration_key varchar(128) NOT NULL, check_name varchar(128) NOT NULL, run_id uuid, status varchar(32) NOT NULL, row_count bigint NOT NULL DEFAULT 0, issue_count bigint NOT NULL DEFAULT 0, details text, checked_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (migration_key, check_name));
-- 548
ALTER TABLE money_migration_checks ADD COLUMN IF NOT EXISTS run_id uuid;
