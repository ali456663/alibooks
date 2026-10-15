import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { copyFileSync, mkdtempSync, rmSync, writeFileSync, mkdirSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const sourceDir = path.dirname(fileURLToPath(import.meta.url));
const sourceRoot = path.resolve(sourceDir, "..");

test("secret check scans untracked release files but not ignored local env", () => {
  const root = mkdtempSync(path.join(os.tmpdir(), "alibooks-secret-check-"));
  try {
    mkdirSync(path.join(root, "scripts"));
    copyFileSync(path.join(sourceRoot, ".gitignore"), path.join(root, ".gitignore"));
    copyFileSync(path.join(sourceDir, "secret-placeholder-check.mjs"), path.join(root, "scripts", "secret-placeholder-check.mjs"));
    const init = spawnSync("git", ["init", "-q", root], { encoding: "utf8" });
    assert.equal(init.status, 0, init.stderr);

    const fakeKey = "sk-" + "A".repeat(32);
    writeFileSync(path.join(root, ".env"), `API_KEY=${fakeKey}\n`);
    mkdirSync(path.join(root, ".m2-cache"));
    writeFileSync(path.join(root, ".m2-cache", "cache.txt"), `API_KEY=${fakeKey}\n`);
    mkdirSync(path.join(root, "backups"));
    writeFileSync(path.join(root, "backups", "restore.txt"), `API_KEY=${fakeKey}\n`);
    let result = spawnSync(process.execPath, [path.join(root, "scripts", "secret-placeholder-check.mjs")], { encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);

    writeFileSync(path.join(root, "new-source.js"), `export const key = "${fakeKey}";\n`);
    result = spawnSync(process.execPath, [path.join(root, "scripts", "secret-placeholder-check.mjs")], { encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /new-source\.js:1 matches OpenAI-compatible API key/);
    assert.doesNotMatch(result.stderr, /\.env:1/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
