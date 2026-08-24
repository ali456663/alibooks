import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function exists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

const doc = read("docs/berakningskontroll.md");
const order = read("backend/src/main/java/se/cloudshop/order/Order.java");
const orderController = read("backend/src/main/java/se/cloudshop/order/OrderController.java");
const supplierInvoice = read("backend/src/main/java/se/cloudshop/supplier/SupplierInvoice.java");
const supplierController = read("backend/src/main/java/se/cloudshop/supplier/SupplierController.java");
const accounting = read("backend/src/main/java/se/cloudshop/accounting/AccountingService.java");
const stripe = read("backend/src/main/java/se/cloudshop/payment/StripePaymentService.java");
const frontend = read("frontend/src/main.jsx");
const styles = read("frontend/src/styles.css");
const orderTest = read("backend/src/test/java/se/cloudshop/order/OrderTest.java");
const orderControllerTest = read("backend/src/test/java/se/cloudshop/order/OrderControllerTest.java");
const accountingTest = read("backend/src/test/java/se/cloudshop/accounting/AccountingServiceTest.java");
const supplierTest = read("backend/src/test/java/se/cloudshop/supplier/SupplierControllerTest.java");
const stripeTest = read("backend/src/test/java/se/cloudshop/payment/StripePaymentServiceTest.java");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("scripts/mvp-evidence-check.mjs");
const useToday = read("scripts/use-today-check.mjs");
const releaseEvidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const frontendPackage = json("frontend/package.json");
const rootPackage = json("package.json");

