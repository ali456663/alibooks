import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");

const ignoredDirectories = new Set([
  ".git",
  ".idea",
  ".agents",
  ".codex",
  ".m2-cache",
  "backups",
  "node_modules",
  "target",
  "dist",
  "outputs",
  "uploads",
  "work"
]);

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

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    if (ignoredDirectories.has(name)) {
      return [];
    }

    const fullPath = path.join(dir, name);
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      return walk(fullPath);
    }

    return ignoredExtensions.has(path.extname(name).toLowerCase()) ? [] : [fullPath];
  });
}

function relative(file) {
  return path.relative(repoRoot, file).replaceAll(path.sep, "/");
}

const findings = [];

for (const file of walk(repoRoot)) {
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

console.log("Secret placeholder check passed: no real-looking API keys found in tracked project files.");
