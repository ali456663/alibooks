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
-- 122
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS name varchar(255);
-- 123
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS email varchar(255);
-- 124
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS org_number varchar(255);
-- 125
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS phone varchar(255);
-- 126
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS payment_info varchar(512);
-- 127
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 128
CREATE INDEX IF NOT EXISTS suppliers_name_idx ON suppliers(name);
-- 129
CREATE TABLE IF NOT EXISTS supplier_invoices (id bigserial PRIMARY KEY);
-- 130
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_id bigint;
-- 131
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_name varchar(255);
-- 132
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_email varchar(255);
-- 133
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS supplier_org_number varchar(255);
-- 134
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS invoice_date date;
-- 135
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS due_date date;
-- 136
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS description varchar(512);
-- 137
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 138
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS total_amount integer DEFAULT 0;
-- 139
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS vat_amount integer DEFAULT 0;
-- 140
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS net_amount integer DEFAULT 0;
-- 141
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS category varchar(64);
-- 142
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'unpaid';
-- 143
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_at date;
-- 144
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS paid_amount integer DEFAULT 0;
-- 145
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_reference varchar(255);
-- 146
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS payment_history text;
-- 147
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancelled_at date;
-- 148
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS cancellation_voucher_number varchar(255);
-- 149
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS self_billing boolean DEFAULT false;
-- 150
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_name varchar(255);
-- 151
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS buyer_reference varchar(255);
-- 152
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS approval_reference varchar(255);
-- 153
ALTER TABLE supplier_invoices ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 154
CREATE INDEX IF NOT EXISTS supplier_invoices_supplier_id_idx ON supplier_invoices(supplier_id);
-- 155
CREATE INDEX IF NOT EXISTS supplier_invoices_due_date_idx ON supplier_invoices(due_date);
-- 156
CREATE TABLE IF NOT EXISTS recurring_contracts (id bigserial PRIMARY KEY);
-- 157
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_id bigint;
-- 158
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS customer_name varchar(255);
-- 155
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_id bigint;
-- 156
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS service_name varchar(255);
-- 157
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS quantity integer DEFAULT 1;
-- 158
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS contract_interval varchar(255);
-- 159
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS next_invoice_date date;
-- 160
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS active boolean DEFAULT true;
-- 161
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived boolean DEFAULT false;
-- 162
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS last_invoice_number varchar(255);
-- 163
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 164
ALTER TABLE recurring_contracts ADD COLUMN IF NOT EXISTS archived_at timestamp;
-- 165
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 166
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS voucher_date date DEFAULT CURRENT_DATE;
-- 167
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS correction_of_voucher_number varchar(255);
-- 168
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS expense_id bigint;
-- 169
ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS supplier_invoice_id bigint;
-- 170
CREATE INDEX IF NOT EXISTS journal_entries_voucher_number_idx ON journal_entries(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 171
CREATE INDEX IF NOT EXISTS journal_entries_expense_id_idx ON journal_entries(expense_id) WHERE expense_id IS NOT NULL;
-- 172
CREATE INDEX IF NOT EXISTS journal_entries_supplier_invoice_id_idx ON journal_entries(supplier_invoice_id) WHERE supplier_invoice_id IS NOT NULL;
-- 173
CREATE TABLE IF NOT EXISTS voucher_approvals (id bigserial PRIMARY KEY);
-- 174
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 175
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS status varchar(255);
-- 176
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS note text;
-- 177
ALTER TABLE voucher_approvals ALTER COLUMN note TYPE text;
-- 178
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewer varchar(255);
-- 179
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS reviewed_at timestamp;
-- 180
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 181
ALTER TABLE voucher_approvals ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 182
CREATE UNIQUE INDEX IF NOT EXISTS voucher_approvals_voucher_number_unique ON voucher_approvals(voucher_number) WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 183
CREATE INDEX IF NOT EXISTS voucher_approvals_reviewed_at_idx ON voucher_approvals(reviewed_at);
-- 184
CREATE TABLE IF NOT EXISTS owner_transactions (id bigserial PRIMARY KEY);
-- 185
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_type varchar(64);
-- 186
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS transaction_date date;
-- 187
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS amount integer DEFAULT 0;
-- 188
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS description varchar(512);
-- 189
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS reference varchar(255);
-- 190
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS debit_account varchar(64);
-- 191
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS credit_account varchar(64);
-- 192
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS status varchar(64) DEFAULT 'draft';
-- 193
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS voucher_number varchar(255);
-- 194
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS created_at timestamp;
-- 195
ALTER TABLE owner_transactions ADD COLUMN IF NOT EXISTS updated_at timestamp;
-- 196
CREATE INDEX IF NOT EXISTS owner_transactions_date_idx ON owner_transactions(transaction_date);
-- 197
CREATE TABLE IF NOT EXISTS app_settings (id bigint PRIMARY KEY);
-- 198
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_name varchar(255);
-- 199
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS contact_email varchar(255);
-- 200
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS plus_giro varchar(255);
-- 201
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS default_ocr varchar(255);
-- 202
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_recipient varchar(255);
-- 203
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'SOLE_TRADER';
-- 204
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_method varchar(255) DEFAULT 'INVOICE_METHOD';
-- 205
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_reporting_period varchar(255) DEFAULT 'QUARTERLY';
-- 206
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_start_month integer DEFAULT 1;
-- 207
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS fiscal_year_end_month integer DEFAULT 12;
-- 208
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS vat_percent integer DEFAULT 25;
-- 209
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS payment_terms_days integer DEFAULT 30;
-- 210
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS f_tax_approved boolean DEFAULT true;
-- 211
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_email_template text;
-- 212
ALTER TABLE app_settings ALTER COLUMN invoice_email_template TYPE text;
-- 213
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS automatic_invoice_reminders_enabled boolean DEFAULT true;
-- 214
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_days_before_due integer DEFAULT 5;
-- 215
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS invoice_reminder_template text;
-- 216
ALTER TABLE app_settings ALTER COLUMN invoice_reminder_template TYPE text;
-- 217
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminders_enabled boolean DEFAULT true;
-- 218
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_days_after_due integer DEFAULT 3;
-- 219
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS overdue_invoice_reminder_template text;
-- 220
ALTER TABLE app_settings ALTER COLUMN overdue_invoice_reminder_template TYPE text;
-- 221
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS accounting_locked_through_date date;
-- 222
UPDATE customer_orders SET status = 'DRAFT' WHERE status IS NULL;
-- 223
UPDATE customer_orders SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 224
UPDATE customer_orders SET ordinary_price = net_amount WHERE ordinary_price IS NULL OR ordinary_price = 0;
-- 225
UPDATE customer_orders SET discount_amount = 0 WHERE discount_amount IS NULL;
-- 226
UPDATE customer_orders SET invoice_date = CURRENT_DATE WHERE invoice_date IS NULL;
-- 227
UPDATE customer_orders SET payment_terms_days = 30 WHERE payment_terms_days IS NULL OR payment_terms_days = 0;
-- 228
UPDATE customer_orders SET due_date = invoice_date + payment_terms_days WHERE due_date IS NULL;
-- 229
UPDATE customer_orders SET f_tax_approved = true WHERE f_tax_approved IS NULL;
-- 230
UPDATE customer_orders SET refunded_amount = 0 WHERE refunded_amount IS NULL;
-- 231
UPDATE customers SET archived = false WHERE archived IS NULL;
-- 232
UPDATE suppliers SET archived = false WHERE archived IS NULL;
-- 233
UPDATE supplier_invoices SET status = 'unpaid' WHERE status IS NULL OR status = '';
-- 234
UPDATE supplier_invoices SET net_amount = GREATEST(total_amount - vat_amount, 0) WHERE net_amount IS NULL OR net_amount = 0;
-- 235
UPDATE supplier_invoices SET paid_amount = 0 WHERE paid_amount IS NULL;
-- 236
UPDATE supplier_invoices SET paid_amount = total_amount WHERE (status = 'paid' OR paid_at IS NOT NULL) AND paid_amount = 0;
-- 237
UPDATE supplier_invoices SET payment_reference = '' WHERE payment_reference IS NULL;
-- 238
UPDATE supplier_invoices SET payment_history = '' WHERE payment_history IS NULL;
-- 239
UPDATE supplier_invoices SET self_billing = false WHERE self_billing IS NULL;
-- 240
UPDATE supplier_invoices SET buyer_name = '' WHERE buyer_name IS NULL;
-- 241
UPDATE supplier_invoices SET buyer_reference = '' WHERE buyer_reference IS NULL;
-- 242
UPDATE supplier_invoices SET approval_reference = '' WHERE approval_reference IS NULL;
-- 243
UPDATE supplier_invoices SET status = 'cancelled' WHERE cancellation_voucher_number IS NOT NULL AND cancellation_voucher_number <> '';
-- 244
UPDATE recurring_contracts SET quantity = 1 WHERE quantity IS NULL OR quantity = 0;
-- 245
UPDATE recurring_contracts SET contract_interval = 'monthly' WHERE contract_interval IS NULL OR contract_interval = '';
-- 246
UPDATE recurring_contracts SET next_invoice_date = CURRENT_DATE WHERE next_invoice_date IS NULL;
-- 247
UPDATE recurring_contracts SET active = true WHERE active IS NULL;
-- 248
UPDATE recurring_contracts SET archived = false WHERE archived IS NULL;
-- 249
UPDATE owner_transactions SET status = 'draft' WHERE status IS NULL OR status = '';
-- 246
UPDATE owner_transactions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
-- 247
UPDATE owner_transactions SET updated_at = created_at WHERE updated_at IS NULL;
-- 248
UPDATE owner_transactions SET status = 'booked' WHERE voucher_number IS NOT NULL AND voucher_number <> '';
-- 249
UPDATE app_settings SET company_type = 'SOLE_TRADER' WHERE company_type IS NULL;
-- 250
UPDATE app_settings SET accounting_method = 'INVOICE_METHOD' WHERE accounting_method IS NULL OR accounting_method = '';
-- 251
UPDATE app_settings SET vat_reporting_period = 'QUARTERLY' WHERE vat_reporting_period IS NULL OR vat_reporting_period = '';
-- 252
UPDATE app_settings SET fiscal_year_start_month = 1 WHERE fiscal_year_start_month IS NULL OR fiscal_year_start_month < 1 OR fiscal_year_start_month > 12;
-- 253
UPDATE app_settings SET fiscal_year_end_month = 12 WHERE fiscal_year_end_month IS NULL OR fiscal_year_end_month < 1 OR fiscal_year_end_month > 12;
-- 254
UPDATE app_settings SET automatic_invoice_reminders_enabled = true WHERE automatic_invoice_reminders_enabled IS NULL;
-- 255
UPDATE app_settings SET invoice_email_template = 'Hej {kundnamn},

Bifogat finns faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_email_template IS NULL OR invoice_email_template = '';
-- 256
UPDATE app_settings SET invoice_reminder_days_before_due = 5 WHERE invoice_reminder_days_before_due IS NULL OR invoice_reminder_days_before_due = 0;
-- 257
UPDATE app_settings SET invoice_reminder_template = 'Hej {kundnamn},

Vi vill paminna om faktura {fakturanummer}.
Forfallodatum: {forfallodatum}.
Kvar att betala: {belopp} SEK.

Betalning kan goras till PlusGiro {plusgiro} med OCR {ocr}.
Betalningsmottagare: {betalningsmottagare}.

Vanliga halsningar,
{foretag}
{kontaktEpost}' WHERE invoice_reminder_template IS NULL OR invoice_reminder_template = '';
-- 258
UPDATE app_settings SET overdue_invoice_reminders_enabled = true WHERE overdue_invoice_reminders_enabled IS NULL;
-- 259
UPDATE app_settings SET overdue_invoice_reminder_days_after_due = 3 WHERE overdue_invoice_reminder_days_after_due IS NULL OR overdue_invoice_reminder_days_after_due = 0;
-- 260
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS company_type varchar(255) DEFAULT 'BOTH';
-- 261
UPDATE accounts SET company_type = 'BOTH' WHERE company_type IS NULL;
-- 262
UPDATE journal_entries SET voucher_date = CURRENT_DATE WHERE voucher_date IS NULL;
