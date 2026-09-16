import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { mkdir } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const origin = new URL(process.env.SUBLEDGER_TEST_URL || "http://localhost:5157").origin;
const output = path.join(os.tmpdir(), "alibooks-subledger-ui");
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: "chrome" });
let releaseOld;
const oldResponse = new Promise(resolve => { releaseOld = resolve; });
try {
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
  const errors = [];
  page.on("pageerror", error => { errors.push(error.message); console.error(error.message); });
  let mode = "matched";
  let requests = 0;
  await page.route("**/*", async route => {
    const url = new URL(route.request().url());
    if (url.origin !== origin) return route.abort();
    if (url.pathname === "/__subledger-test.html") return route.fulfill({ contentType: "text/html", body: `
      <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1" /></head>
      <body style="margin:16px"><div id="fixture" class="orders-section"></div><script type="module">
      import RefreshRuntime from '/@react-refresh';
      RefreshRuntime.injectIntoGlobalHook(window);
      window.$RefreshReg$ = () => {};
      window.$RefreshSig$ = () => type => type;
      window.__vite_plugin_react_preamble_installed__ = true;
      </script><script type="module">
      import React from '/node_modules/.vite/deps/react.js';
      import ReactDOM from '/node_modules/.vite/deps/react-dom_client.js';
      import Component from '/src/components/ui/SubledgerControl.jsx';
      import '/src/styles.css';
      const root = ReactDOM.createRoot(document.getElementById('fixture'));
      window.mount = token => root.render(React.createElement(Component, {apiUrl: location.origin, token, language: 'sv'}));
      window.mount('isolated-test-token');
      </script></body></html>` });
    if (url.pathname !== "/subledger-control") return route.continue();
    requests++;
    assert.match(route.request().headers().authorization, /^Bearer (isolated|changed)-test-token$/);
    const currentMode = mode;
    if (currentMode === "delayed") await oldResponse;
    const report = {
      asOf: url.searchParams.get("asOf"), accountingMethod: "INVOICE_METHOD", status: "MATCHED",
      accounts: ["1510", "2440"].map(accountNumber => ({accountNumber, subledgerBalance: 75, ledgerBalance: 75,
        difference: 0, status: "MATCHED", unlinkedEntryCount: 0, undatedEntryCount: 0, differences: []}))
    };
    if (currentMode === "review") {
      report.status = report.accounts[0].status = "REVIEW_REQUIRED";
      report.accounts[0].differences = [
        {invoiceId: 1, subledgerBalance: 50, ledgerBalance: 25, difference: -25},
        {invoiceId: 2, subledgerBalance: 25, ledgerBalance: 50, difference: 25}
      ];
    }
    if (currentMode === "malformed") report.accounts[0].ledgerBalance = "75";
    await route.fulfill({ status: currentMode === "error" ? 422 : 200, contentType: "application/json",
      body: JSON.stringify(currentMode === "error" ? {message: "Historik saknas i testet"} : report) }).catch(error => {
        if (currentMode !== "delayed") throw error;
      });
  });
  await page.goto(`${origin}/__subledger-test.html`);
  await page.getByRole("table").waitFor();
  assert.equal(await page.getByRole("table").count(), 1);
  await page.screenshot({ path: path.join(output, "desktop.png"), fullPage: true });

  mode = "error";
  await page.getByRole("button", { name: "Kontrollera", exact: true }).click();
  await page.getByRole("alert").waitFor();
  assert.match(await page.getByRole("alert").innerText(), /Historik saknas/);
  assert.equal(await page.getByRole("table").count(), 0);

  mode = "malformed";
  await page.getByRole("button", { name: "Kontrollera", exact: true }).click();
  await page.getByText("Kunde inte verifiera reskontra mot huvudbok.", { exact: true }).waitFor();
  assert.equal(await page.getByRole("table").count(), 0);

  const before = requests;
  mode = "delayed";
  await page.getByRole("button", { name: "Kontrollera", exact: true }).click();
  for (let attempt = 0; requests === before && attempt < 100; attempt++) await page.waitForTimeout(20);
  assert.equal(requests, before + 1);
  mode = "review";
  await page.getByLabel("Saldodag", { exact: true }).fill("2026-01-02");
  await page.getByRole("table").waitFor();
  assert.match(await page.locator(".subledger-control").innerText(), /Faktura-ID 1/);
  releaseOld();
  await page.waitForTimeout(200);
  assert.match(await page.getByRole("status").innerText(), /Krav pa granskning/);
  await page.setViewportSize({ width: 375, height: 812 });
  await page.screenshot({ path: path.join(output, "mobile.png"), fullPage: true });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), true);

  mode = "error";
  await page.evaluate(() => window.mount("changed-test-token"));
  await page.getByRole("alert").waitFor();
  assert.equal(await page.getByRole("table").count(), 0);
  await page.getByLabel("Saldodag", { exact: true }).fill("");
  assert.equal(await page.getByRole("table").count(), 0);
  assert.deepEqual(errors, []);
  console.log(`Subledger UI passed: desktop/mobile, retry, HTTP errors, malformed JSON data, date races, session changes. Screenshots: ${output}`);
} finally {
  releaseOld();
  await browser.close();
}
