import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const shell = process.platform === "win32" ? "C:\\Program Files\\Git\\usr\\bin\\sh.exe" : "/bin/sh";

function fixture() {
  const directory = mkdtempSync(path.join(tmpdir(), "alibooks-deploy-"));
  mkdirSync(path.join(directory, "scripts"));
  const deploy = readFileSync(path.join(root, "scripts/ec2-deploy.sh"), "utf8").replaceAll("\r\n", "\n");
  writeFileSync(path.join(directory, "scripts/ec2-deploy.sh"), deploy);
  writeFileSync(path.join(directory, "docker-compose.prod.yml"), "services: {}\n");
  writeFileSync(path.join(directory, ".env"), [
    "DOCKERHUB_USERNAME=your-dockerhub-user",
    "IMAGE_TAG=sha-1234567",
    "APP_FRONTEND_URL=https://books.example.test",
    "APP_CORS_ALLOWED_ORIGINS=https://books.example.test",
    "SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.test:5432/books",
    "SPRING_DATASOURCE_USERNAME=books",
    "SPRING_DATASOURCE_PASSWORD=test-only-password",
    "JWT_SECRET=test-only-jwt-secret",
    "APP_AUTH_REGISTRATION_BOOTSTRAP_KEY=test-only-bootstrap-key",
    "APP_AUTH_MFA_ENCRYPTION_KEY=test-only-mfa-key",
    "APP_AUTH_REQUIRE_MFA=true",
  ].join("\n") + "\n");
  return directory;
}

function deploy(directory, overrides = {}) {
  return spawnSync(shell, ["./scripts/ec2-deploy.sh"], {
    cwd: directory,
    encoding: "utf8",
    env: {
      ...process.env,
      PATH: process.platform === "win32"
        ? `C:\\Program Files\\Git\\usr\\bin;${process.env.PATH || ""}`
        : process.env.PATH,
      FRONTEND_URL: "",
      BACKEND_URL: "",
      AUTH_TOKEN: "",
      ALIBOOKS_AUTH_TOKEN: "",
      ALLOW_UNVERIFIED_BOOTSTRAP: "false",
      ...overrides,
    },
  });
}

test("deploy refuses to skip public smoke when URLs are absent", () => {
  const directory = fixture();
  try {
    const result = deploy(directory);
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stdout, /FRONTEND_URL and BACKEND_URL are required/);
    assert.doesNotMatch(result.stdout, /Pulling latest Docker images/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("deploy requires authenticated database proof unless bootstrap is explicit", () => {
  const directory = fixture();
  try {
    const result = deploy(directory, {
      FRONTEND_URL: "https://books.example.test",
      BACKEND_URL: "https://books.example.test/api",
    });
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stdout, /set ALIBOOKS_AUTH_TOKEN/);
    assert.doesNotMatch(result.stdout, /Pulling latest Docker images/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("deploy refuses HTTP smoke endpoints before pulling images", () => {
  const directory = fixture();
  try {
    const result = deploy(directory, {
      FRONTEND_URL: "http://books.example.test",
      BACKEND_URL: "https://books.example.test/api",
      ALIBOOKS_AUTH_TOKEN: "test-only-token",
    });
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stdout, /public smoke URLs must use HTTPS/);
    assert.doesNotMatch(result.stdout, /Pulling latest Docker images/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("deploy always runs smoke and passes the authenticated token", () => {
  const deployScript = readFileSync(path.join(root, "scripts/ec2-deploy.sh"), "utf8");
  const windowsSmoke = readFileSync(path.join(root, "scripts/prod-smoke-test.ps1"), "utf8");
  assert.doesNotMatch(deployScript, /Smoke test skipped/);
  assert.match(deployScript, /AUTH_TOKEN="\$AUTH_TOKEN" sh \.\/scripts\/prod-smoke-test\.sh/);
  assert.ok(deployScript.indexOf("Running public smoke test") > deployScript.indexOf("up -d --wait"));
  assert.ok(windowsSmoke.indexOf("Expected HTTP 401") < windowsSmoke.indexOf("if ([string]::IsNullOrWhiteSpace($AuthToken))"));
});

function smoke(directory, overrides = {}) {
  const binaryDirectory = path.join(directory, "bin");
  mkdirSync(binaryDirectory, { recursive: true });
  const curl = path.join(binaryDirectory, "curl");
  writeFileSync(curl, `#!/bin/sh
case "$*" in
  *'/health'*) printf '{"status":"ok"}' ;;
  *'/system/status'*)
    case "$*" in
      *'Authorization: Bearer '*) printf '{"database":{"ok":%s}}' "\${FAKE_DB_OK:-true}" ;;
      *) printf '%s' "\${FAKE_ANON_STATUS:-401}" ;;
    esac ;;
  *) printf '200' ;;
esac
`.replaceAll("\r\n", "\n"));
  chmodSync(curl, 0o755);
  const smokeScript = readFileSync(path.join(root, "scripts/prod-smoke-test.sh"), "utf8").replaceAll("\r\n", "\n");
  writeFileSync(path.join(directory, "scripts/prod-smoke-test.sh"), smokeScript);
  const searchPath = process.platform === "win32"
    ? `${binaryDirectory};C:\\Program Files\\Git\\usr\\bin;${process.env.PATH || ""}`
    : `${binaryDirectory}:${process.env.PATH || ""}`;
  return spawnSync(shell, ["./scripts/prod-smoke-test.sh"], {
    cwd: directory,
    encoding: "utf8",
    env: {
      ...process.env,
      PATH: searchPath,
      FRONTEND_URL: "https://books.example.test",
      BACKEND_URL: "https://books.example.test/api",
      AUTH_TOKEN: "test-only-token",
      FAKE_ANON_STATUS: "401",
      FAKE_DB_OK: "true",
      ...overrides,
    },
  });
}

test("public smoke proves anonymous denial and authenticated database health", () => {
  const directory = fixture();
  try {
    const result = smoke(directory);
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /Authenticated system status and database OK/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("public smoke rejects unprotected status and an unhealthy database", () => {
  const directory = fixture();
  try {
    const unprotected = smoke(directory, { FAKE_ANON_STATUS: "200" });
    assert.equal(unprotected.status, 1, unprotected.stderr || unprotected.stdout);
    assert.match(unprotected.stdout, /Expected HTTP 401, got 200/);
    const unhealthy = smoke(directory, { FAKE_DB_OK: "false" });
    assert.equal(unhealthy.status, 1, unhealthy.stderr || unhealthy.stdout);
    assert.match(unhealthy.stdout, /Database check failed/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
