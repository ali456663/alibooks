import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function json(relativePath) {
  return JSON.parse(read(relativePath));
}

function check(name, ok, detail) {
  checks.push({ name, ok: Boolean(ok), detail });
}

function includesAll(source, values) {
  return values.every((value) => source.includes(value));
}

const styles = read("frontend/src/styles.css");
const mainSource = read("frontend/src/main.jsx");
const frontendPackage = json("frontend/package.json");
const rootPackage = json("package.json");
const releaseGate = read("scripts/release-gate.mjs");

const finalLayerIndex = styles.lastIndexOf("ALIBOOKS PROFESSIONAL FINANCE UI FINAL CASCADE LAYER");
const finalLayer = finalLayerIndex >= 0 ? styles.slice(finalLayerIndex) : "";
const navViewCount = (mainSource.match(/navButton\("/g) || []).length;

check(
  "Professional finance theme exists at the end",
  finalLayerIndex > styles.length * 0.75 && styles.trimEnd().endsWith("}"),
  "The professional accounting UI layer should be late in the stylesheet so it wins over older theme experiments."
);
check(
  "Light neutral base is default",
  includesAll(finalLayer, ["--finance-bg: #f7f8fa", "--finance-bg-soft: #fafafa", "background:", "var(--finance-bg)"]),
  "Default app background should be off-white/light gray, not pure white."
);
check(
  "Stable finance accent is used",
  finalLayer.includes("--finance-accent: #1d4ed8"),
  "The primary accent should be deep blue for a stable finance signal."
);
check(
  "Functional positive color exists",
  includesAll(finalLayer, ["--finance-positive: #047857", ".message.success", ".status-paid", ".positive"]),
  "Green should be reserved for revenue, paid, ok and positive states."
);
check(
  "Functional warning and danger colors exist",
  includesAll(finalLayer, ["--finance-warning: #b45309", "--finance-danger: #b42318", ".message.warning", ".message.error"]),
  "Orange/red should be reserved for warning, cost, risk and negative states."
);
check(
  "Accent is not decorative saturation",
  includesAll(finalLayer, ["background: var(--finance-accent)", "background: var(--finance-surface)", "background: var(--finance-surface-muted)"]),
  "Most of the interface should stay neutral, with accent used for primary actions and selected states."
);
check(
  "Dark mode is available",
  includesAll(finalLayer, ["@media (prefers-color-scheme: dark)", "--finance-bg: #0b1120", "--finance-surface: rgba(17, 24, 39, 0.94)"]),
  "A real dark-mode palette should exist for long accounting sessions."
);
check(
  "Heavy decorative motion is disabled",
  includesAll(finalLayer, ["body::before", "body::after", ".app-shell::after", ".brand::after", "display: none"]),
  "Old decorative overlays and glowing wordmark/dot should not distract inside the app."
);
check(
  "Minimal motion is used",
  includesAll(finalLayer, ["@keyframes alibooks-finance-reveal", "translateY(6px)", "220ms ease-out"]),
  "App motion should be limited to subtle view transitions."
);
check(
  "Reduced motion is respected",
  includesAll(finalLayer, ["@media (prefers-reduced-motion: reduce)", "transition-duration: 1ms", "scroll-behavior: auto"]),
  "Users who request reduced motion should not get animated dashboard effects."
);
check(
  "Glass-light cards replace heavy 3D",
  includesAll(finalLayer, ["--finance-shadow-small", ".stats article", ".order-card", ".report-card", "backdrop-filter: none"]),
  "Cards should have subtle depth, not 3D scenes or strong glassmorphism."
);
check(
  "All left-menu views inherit the theme",
  navViewCount >= 45 && includesAll(finalLayer, [".nav-button.active", "box-shadow: inset 4px 0 0 var(--finance-accent)"]),
  "The global selectors should cover every AliBooks page in the left menu."
);
check(
  "Forms remain readable",
  includesAll(finalLayer, ["input,", "select,", "textarea,", "background: #ffffff", "input::placeholder"]),
  "Inputs and filters should be calm, readable and table-friendly."
);
check(
  "Buttons are utilitarian",
  includesAll(finalLayer, ["border-radius: 8px", ".primary-action-button", ".secondary-button:not(:disabled):hover"]),
  "Buttons should look like professional app controls, not landing-page pills."
);
check(
  "Table readability is protected",
  includesAll(finalLayer, ["table", "thead", "th", "td", "var(--finance-surface-muted)"]),
  "Accounting tables should have neutral headers and readable cells."
);
check(
  "Numbers use tabular figures",
  includesAll(finalLayer, ["font-variant-numeric: tabular-nums", "font-feature-settings: \"tnum\" 1, \"lnum\" 1", "input[type=\"number\"]"]),
  "Amounts, table numbers and numeric inputs should line up cleanly."
);
check(
  "Whitespace and hierarchy are preserved",
  includesAll(finalLayer, ["--finance-shadow", ".topbar", ".brand", ".eyebrow"]),
  "The theme should support clear hierarchy without cramped financial data."
);
check(
  "AI assistant fits the app theme",
  includesAll(finalLayer, [".floating-ai-button", ".floating-ai-panel", "linear-gradient(135deg, var(--finance-accent), #047857)"]),
  "The always-available AI assistant should match the professional app theme."
);
check(
  "Frontend exposes finance UI check",
  frontendPackage.scripts?.["check:finance-ui"] === "node ../scripts/finance-ui-check.mjs",
  "frontend/package.json should expose npm run check:finance-ui."
);
check(
  "Root and release gate expose finance UI check",
  ["npm --prefix frontend run check:finance-ui", "npm --prefix frontend run check:finance-ui --"].includes(rootPackage.scripts?.["check:finance-ui"])
    && releaseGate.includes('"check:finance-ui"'),
  "The root command center and release gate should fail if the professional finance UI layer is removed."
);

const failed = checks.filter((result) => !result.ok);
for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

const score = Math.round(((checks.length - failed.length) / checks.length) * 100);
console.log("");
console.log(`AliBooks professional finance UI check: ${score}/100 (${checks.length - failed.length}/${checks.length} checks passed).`);

if (failed.length > 0) {
  console.error(`${failed.length} professional finance UI check(s) failed.`);
  process.exit(1);
}
