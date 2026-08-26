import { spawn } from "node:child_process";
import { existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const frontendDir = path.join(repoRoot, "frontend");
const smokeUrl = process.env.ALIBOOKS_SMOKE_URL || "http://127.0.0.1:5157/?reset=1";
const debugPort = Number(process.env.ALIBOOKS_SMOKE_DEBUG_PORT || 9333);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function npmCommand() {
  return process.platform === "win32" ? "npm.cmd" : "npm";
}

function chromeCandidates() {
  return [
    process.env.CHROME_PATH,
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
    "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
  ]
    .filter(Boolean)
    .map((candidate) => String(candidate).trim().replace(/^["']|["']$/g, ""));
}

function findChrome() {
  return chromeCandidates().find((candidate) => existsSync(candidate));
}

async function urlResponds(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(2500) });
    return response.ok;
  } catch {
    return false;
  }
}

async function waitForUrl(url, timeoutMs = 25000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await urlResponds(url)) {
      return true;
    }
    await sleep(750);
  }
  return false;
}

async function connectToAliBooksPage() {
  const targets = await fetch(`http://127.0.0.1:${debugPort}/json`, {
    signal: AbortSignal.timeout(5000)
  }).then((response) => response.json());

  const target = targets.find((item) => (
    item.type === "page"
    && (item.url.startsWith("http://127.0.0.1:5157") || item.url.startsWith("http://localhost:5157"))
  ));

  if (!target?.webSocketDebuggerUrl) {
    throw new Error("Could not find the AliBooks browser target.");
  }

  const ws = new WebSocket(target.webSocketDebuggerUrl);
  let nextId = 1;
  const pending = new Map();

  ws.addEventListener("message", (event) => {
    const data = JSON.parse(event.data);
    if (data.id && pending.has(data.id)) {
      pending.get(data.id)(data);
      pending.delete(data.id);
    }
  });

  await new Promise((resolve, reject) => {
    ws.addEventListener("open", resolve, { once: true });
    ws.addEventListener("error", reject, { once: true });
  });

  const send = (method, params = {}) => new Promise((resolve) => {
    const id = nextId++;
    pending.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params }));
  });

  return { ws, send };
}

async function evaluateJson(send, expression) {
  const result = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.result?.exceptionDetails) {
    throw new Error(`Browser evaluation failed: ${result.result.exceptionDetails.text || "unknown error"}`);
  }

  return JSON.parse(result.result?.result?.value || "{}");
}

