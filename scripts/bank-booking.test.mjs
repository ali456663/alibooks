import test from "node:test";
import assert from "node:assert/strict";
import { identifyBankRows, bankRowPayload, bankPaymentPayload, bankRequest, uniqueBankInvoice } from "../frontend/src/lib/bank-booking.js";
import { parseBankCsv } from "../frontend/src/lib/bank-csv.js";

const sample = { date: "2026-09-01", amount: 125, description: "Payment", reference: "ref" };
const [row] = await identifyBankRows([sample]);
const invoice = { status: "SENT", invoiceNumber: "F-1", customerName: "Example", remainingAmount: 125 };
const match = (row, invoices) => uniqueBankInvoice(row, invoices, i => i.remainingAmount, i => i.invoiceNumber);
test("same amount alone does not select an arbitrary customer", () => {
  assert.equal(match(sample, [invoice]), null);
});
test("ambiguous references never choose the first invoice", () => {
  assert.equal(match({ ...sample, reference: "F-1 F-2" }, [invoice, { ...invoice, invoiceNumber: "F-2" }]), null);
});
test("explicit unique reference can suggest a partial payment", () => {
  assert.equal(match({ ...sample, reference: "F-1", amount: 50 }, [invoice]), invoice);
});
test("reference prefixes cannot match another invoice", () => {
  assert.equal(match({ ...sample, reference: "F-10" }, [invoice]), null);
  assert.equal(match({ ...sample, reference: "123456" }, [{ ...invoice, ocrNumber: "123" }]), null);
});
test("reference regex punctuation is treated literally", () => {
  assert.equal(match({ ...sample, reference: "F[1]" }, [{ ...invoice, invoiceNumber: "F[1]" }])?.invoiceNumber, "F[1]");
});
test("draft invoices and outgoing rows cannot receive suggestions", () => {
  assert.equal(match({ ...sample, reference: "F-1" }, [{ ...invoice, status: "DRAFT" }]), null);
  assert.equal(match({ ...sample, reference: "F-1", amount: -125 }, [invoice]), null);
});
test("customer and amount fallback must also be unique", () => {
  const row = { ...sample, description: "Example" };
  assert.equal(match(row, [invoice]), invoice);
  assert.equal(match(row, [invoice, { ...invoice, invoiceNumber: "F-2" }]), null);
});

test("same content has stable identity across imports", async () => {
  assert.equal((await identifyBankRows([sample]))[0].id, row.id);
});
test("same-day identical transactions retain distinct occurrences", async () => {
  const rows = await identifyBankRows([sample, sample]);
  assert.notEqual(rows[0].id, rows[1].id);
});
test("unrelated ordering does not change row identity", async () => {
  assert.equal((await identifyBankRows([{ ...sample, reference: "other" }, sample]))[1].id, row.id);
});
for (const field of ["date", "amount", "reference", "description"]) {
  test(`identity includes ${field}`, async () => {
    const changed = { ...sample, [field]: field === "amount" ? 126 : "changed" };
    assert.notEqual((await identifyBankRows([changed]))[0].id, row.id);
  });
}
test("full amount preserved, no overpayment clamp", () => {
  assert.equal(bankPaymentPayload(row, 200).paidAmount, 125);
  assert.throws(() => bankPaymentPayload(row, 100));
});
for (const amount of [-125, 0, 0.01, 2147483648]) {
  test(`invalid payment amount ${amount} rejected before request`, () => {
    assert.throws(() => bankPaymentPayload({ ...row, amount }, 500));
  });
}
test("old timestamp identities require fresh import", () => {
  assert.throws(() => bankRowPayload({ ...row, id: "bank-123456-0" }));
});
for (const date of ["", "2026-02-30", "2026-13-01", "01/09/2026"]) {
  test(`bank date ${date} is not replaced with today`, () => {
    assert.throws(() => parseBankCsv(`Date;Description;Amount\n${date};Test;125`));
  });
}
for (const status of [400, 401, 404, 409, 422, 500]) {
  test(`server ${status} cannot become locally successful history`, async () => {
    await assert.rejects(bankRequest("/bank", {}, {}, "POST", async () => new Response("{}", { status })));
  });
}
test("network failure propagates without local success", async () => {
  await assert.rejects(bankRequest("/bank", {}, {}, "POST", async () => { throw new Error("offline"); }));
});
test("malformed successful response cannot remove a bank row", async () => {
  for (const body of ["null", "{}", "<html>", '{"id":0}']) {
    await assert.rejects(bankRequest("/bank", {}, {}, "POST", async () => new Response(body)));
  }
});
test("atomic payload and authentication reach the new endpoint", async () => {
  const payload = bankPaymentPayload(row, 125);
  const result = await bankRequest("/bank-import/invoices/1/paid", { Authorization: "Bearer test" }, payload, "POST", async (url, options) => {
    assert.equal(url, "/bank-import/invoices/1/paid");
    assert.equal(options.headers.Authorization, "Bearer test");
    assert.deepEqual(JSON.parse(options.body), payload);
    return new Response('{"id":1}');
  });
  assert.equal(result.id, 1);
});
test("skipped restore checks server status before UI state changes", async () => {
  await assert.rejects(bankRequest("/bank", {}, null, "DELETE", async () => new Response(null, { status: 500 })));
  assert.equal(await bankRequest("/bank", {}, null, "DELETE", async () => new Response(null, { status: 204 })), null);
});
