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
ALTER TABLE products ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 002
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_price integer DEFAULT 0;
-- 003
ALTER TABLE products ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 004
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 005
UPDATE products SET active = true WHERE active IS NULL;
-- 006
UPDATE products SET discount_price = 0 WHERE discount_price IS NULL;
-- 007
ALTER TABLE products ADD COLUMN IF NOT EXISTS price_minor bigint;
-- 008
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_price_minor bigint;
-- 009
UPDATE products SET price_minor = CAST(price AS bigint) * 100 WHERE price_minor IS NULL AND price IS NOT NULL;
-- 010
UPDATE products SET discount_price_minor = CAST(discount_price AS bigint) * 100 WHERE discount_price_minor IS NULL AND discount_price IS NOT NULL;
-- 011
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 012
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 013
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 014
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 015
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 016
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price integer DEFAULT 0;
-- 017
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount integer DEFAULT 0;
-- 018
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 019
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS status varchar(255) DEFAULT 'DRAFT';
-- 020
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS stripe_checkout_session_id varchar(255);
-- 021
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_number varchar(255);
-- 022
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_date date DEFAULT CURRENT_DATE;
-- 023
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS due_date date;
-- 024
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 025
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 026
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ocr_number varchar(255);
-- 027
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 028
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 029
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_date date;
-- 030
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 031
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 032
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_date date;
-- 033
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount integer DEFAULT 0;
-- 034
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_reference varchar(255);
-- 035
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS reminder_sent_date date;
-- 036
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credit_invoice boolean DEFAULT false;
-- 037
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credited_invoice_id bigint;
-- 038
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price_minor bigint;
-- 039
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount_minor bigint;
-- 040
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 041
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 042
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 043
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount_minor bigint;
-- 044
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount_minor bigint;
-- 045
UPDATE customer_orders SET ordinary_price_minor = CAST(ordinary_price AS bigint) * 100 WHERE ordinary_price_minor IS NULL AND ordinary_price IS NOT NULL;
-- 046
UPDATE customer_orders SET discount_amount_minor = CAST(discount_amount AS bigint) * 100 WHERE discount_amount_minor IS NULL AND discount_amount IS NOT NULL;
-- 047
UPDATE customer_orders SET net_amount_minor = CAST(net_amount AS bigint) * 100 WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 048
UPDATE customer_orders SET vat_amount_minor = CAST(vat_amount AS bigint) * 100 WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 049
UPDATE customer_orders SET total_amount_minor = CAST(total_amount AS bigint) * 100 WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 050
UPDATE customer_orders SET paid_amount_minor = CAST(paid_amount AS bigint) * 100 WHERE paid_amount_minor IS NULL AND paid_amount IS NOT NULL;
-- 051
UPDATE customer_orders SET refunded_amount_minor = CAST(refunded_amount AS bigint) * 100 WHERE refunded_amount_minor IS NULL AND refunded_amount IS NOT NULL;
-- 052
CREATE INDEX IF NOT EXISTS customer_orders_invoice_number_idx ON customer_orders(invoice_number) WHERE invoice_number IS NOT NULL AND invoice_number <> '';
-- 053
CREATE INDEX IF NOT EXISTS customer_orders_ocr_number_idx ON customer_orders(ocr_number) WHERE ocr_number IS NOT NULL AND ocr_number <> '';
-- 054
CREATE TABLE IF NOT EXISTS invoice_payments (id bigserial PRIMARY KEY);
-- 055
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 056
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS payment_date date;
-- 057
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 058
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 059
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 060
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 061
UPDATE invoice_payments SET amount_minor = CAST(amount AS bigint) * 100 WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 062
CREATE TABLE IF NOT EXISTS money_migration_ledger (migration_key varchar(128) PRIMARY KEY, source_unit varchar(32) NOT NULL, target_unit varchar(32) NOT NULL, applied_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- 063
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('core-invoice-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 064
CREATE TABLE IF NOT EXISTS invoice_reminders (id bigserial PRIMARY KEY);
-- 065
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 066
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 067
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS method varchar(255);
-- 068
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS status varchar(255);
-- 069
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS recipient_email varchar(255);
-- 070
CREATE TABLE IF NOT EXISTS stripe_webhook_events (event_id varchar(255) PRIMARY KEY);
-- 071
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 072
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS processed_at timestamp;
-- 073
CREATE TABLE IF NOT EXISTS stripe_payouts (id bigserial PRIMARY KEY);
-- 074
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS payout_date date;
-- 075
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount integer DEFAULT 0;
-- 076
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount integer DEFAULT 0;
-- 077
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 078
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount_minor bigint;
-- 079
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount_minor bigint;
-- 080
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 081
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 082
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 083
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 084
CREATE UNIQUE INDEX IF NOT EXISTS stripe_payouts_reference_unique ON stripe_payouts(reference) WHERE reference IS NOT NULL AND reference <> '';
-- 085
CREATE TABLE IF NOT EXISTS bank_reconciliation_entries (id bigserial PRIMARY KEY);
-- 086
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_row_id varchar(255);
-- 087
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_date date;
-- 088
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS description varchar(255);
-- 089
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 090
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 091
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount_minor bigint;
-- 092
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS entry_type varchar(255);
-- 093
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS status varchar(255);
-- 094
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS match_label varchar(255);
-- 095
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS booked_at timestamp;
-- 096
CREATE TABLE IF NOT EXISTS vat_filings (id bigserial PRIMARY KEY);
-- 097
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_from date;
-- 098
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_to date;
-- 099
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat integer DEFAULT 0;
-- 100
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat integer DEFAULT 0;
-- 101
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay integer DEFAULT 0;
-- 102
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat_minor bigint;
-- 103
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat_minor bigint;
-- 104
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay_minor bigint;
-- 105
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS status varchar(255);
-- 106
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submission_reference varchar(255);
-- 107
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 108
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS note varchar(1000);
-- 109
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submitted_at timestamp;
-- 110
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS paid_at timestamp;
-- 111
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 112
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 113
CREATE UNIQUE INDEX IF NOT EXISTS vat_filings_period_unique ON vat_filings(period_from, period_to);
-- 114
CREATE TABLE IF NOT EXISTS audit_events (id bigserial PRIMARY KEY);
-- 115
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 116
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_type varchar(255);
-- 117
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_id varchar(255);
-- 118
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_action varchar(255);
-- 119
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 120
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS message varchar(1000);
-- 121
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 122
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS actor_email varchar(255);
-- 123
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 124
CREATE INDEX IF NOT EXISTS audit_events_created_at_idx ON audit_events(created_at);
-- 125
CREATE INDEX IF NOT EXISTS audit_events_entity_idx ON audit_events(entity_type, entity_id);
-- 126
CREATE TABLE IF NOT EXISTS payroll_snapshots (id bigint PRIMARY KEY);
-- 127
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS content text;
-- 128
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 129
CREATE TABLE IF NOT EXISTS payroll_snapshot_revisions (id bigserial PRIMARY KEY);
-- 130
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS content text;
-- 131
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 132
ALTER TABLE customers ADD COLUMN IF NOT EXISTS personal_number varchar(255);
-- 133
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address varchar(255);
-- 134
ALTER TABLE customers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 135
ALTER TABLE customers ADD COLUMN IF NOT EXISTS postal_code varchar(255);
-- 136
ALTER TABLE customers ADD COLUMN IF NOT EXISTS city varchar(255);
-- 137
ALTER TABLE customers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 138
CREATE TABLE IF NOT EXISTS expenses (id bigserial PRIMARY KEY);
-- 139
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS expense_date date;
-- 140
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS description varchar(255);
-- 141
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 142
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 143
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 144
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 145
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 146
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 147
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS category varchar(255);
-- 148
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS paid_from varchar(255);
-- 149
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_file_name varchar(255);
-- 150
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_content_type varchar(255);
-- 151
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_storage_path varchar(1000);
-- 152
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_sha256 varchar(64);
-- 153
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_uploaded_at timestamp;
-- 154
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 155
CREATE TABLE IF NOT EXISTS card_purchases (id bigserial PRIMARY KEY);
-- 156
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS purchase_date date;
-- 157
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS merchant_name varchar(255);
-- 158
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_holder varchar(255);
-- 159
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_last4 varchar(16);
-- 160
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 161
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 162
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 163
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 164
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 165
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 166
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 167
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS category varchar(255);
-- 168
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS clearing_account varchar(255);
-- 169
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'review';
-- 170
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS booked_expense_id bigint;
-- 171
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 172
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 173
CREATE INDEX IF NOT EXISTS card_purchases_purchase_date_idx ON card_purchases(purchase_date);
-- 174
CREATE TABLE IF NOT EXISTS suppliers (id bigserial PRIMARY KEY);
-- 175
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS name varchar(255);
-- 176
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS email varchar(255);
-- 177
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS org_number varchar(255);
-- 178
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 179
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS payment_info varchar(512);
-- 180
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 181
CREATE INDEX IF NOT EXISTS suppliers_name_idx ON suppliers(name);
-- 182
CREATE TABLE IF NOT EXISTS supplier_invoices (id bigserial PRIMARY KEY);
-- 183
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_id bigint;
-- 184
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_name varchar(255);
-- 185
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_email varchar(255);
-- 186
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_org_number varchar(255);
-- 187
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS invoice_date date;
-- 188
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS due_date date;
-- 189
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS description varchar(512);
-- 190
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 191
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 192
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 193
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 194
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount_minor bigint;
-- 195
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount_minor bigint;
-- 196
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount_minor bigint;
-- 197
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS category varchar(64);
-- 198
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'unpaid';
-- 199
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_at date;
-- 200
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 201
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount_minor bigint;
-- 202
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 203
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_history text;
-- 204
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancelled_at date;
-- 205
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancellation_voucher_number varchar(255);
-- 206
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS self_billing boolean DEFAULT false;
-- 207
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_name varchar(255);
-- 208
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_reference varchar(255);
-- 209
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS approval_reference varchar(255);
-- 210
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 211
CREATE INDEX IF NOT EXISTS supplier_invoices_supplier_id_idx ON supplier_invoices(supplier_id);
-- 212
CREATE INDEX IF NOT EXISTS supplier_invoices_due_date_idx ON supplier_invoices(due_date);
-- 213
CREATE TABLE IF NOT EXISTS recurring_contracts (id bigserial PRIMARY KEY);
-- 214
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_id bigint;
-- 215
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_name varchar(255);
-- 216
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_id bigint;
-- 217
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_name varchar(255);
-- 218
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 219
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS contract_interval varchar(255);
-- 220
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS next_invoice_date date;
-- 221
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 222
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 223
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS last_invoice_number varchar(255);
-- 224
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 225
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived_at timestamp;
-- 226
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 227
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_date date DEFAULT CURRENT_DATE;
-- 228
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS correction_of_voucher_number varchar(255);
-- 229
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS expense_id bigint;
-- 230
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 231
CREATE INDEX IF NOT EXISTS journal_entries_voucher_number_idx ON journal_entries(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 232
CREATE INDEX IF NOT EXISTS journal_entries_expense_id_idx ON journal_entries(expense_id) WHERE expense_id IS NOT NULL;
-- 233
CREATE INDEX IF NOT EXISTS journal_entries_supplier_invoice_id_idx ON journal_entries(supplier_invoice_id) WHERE supplier_invoice_id IS NOT NULL;
-- 234
CREATE TABLE IF NOT EXISTS voucher_approvals (id bigserial PRIMARY KEY);
-- 235
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 236
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS status varchar(255);
-- 237
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS note text;
-- 238
ALTER TABLE voucher_approvals ALTER COLUMN note TYPE text;
-- 239
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewer varchar(255);
-- 240
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewed_at timestamp;
-- 241
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 242
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 243
CREATE UNIQUE INDEX IF NOT EXISTS voucher_approvals_voucher_number_unique ON voucher_approvals(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 244
CREATE INDEX IF NOT EXISTS voucher_approvals_reviewed_at_idx ON voucher_approvals(reviewed_at);
-- 245
CREATE TABLE IF NOT EXISTS owner_transactions (id bigserial PRIMARY KEY);
-- 246
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_type varchar(64);
-- 247
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_date date;
-- 248
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 249
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS description varchar(512);
-- 250
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 251
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS debit_account varchar(64);
-- 252
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS credit_account varchar(64);
-- 253
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'draft';
-- 254
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 255
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 256
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 257
CREATE INDEX IF NOT EXISTS owner_transactions_date_idx ON owner_transactions(transaction_date);
-- 258
CREATE TABLE IF NOT EXISTS app_settings (id bigint PRIMARY KEY);
-- 259
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_name varchar(255);
-- 260
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_address varchar(1000);
-- 261
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_postal_code varchar(255);
-- 262
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_city varchar(255);
-- 263
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_organization_number varchar(255);
-- 264
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_registration_number varchar(255);
-- 265
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS contact_email varchar(255);
-- 266
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 267
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS default_ocr varchar(255);
-- 268
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 269
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'SOLE_TRADER';
-- 270
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_method varchar(255) DEFAULT 'INVOICE_METHOD';
-- 271
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_reporting_period varchar(255) DEFAULT 'QUARTERLY';
-- 272
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_start_month integer DEFAULT 1;
-- 273
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_end_month integer DEFAULT 12;
-- 274
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 275
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 276
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 277
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_email_template text;
-- 278
ALTER TABLE app_settings ALTER COLUMN invoice_email_template TYPE text;
-- 279
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS automatic_invoice_reminders_enabled boolean DEFAULT true;
-- 280
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_days_before_due integer DEFAULT 5;
-- 281
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_template text;
-- 282
ALTER TABLE app_settings ALTER COLUMN invoice_reminder_template TYPE text;
-- 283
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminders_enabled boolean DEFAULT true;
-- 284
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_days_after_due integer DEFAULT 3;
-- 285
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_template text;
-- 286
ALTER TABLE app_settings ALTER COLUMN overdue_invoice_reminder_template TYPE text;
-- 287
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_locked_through_date date;
-- 288
UPDATE customer_orders SET status = 'DRAFT' WHERE status IS NULL;
-- 289
UPDATE customer_orders SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 290
UPDATE customer_orders SET ordinary_price = net_amount WHERE ordinary_price IS NULL OR ordinary_price = 0;
-- 291
UPDATE customer_orders SET discount_amount = 0 WHERE discount_amount IS NULL;
-- 292
UPDATE customer_orders SET invoice_date = CURRENT_DATE WHERE invoice_date IS NULL;
-- 293
UPDATE customer_orders SET payment_terms_days = 30 WHERE payment_terms_days IS NULL OR payment_terms_days = 0;
-- 294
UPDATE customer_orders SET due_date = invoice_date + payment_terms_days WHERE due_date IS NULL;
-- 295
UPDATE customer_orders SET f_tax_approved = true WHERE f_tax_approved IS NULL;
-- 296
UPDATE customer_orders SET refunded_amount = 0 WHERE refunded_amount IS NULL;
-- 297
UPDATE customers SET archived = false WHERE archived IS NULL;
-- 298
UPDATE suppliers SET archived = false WHERE archived IS NULL;
-- 299
UPDATE supplier_invoices SET status = 'unpaid' WHERE status IS NULL OR status = '';
-- 300
UPDATE supplier_invoices SET net_amount = GREATEST(total_amount - vat_amount, 0) WHERE net_amount IS NULL OR net_amount = 0;
-- 301
UPDATE supplier_invoices SET paid_amount = 0 WHERE paid_amount IS NULL;
-- 302
UPDATE supplier_invoices SET paid_amount = total_amount WHERE (status = 'paid' OR paid_at IS NOT NULL) AND paid_amount = 0;
-- 303
UPDATE supplier_invoices SET total_amount_minor = CAST(total_amount AS bigint) * 100 WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 304
UPDATE supplier_invoices SET vat_amount_minor = CAST(vat_amount AS bigint) * 100 WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 305
UPDATE supplier_invoices SET net_amount_minor = CAST(net_amount AS bigint) * 100 WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 306
UPDATE supplier_invoices SET paid_amount_minor = CAST(paid_amount AS bigint) * 100 WHERE paid_amount_minor IS NULL AND paid_amount IS NOT NULL;
-- 307
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('supplier-invoice-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 308
UPDATE supplier_invoices SET payment_reference = '' WHERE payment_reference IS NULL;
-- 309
UPDATE supplier_invoices SET payment_history = '' WHERE payment_history IS NULL;
-- 310
UPDATE supplier_invoices SET self_billing = false WHERE self_billing IS NULL;
-- 311
UPDATE supplier_invoices SET buyer_name = '' WHERE buyer_name IS NULL;
-- 312
UPDATE supplier_invoices SET buyer_reference = '' WHERE buyer_reference IS NULL;
-- 313
UPDATE supplier_invoices SET approval_reference = '' WHERE approval_reference IS NULL;
-- 314
UPDATE supplier_invoices SET status = 'cancelled' WHERE cancellation_voucher_number IS NOT NULL AND cancellation_voucher_number <> '';
-- 315
UPDATE expenses SET net_amount_minor = CAST(net_amount AS bigint) * 100 WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 316
UPDATE expenses SET vat_amount_minor = CAST(vat_amount AS bigint) * 100 WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 317
UPDATE expenses SET total_amount_minor = CAST(total_amount AS bigint) * 100 WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 318
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('expense-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 319
UPDATE stripe_payouts SET gross_amount_minor = CAST(gross_amount AS bigint) * 100 WHERE gross_amount_minor IS NULL AND gross_amount IS NOT NULL;
-- 320
UPDATE stripe_payouts SET fee_amount_minor = CAST(fee_amount AS bigint) * 100 WHERE fee_amount_minor IS NULL AND fee_amount IS NOT NULL;
-- 321
UPDATE stripe_payouts SET net_amount_minor = CAST(net_amount AS bigint) * 100 WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 322
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('stripe-payout-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 323
UPDATE vat_filings SET output_vat_minor = CAST(output_vat AS bigint) * 100 WHERE output_vat_minor IS NULL AND output_vat IS NOT NULL;
-- 324
UPDATE vat_filings SET input_vat_minor = CAST(input_vat AS bigint) * 100 WHERE input_vat_minor IS NULL AND input_vat IS NOT NULL;
-- 325
UPDATE vat_filings SET vat_to_pay_minor = CAST(vat_to_pay AS bigint) * 100 WHERE vat_to_pay_minor IS NULL AND vat_to_pay IS NOT NULL;
-- 326
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('vat-filing-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 327
UPDATE card_purchases SET net_amount_minor = CAST(net_amount AS bigint) * 100 WHERE net_amount_minor IS NULL AND net_amount IS NOT NULL;
-- 328
UPDATE card_purchases SET vat_amount_minor = CAST(vat_amount AS bigint) * 100 WHERE vat_amount_minor IS NULL AND vat_amount IS NOT NULL;
-- 329
UPDATE card_purchases SET total_amount_minor = CAST(total_amount AS bigint) * 100 WHERE total_amount_minor IS NULL AND total_amount IS NOT NULL;
-- 330
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('card-purchase-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 331
UPDATE bank_reconciliation_entries SET amount_minor = CAST(amount AS bigint) * 100 WHERE amount_minor IS NULL AND amount IS NOT NULL;
-- 332
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('bank-reconciliation-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
-- 333
UPDATE recurring_contracts SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 334
UPDATE recurring_contracts SET contract_interval = 'monthly' WHERE contract_interval IS NULL OR contract_interval = '';
-- 335
UPDATE recurring_contracts SET next_invoice_date = CURRENT_DATE WHERE next_invoice_date IS NULL;
-- 336
UPDATE recurring_contracts SET active = true WHERE active IS NULL;
-- 337
UPDATE recurring_contracts SET archived = false WHERE archived IS NULL;
-- 338
UPDATE owner_transactions SET status = 'draft' WHERE status IS NULL OR status = '';
-- 339
UPDATE owner_transactions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
-- 340
UPDATE owner_transactions SET updated_at = created_at WHERE updated_at IS NULL;
-- 341
UPDATE owner_transactions SET status = 'booked' WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 342
UPDATE app_settings SET company_type = 'SOLE_TRADER' WHERE company_type IS NULL;
-- 343
UPDATE app_settings SET accounting_method = 'INVOICE_METHOD' WHERE accounting_method IS NULL OR accounting_method = '';
-- 344
UPDATE app_settings SET vat_reporting_period = 'QUARTERLY' WHERE vat_reporting_period IS NULL OR vat_reporting_period = '';
-- 345
UPDATE app_settings SET fiscal_year_start_month = 1 WHERE fiscal_year_start_month IS NULL OR fiscal_year_start_month < 1 OR fiscal_year_start_month > 12;
-- 346
UPDATE app_settings SET fiscal_year_end_month = 12 WHERE fiscal_year_end_month IS NULL OR fiscal_year_end_month < 1 OR fiscal_year_end_month > 12;
-- 347
UPDATE app_settings SET automatic_invoice_reminders_enabled = true WHERE automatic_invoice_reminders_enabled IS NULL;
-- 348
UPDATE app_settings SET invoice_email_template = 'Hej {kundnamn},

Bifogat finns faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_email_template IS NULL OR invoice_email_template = '';
-- 349
UPDATE app_settings SET invoice_reminder_days_before_due = 5 WHERE invoice_reminder_days_before_due IS NULL OR invoice_reminder_days_before_due = 0;
-- 350
UPDATE app_settings SET invoice_reminder_template = 'Hej {kundnamn},

Vi vill paminna om faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Kvar att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_reminder_template IS NULL OR invoice_reminder_template = '';
-- 351
UPDATE app_settings SET overdue_invoice_reminders_enabled = true WHERE overdue_invoice_reminders_enabled IS NULL;
-- 352
UPDATE app_settings SET overdue_invoice_reminder_days_after_due = 3 WHERE overdue_invoice_reminder_days_after_due IS NULL OR overdue_invoice_reminder_days_after_due = 0;
-- 353
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'BOTH';
-- 354
UPDATE accounts SET company_type = 'BOTH' WHERE company_type IS NULL;
-- 355
UPDATE journal_entries SET voucher_date = CURRENT_DATE WHERE voucher_date IS NULL;
-- 356
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS journal_entry_id bigint;
-- 357
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS document_snapshot text;
-- 358
CREATE TABLE IF NOT EXISTS invoice_originals (invoice_id bigint PRIMARY KEY REFERENCES customer_orders(id), pdf bytea NOT NULL, sha256 varchar(64) NOT NULL, archived_at timestamp with time zone NOT NULL);
-- 359
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoice_originals_invoice') THEN ALTER TABLE invoice_originals ADD CONSTRAINT fk_invoice_originals_invoice FOREIGN KEY (invoice_id) REFERENCES customer_orders(id); END IF; END $$;
-- 360
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_reconciliation_journal ON bank_reconciliation_entries (journal_entry_id);
-- 361
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_reconciliation_journal' AND conrelid = 'bank_reconciliation_entries'::regclass) THEN ALTER TABLE bank_reconciliation_entries ADD CONSTRAINT fk_bank_reconciliation_journal FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id); END IF; END $$;
-- 362
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS debit_minor bigint;
-- 363
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS credit_minor bigint;
-- 364
UPDATE journal_entries SET debit_minor = CAST(debit AS bigint) * 100 WHERE debit_minor IS NULL AND debit IS NOT NULL;
-- 365
UPDATE journal_entries SET credit_minor = CAST(credit AS bigint) * 100 WHERE credit_minor IS NULL AND credit IS NOT NULL;
-- 366
INSERT INTO money_migration_ledger (migration_key, source_unit, target_unit) VALUES ('journal-entry-shadow-v1', 'whole-krona', 'minor-unit') ON CONFLICT (migration_key) DO NOTHING;
