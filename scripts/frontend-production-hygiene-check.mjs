import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const frontendSrc = path.join(repoRoot, "frontend", "src");
const checkedExtensions = new Set([".js", ".jsx", ".ts", ".tsx"]);
const issues = [];

function walk(directory) {
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry);
    const stats = statSync(fullPath);

    if (stats.isDirectory()) {
      walk(fullPath);
      continue;
    }

    if (!checkedExtensions.has(path.extname(entry))) {
      continue;
    }

    const relativePath = path.relative(repoRoot, fullPath).replaceAll("\\", "/");
    const lowerName = entry.toLowerCase();
    const source = readFileSync(fullPath, "utf8");

    if (lowerName.includes("demo")) {
      issues.push(`${relativePath}: demo files should not live in production src.`);
    }

    if (/\balert\s*\(/.test(source)) {
      issues.push(`${relativePath}: browser alert() should not be used in production UI.`);
    }
  }
}

if (!existsSync(frontendSrc)) {
  console.error("frontend/src does not exist.");
  process.exit(1);
}

walk(frontendSrc);

if (issues.length > 0) {
  console.error("AliBooks frontend production hygiene check failed:");
  for (const issue of issues) {
    console.error(`- ${issue}`);
  }
  process.exit(1);
}

console.log("AliBooks frontend production hygiene check passed: no demo files or alert() calls in frontend/src.");
