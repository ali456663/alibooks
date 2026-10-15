import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const frontend = readFileSync(path.join(root, "frontend/src/main.jsx"), "utf8");

test("SIE downloads only use the backend export", () => {
  assert.ok(frontend.includes("async function downloadBackendSieExport()"));
  assert.ok(frontend.includes("downloadCsv(`/sie/export${selectedBookkeepingQuery()}`"));
  assert.equal(frontend.includes("function downloadSieExport()"), false);
  assert.equal(frontend.includes("downloadLocalText(`alibooks-sie-"), false);
  assert.equal(frontend.includes("#FORMAT PC8"), false);
});
