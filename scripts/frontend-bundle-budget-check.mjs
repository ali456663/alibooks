import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const frontendDir = path.join(repoRoot, "frontend");
const assetsDir = path.join(frontendDir, "dist", "assets");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function sizeLabel(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`;
}

const frontendPackage = JSON.parse(read("frontend/package.json"));
const rootPackage = JSON.parse(read("package.json"));
const releaseGate = read("scripts/release-gate.mjs");
const hasAssets = existsSync(assetsDir);
const assets = hasAssets ? readdirSync(assetsDir) : [];
const jsAssets = assets
  .filter((file) => file.endsWith(".js"))
  .map((file) => ({
    file,
    size: statSync(path.join(assetsDir, file)).size
  }));

const entryChunks = jsAssets.filter((asset) => asset.file.startsWith("index-"));
const visualChunks = jsAssets.filter((asset) => asset.file.startsWith("vendor-visuals-"));
const motionChunks = jsAssets.filter((asset) => asset.file.startsWith("vendor-motion-"));
const animationChunks = jsAssets.filter((asset) => asset.file.startsWith("vendor-animation-"));
const totalJsSize = jsAssets.reduce((sum, asset) => sum + asset.size, 0);
const largestEntrySize = entryChunks.reduce((max, asset) => Math.max(max, asset.size), 0);
const largestVisualSize = visualChunks.reduce((max, asset) => Math.max(max, asset.size), 0);

const maxEntryBytes = 1_450_000;
const maxVisualBytes = 650_000;
const maxTotalJsBytes = 2_600_000;

check(
  "Frontend dist assets exist",
  hasAssets,
  "Run npm run build before checking the production bundle budget."
);

check(
  "JavaScript assets are present",
  jsAssets.length > 0,
  "The production build should emit JavaScript assets."
);

check(
  "Main app entry chunk stays under budget",
  entryChunks.length > 0 && largestEntrySize <= maxEntryBytes,
  `Largest index chunk is ${sizeLabel(largestEntrySize)}; budget is ${sizeLabel(maxEntryBytes)}.`
);

check(
  "Decorative 3D shader chunk is absent or bounded",
  visualChunks.length === 0 || largestVisualSize <= maxVisualBytes,
  visualChunks.length === 0
    ? "No decorative 3D shader chunk is shipped with the accounting workspace."
    : `Largest lazy visual chunk is ${sizeLabel(largestVisualSize)}; budget is ${sizeLabel(maxVisualBytes)}.`
);

check(
  "Landing motion is split and unused GSAP is absent",
  motionChunks.length > 0 && animationChunks.length === 0,
  "framer-motion should stay in its own landing chunk; unused GSAP should not ship with the accounting workspace."
);

check(
  "Total JavaScript stays within MVP budget",
  totalJsSize > 0 && totalJsSize <= maxTotalJsBytes,
  `Total JS is ${sizeLabel(totalJsSize)}; budget is ${sizeLabel(maxTotalJsBytes)}.`
);

check(
  "Frontend exposes bundle budget script",
  frontendPackage.scripts?.["check:bundle"] === "node ../scripts/frontend-bundle-budget-check.mjs",
  "frontend/package.json should expose npm run check:bundle."
);

check(
  "Root exposes bundle budget script",
  rootPackage.scripts?.["check:bundle"] === "npm --prefix frontend run check:bundle --",
  "package.json should expose npm run check:bundle from the repo root."
);

check(
  "Release gate runs bundle budget after build",
  releaseGate.includes('["build", "Build frontend"]') &&
    releaseGate.includes('["check:bundle", "Check frontend bundle budget"]') &&
    releaseGate.indexOf('["build", "Build frontend"]') < releaseGate.indexOf('["check:bundle", "Check frontend bundle budget"]'),
  "Release gate should check bundle size immediately after the production build."
);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

const failures = checks.filter((result) => !result.ok);
console.log("");
console.log(`AliBooks frontend bundle budget check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} frontend bundle budget check(s) failed.`);
  process.exit(1);
}
