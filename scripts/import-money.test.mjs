import test from "node:test";
import assert from "node:assert/strict";
import { parseWholeSekInput, parseSekMinor, wholeSekFromMinor, bankRowAmount, verifiedSieLines } from "../frontend/src/lib/import-money.js";
import { parseBankCsv } from "../frontend/src/lib/bank-csv.js";
import { analyzeSieText } from "../frontend/src/lib/sie-analysis.js";

for (const [input, expected] of [["0,01", 1n], ["-0.01", -1n], ["1 234,56 SEK", 123456n], ["1\u00a0234.50 kr", 123450n], ["+100", 10000n], ["2147483647.00", 214748364700n]]) {
  test(`exact minor units: ${input}`, () => assert.equal(parseSekMinor(input), expected));
}
for (const input of ["", "abc", "12foo34", "1e3", "1,234", "1.234,56", "1 23", "12.345", "Infinity", "2147483647.01", "--1", null]) {
  test(`reject ambiguous money: ${input}`, () => assert.throws(() => parseSekMinor(input)));
}
test("whole SEK form input never rounds decimals", () => {
  assert.equal(parseWholeSekInput("1250"), 1250);
  assert.equal(parseWholeSekInput(""), 0);
  assert.equal(parseWholeSekInput("1250,50"), null);
  assert.equal(parseWholeSekInput("1250.50"), null);
  assert.equal(parseWholeSekInput("2147483648"), null);
});
test("whole-SEK boundary refuses positive and negative ore", () => {
  assert.equal(wholeSekFromMinor(-10000n), -100);
  for (const amount of [1n, -1n, 123456n, -123456n]) assert.throws(() => wholeSekFromMinor(amount), /oren/);
});
test("explicit zero does not fall back to balance or other columns", () => {
  assert.equal(bankRowAmount(["0", "900", "800"], { amountIndex: 0, creditIndex: 1, debitIndex: 2 }), 0);
  assert.equal(parseBankCsv("Datum;Text;Belopp;Saldo\n2026-09-01;zero;0;900")[0].amount, 0);
});
test("separate credit/debit columns are exact and unambiguous", () => {
  const columns = { amountIndex: -1, creditIndex: 0, debitIndex: 1 };
  assert.equal(bankRowAmount(["", "125.00"], columns), -125);
  assert.equal(bankRowAmount(["100", ""], columns), 100);
  assert.throws(() => bankRowAmount(["100", "20"], columns));
  assert.throws(() => bankRowAmount(["", "-20"], columns));
});
for (const csv of [
  "Datum;Text;Saldo\n2026-09-01;payment;999",
  "Datum;Belopp;Amount\n2026-09-01;100;200",
  "Datum;Belopp\n2026-09-01;100;200",
  "Datum;Belopp\n2026-09-01;invalid",
  "Datum;Belopp\n2026-09-01;100\n2026-09-02;0,01",
  "Datum;Belopp\n2026-09-01;\"100",
  'Datum;Belopp\n2026-09-01;"1"00',
  'Datum;Belopp\n2026-09-01;1"00"',
]) {
  test(`bank file rejects entire invalid input: ${csv}`, () => assert.throws(() => parseBankCsv(csv)));
}
test("quoted decimal field accepted only when exactly whole SEK", () => {
  assert.equal(parseBankCsv('Date,Description,Amount\n2026-09-01,Sale,"125,00"')[0].amount, 125);
  assert.throws(() => parseBankCsv('Date,Description,Amount\n2026-09-01,Sale,"125,01"'), /Rad 2.*oren/);
});

const sie = (debit, credit, tag = "TRANS", date = "20260901") => `#VER "A" "1" ${date} "Test"\n{\n#${tag} 1930 {} ${debit}\n#TRANS 3000 {} ${credit}\n}`;
test("SIE preview preserves ore, serializes, but cannot book it", () => {
  const analysis = analyzeSieText(sie("100.01", "-100.01"));
  const voucher = JSON.parse(JSON.stringify(analysis)).previewVouchers[0];
  assert.equal(voucher.transactionsList[0].amountMinor, "10001");
  assert.equal(voucher.transactionsList[0].debit, 100.01);
  assert.equal(voucher.simpleManualImport, false);
  assert.equal(analysis.unbalancedVouchers, 0);
  assert.throws(() => verifiedSieLines(voucher), /oren/);
});
test("one ore imbalance cannot pass as balanced", () => {
  const analysis = analyzeSieText(sie("100.01", "-100"));
  assert.equal(analysis.unbalancedVouchers, 1);
  assert.equal(analysis.totalDifference, 0.01);
  assert.equal(analysis.previewVouchers[0].simpleManualImport, false);
});
test("whole-SEK SIE can be imported without repricing", () => {
  const voucher = analyzeSieText(sie("100.00", "-100.00")).previewVouchers[0];
  assert.equal(verifiedSieLines(voucher).amount, 100);
  assert.equal(voucher.simpleManualImport, true);
});
for (const amount of ["100junk", "100.001", "1e2", "invalid"]) {
  test(`SIE never parses a numeric prefix: ${amount}`, () => assert.throws(() => analyzeSieText(sie(amount, "-100"))));
}
test("legacy preview and modified cached amounts must not book", () => {
  const original = analyzeSieText(sie("100", "-100")).previewVouchers[0];
  for (const mutate of [v => delete v.moneyVersion, v => v.transactionsList[0].debit = 99, v => v.transactionsList[0].amountMinor = "9900", v => v.date = "2026-02-30"]) {
    const voucher = structuredClone(original);
    mutate(voucher);
    assert.throws(() => verifiedSieLines(voucher));
  }
});
test("alternate SIE transaction types require manual handling", () => {
  for (const tag of ["RTRANS", "BTRANS"]) {
    const voucher = analyzeSieText(sie("100", "-100", tag)).previewVouchers[0];
    assert.equal(voucher.simpleManualImport, false);
    assert.throws(() => verifiedSieLines(voucher));
  }
});
test("tab-separated SIE rows are not silently skipped", () => {
  const analysis = analyzeSieText(sie("100", "-100").replaceAll("#TRANS ", "#TRANS\t"));
  assert.equal(analysis.transactions, 2);
  assert.equal(verifiedSieLines(analysis.previewVouchers[0]).amount, 100);
  assert.throws(() => analyzeSieText(sie("100", "-100") + "\n#TRANS"));
});
test("bank descriptions preserve escaped quotes without altering money", () => {
  const row = parseBankCsv('Datum;Text;Belopp\n2026-09-01;"A ""B""";100')[0];
  assert.equal(row.description, 'A "B"');
  assert.equal(row.amount, 100);
});