async function main() {
  let devServer = null;
  let chrome = null;

  try {
    if (!(await urlResponds(smokeUrl))) {
      devServer = spawn(npmCommand(), ["run", "dev"], {
        cwd: frontendDir,
        env: { ...process.env, BROWSER: "none" },
        stdio: ["ignore", "pipe", "pipe"]
      });

      devServer.stdout.on("data", (chunk) => process.stdout.write(chunk));
      devServer.stderr.on("data", (chunk) => process.stderr.write(chunk));

      if (!(await waitForUrl(smokeUrl))) {
        throw new Error(`Vite did not respond at ${smokeUrl}`);
      }
    }

    const chromePath = findChrome();
    if (!chromePath) {
      throw new Error("Chrome/Chromium was not found. Set CHROME_PATH to run the frontend smoke test.");
    }

    const profile = mkdtempSync(path.join(tmpdir(), "alibooks-smoke-"));
    chrome = spawn(chromePath, [
      "--headless=new",
      "--disable-gpu",
      "--disable-gpu-compositing",
      "--disable-software-rasterizer",
      "--disable-webgl",
      "--disable-vulkan",
      "--disable-dev-shm-usage",
      "--no-sandbox",
      "--no-first-run",
      "--disable-extensions",
      `--remote-debugging-port=${debugPort}`,
      `--user-data-dir=${profile}`,
      smokeUrl
    ], { stdio: "ignore" });

    await sleep(5000);
    const { ws, send } = await connectToAliBooksPage();
    await send("Runtime.enable");
    await sleep(1500);

    const expression = `JSON.stringify({
      title: document.title,
      text: document.body.innerText.slice(0, 1200),
      hasCrashFallback: Boolean(document.querySelector('.app-crash-fallback')) || document.body.innerText.includes('kunde inte visa sidan'),
      hasAliBooks: document.body.innerText.includes('AliBooks'),
      hasLogin: document.body.innerText.includes('Logga in') || document.body.innerText.includes('Login'),
      hasAuthEntry: Boolean(document.querySelector('.auth-entry')),
      authFormInlineCount: document.querySelectorAll('.auth-form-inline').length,
      hasInternalCustomerNav: [...document.querySelectorAll('button')].some((button) => ['Kunder', 'Customers'].includes(button.textContent.trim())),
      activeView: localStorage.getItem('alibooks-active-view'),
      hasRenderRecoveryAttempt: localStorage.getItem('alibooks-render-recovery-attempted') === 'true',
      lastRenderError: sessionStorage.getItem('alibooks-last-render-error'),
      bodyTextLength: document.body.innerText.trim().length,
      rootChildCount: document.getElementById('root')?.childElementCount || 0,
      viewportWidth: document.documentElement.clientWidth,
      viewportHeight: document.documentElement.clientHeight,
      scrollHeight: document.documentElement.scrollHeight
    })`;
    const value = await evaluateJson(send, expression);
    if (value.hasCrashFallback) {
      throw new Error(`AliBooks render crash: ${value.lastRenderError || value.text}`);
    }
    if (value.lastRenderError || value.hasRenderRecoveryAttempt) {
      throw new Error(`AliBooks render recovery was triggered during smoke test: ${value.lastRenderError || "-"}`);
    }
    if (value.activeView !== "overview") {
      throw new Error(`AliBooks reset did not land on overview. Active view: ${value.activeView || "-"}`);
    }
    if (!value.hasAliBooks || !value.hasLogin) {
      throw new Error(`AliBooks did not render the expected shell. Text: ${value.text || "-"}`);
    }
    if (!value.hasAuthEntry || value.authFormInlineCount !== 0) {
      throw new Error(`AliBooks logged-out overview should show compact auth buttons with a closed form: ${JSON.stringify(value)}`);
    }
    if (value.hasInternalCustomerNav) {
      throw new Error(`AliBooks logged-out overview should not expose internal navigation before login: ${JSON.stringify(value)}`);
    }
    if (value.bodyTextLength < 80 || value.rootChildCount < 1 || value.viewportWidth < 320 || value.viewportHeight < 300 || value.scrollHeight < 300) {
      throw new Error(`AliBooks rendered an unexpectedly small or blank page: ${JSON.stringify(value)}`);
    }

    await evaluateJson(send, `JSON.stringify((() => {
      localStorage.removeItem('alibooks-token');
      localStorage.removeItem('alibooks-email');
      localStorage.setItem('alibooks-active-view', 'customers');
      location.reload();
      return { seededSavedView: 'customers' };
    })())`);
    await sleep(2000);

    const loggedOutSavedViewRecovery = await evaluateJson(send, `JSON.stringify({
      activeView: localStorage.getItem('alibooks-active-view'),
      hasCrashFallback: Boolean(document.querySelector('.app-crash-fallback')) || document.body.innerText.includes('kunde inte visa sidan'),
      hasAliBooks: document.body.innerText.includes('AliBooks'),
      hasLogin: document.body.innerText.includes('Logga in') || document.body.innerText.includes('Login'),
      hasAuthEntry: Boolean(document.querySelector('.auth-entry')),
      authFormInlineCount: document.querySelectorAll('.auth-form-inline').length,
      hasInternalCustomerNav: [...document.querySelectorAll('button')].some((button) => ['Kunder', 'Customers'].includes(button.textContent.trim())),
      text: document.body.innerText.slice(0, 1200)
    })`);
    if (loggedOutSavedViewRecovery.hasCrashFallback) {
      throw new Error(`AliBooks crashed after restoring a logged-out saved internal view: ${JSON.stringify(loggedOutSavedViewRecovery)}`);
    }
    if (loggedOutSavedViewRecovery.activeView !== "overview") {
      throw new Error(`AliBooks should force logged-out startup to overview, not ${loggedOutSavedViewRecovery.activeView || "-"}: ${JSON.stringify(loggedOutSavedViewRecovery)}`);
    }
    if (!loggedOutSavedViewRecovery.hasAliBooks || !loggedOutSavedViewRecovery.hasLogin || !loggedOutSavedViewRecovery.hasAuthEntry || loggedOutSavedViewRecovery.authFormInlineCount !== 0) {
      throw new Error(`AliBooks logged-out saved-view recovery should show compact overview auth controls: ${JSON.stringify(loggedOutSavedViewRecovery)}`);
    }
    if (loggedOutSavedViewRecovery.hasInternalCustomerNav) {
      throw new Error(`AliBooks logged-out saved-view recovery should hide internal navigation before login: ${JSON.stringify(loggedOutSavedViewRecovery)}`);
    }

    await sleep(750);
    const loggedOutNavigationGuard = await evaluateJson(send, `JSON.stringify({
      text: document.body.innerText.slice(0, 1200),
      activeView: localStorage.getItem('alibooks-active-view'),
      hasCrashFallback: Boolean(document.querySelector('.app-crash-fallback')) || document.body.innerText.includes('kunde inte visa sidan'),
      hasAuthEntry: Boolean(document.querySelector('.auth-entry')),
      hasAuthFormInline: Boolean(document.querySelector('.auth-form-inline')),
      hasLanguageSelectInTopbar: Boolean(document.querySelector('.account-box .language-select')),
      hasInternalCustomerNav: [...document.querySelectorAll('button')].some((button) => ['Kunder', 'Customers'].includes(button.textContent.trim()))
    })`);
    if (loggedOutNavigationGuard.hasCrashFallback) {
      throw new Error(`AliBooks crashed after logged-out internal navigation: ${JSON.stringify(loggedOutNavigationGuard)}`);
    }
    if (loggedOutNavigationGuard.activeView !== "overview") {
      throw new Error(`AliBooks should keep logged-out internal navigation on overview, not ${loggedOutNavigationGuard.activeView || "-"}: ${JSON.stringify(loggedOutNavigationGuard)}`);
    }
    if (!loggedOutNavigationGuard.hasAuthEntry || loggedOutNavigationGuard.hasAuthFormInline || !loggedOutNavigationGuard.hasLanguageSelectInTopbar) {
      throw new Error(`AliBooks logged-out overview should keep compact auth/language controls after internal navigation: ${JSON.stringify(loggedOutNavigationGuard)}`);
    }
    if (loggedOutNavigationGuard.hasInternalCustomerNav) {
      throw new Error(`AliBooks logged-out overview should not expose internal navigation after saved-view recovery: ${JSON.stringify(loggedOutNavigationGuard)}`);
    }

    ws.close();
    console.log("AliBooks frontend smoke test passed.");
  } finally {
    if (chrome) chrome.kill("SIGKILL");
    if (devServer) devServer.kill("SIGTERM");
  }
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exit(1);
});
