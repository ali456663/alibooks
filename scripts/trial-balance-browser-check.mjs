import assert from "node:assert/strict";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// Synthetic data and authentication stay inside the smoke browser's temporary profile.
export async function checkTrialBalanceBrowser(send, evaluateJson, smokeUrl) {
  await send("Page.enable");
  const line = (number, debit, credit) => ({ accountNumber: number, accountName: "Test account",
    openingDebitMinor: 0, openingCreditMinor: 0, periodDebitMinor: debit, periodCreditMinor: credit,
    closingDebitMinor: debit, closingCreditMinor: credit });
  const report = { currencyCode: "SEK", minorUnitExponent: 2, periodFrom: "2026-07-01", periodTo: "2026-07-31",
    lines: [line("1930", 125050, 0), line("3041", 0, 125049)],
    openingDebitTotalMinor: 0, openingCreditTotalMinor: 0, periodDebitTotalMinor: 125050,
    periodCreditTotalMinor: 125049, closingDebitTotalMinor: 125050, closingCreditTotalMinor: 125049, differenceMinor: 1 };
  const init = await send("Page.addScriptToEvaluateOnNewDocument", { source: `
    window.__ALIBOOKS_CONFIG__ = { apiUrl: location.origin + '/__trial_fixture__' };
    localStorage.setItem('alibooks-token', 'fixture.' + btoa(JSON.stringify({ exp: Math.floor(Date.now()/1000)+3600 })) + '.not-a-real-signature');
    localStorage.setItem('alibooks-active-view', 'reports');
    localStorage.setItem('alibooks-language', 'sv');
    window.__trialReport = ${JSON.stringify(report)};
    window.__trialRequests = [];
    window.fetch = async (url) => {
      const route = new URL(String(url), location.href).pathname.replace('/__trial_fixture__', '');
      window.__trialRequests.push(route);
      let data = [];
      if (route === '/v2/trial-balance') {
        data = structuredClone(window.__trialReport);
        await new Promise(resolve => setTimeout(resolve, window.__trialDelay || 0));
      } else if (route === '/profit-and-loss') data = { totalRevenue: 0, totalExpenses: 0, result: 0, revenue: [], expenses: [] };
      else if (route === '/balance-report') data = { totalAssets: 0, totalLiabilitiesAndEquity: 0, difference: 0, assets: [], liabilitiesAndEquity: [] };
      else if (route === '/vat-report') data = { outputVat: 0, inputVat: 0, vatToPay: 0 };
      else if (route === '/v2/trial-balance/export') return new Response('Currency,MinorUnitExponent,PeriodDebitMinor\\nSEK,2,125050\\n');
      return new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } });
    };
  ` });
  const evaluate = expression => evaluateJson(send, `JSON.stringify(${expression})`);
  const waitFor = async (expression) => {
    for (let i = 0; i < 100; i++) {
      if (await evaluate(expression)) return;
      await sleep(100);
    }
    const state = await evaluate(`({ view: localStorage.getItem('alibooks-active-view'),
      requests: window.__trialRequests, error: sessionStorage.getItem('alibooks-last-render-error'),
      text: document.body.textContent.slice(0, 3000) })`);
    throw new Error(`Trial balance browser condition failed: ${expression}: ${JSON.stringify(state)}`);
  };
  const heading = `[...document.querySelectorAll('.report-subheading')].find(el => el.querySelector('h2')?.textContent === 'Saldobalans')`;
  const refresh = () => evaluate(`(() => { ${heading}.querySelector('button').click(); return true; })()`);
  try {
    await send("Page.navigate", { url: new URL("/", smokeUrl).href });
    await waitFor(`Boolean(document.querySelector('.trial-balance-table'))`);
    assert.equal(await evaluate(`Boolean(document.querySelector('.app-crash-fallback'))`), false);
    assert.match(await evaluate(`${heading}.textContent`), /Differens 0,01 SEK/);
    assert.equal(await evaluate(`Boolean(${heading}.querySelector('.warning-status'))`), true);
    assert.match(await evaluate(`document.querySelector('.trial-balance-table').textContent.replace(/\\s/g, '')`), /1250,50SEK/);
    await evaluate(`(() => { ${heading}.querySelectorAll('button')[1].click(); return true; })()`);
    await waitFor(`window.__trialRequests.includes('/v2/trial-balance/export')`);

    await evaluate(`(() => { window.__trialDelay=700; window.__trialReport.lines[0].accountName='Stale account'; return true; })()`);
    await refresh();
    await sleep(50);
    await evaluate(`(() => { window.__trialDelay=0; window.__trialReport.lines[0].accountName='Current account'; return true; })()`);
    await refresh();
    await waitFor(`document.querySelector('.trial-balance-table')?.textContent.includes('Current account')`);
    await sleep(800);
    assert.equal(await evaluate(`document.querySelector('.trial-balance-table').textContent.includes('Stale account')`), false);

    await evaluate(`(() => {
      const r = window.__trialReport;
      r.lines[0].periodDebitMinor = r.lines[0].closingDebitMinor = r.periodDebitTotalMinor = r.closingDebitTotalMinor = Number.MAX_SAFE_INTEGER;
      r.lines[1].periodCreditMinor = r.lines[1].closingCreditMinor = r.periodCreditTotalMinor = r.closingCreditTotalMinor = Number.MAX_SAFE_INTEGER - 1;
      return true;
    })()`);
    await refresh();
    await waitFor(`document.querySelector('.trial-balance-table')?.textContent.replace(/\\s/g, '').includes('90071992547409,91SEK')`);

    for (const [width, height] of [[1365, 900], [390, 844]]) {
      await send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: false });
      await evaluate(`(() => { ${heading}.scrollIntoView(); return true; })()`);
      await sleep(200);
      if (process.env.ALIBOOKS_SMOKE_ARTIFACT_DIR) {
        mkdirSync(process.env.ALIBOOKS_SMOKE_ARTIFACT_DIR, { recursive: true });
        const shot = await send("Page.captureScreenshot", { format: "png" });
        writeFileSync(path.join(process.env.ALIBOOKS_SMOKE_ARTIFACT_DIR, `trial-balance-${width}.png`), Buffer.from(shot.result.data, "base64"));
      }
      const overflow = await evaluate(`[...document.querySelectorAll('.trial-balance-table .account-row > span, .trial-balance-table .account-row > strong')].filter(el => el.scrollWidth > el.clientWidth + 1).map(el => ({ text: el.textContent, width: el.clientWidth, scroll: el.scrollWidth }))`);
      assert.deepEqual(overflow, [], `Overlapping trial balance cells at ${width}px`);
    }

    await evaluate(`(() => { window.__trialReport.currencyCode='EUR'; return true; })()`);
    await refresh();
    await waitFor(`!document.querySelector('.trial-balance-table') && document.body.textContent.includes('Kunde inte ladda saldobalans.')`);
    assert.equal(await evaluate(`Boolean(${heading}.querySelector('.success-status'))`), false);
    console.log("Trial balance browser check passed: ore, export route, stale responses, invalid data, desktop and mobile.");
  } finally {
    await send("Page.removeScriptToEvaluateOnNewDocument", { identifier: init.result.identifier });
    await send("Emulation.clearDeviceMetricsOverride");
  }
}
