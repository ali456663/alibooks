import assert from "node:assert/strict";
import test from "node:test";
import { backendStatusUrl, evaluateAccountingReadiness } from "./live-accounting-readiness.mjs";

const ready = {
  database: { ok: true },
  security: { jwtConfigured: true, jwtStrong: true },
  auth: { loginAttemptLockEnabled: true, mfaRequired: true, mfaKeyStrong: true, ownerMfaEnabled: true },
  cors: { productionReady: true },
  maintenance: { safeForProduction: true },
  moneyModel: {
    supportsMinorUnits: true,
    productionBookkeepingReady: true,
    migration: { verified: true, readyForAuthoritativeCutover: true, issueCount: 0 }
  }
};

test("live accounting check fails closed when status is absent or incomplete", () => {
  assert.ok(evaluateAccountingReadiness(null).every((check) => !check.ok));
  assert.ok(evaluateAccountingReadiness({}).every((check) => !check.ok));
});

test("shadow money migration is not production-ready", () => {
  const shadow = structuredClone(ready);
  shadow.moneyModel.supportsMinorUnits = false;
  shadow.moneyModel.productionBookkeepingReady = false;
  shadow.moneyModel.migration.readyForAuthoritativeCutover = false;
  assert.equal(evaluateAccountingReadiness(shadow).filter((check) => !check.ok).length, 2);
});

test("only exact affirmative live status passes", () => {
  assert.ok(evaluateAccountingReadiness(ready).every((check) => check.ok));
  const issues = structuredClone(ready);
  issues.moneyModel.migration.issueCount = 1;
  assert.equal(evaluateAccountingReadiness(issues).at(-1).ok, false);
});

test("live accounting fails closed when MFA is optional, unconfigured or unenrolled", () => {
  for (const field of ["mfaRequired", "mfaKeyStrong", "ownerMfaEnabled"]) {
    const status = structuredClone(ready);
    delete status.auth[field];
    assert.equal(evaluateAccountingReadiness(status).find(check => check.name === "MFA for agarkontot").ok, false);
    status.auth[field] = false;
    assert.equal(evaluateAccountingReadiness(status).find(check => check.name === "MFA for agarkontot").ok, false);
  }
});

test("remote status requests require HTTPS and cannot carry credentials", () => {
  assert.equal(backendStatusUrl("http://127.0.0.1:3000"), "http://127.0.0.1:3000/system/status");
  assert.equal(backendStatusUrl("https://books.example.com/api"), "https://books.example.com/api/system/status");
  assert.throws(() => backendStatusUrl("http://books.example.com"));
  assert.throws(() => backendStatusUrl("https://user:secret@books.example.com"));
});
