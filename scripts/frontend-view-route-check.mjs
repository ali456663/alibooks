import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const mainPath = path.join(repoRoot, "frontend", "src", "main.jsx");
const source = readFileSync(mainPath, "utf8");

function fail(message) {
  console.error(`Frontend view route check failed: ${message}`);
  process.exit(1);
}

function unique(values) {
  return [...new Set(values)].sort((first, second) => first.localeCompare(second));
}

function extract(pattern, text = source) {
  return unique([...text.matchAll(pattern)].map((match) => match[1]));
}

const viewKeysMatch = source.match(/const viewKeys = \[([\s\S]*?)\];/);
if (!viewKeysMatch) {
  fail("Could not find viewKeys.");
}

const viewKeys = extract(/"([^"]+)"/g, viewKeysMatch[1]);
const viewKeySet = new Set(viewKeys);
const navViews = extract(/navButton\("([^"]+)"/g);
const setActiveViewTargets = extract(/setActiveView\("([^"]+)"\)/g);
const appShellIndex = source.indexOf('<main className="app-shell">');

if (appShellIndex < 0) {
  fail("Could not find the app shell render block.");
}

const renderSource = source.slice(appShellIndex);
const renderedViews = extract(/activeView === "([^"]+)"/g, renderSource);
const titleViews = extract(/if \(activeView === "([^"]+)"\) return/g);

const navMissingFromViewKeys = navViews.filter((view) => !viewKeySet.has(view));
if (navMissingFromViewKeys.length > 0) {
  fail(`Menu links missing from viewKeys: ${navMissingFromViewKeys.join(", ")}.`);
}

const navMissingRender = navViews.filter((view) => !renderedViews.includes(view));
if (navMissingRender.length > 0) {
  fail(`Menu links without rendered view block: ${navMissingRender.join(", ")}.`);
}

const navMissingTitle = navViews.filter((view) => view !== "overview" && !titleViews.includes(view));
if (navMissingTitle.length > 0) {
  fail(`Menu links without page title mapping: ${navMissingTitle.join(", ")}.`);
}

const invalidTargets = setActiveViewTargets.filter((view) => !viewKeySet.has(view));
if (invalidTargets.length > 0) {
  fail(`setActiveView points to unknown view key(s): ${invalidTargets.join(", ")}.`);
}

const unusedViewKeys = viewKeys.filter((view) => !navViews.includes(view) && !setActiveViewTargets.includes(view));
if (unusedViewKeys.length > 0) {
  fail(`viewKeys not reachable from menu or action buttons: ${unusedViewKeys.join(", ")}.`);
}

console.log(`Frontend view route check passed: ${navViews.length} menu views, ${renderedViews.length} rendered view references.`);
