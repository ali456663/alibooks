import { pathToFileURL } from "node:url";

const requirements = [
  ["Databas", (status) => status.database?.ok === true],
  ["Stark JWT-konfiguration", (status) => status.security?.jwtConfigured === true && status.security?.jwtStrong === true],
  ["Inloggningsskydd", (status) => status.auth?.loginAttemptLockEnabled === true],
  ["MFA for agarkontot", (status) => status.auth?.mfaRequired === true
    && status.auth?.mfaKeyStrong === true && status.auth?.ownerMfaEnabled === true],
  ["Produktions-CORS", (status) => status.cors?.productionReady === true],
  ["Testaterstallning avstangd", (status) => status.maintenance?.safeForProduction === true],
  ["Exakta oren i bokforingen", (status) => status.moneyModel?.supportsMinorUnits === true && status.moneyModel?.productionBookkeepingReady === true],
  ["Verifierad pengamigrering", (status) => status.moneyModel?.migration?.verified === true
    && status.moneyModel?.migration?.readyForAuthoritativeCutover === true
    && status.moneyModel?.migration?.issueCount === 0]
];

export function evaluateAccountingReadiness(status) {
  return requirements.map(([name, predicate]) => ({
    name,
    ok: status != null && typeof status === "object" && predicate(status)
  }));
}

export function backendStatusUrl(rawUrl) {
  const url = new URL(rawUrl);
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  if ((url.protocol !== "https:" && !(url.protocol === "http:" && loopback))
      || url.username || url.password || url.search || url.hash) {
    throw new Error("Anvand HTTPS for fjarrbackend och lagg inte in hemligheter i URL:en.");
  }
  return new URL("system/status", `${url.toString().replace(/\/$/, "")}/`).toString();
}

async function main() {
  const backendArg = process.argv.indexOf("--backend-url");
  const backendUrl = backendArg < 0 ? (process.env.ALIBOOKS_BACKEND_URL || "http://127.0.0.1:3000") : process.argv[backendArg + 1];
  const token = process.env.ALIBOOKS_AUTH_TOKEN?.replace(/^Bearer\s+/i, "").trim();
  if (!token) {
    throw new Error("Satt ALIBOOKS_AUTH_TOKEN for att kontrollera skyddad systemstatus.");
  }
  const url = backendStatusUrl(backendUrl);
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(5000)
  });
  if (!response.ok) {
    throw new Error(`Systemstatus svarade HTTP ${response.status}; ingen GO-bedömning kan goras.`);
  }
  const checks = evaluateAccountingReadiness(await response.json());
  for (const check of checks) {
    console.log(`${check.ok ? "OK" : "NO-GO"} - ${check.name}`);
  }
  if (checks.some((check) => !check.ok)) {
    throw new Error("Skarp bokforing ar blockerad. Grona statiska checkar andrar inte detta.");
  }
  console.log("Teknisk live-kontroll godkand. Externa driftbevis och redovisningsgranskning kravs fortfarande.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(`NO-GO - ${error.message}`);
    process.exitCode = 1;
  });
}
