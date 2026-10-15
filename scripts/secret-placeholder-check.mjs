import { existsSync, lstatSync, readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");

const ignoredExtensions = new Set([
  ".png",
  ".jpg",
  ".jpeg",
  ".webp",
  ".gif",
  ".pdf",
  ".zip",
  ".jar",
  ".class"
]);

const patterns = [
  { name: "OpenAI-compatible API key", regex: /\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\b/g },
  { name: "OpenRouter API key", regex: /\bsk-or-v1-[A-Za-z0-9]{32,}\b/g },
  { name: "Stripe secret key", regex: /\bsk_(?:live|test)_[A-Za-z0-9]{16,}\b/g },
  { name: "Stripe webhook secret", regex: /\bwhsec_[A-Za-z0-9]{16,}\b/g },
  { name: "Hugging Face token", regex: /\bhf_[A-Za-z0-9]{20,}\b/g },
  { name: "Google/Gemini API key", regex: /\bAIzaSy[A-Za-z0-9_-]{20,}\b/g },
  { name: "AWS access key", regex: /\bAKIA[0-9A-Z]{16}\b/g },
  { name: "GitHub token", regex: /\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\b|\bgithub_pat_[A-Za-z0-9_]{22,}\b/g },
  { name: "JWT token", regex: /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/g },
  { name: "Private key", regex: /-----BEGIN [A-Z ]*PRIVATE KEY-----/g }
];

const gitignore = readFileSync(path.join(repoRoot, ".gitignore"), "utf8");
const requiredIgnoreRules = [
  ".env",
  ".env.*",
  "!.env.example",
  "!.env.production.example",
  "*.dump",
  "*.backup",
  "*.bak",
  "*.db",
  "*.sqlite",
  "*.sqlite3",
  "*.csv",
  "*.xls",
  "*.xlsx",
  "*.ofx",
  "*.qif"
];

const missingIgnoreRules = requiredIgnoreRules.filter((rule) => !gitignore.split(/\r?\n/).includes(rule));
if (missingIgnoreRules.length > 0) {
  console.error("Secret placeholder check failed. .gitignore is missing env safety rules:");
  missingIgnoreRules.forEach((rule) => console.error(`- ${rule}`));
  process.exit(1);
}

function candidateFiles() {
  const result = spawnSync("git", ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], {
    cwd: repoRoot,
    encoding: "buffer",
    shell: false
  });
  if (result.error || result.status !== 0) {
    console.error("Secret placeholder check failed: cannot enumerate Git release candidates.");
    process.exit(1);
  }

  return [...new Set(result.stdout.toString("utf8").split("\0").filter(Boolean))]
    .filter((file) => !ignoredExtensions.has(path.extname(file).toLowerCase()))
    .map((file) => path.join(repoRoot, file))
    .filter(existsSync);
}

function relative(file) {
  return path.relative(repoRoot, file).replaceAll(path.sep, "/");
}

const findings = [];

for (const file of candidateFiles()) {
  if (lstatSync(file).isSymbolicLink()) {
    findings.push(`${relative(file)} is a symlink; review its target before release`);
    continue;
  }
  const text = readFileSync(file, "utf8");

  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern.regex)) {
      const line = text.slice(0, match.index).split(/\r?\n/).length;
      findings.push(`${relative(file)}:${line} matches ${pattern.name}`);
    }
  }
}

if (findings.length > 0) {
  console.error("Secret placeholder check failed. Move real keys to environment variables before committing:");
  findings.forEach((finding) => console.error(`- ${finding}`));
  process.exit(1);
}

console.log("Secret placeholder check passed: no real-looking API keys found in tracked or unignored new project files.");
