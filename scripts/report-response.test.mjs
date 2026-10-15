import assert from "node:assert/strict";
import test from "node:test";
import { readReportResponse } from "../frontend/src/lib/report-response.js";
import { readSubledgerResponse } from "../frontend/src/lib/subledger-response.js";
import { formatMinorAmount } from "../frontend/src/lib/minor-money.js";

const row = { accountNumber: "5420", accountName: "Cost", amount: 300 };
const profit = { totalRevenue: 0, totalExpenses: 300, result: -300, revenue: [], expenses: [row] };
const reply = (data, ok = true) => ({ ok, json: async () => data });
const fallback = "Report unavailable";

function minorTrial(amount = 125050) {
  return {
    currencyCode: "SEK", minorUnitExponent: 2,
    openingDebitTotalMinor: 0, openingCreditTotalMinor: 0,
    periodDebitTotalMinor: amount, periodCreditTotalMinor: 0,
    closingDebitTotalMinor: amount, closingCreditTotalMinor: 0, differenceMinor: amount,
    lines: [{ accountNumber: "1930", accountName: "Bank", openingDebitMinor: 0,
      openingCreditMinor: 0, periodDebitMinor: amount, periodCreditMinor: 0,
      closingDebitMinor: amount, closingCreditMinor: 0 }]
  };
}

for (const amount of [0, 1, 125050, 2147483648, Number.MAX_SAFE_INTEGER]) {
  test(`minor trial preserves exact amounts and differences: ${amount}`, async () => {
    const report = minorTrial(amount);
    assert.deepEqual(await readReportResponse(reply(report), "trialMinor", fallback), report);
  });
}

test("minor trial accepts a credit balance and a signed one-cent difference", async () => {
  const report = minorTrial(0);
  Object.assign(report.lines[0], { periodCreditMinor: 1, closingCreditMinor: 1 });
  Object.assign(report, { periodCreditTotalMinor: 1, closingCreditTotalMinor: 1, differenceMinor: -1 });
  assert.equal((await readReportResponse(reply(report), "trialMinor", fallback)).differenceMinor, -1);
});

const minorCorruptions = [
  report => { report.currencyCode = "EUR"; },
  report => { report.minorUnitExponent = 0; },
  report => { delete report.periodDebitTotalMinor; },
  report => { report.lines[0].periodDebitMinor = "125050"; },
  report => { report.lines[0].periodDebitMinor = 0.01; },
  report => { report.lines[0].periodDebitMinor = Number.MAX_SAFE_INTEGER + 1; },
  report => { report.lines[0].periodDebitMinor = -1; },
  report => { report.lines[0].accountNumber = " "; },
  report => { report.lines.push({ ...report.lines[0] }); },
  report => { report.lines[0].closingDebitMinor++; },
  report => { report.periodDebitTotalMinor++; },
  report => { report.differenceMinor++; },
  report => { report.lines[0].openingDebitMinor = report.lines[0].openingCreditMinor = 1; },
  report => { report.lines[0].closingDebitMinor++; report.lines[0].closingCreditMinor = 1; }
];
minorCorruptions.forEach((corrupt, index) => test(`minor trial rejects contradictory payload ${index}`, async () => {
  const report = minorTrial();
  corrupt(report);
  await assert.rejects(readReportResponse(reply(report), "trialMinor", fallback), { message: fallback });
}));

test("minor trial rejects totals outside the safe JSON range even when each line is safe", async () => {
  const report = minorTrial(Number.MAX_SAFE_INTEGER);
  report.lines.push({ ...minorTrial(1).lines[0], accountNumber: "1910" });
  await assert.rejects(readReportResponse(reply(report), "trialMinor", fallback), { message: fallback });
});

test("minor currency formatting never rounds through decimal floating-point division", () => {
  assert.equal(formatMinorAmount(1, "en"), "0.01 SEK");
  assert.equal(formatMinorAmount(-1, "en"), "-0.01 SEK");
  assert.equal(formatMinorAmount(125050, "en"), "1,250.50 SEK");
  assert.equal(formatMinorAmount(Number.MAX_SAFE_INTEGER, "en"), "90,071,992,547,409.91 SEK");
  assert.equal(formatMinorAmount(1, "sv"), "0,01 SEK");
  for (const invalid of [NaN, Infinity, 1.5, "100", null, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => formatMinorAmount(invalid));
  }
});

const reconciliation = {
  asOf: "2026-07-31", accountingMethod: "INVOICE_METHOD", status: "MATCHED",
  accounts: ["1510", "2440"].map(accountNumber => ({ accountNumber, subledgerBalance: 75, ledgerBalance: 75,
    difference: 0, status: "MATCHED", unlinkedEntryCount: 0, undatedEntryCount: 0, differences: [] }))
};

test("subledger response accepts exact account matches and explicit unsupported method", async () => {
  await readSubledgerResponse(reply(reconciliation), reconciliation.asOf, fallback);
  const unsupported = structuredClone(reconciliation);
  unsupported.accountingMethod = "CASH_METHOD";
  unsupported.status = "UNSUPPORTED_METHOD";
  unsupported.accounts.forEach(row => { row.status = "UNSUPPORTED_METHOD"; row.difference = null; });
  await readSubledgerResponse(reply(unsupported), reconciliation.asOf, fallback);
});

