import { test } from "node:test";
import assert from "node:assert/strict";
import { isTraceableCheckout, workingTreeChangeCount } from "./lib/release-traceability.mjs";

const headSha = "0123456789abcdef0123456789abcdef01234567";

test("accepts a normal local checkout with an upstream", () => {
  assert.equal(isTraceableCheckout({
    upstream: "origin/main",
    githubActions: "",
    githubSha: "",
    headSha,
    githubRef: ""
  }), true);
});

test("accepts detached GitHub Actions checkout when SHA and ref are traceable", () => {
  assert.equal(isTraceableCheckout({
    upstream: "",
    githubActions: "true",
    githubSha: headSha,
    headSha,
    githubRef: "refs/heads/main"
  }), true);
});

test("rejects detached checkout with a mismatched GitHub SHA", () => {
  assert.equal(isTraceableCheckout({
    upstream: "",
    githubActions: "true",
    githubSha: "fedcba9876543210fedcba9876543210fedcba98",
    headSha,
    githubRef: "refs/pull/11/merge"
  }), false);
});

test("rejects a local checkout without upstream", () => {
  assert.equal(isTraceableCheckout({
    upstream: "",
    githubActions: "",
    githubSha: "",
    headSha,
    githubRef: ""
  }), false);
});

test("counts tracked and untracked changes without exposing file names", () => {
  assert.equal(workingTreeChangeCount({ status: 0, stdout: " M backend/pom.xml\n?? local.txt\n" }), 2);
  assert.equal(workingTreeChangeCount({ status: 0, stdout: "" }), 0);
});

test("does not treat failed or incomplete git status as a clean checkout", () => {
  assert.equal(workingTreeChangeCount({ status: 1, stdout: "" }), null);
  assert.equal(workingTreeChangeCount({ status: 0 }), null);
  assert.equal(workingTreeChangeCount({ error: new Error("git unavailable"), status: null, stdout: "" }), null);
});
