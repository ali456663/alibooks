import { test } from "node:test";
import assert from "node:assert/strict";
import { isAliBooksRemote } from "./lib/git-remote.mjs";

for (const remote of [
  "https://github.com/ali456663/alibooks",
  "https://github.com/ali456663/alibooks.git",
  "https://github.com/ali456663/alibooks/",
  "git@github.com:ali456663/alibooks.git",
  "ssh://git@github.com/ali456663/alibooks.git"
]) {
  test(`accepts ${remote}`, () => assert.equal(isAliBooksRemote(remote), true));
}
for (const remote of [
  "https://example.com/ali456663/alibooks.git",
  "https://github.com/other/alibooks.git",
  "https://github.com/ali456663/alibooks-other",
  "/tmp/alibooks",
  "https://example.com/https://github.com/ali456663/alibooks.git"
]) {
  test(`rejects ${remote}`, () => assert.equal(isAliBooksRemote(remote), false));
}
