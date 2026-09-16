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
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 004
UPDATE products SET active = true WHERE active IS NULL;
-- 005
UPDATE products SET discount_price = 0 WHERE discount_price IS NULL;
-- 006
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 007
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 008
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 009
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 010
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ordinary_price integer DEFAULT 0;
-- 011
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_amount integer DEFAULT 0;
-- 012
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS discount_label varchar(255);
-- 013
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS status varchar(255) DEFAULT 'DRAFT';
-- 014
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS stripe_checkout_session_id varchar(255);
-- 015
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_number varchar(255);
-- 016
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS invoice_date date DEFAULT CURRENT_DATE;
-- 017
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS due_date date;
-- 018
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 019
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 020
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS ocr_number varchar(255);
-- 021
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 022
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 023
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_date date;
-- 024
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 025
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 026
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_date date;
-- 027
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refunded_amount integer DEFAULT 0;
-- 028
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS refund_reference varchar(255);
-- 029
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS reminder_sent_date date;
-- 030
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credit_invoice boolean DEFAULT false;
-- 031
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS credited_invoice_id bigint;
-- 032
CREATE INDEX IF NOT EXISTS customer_orders_invoice_number_idx ON customer_orders(invoice_number) WHERE invoice_number IS NOT NULL AND invoice_number <> '';
-- 033
CREATE INDEX IF NOT EXISTS customer_orders_ocr_number_idx ON customer_orders(ocr_number) WHERE ocr_number IS NOT NULL AND ocr_number <> '';
-- 034
CREATE TABLE IF NOT EXISTS invoice_payments (id bigserial PRIMARY KEY);
-- 035
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 036
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS payment_date date;
-- 037
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 038
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 039
ALTER TABLE invoice_payments ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 040
CREATE TABLE IF NOT EXISTS invoice_reminders (id bigserial PRIMARY KEY);
-- 041
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS invoice_id bigint;
-- 042
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 043
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS method varchar(255);
-- 044
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS status varchar(255);
-- 045
ALTER TABLE invoice_reminders ADD COLUMN IF NOT EXISTS recipient_email varchar(255);
-- 046
CREATE TABLE IF NOT EXISTS stripe_webhook_events (event_id varchar(255) PRIMARY KEY);
-- 047
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 048
ALTER TABLE stripe_webhook_events ADD COLUMN IF NOT EXISTS processed_at timestamp;
-- 049
CREATE TABLE IF NOT EXISTS stripe_payouts (id bigserial PRIMARY KEY);
-- 050
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS payout_date date;
-- 051
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS gross_amount integer DEFAULT 0;
-- 052
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS fee_amount integer DEFAULT 0;
-- 053
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 054
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 055
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 056
ALTER TABLE stripe_payouts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 057
CREATE UNIQUE INDEX IF NOT EXISTS stripe_payouts_reference_unique ON stripe_payouts(reference) WHERE reference IS NOT NULL AND reference <> '';
-- 058
CREATE TABLE IF NOT EXISTS bank_reconciliation_entries (id bigserial PRIMARY KEY);
-- 059
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_row_id varchar(255);
-- 060
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS bank_date date;
-- 061
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS description varchar(255);
-- 062
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 063
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 064
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS entry_type varchar(255);
-- 065
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS status varchar(255);
-- 066
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS match_label varchar(255);
-- 067
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS booked_at timestamp;
-- 068
CREATE TABLE IF NOT EXISTS vat_filings (id bigserial PRIMARY KEY);
-- 069
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_from date;
-- 070
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS period_to date;
-- 071
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS output_vat integer DEFAULT 0;
-- 072
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS input_vat integer DEFAULT 0;
-- 073
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS vat_to_pay integer DEFAULT 0;
-- 074
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS status varchar(255);
-- 075
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submission_reference varchar(255);
-- 076
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 077
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS note varchar(1000);
-- 078
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS submitted_at timestamp;
-- 079
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS paid_at timestamp;
-- 080
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 081
ALTER TABLE vat_filings ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 082
CREATE UNIQUE INDEX IF NOT EXISTS vat_filings_period_unique ON vat_filings(period_from, period_to);
-- 083
CREATE TABLE IF NOT EXISTS audit_events (id bigserial PRIMARY KEY);
-- 084
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_type varchar(255);
-- 085
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_type varchar(255);
-- 086
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS entity_id varchar(255);
-- 087
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS event_action varchar(255);
-- 088
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 089
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS message varchar(1000);
-- 090
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 091
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS actor_email varchar(255);
-- 092
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 093
CREATE INDEX IF NOT EXISTS audit_events_created_at_idx ON audit_events(created_at);
-- 094
CREATE INDEX IF NOT EXISTS audit_events_entity_idx ON audit_events(entity_type, entity_id);
-- 095
CREATE TABLE IF NOT EXISTS payroll_snapshots (id bigint PRIMARY KEY);
-- 096
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS content text;
-- 097
ALTER TABLE payroll_snapshots ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 098
CREATE TABLE IF NOT EXISTS payroll_snapshot_revisions (id bigserial PRIMARY KEY);
-- 099
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS content text;
-- 100
ALTER TABLE payroll_snapshot_revisions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 101
ALTER TABLE customers ADD COLUMN IF NOT EXISTS personal_number varchar(255);
-- 102
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address varchar(255);
-- 103
ALTER TABLE customers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 104
ALTER TABLE customers ADD COLUMN IF NOT EXISTS postal_code varchar(255);
-- 105
ALTER TABLE customers ADD COLUMN IF NOT EXISTS city varchar(255);
-- 106
ALTER TABLE customers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 107
CREATE TABLE IF NOT EXISTS expenses (id bigserial PRIMARY KEY);
-- 108
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS expense_date date;
-- 109
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS description varchar(255);
-- 110
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 111
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 112
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 113
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS category varchar(255);
-- 114
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS paid_from varchar(255);
-- 115
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_file_name varchar(255);
-- 116
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_content_type varchar(255);
-- 117
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_storage_path varchar(1000);
-- 118
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_sha256 varchar(64);
-- 119
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS receipt_uploaded_at timestamp;
-- 120
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 121
CREATE TABLE IF NOT EXISTS card_purchases (id bigserial PRIMARY KEY);
-- 122
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS purchase_date date;
-- 123
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS merchant_name varchar(255);
-- 124
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_holder varchar(255);
-- 125
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS card_last4 varchar(16);
-- 126
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 127
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 128
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 129
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 130
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS category varchar(255);
-- 131
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS clearing_account varchar(255);
-- 132
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'review';
-- 133
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS booked_expense_id bigint;
-- 134
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 135
ALTER TABLE card_purchases ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 136
CREATE INDEX IF NOT EXISTS card_purchases_purchase_date_idx ON card_purchases(purchase_date);
-- 137
CREATE TABLE IF NOT EXISTS suppliers (id bigserial PRIMARY KEY);
-- 138
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS name varchar(255);
-- 139
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS email varchar(255);
-- 140
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS org_number varchar(255);
-- 141
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 142
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS payment_info varchar(512);
-- 143
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 144
CREATE INDEX IF NOT EXISTS suppliers_name_idx ON suppliers(name);
-- 145
CREATE TABLE IF NOT EXISTS supplier_invoices (id bigserial PRIMARY KEY);
-- 146
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_id bigint;
-- 147
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_name varchar(255);
-- 148
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_email varchar(255);
-- 149
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_org_number varchar(255);
-- 150
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS invoice_date date;
-- 151
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS due_date date;
-- 152
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS description varchar(512);
-- 153
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 154
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 155
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 156
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 157
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS category varchar(64);
-- 158
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'unpaid';
-- 159
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_at date;
-- 160
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 161
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 162
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_history text;
-- 163
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancelled_at date;
-- 164
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancellation_voucher_number varchar(255);
-- 165
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS self_billing boolean DEFAULT false;
-- 166
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_name varchar(255);
-- 167
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_reference varchar(255);
-- 168
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS approval_reference varchar(255);
-- 169
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 170
CREATE INDEX IF NOT EXISTS supplier_invoices_supplier_id_idx ON supplier_invoices(supplier_id);
-- 171
CREATE INDEX IF NOT EXISTS supplier_invoices_due_date_idx ON supplier_invoices(due_date);
-- 172
CREATE TABLE IF NOT EXISTS recurring_contracts (id bigserial PRIMARY KEY);
-- 173
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_id bigint;
-- 174
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_name varchar(255);
-- 175
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_id bigint;
-- 176
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_name varchar(255);
-- 177
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 178
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS contract_interval varchar(255);
-- 179
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS next_invoice_date date;
-- 180
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 181
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 182
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS last_invoice_number varchar(255);
-- 183
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 184
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived_at timestamp;
-- 185
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 186
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_date date DEFAULT CURRENT_DATE;
-- 187
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS correction_of_voucher_number varchar(255);
-- 188
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS expense_id bigint;
-- 189
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 190
CREATE INDEX IF NOT EXISTS journal_entries_voucher_number_idx ON journal_entries(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 191
CREATE INDEX IF NOT EXISTS journal_entries_expense_id_idx ON journal_entries(expense_id) WHERE expense_id IS NOT NULL;
-- 192
CREATE INDEX IF NOT EXISTS journal_entries_supplier_invoice_id_idx ON journal_entries(supplier_invoice_id) WHERE supplier_invoice_id IS NOT NULL;
-- 193
CREATE TABLE IF NOT EXISTS voucher_approvals (id bigserial PRIMARY KEY);
-- 194
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 195
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS status varchar(255);
-- 196
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS note text;
-- 197
ALTER TABLE voucher_approvals ALTER COLUMN note TYPE text;
-- 198
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewer varchar(255);
-- 199
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewed_at timestamp;
-- 200
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 201
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 202
CREATE UNIQUE INDEX IF NOT EXISTS voucher_approvals_voucher_number_unique ON voucher_approvals(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 203
CREATE INDEX IF NOT EXISTS voucher_approvals_reviewed_at_idx ON voucher_approvals(reviewed_at);
-- 204
CREATE TABLE IF NOT EXISTS owner_transactions (id bigserial PRIMARY KEY);
-- 205
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_type varchar(64);
-- 206
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_date date;
-- 207
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 208
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS description varchar(512);
-- 209
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 210
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS debit_account varchar(64);
-- 211
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS credit_account varchar(64);
-- 212
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'draft';
-- 213
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 214
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 215
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 216
CREATE INDEX IF NOT EXISTS owner_transactions_date_idx ON owner_transactions(transaction_date);
-- 217
CREATE TABLE IF NOT EXISTS app_settings (id bigint PRIMARY KEY);
-- 218
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_name varchar(255);
-- 219
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS contact_email varchar(255);
-- 220
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 221
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS default_ocr varchar(255);
-- 222
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 223
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'SOLE_TRADER';
-- 224
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_method varchar(255) DEFAULT 'INVOICE_METHOD';
-- 225
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_reporting_period varchar(255) DEFAULT 'QUARTERLY';
-- 226
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_start_month integer DEFAULT 1;
-- 227
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_end_month integer DEFAULT 12;
-- 228
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 229
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 230
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 231
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_email_template text;
-- 232
ALTER TABLE app_settings ALTER COLUMN invoice_email_template TYPE text;
-- 233
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS automatic_invoice_reminders_enabled boolean DEFAULT true;
-- 234
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_days_before_due integer DEFAULT 5;
-- 235
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_template text;
-- 236
ALTER TABLE app_settings ALTER COLUMN invoice_reminder_template TYPE text;
-- 237
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminders_enabled boolean DEFAULT true;
-- 238
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_days_after_due integer DEFAULT 3;
-- 239
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_template text;
-- 240
ALTER TABLE app_settings ALTER COLUMN overdue_invoice_reminder_template TYPE text;
-- 241
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_locked_through_date date;
-- 242
UPDATE customer_orders SET status = 'DRAFT' WHERE status IS NULL;
-- 243
UPDATE customer_orders SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 244
UPDATE customer_orders SET ordinary_price = net_amount WHERE ordinary_price IS NULL OR ordinary_price = 0;
-- 245
UPDATE customer_orders SET discount_amount = 0 WHERE discount_amount IS NULL;
-- 246
UPDATE customer_orders SET invoice_date = CURRENT_DATE WHERE invoice_date IS NULL;
-- 247
UPDATE customer_orders SET payment_terms_days = 30 WHERE payment_terms_days IS NULL OR payment_terms_days = 0;
-- 248
UPDATE customer_orders SET due_date = invoice_date + payment_terms_days WHERE due_date IS NULL;
-- 249
UPDATE customer_orders SET f_tax_approved = true WHERE f_tax_approved IS NULL;
-- 250
UPDATE customer_orders SET refunded_amount = 0 WHERE refunded_amount IS NULL;
-- 251
UPDATE customers SET archived = false WHERE archived IS NULL;
-- 252
UPDATE suppliers SET archived = false WHERE archived IS NULL;
-- 253
UPDATE supplier_invoices SET status = 'unpaid' WHERE status IS NULL OR status = '';
-- 254
UPDATE supplier_invoices SET net_amount = GREATEST(total_amount - vat_amount, 0) WHERE net_amount IS NULL OR net_amount = 0;
-- 255
UPDATE supplier_invoices SET paid_amount = 0 WHERE paid_amount IS NULL;
-- 256
UPDATE supplier_invoices SET paid_amount = total_amount WHERE (status = 'paid' OR paid_at IS NOT NULL) AND paid_amount = 0;
-- 257
UPDATE supplier_invoices SET payment_reference = '' WHERE payment_reference IS NULL;
-- 258
UPDATE supplier_invoices SET payment_history = '' WHERE payment_history IS NULL;
-- 259
UPDATE supplier_invoices SET self_billing = false WHERE self_billing IS NULL;
-- 260
UPDATE supplier_invoices SET buyer_name = '' WHERE buyer_name IS NULL;
-- 261
UPDATE supplier_invoices SET buyer_reference = '' WHERE buyer_reference IS NULL;
-- 262
UPDATE supplier_invoices SET approval_reference = '' WHERE approval_reference IS NULL;
-- 263
UPDATE supplier_invoices SET status = 'cancelled' WHERE cancellation_voucher_number IS NOT NULL AND cancellation_voucher_number <> '';
-- 264
UPDATE recurring_contracts SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 265
UPDATE recurring_contracts SET contract_interval = 'monthly' WHERE contract_interval IS NULL OR contract_interval = '';
-- 266
UPDATE recurring_contracts SET next_invoice_date = CURRENT_DATE WHERE next_invoice_date IS NULL;
-- 267
UPDATE recurring_contracts SET active = true WHERE active IS NULL;
-- 268
UPDATE recurring_contracts SET archived = false WHERE archived IS NULL;
-- 269
UPDATE owner_transactions SET status = 'draft' WHERE status IS NULL OR status = '';
-- 270
UPDATE owner_transactions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
-- 271
UPDATE owner_transactions SET updated_at = created_at WHERE updated_at IS NULL;
-- 272
UPDATE owner_transactions SET status = 'booked' WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 273
UPDATE app_settings SET company_type = 'SOLE_TRADER' WHERE company_type IS NULL;
-- 274
UPDATE app_settings SET accounting_method = 'INVOICE_METHOD' WHERE accounting_method IS NULL OR accounting_method = '';
-- 275
UPDATE app_settings SET vat_reporting_period = 'QUARTERLY' WHERE vat_reporting_period IS NULL OR vat_reporting_period = '';
-- 276
UPDATE app_settings SET fiscal_year_start_month = 1 WHERE fiscal_year_start_month IS NULL OR fiscal_year_start_month < 1 OR fiscal_year_start_month > 12;
-- 277
UPDATE app_settings SET fiscal_year_end_month = 12 WHERE fiscal_year_end_month IS NULL OR fiscal_year_end_month < 1 OR fiscal_year_end_month > 12;
-- 278
UPDATE app_settings SET automatic_invoice_reminders_enabled = true WHERE automatic_invoice_reminders_enabled IS NULL;
-- 279
UPDATE app_settings SET invoice_email_template = 'Hej {kundnamn},

Bifogat finns faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_email_template IS NULL OR invoice_email_template = '';
-- 280
UPDATE app_settings SET invoice_reminder_days_before_due = 5 WHERE invoice_reminder_days_before_due IS NULL OR invoice_reminder_days_before_due = 0;
-- 281
UPDATE app_settings SET invoice_reminder_template = 'Hej {kundnamn},

Vi vill paminna om faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Kvar att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_reminder_template IS NULL OR invoice_reminder_template = '';
-- 282
UPDATE app_settings SET overdue_invoice_reminders_enabled = true WHERE overdue_invoice_reminders_enabled IS NULL;
-- 283
UPDATE app_settings SET overdue_invoice_reminder_days_after_due = 3 WHERE overdue_invoice_reminder_days_after_due IS NULL OR overdue_invoice_reminder_days_after_due = 0;
-- 284
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'BOTH';
-- 285
UPDATE accounts SET company_type = 'BOTH' WHERE company_type IS NULL;
-- 286
UPDATE journal_entries SET voucher_date = CURRENT_DATE WHERE voucher_date IS NULL;
-- 287
ALTER TABLE bank_reconciliation_entries ADD COLUMN IF NOT EXISTS journal_entry_id bigint;
-- 288
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS document_snapshot text;
-- 289
CREATE TABLE IF NOT EXISTS invoice_originals (invoice_id bigint PRIMARY KEY REFERENCES customer_orders(id), pdf bytea NOT NULL, sha256 varchar(64) NOT NULL, archived_at timestamp with time zone NOT NULL);
-- 290
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoice_originals_invoice') THEN ALTER TABLE invoice_originals ADD CONSTRAINT fk_invoice_originals_invoice FOREIGN KEY (invoice_id) REFERENCES customer_orders(id); END IF; END $$;
-- 291
CREATE UNIQUE INDEX IF NOT EXISTS uk_bank_reconciliation_journal ON bank_reconciliation_entries (journal_entry_id);
-- 292
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_bank_reconciliation_journal' AND conrelid = 'bank_reconciliation_entries'::regclass) THEN ALTER TABLE bank_reconciliation_entries ADD CONSTRAINT fk_bank_reconciliation_journal FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id); END IF; END $$;
