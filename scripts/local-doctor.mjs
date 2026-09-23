import { spawnSync } from "node:child_process";
import net from "node:net";

const args = new Set(process.argv.slice(2));
const soft = args.has("--soft");
const backendUrl = readArg("--backend-url") || process.env.ALIBOOKS_BACKEND_URL || "http://127.0.0.1:3000";
const frontendUrl = readArg("--frontend-url") || process.env.ALIBOOKS_FRONTEND_URL || "http://127.0.0.1:5157";
const databaseHost = readArg("--db-host") || process.env.ALIBOOKS_DB_HOST || "127.0.0.1";
const databasePort = Number(readArg("--db-port") || process.env.ALIBOOKS_DB_PORT || 5432);
const skipFrontend = args.has("--skip-frontend");
const configuredAuthToken = readArg("--auth-token") || process.env.ALIBOOKS_AUTH_TOKEN || "";
const authHeader = configuredAuthToken
  ? (configuredAuthToken.startsWith("Bearer ") ? configuredAuthToken : `Bearer ${configuredAuthToken}`)
  : "";

const results = [];

function readArg(name) {
  const index = process.argv.indexOf(name);
  if (index < 0) {
    return "";
  }
  return process.argv[index + 1] || "";
}

function record(name, ok, detail, severity = "fail", fix = "") {
  results.push({ name, ok: Boolean(ok), detail, severity, fix });
}

async function portOpen(host, port, timeoutMs = 1500) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    const done = (ok) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(ok);
    };
    socket.setTimeout(timeoutMs);
    socket.once("connect", () => done(true));
    socket.once("timeout", () => done(false));
    socket.once("error", () => done(false));
  });
}

async function fetchText(url, timeoutMs = 4000, headers = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { signal: controller.signal, headers });
    const text = await response.text();
    return { ok: response.ok, status: response.status, text };
  } catch (error) {
    return { ok: false, status: 0, text: "", error: error?.message || String(error) };
  } finally {
    clearTimeout(timeout);
  }
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function dockerComposePs() {
  const result = spawnSync("docker", ["compose", "ps"], {
    cwd: new URL("..", import.meta.url),
    encoding: "utf8",
    shell: false
  });
  if (result.error) {
    return { ok: false, output: result.error.message };
  }
  return {
    ok: result.status === 0,
    output: `${result.stdout || ""}${result.stderr || ""}`.trim()
  };
}

async function main() {
  const dbOpen = await portOpen(databaseHost, databasePort);
  record(
    "PostgreSQL port",
    dbOpen,
    `${databaseHost}:${databasePort}`,
    "fail",
    "Starta Docker Desktop och kor: docker compose up db"
  );

  const health = await fetchText(`${backendUrl}/health`);
  const healthJson = parseJson(health.text);
  record(
    "Backend /health",
    health.ok && healthJson?.status === "ok",
    health.ok ? health.text : (health.error || `HTTP ${health.status}`),
    "fail",
    "Starta CloudShopApplication i IntelliJ eller kontrollera att port 3000 ar ledig."
  );

  const systemStatus = await fetchText(
    `${backendUrl}/system/status`,
    4000,
    authHeader ? { Authorization: authHeader } : {}
  );
  const systemJson = parseJson(systemStatus.text);
  const statusProtected = systemStatus.status === 401 && !authHeader;
  record(
    "Backend /system/status protection",
    (systemStatus.ok && systemJson?.backend?.ok === true) || statusProtected,
    statusProtected ? "detaljerad status ar skyddad (401 utan token)" : (systemStatus.ok ? "backend status endpoint svarar" : (systemStatus.error || `HTTP ${systemStatus.status}`)),
    "fail",
    "Satt ALIBOOKS_AUTH_TOKEN eller anvand --auth-token for detaljerad status."
  );
  record(
    "Backend database connection",
    systemStatus.ok && systemJson?.database?.ok === true,
    systemStatus.ok ? `database.ok=${String(systemJson?.database?.ok)}` : (statusProtected ? "kraver auth-token for detaljkontroll" : "systemstatus saknas"),
    statusProtected ? "info" : "fail",
    statusProtected ? "Satt ALIBOOKS_AUTH_TOKEN eller anvand --auth-token for databasdiagnostik." : "Kontrollera SPRING_DATASOURCE_URL och att PostgreSQL lyssnar pa 5432."
  );
  record(
    "JWT configuration",
    systemStatus.ok && systemJson?.security?.jwtConfigured === true,
    systemStatus.ok ? `jwtConfigured=${String(systemJson?.security?.jwtConfigured)}, strong=${String(systemJson?.security?.jwtStrong)}` : (statusProtected ? "kraver auth-token for konfigurationskontroll" : "systemstatus saknas"),
    statusProtected ? "info" : "warn",
    "Satt en lang JWT_SECRET i IntelliJ Run Configuration innan skarp anvandning."
  );
  record(
    "Login protection",
    systemStatus.ok && systemJson?.auth?.loginAttemptLockEnabled === true,
    systemStatus.ok ? `loginAttemptLockEnabled=${String(systemJson?.auth?.loginAttemptLockEnabled)}` : (statusProtected ? "kraver auth-token for konfigurationskontroll" : "systemstatus saknas"),
    statusProtected ? "info" : "warn",
    "Kontrollera app.auth.* om inloggningsskyddet ar avstangt."
  );

  if (!skipFrontend) {
    const frontend = await fetchText(frontendUrl);
    record(
      "Frontend",
      frontend.ok && frontend.text.includes("<div id=\"root\""),
      frontend.ok ? `${frontendUrl} svarar` : (frontend.error || `HTTP ${frontend.status}`),
      "fail",
      "Oppna en ny Git Bash: cd frontend && npm run dev"
    );
  }

  const docker = dockerComposePs();
  record(
    "Docker Compose status",
    docker.ok && docker.output.includes("db"),
    docker.ok ? docker.output.split(/\r?\n/).slice(0, 4).join(" | ") : docker.output,
    "warn",
    "Om Docker inte syns men appen fungerar kan du ignorera detta; annars starta Docker Desktop."
  );

  for (const result of results) {
    const marker = result.ok ? "OK" : result.severity === "info" ? "INFO" : result.severity === "warn" ? "WARN" : "FAIL";
    console.log(`${marker} - ${result.name}: ${result.detail}`);
    if (!result.ok && result.fix && result.severity !== "info") {
      console.log(`      Fix: ${result.fix}`);
    }
  }

  const failures = results.filter((result) => !result.ok && result.severity === "fail");
  const warnings = results.filter((result) => !result.ok && result.severity === "warn");
  const informational = results.filter((result) => !result.ok && result.severity === "info");
  const passed = results.filter((result) => result.ok);
  console.log("");
  console.log(`AliBooks local doctor: ${passed.length}/${results.length} checks passed.`);
  if (warnings.length > 0) {
    console.log(`${warnings.length} warning(s) need review.`);
  }
  if (informational.length > 0) {
    console.log(`${informational.length} auth-gated check(s) skipped without ALIBOOKS_AUTH_TOKEN.`);
  }
  if (failures.length > 0 && !soft) {
    console.error(`${failures.length} local startup check(s) failed.`);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(`AliBooks local doctor failed: ${error?.message || String(error)}`);
  process.exit(1);
});
