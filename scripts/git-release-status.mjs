import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const args = new Set(process.argv.slice(2));
const strict = args.has("--strict");
const requirePushed = args.has("--require-pushed");

const importantPrefixes = [
  ".env.example",
  ".env.production.example",
  ".github/workflows/",
  ".gitignore",
  "README.md",
  "backend/pom.xml",
  "backend/Dockerfile",
  "backend/src/main/",
  "backend/src/test/",
  "docker-compose.yml",
  "docker-compose.prod.yml",
  "docs/",
  "frontend/Dockerfile",
  "frontend/Dockerfile.prod",
  "frontend/nginx.conf",
  "frontend/package.json",
  "frontend/package-lock.json",
  "frontend/src/",
  "frontend/vite.config.js",
  "scripts/"
];

function runGit(commandArgs) {
  const result = spawnSync("git", commandArgs, {
    cwd: repoRoot,
    encoding: "utf8",
    shell: false
  });

  if (result.error) {
    console.error(`Could not run git: ${result.error.message}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    console.error(result.stderr || result.stdout);
    process.exit(result.status || 1);
  }

  return result.stdout.trim();
}

function normalizePath(value) {
  return value.replaceAll("\\", "/");
}

function parseStatusLine(line) {
  const status = line.slice(0, 2);
  let file = line.slice(3).trim();

  if (file.includes(" -> ")) {
    file = file.split(" -> ").pop().trim();
  }

  return {
    status,
    file: normalizePath(file),
    staged: status[0] !== " " && status[0] !== "?",
    unstaged: status[1] !== " ",
    untracked: status === "??"
  };
}

function isImportant(file) {
  return importantPrefixes.some((prefix) => {
    if (prefix.endsWith("/")) {
      return file.startsWith(prefix);
    }
    return file === prefix;
  });
}

function parseBranchStatus(summaryLine) {
  const aheadMatch = summaryLine.match(/ahead (\d+)/);
  const behindMatch = summaryLine.match(/behind (\d+)/);
  const branchMatch = summaryLine.match(/^##\s+([^\s.]+)(?:\.\.\.([^\s\[]+))?/);

  return {
    branch: branchMatch?.[1] || "",
    upstream: branchMatch?.[2] || "",
    ahead: aheadMatch ? Number(aheadMatch[1]) : 0,
    behind: behindMatch ? Number(behindMatch[1]) : 0
  };
}

const rawStatus = runGit(["status", "--porcelain=v1"]);
const branchStatus = parseBranchStatus(runGit(["status", "-sb"]).split(/\r?\n/)[0] || "");
const entries = rawStatus
  .split(/\r?\n/)
  .filter(Boolean)
  .map(parseStatusLine)
  .filter((entry) => isImportant(entry.file));

const unstaged = entries.filter((entry) => entry.unstaged || entry.untracked);
const staged = entries.filter((entry) => entry.staged);
const untracked = entries.filter((entry) => entry.untracked);

console.log("AliBooks git release status");
console.log("===========================");
console.log(`Important changed files: ${entries.length}`);
console.log(`Staged important files: ${staged.length}`);
console.log(`Unstaged/untracked important files: ${unstaged.length}`);
console.log(`Untracked important files: ${untracked.length}`);
console.log(`Branch: ${branchStatus.branch || "unknown"}`);
console.log(`Upstream: ${branchStatus.upstream || "not configured"}`);
console.log(`Commits ahead of upstream: ${branchStatus.ahead}`);
console.log(`Commits behind upstream: ${branchStatus.behind}`);

if (entries.length > 0) {
  console.log("");
  console.log("Important files not yet clean:");
  for (const entry of entries) {
    console.log(`${entry.status} ${entry.file}`);
  }
}

console.log("");
if (strict && entries.length > 0) {
  console.error("Git release status failed in strict mode.");
  console.error("Commit or deliberately remove these changes before push/deploy.");
  process.exit(1);
}

if (requirePushed && branchStatus.ahead > 0) {
  console.error("Git sync check failed.");
  console.error(`${branchStatus.ahead} local commit(s) have not been pushed to ${branchStatus.upstream || "the upstream branch"}.`);
  process.exit(1);
}

if (requirePushed && branchStatus.behind > 0) {
  console.error("Git sync check failed.");
  console.error(`${branchStatus.behind} remote commit(s) are not present locally. Pull/review before release.`);
  process.exit(1);
}

if (entries.length > 0) {
  console.log("Next before push:");
  console.log("1. Review these files.");
  console.log("2. Run npm run check:release -- --with-backend --with-docker-build.");
  console.log("3. Stage, commit and push the intended release changes.");
  console.log("4. Run npm run check:git -- --strict after commit to confirm nothing important was left outside git.");
} else {
  console.log("Git release status is clean for important AliBooks files.");
  if (branchStatus.ahead > 0) {
    console.log(`Push pending: ${branchStatus.ahead} local commit(s) are not on ${branchStatus.upstream || "upstream"}.`);
  }
  if (branchStatus.behind > 0) {
    console.log(`Pull/review pending: ${branchStatus.behind} upstream commit(s) are not local.`);
  }
}