check("Calculation document exists", exists("docs/berakningskontroll.md"), "docs/berakningskontroll.md should exist.");
check("Document states total formula", doc.includes("netto + moms"), "Invoice total must be net plus VAT.");
check("Document blocks mixed signs", includesAll(doc, ["positiva", "negativa", "Kreditfakturor"]), "Credit invoice signs should be explicit.");
check("Document covers partial payment rounding", includesAll(doc, ["Delbetalningar", "avrundas", "sista delbetalningen"]), "Partial payment VAT rounding should be explicit.");
check("Document covers voucher balance", includesAll(doc, ["Verifikat", "debet", "kredit"]), "Voucher balance should be explicit.");
check("Document covers period lock", doc.includes("Periodlasning"), "Period lock should be part of calculation safety.");
check("Document covers VAT accounts", includesAll(doc, ["2611", "2641", "2650"]), "VAT accounts should be named.");
check("Document covers Stripe accounts", includesAll(doc, ["1580", "3041", "6570"]), "Stripe flow should name receivable, sales and fee accounts.");
check("Document covers payroll limits", includesAll(doc, ["Lon ar MVP-arbetsunderlag", "bruttolon", "arbetsgivaravgift"]), "Payroll should be treated as work-paper MVP.");
check("Document lists stop signals", includesAll(doc, ["Stoppsignaler", "obalanserat", "dubbel bokforing", "kvar att betala"]), "Calculation stop signs should be visible.");
check("Order validates total equals net plus VAT", includesAll(order, ["setAmounts", "netAmount + vatAmount", "total must equal net amount plus VAT"]), "Order should reject inconsistent invoice amounts.");
check("Order rejects mixed signs", includesAll(order, ["hasPositive", "hasNegative", "must not mix positive and negative"]), "Order should reject mixed positive/negative invoice amounts.");
check("Order remaining blocks drafts and credits", includesAll(order, ["getRemainingAmount", '"DRAFT"', '"CREDITED"', "creditInvoice"]), "Drafts and credit invoices should not be payable.");
check("Order payment caps remaining amount", includesAll(accounting, ["paidAmount > invoice.getRemainingAmount()", "Paid amount cannot be greater than remaining amount"]) && orderController.includes("Paid amount cannot be greater than remaining amount"), "Customer payment should not exceed remaining amount.");
check("Supplier payment caps remaining amount", includesAll(accounting, ["paidAmount > supplierInvoice.getRemainingAmount()", "Supplier invoice payment amount cannot be greater than remaining amount"]) && supplierController.includes("Paid amount cannot be greater than remaining amount"), "Supplier payment should not exceed remaining amount.");
check("Invoice method books 1510/3041/2611", includesAll(accounting, ["createInvoiceEntries", '"1510"', '"3041"', '"2611"']), "Invoice method should book receivable, sale and output VAT.");
check("Cash method books VAT on payment", includesAll(accounting, ["createCashMethodPaymentEntries", "cashMethodVatForPayment", "paidAmount"]), "Cash method should book sale and VAT when paid.");
check("Partial payment VAT rounding exists", includesAll(accounting, ["cashMethodVatForPayment", "cashMethodVatForSupplierInvoicePayment", "Math.round"]), "Partial payments should calculate VAT proportionally with final rounding.");
check("Manual voucher balance validation exists", includesAll(accounting, ["Voucher must balance debit and credit", "cannot have negative debit or credit", "Opening balance must balance"]), "Manual vouchers and opening balances should be balanced.");
check("VAT report uses 2611 and 2641", includesAll(accounting, ["createVatReport", '"2611"', '"2641"', "outputVat - inputVat"]), "VAT report should use output minus input VAT.");
check("VAT settlement uses 2650", includesAll(accounting, ["createVatSettlement", '"2650"', "VAT settlement"]), "VAT settlement should move VAT through 2650.");
check("Critical VAT issues block settlement", includesAll(accounting, ["critical VAT control issues", "createVatControlReport", "createVoucherControlReport"]), "VAT settlement should be blocked by critical VAT/voucher issues.");
check("Voucher control finds unbalanced vouchers", includesAll(accounting, ["unbalanced_voucher", "Voucher does not balance", "balancedVoucherCount"]), "Voucher control should detect imbalance.");
check("SIE export blocks imbalance", includesAll(accounting, ["SIE export stopped", "do not balance"]), "Exports should not hide unbalanced bookkeeping.");
check("Account sign diagnosis covers key accounts", includesAll(accounting, ["AccountSignRule", '"1580"', '"2611"', '"2641"', "ovantat minussaldo"]), "Unexpected negative balances should be reported.");
check("Stripe website sale books VAT and receivable", includesAll(accounting, ["createStripeExternalSaleEntries", '"1580"', '"3041"', '"2611"', "totalAmount - netAmount"]), "Stripe sales should split gross into net and VAT.");
check("Stripe payout validates fee", includesAll(accounting, ["createStripePayoutEntry", '"1930"', '"1580"', '"6570"', "Stripe fee must be less than gross amount"]), "Stripe payout should validate bank, receivable and fee.");
check("Stripe webhook is idempotent and SEK-only", includesAll(stripe, ["stripeWebhookEventRepository", "Only SEK Stripe sales", "checkout.session.completed"]), "Stripe webhook should avoid duplicates and wrong currency.");
check("Frontend shows calculation control views", includesAll(frontend, ["Redovisningskontroll", "Resultat- och balansdiagnos", "Momsrapport", "Kontoplan"]), "The UI should expose calculation review views.");
check("Frontend uses tabular numbers", styles.includes("font-variant-numeric: tabular-nums"), "Financial numbers should align in the UI.");
check("Order tests cover amount formula and mixed signs", includesAll(orderTest, ["rejectsInvoiceAmountsWhenTotalDoesNotMatchNetPlusVat", "rejectsInvoiceAmountsThatMixPositiveAndNegativeValues"]), "Order tests should cover core amount validation.");
check("Order controller tests cover refunds", includesAll(orderControllerTest, ["refundInvoiceRequiresCreditedInvoice", "refundInvoiceRegistersRefundForCreditedInvoice"]), "Refund tests should protect credit/refund flow.");
check("Accounting tests cover invoice and cash method", includesAll(accountingTest, ["invoiceMethodBooksInvoiceWhenCreated", "cashMethodBooksSaleAndVatWhenInvoiceIsPaid", "cashMethodFinalPartialPaymentBooksRemainingVatRoundingDifference"]), "Accounting tests should cover invoice method, cash method and rounding.");
check("Accounting tests cover VAT controls", includesAll(accountingTest, ["vatControlWarnsWhenPurchaseIsBookedWithoutInputVat", "rejectsVatSettlementWhenCriticalVatControlIssuesRemain", "rejectsVatSettlementWhenCriticalVoucherControlIssuesRemain"]), "VAT control tests should block risky settlements.");
check("Accounting tests cover voucher controls", includesAll(accountingTest, ["createsVoucherControlReportWithGapAndUnbalancedVoucher", "rejectsManualMultiLineVoucherWithZeroLineBeforeSaving", "rejectsOpeningBalanceLineWithBothDebitAndCreditBeforeSaving"]), "Voucher tests should catch imbalance and invalid lines.");
check("Accounting tests cover Stripe calculations", includesAll(accountingTest, ["createsJournalEntriesForStripeWebsiteSale", "createsJournalEntriesForStripePayout", "rejectsStripePayoutWhenFeeWouldMakeBankLineZero"]), "Stripe accounting tests should protect 1580 and fee calculations.");
check("Supplier tests cover net/VAT/total and partials", includesAll(supplierTest, ["createSupplierInvoiceStoresNetVatAndTotal", "supplierInvoiceCanBePartlyPaid", "supplierInvoiceRejectsDuplicatePaymentReference"]), "Supplier tests should protect purchase VAT and partial payments.");
check("Stripe service tests block non-payable invoices", includesAll(stripeTest, ["checkoutRejectsCreditInvoice", "checkoutRejectsDraftInvoice", "checkoutRejectsFullyPaidInvoice"]), "Stripe checkout should reject non-payable invoices.");
check("Frontend exposes calculations check", frontendPackage.scripts?.["check:calculations"] === "node ../scripts/calculation-integrity-check.mjs", "frontend/package.json should expose npm run check:calculations.");
check("Root exposes calculations check", rootPackage.scripts?.["check:calculations"] === "npm --prefix frontend run check:calculations --", "package.json should expose npm run check:calculations.");
check("Release gate runs calculations check", releaseGate.includes('"check:calculations"'), "Release gate should fail if calculation integrity is removed.");
check("Readiness includes calculations check", readiness.includes("scripts/calculation-integrity-check.mjs") && readiness.includes("check:calculations"), "Readiness should require calculation integrity.");
check("Evidence includes calculations check", evidence.includes("scripts/calculation-integrity-check.mjs") && evidence.includes("check:calculations"), "Evidence should keep calculation proof fresh.");
check("Use-today includes calculations check", useToday.includes("check:calculations"), "Final local-use decision should include calculation integrity.");
check("Release evidence includes calculations proof", releaseEvidence.includes("check:calculations"), "Release evidence should mention calculation integrity.");
check("Roadmap includes calculations command", roadmap.includes("npm run check:calculations"), "Roadmap should expose calculation integrity command.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks calculation integrity check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} calculation integrity check(s) failed.`);
  process.exit(1);
}
