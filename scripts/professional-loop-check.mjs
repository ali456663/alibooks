import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const mainPath = path.join(repoRoot, "frontend", "src", "main.jsx");
const source = readFileSync(mainPath, "utf8");

function fail(message) {
  console.error(`Professional loop check failed: ${message}`);
  process.exit(1);
}

const viewKeysMatch = source.match(/const viewKeys = \[([\s\S]*?)\];/);
if (!viewKeysMatch) {
  fail("Could not find viewKeys.");
}

const viewKeys = new Set([...viewKeysMatch[1].matchAll(/"([^"]+)"/g)].map((match) => match[1]));
const planMatch = source.match(/const professionalActionPlanRows = \[([\s\S]*?)\]\.map\(\(row\) => \(\{/);
if (!planMatch) {
  fail("Could not find professionalActionPlanRows.");
}

const planSource = planMatch[1];
const steps = [...planSource.matchAll(/\bstep:\s*(\d+)/g)].map((match) => Number(match[1]));
const expectedSteps = Array.from({ length: 20 }, (_, index) => index + 1);

if (steps.length !== expectedSteps.length) {
  fail(`Expected 20 steps, found ${steps.length}.`);
}

const missingSteps = expectedSteps.filter((step) => !steps.includes(step));
if (missingSteps.length > 0) {
  fail(`Missing step(s): ${missingSteps.join(", ")}.`);
}

const duplicateSteps = steps.filter((step, index) => steps.indexOf(step) !== index);
if (duplicateSteps.length > 0) {
  fail(`Duplicate step(s): ${[...new Set(duplicateSteps)].join(", ")}.`);
}

const keys = [...planSource.matchAll(/\bkey:\s*"([^"]+)"/g)].map((match) => match[1]);
const duplicateKeys = keys.filter((key, index) => keys.indexOf(key) !== index);
if (duplicateKeys.length > 0) {
  fail(`Duplicate key(s): ${[...new Set(duplicateKeys)].join(", ")}.`);
}

const referencedViews = [...planSource.matchAll(/setActiveView\("([^"]+)"\)/g)].map((match) => match[1]);
const invalidViews = referencedViews.filter((view) => !viewKeys.has(view));
if (invalidViews.length > 0) {
  fail(`Unknown view key(s): ${[...new Set(invalidViews)].join(", ")}.`);
}

console.log("Professional 20-step loop check passed.");
