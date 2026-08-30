import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const checks = [];

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function exists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
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

const docPath = "docs/mvp-slutspurt.md";
const doc = exists(docPath) ? read(docPath) : "";
const rootPackage = json("package.json");
const frontendPackage = json("frontend/package.json");
const releaseGate = read("scripts/release-gate.mjs");
const readiness = read("scripts/alibooks-readiness-check.mjs");
const evidence = read("docs/release-evidence.md");
const roadmap = read("docs/roadmap-kvar.md");
const riskRegister = read("docs/go-live-riskregister.md");
const goLiveDecision = read("docs/go-live-beslut.md");
const externalProof = read("docs/externa-go-live-bevis.md");
const mvpUse = read("docs/anvandningsklar-mvp.md");
const frontend = read("frontend/src/main.jsx");

const stepRows = Array.from(doc.matchAll(/^\|\s*(\d{2})\s*\|/gm)).map((match) => match[1]);
const expectedSteps = Array.from({ length: 20 }, (_, index) => String(index + 1).padStart(2, "0"));

check("MVP finish-line document exists", exists(docPath), docPath);
check("Finish-line has exactly 20 numbered steps", stepRows.length === 20 && expectedSteps.every((step) => stepRows.includes(step)), "The final MVP path should stay concrete.");
check("Finish-line separates local and production", includesAll(doc, ["Lokalt gront", "Skarp drift", "externa bevis"]), "Local MVP must not be confused with production readiness.");
check("Finish-line covers Git and CI", includesAll(doc, ["check:git -- --strict", "git push", "check:sync", "GitHub Actions", "Dockerhub"]), "The first blocker is sync plus external CI proof.");
check("Finish-line covers startup and blank-page proof", includesAll(doc, ["npm run doctor", "npm run smoke:runtime", "vit sida"]), "Startup failures should be first-class MVP blockers.");
check("Finish-line covers company settings", includesAll(doc, ["Foretagsinstallningar", "Foretagsform", "bokforingsmetod", "momsperiod", "F-skatt"]), "Real use needs controlled business settings before invoices.");
check("Finish-line covers customer and invoice flow", includesAll(doc, ["Kundflode", "Fakturaflode", "PDF", "betalning/delbetalning"]), "The core daily workflow should stay visible.");
check("Finish-line covers accounting integrity", includesAll(doc, ["Verifikat balanserar", "check:calculations", "check:period-close"]), "Bookkeeping math and period locks should be guarded.");
check("Finish-line covers evidence and bank CSV", includesAll(doc, ["Underlag", "Bank-CSV", "bankavstamning"]), "Receipts and bank import are part of the practical MVP.");
check("Finish-line covers Stripe and SMTP as tested integrations", includesAll(doc, ["Stripe MVP", "E-post MVP", "SMTP-test", "1580"]), "Payment and email should not be overclaimed before tests.");
check("Finish-line covers VAT and reports", includesAll(doc, ["Momsrapport", "Resultat", "balans", "huvudbok", "saldobalans", "reskontra"]), "The MVP must produce reviewable reports.");
check("Finish-line covers backup and restore", includesAll(doc, ["Backup", "restore drill", "separat testdatabas"]), "Backup is not enough without restore proof.");
check("Finish-line has stop rules", includesAll(doc, ["Stoppregler", "check:release", "PostgreSQL", "API-nycklar", "GitHub"]), "The user needs clear stop signs before real data.");
check("Root exposes finish-line check", rootPackage.scripts?.["check:finish-line"] === "npm --prefix frontend run check:finish-line --", "package.json should expose npm run check:finish-line.");
check("Frontend exposes finish-line check", frontendPackage.scripts?.["check:finish-line"] === "node ../scripts/mvp-finish-line-check.mjs", "frontend/package.json should expose npm run check:finish-line.");
check("Release gate runs finish-line check", releaseGate.includes('"check:finish-line"'), "Release gate should fail if the finish-line contract disappears.");
check("Readiness requires finish-line artifacts", readiness.includes(docPath) && readiness.includes("scripts/mvp-finish-line-check.mjs") && readiness.includes("check:finish-line"), "Readiness should include the finish-line document and command.");
check("Release evidence documents finish-line proof", evidence.includes("check:finish-line"), "Release evidence should show finish-line proof.");
check("Roadmap points to finish-line", roadmap.includes("npm run check:finish-line") && roadmap.includes("mvp-slutspurt.md"), "Roadmap should tell the user how to run the finish-line check.");
check("Finish-line agrees with existing go-live docs", includesAll(`${riskRegister}\n${goLiveDecision}\n${externalProof}\n${mvpUse}`, ["check:sync", "GitHub Actions", "Dockerhub", "RDS", "Stripe", "SMTP", "restore drill"]), "Existing docs should support the same blockers.");
check("Startklar UI exposes the 20-step finish-line", includesAll(frontend, ["mvpFinishLineRows", "MVP-slutspurt", "mvp-finish-line-panel", "mvp-finish-line-row", "01", "20"]), "The final MVP checklist should be visible in the AliBooks Startklar view.");

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks MVP finish-line check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} MVP finish-line check(s) failed.`);
  process.exit(1);
}
