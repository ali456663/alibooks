import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { mkdir, readFile } from "node:fs/promises";
import path from "node:path";
import os from "node:os";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const origin = "http://localhost:5157";
const output = path.join(os.tmpdir(), "alibooks-bank-ui");
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: "chrome" });
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const errors = [];
  page.on("pageerror", e => errors.push(e.message));
  let mode = "failure";
  let writes = 0;
  let linkWrites = 0;
  let linkFailure = true;
  const history = [];
  const invoice = { id: 1, invoiceNumber: "F-TEST-0001", customerName: "Test customer", status: "SENT", totalAmount: 125,
    netAmount: 100, vatAmount: 25, paidAmount: 0, remainingAmount: 125, invoiceDate: "2026-09-01", dueDate: "2026-09-30",
    payments: [], product: { name: "Test service", price: 100 }, quantity: 1 };
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ sub: "test@example.invalid", exp: Math.floor(Date.now() / 1000) + 3600 }));
    localStorage.setItem("alibooks-token", `e30.${payload}.test`);
    localStorage.setItem("alibooks-email", "test@example.invalid");
    localStorage.setItem("alibooks-language", "sv");
    localStorage.setItem("alibooks-active-view", "payments");
    window.__ALIBOOKS_CONFIG__ = { apiUrl: "http://bank-test.invalid" };
  });
  await page.route("**/*", async route => {
    const url = new URL(route.request().url());
    if (url.pathname === "/config.js") return route.fulfill({ contentType: "application/javascript", body: 'window.__ALIBOOKS_CONFIG__={apiUrl:"http://bank-test.invalid"};' });
    if (url.hostname === "bank-test.invalid" || url.port === "3000") {
      const headers = { "access-control-allow-origin": origin, "access-control-allow-headers": "*", "access-control-allow-methods": "GET,POST,DELETE,OPTIONS" };
      if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers });
      let body = [];
      if (url.pathname === "/invoices" || url.pathname === "/orders") body = [invoice];
      if (url.pathname === "/settings") body = { companyName: "Isolated test", accountingMethod: "INVOICE_METHOD", paymentTermsDays: 30 };
      if (url.pathname === "/bank-reconciliations") body = history;
      if (url.pathname === "/bank-reconciliations/1/journal-candidates") body = [
        { id: 42, voucherNumber: "B-TEST-42", date: "2026-09-02", amount: 50, description: "Previously booked payment" }
      ];
      if (url.pathname === "/bank-reconciliations/1/journal-link") {
        linkWrites++;
        assert.equal(route.request().postDataJSON().journalEntryId, 42);
        if (linkFailure) return route.fulfill({ status: 409, headers, json: {} });
        history[0].journalEntryId = 42;
        body = history[0];
      }
      if (url.pathname === "/bank-import/invoices/1/paid") {
        writes++;
        const request = route.request().postDataJSON();
        assert.equal(request.paidAmount, request.bankRow.amount);
        assert.match(request.bankRow.bankRowId, /^bank-v1-/);
        if (mode === "failure") return route.fulfill({ status: 500, headers, json: { message: "Test failure" } });
        if (mode === "conflict") return route.fulfill({ status: 409, headers, json: {} });
        invoice.paidAmount = 50;
        invoice.remainingAmount = 75;
        invoice.status = "PARTIALLY_PAID";
        history.push({ id: 1, ...request.bankRow, type: "invoice_payment", status: "booked", bookedAt: new Date().toISOString(), matchLabel: "Invoice 1" });
        body = invoice;
      }
      return route.fulfill({ headers, json: body });
    }
    if (url.origin !== origin) return route.abort();
    return route.continue();
  });
  await page.goto(origin);
  const upload = page.locator('.bank-import-panel input[type="file"]');
  await upload.waitFor({ state: "attached", timeout: 45000 });
  const csv = "Date;Description;Reference;Amount\n2026-09-02;Test payment;F-TEST-0001;50";
  const importFile = async text => {
    await upload.setInputFiles({ name: "bank.csv", mimeType: "text/csv", buffer: Buffer.from(text) });
    await page.locator(".bank-import-row").waitFor();
  };
  const pay = () => page.locator(".bank-import-row").getByRole("button", { name: "Registrera betalning", exact: true }).click();
  await importFile(csv);
  await pay();
  await page.getByText(/Bankanropet misslyckades \(500\)/).waitFor();
  assert.equal(await page.locator(".bank-import-row").count(), 1);
  assert.equal(history.length, 0);
  await page.locator(".bank-import-row").scrollIntoViewIfNeeded();
  await page.screenshot({ path: path.join(output, "desktop-server-error.png") });
  mode = "success";
  await pay();
  await page.locator(".bank-import-row").waitFor({ state: "detached" });
  assert.equal(history.length, 1);
  await importFile(csv);
  mode = "conflict";
  await pay();
  await page.getByText(/Bankraden finns redan/).waitFor();
  assert.equal(await page.locator(".bank-import-row").count(), 1);
  await importFile(csv.replace(";50", ";100"));
  const before = writes;
  await pay();
  await page.getByText(/Overskott kraver separat avstamning/).waitFor();
  assert.equal(writes, before);
  const link = page.locator(".bank-journal-link");
  await link.getByRole("button", { name: "Granska koppling", exact: true }).click();
  const confirm = link.getByRole("button", { name: "Bekrafta koppling", exact: true });
  await confirm.waitFor();
  assert.equal(await confirm.isDisabled(), true);
  assert.equal(linkWrites, 0);
  await link.getByRole("combobox").selectOption("42");
  await confirm.click();
  await link.getByRole("alert").waitFor();
  assert.equal(history[0].journalEntryId, undefined);
  assert.equal(writes, before);
  await page.setViewportSize({ width: 390, height: 844 });
  await link.scrollIntoViewIfNeeded();
  const linkBounds = await link.boundingBox();
  assert.ok(linkBounds.x >= 0 && linkBounds.x + linkBounds.width <= 391, JSON.stringify(linkBounds));
  const historyBounds = await page.locator(".bank-reconciliation-row").boundingBox();
  assert.ok(historyBounds.x >= 0 && historyBounds.x + historyBounds.width <= 391, JSON.stringify(historyBounds));
  assert.ok(linkBounds.x + linkBounds.width <= historyBounds.x + historyBounds.width - 8, "Link control must fit inside history row");
  await page.screenshot({ path: path.join(output, "mobile-journal-link.png") });
  linkFailure = false;
  await confirm.click();
  await page.getByText("Bokforingsrad: #42", { exact: true }).waitFor();
  assert.equal(linkWrites, 2);
  assert.equal(writes, before);
  const downloadEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportera avstamning", exact: true }).click();
  const download = await downloadEvent;
  const exported = await readFile(await download.path(), "utf8");
  assert.ok(exported.includes("Bankrad-ID") && exported.includes("Bokforingsrad-ID"));
  assert.ok(exported.includes(history[0].bankRowId));
  assert.match(exported, /42/);
  await page.locator(".bank-import-row").scrollIntoViewIfNeeded();
  const bounds = await page.locator(".bank-import-row button, .bank-import-row, .topbar").evaluateAll(elements => elements.map(el => {
    const rect = el.getBoundingClientRect();
    return { left: rect.left, right: rect.right, width: rect.width };
  }));
  assert.ok(bounds.every(rect => rect.left >= -1 && rect.right <= 391 && rect.width <= 391), JSON.stringify(bounds));
  await page.screenshot({ path: path.join(output, "mobile-overpayment.png") });
  assert.equal(await page.locator(".app-crash-fallback").count(), 0);
  assert.deepEqual(errors, []);
  console.log("PASS: bank UI retains failed/conflicting rows, rejects overpayment and explicitly links legacy rows without new payment writes; desktop/mobile screenshots in " + output);
} finally {
  await browser.close();
}