test("subledger response preserves opposite invoice differences with a matching total", async () => {
  const report = structuredClone(reconciliation);
  report.status = report.accounts[0].status = "REVIEW_REQUIRED";
  report.accounts[0].differences = [
    { invoiceId: 1, subledgerBalance: 50, ledgerBalance: 25, difference: -25 },
    { invoiceId: 2, subledgerBalance: 25, ledgerBalance: 50, difference: 25 }
  ];
  const parsed = await readSubledgerResponse(reply(report), reconciliation.asOf, fallback);
  assert.equal(parsed.accounts[0].difference, 0);
  assert.equal(parsed.status, "REVIEW_REQUIRED");
});

const corruptions = [
  report => { report.asOf = "2026-07-30"; },
  report => { report.accounts = []; },
  report => { report.accounts[1].accountNumber = "1510"; },
  report => { report.accounts[0].ledgerBalance = "75"; },
  report => { report.accounts[0].ledgerBalance = 75.5; },
  report => { report.accounts[0].ledgerBalance = 2147483648; },
  report => { report.accounts[0].difference = 20; },
  report => { report.accounts[0].difference = null; },
  report => { report.accounts[0].unlinkedEntryCount = -1; },
  report => { report.accounts[0].unlinkedEntryCount = 1; },
  report => { report.accounts[0].undatedEntryCount = 1; },
  report => { report.status = "REVIEW_REQUIRED"; },
  report => { report.accounts[0].differences = [{}]; },
  report => { report.accounts[0].status = "NO_DATA"; },
  report => { report.accountingMethod = "CASH_METHOD"; },
  report => { report.status = "all_good"; }
];
corruptions.forEach((corrupt, index) => test(`subledger response rejects contradictory successful payload ${index}`, async () => {
  const report = structuredClone(reconciliation);
  corrupt(report);
  await assert.rejects(readSubledgerResponse(reply(report), reconciliation.asOf, fallback), { message: fallback });
}));

test("subledger response rejects failed requests and unreadable JSON", async () => {
  await assert.rejects(readSubledgerResponse(reply({ message: "History incomplete" }, false), reconciliation.asOf, fallback), { message: "History incomplete" });
  await assert.rejects(readSubledgerResponse({ ok: true, json: async () => { throw new Error("bad JSON"); } }, reconciliation.asOf, fallback), { message: fallback });
});

test("preserves a real loss and valid empty reports", async () => {
  assert.equal((await readReportResponse(reply(profit), "profit", fallback)).result, -300);
  assert.equal((await readReportResponse(reply({ totalRevenue: 0, totalExpenses: 0, result: 0, revenue: [], expenses: [] }), "profit", fallback)).result, 0);
});

test("accepts valid balance, VAT and trial balance", async () => {
  await readReportResponse(reply({ totalAssets: 0, totalLiabilitiesAndEquity: 0, difference: 0, assets: [], liabilitiesAndEquity: [] }), "balance", fallback);
  await readReportResponse(reply({ outputVat: 0, inputVat: 300, vatToPay: -300 }), "vat", fallback);
  await readReportResponse(reply({ openingDebitTotal: 0, openingCreditTotal: 0, periodDebitTotal: 0, periodCreditTotal: 0, closingDebitTotal: 0, closingCreditTotal: 0, difference: 0, lines: [] }), "trial", fallback);
});

for (const data of [null, [], {}, { message: "Error payload with status 200" },
  { ...profit, revenue: null }, { ...profit, expenses: [{}] },
  { ...profit, result: "-300" }, { ...profit, result: 0.5 },
  { ...profit, result: Number.MAX_SAFE_INTEGER + 1 }, { ...profit, result: NaN },
  { ...profit, expenses: [{ ...row, accountName: {} }] }]) {
  test(`rejects malformed successful response ${JSON.stringify(data)}`, async () => {
    await assert.rejects(readReportResponse(reply(data), "profit", fallback), { message: fallback });
  });
}

for (const kind of ["profit", "balance", "vat", "trial"]) {
  test(`${kind} propagates explicit report limit without treating it as data`, async () => {
    await assert.rejects(readReportResponse(reply({ code: "REPORT_AMOUNT_LIMIT", message: "Rapporten har stoppats" }, false), kind, fallback), { message: "Rapporten har stoppats" });
  });
}

test("uses safe fallback when server error has no usable text", async () => {
  await assert.rejects(readReportResponse(reply({ message: {} }, false), "profit", fallback), { message: fallback });
});

for (const kind of ["profit", "balance", "vat", "trial", "trialMinor"]) {
  test(`${kind} stops on stored ledger corruption instead of returning report data`, async () => {
    const error = { code: "LEDGER_MONEY_INTEGRITY", message: "Bokforingsraden har oforenliga belopp eller valuta." };
    await assert.rejects(readReportResponse(reply(error, false), kind, fallback), { message: error.message });
  });
}

test("rejects unreadable JSON", async () => {
  await assert.rejects(readReportResponse({ ok: false, json: async () => { throw new SyntaxError("Invalid JSON"); } }, "profit", fallback));
});
