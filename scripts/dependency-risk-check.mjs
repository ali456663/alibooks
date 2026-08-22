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

const packagePath = "frontend/package.json";
const lockPath = "frontend/package-lock.json";

check("Frontend package exists", exists(packagePath), packagePath);
check("Frontend lockfile exists", exists(lockPath), lockPath);

const packageJson = exists(packagePath) ? json(packagePath) : {};
const lockJson = exists(lockPath) ? json(lockPath) : {};
const dependencies = packageJson.dependencies || {};
const devDependencies = packageJson.devDependencies || {};
const rootLockPackage = lockJson.packages?.[""] || {};

check(
  "NPM lockfile v3 is used",
  lockJson.lockfileVersion >= 3,
  "package-lock.json should be modern and reproducible for npm ci."
);

check(
  "Runtime dependencies are declared",
  Object.keys(dependencies).length > 0,
  "frontend/package.json should list runtime dependencies explicitly."
);

for (const [name, spec] of Object.entries(dependencies)) {
  const lockEntry = lockJson.packages?.[`node_modules/${name}`];
  const isUnsafeSpec = /^(?:\*|latest)$/i.test(spec) || /^(?:git\+|git:|https?:|file:)/i.test(spec);

  check(
    `Runtime dependency has safe package spec: ${name}`,
    !isUnsafeSpec,
    `${name} should use an npm semver range, not latest, wildcard, git, file or URL specs.`
  );

  check(
    `Runtime dependency is locked: ${name}`,
    Boolean(lockEntry?.version),
    `${name} should exist in frontend/package-lock.json with an exact resolved version.`
  );
}

check(
  "Build dependencies are declared",
  Object.keys(devDependencies).length > 0,
  "frontend/package.json should list build tooling such as Vite as devDependencies."
);

for (const [name, spec] of Object.entries(devDependencies)) {
  const lockEntry = lockJson.packages?.[`node_modules/${name}`];
  const isUnsafeSpec = /^(?:\*|latest)$/i.test(spec) || /^(?:git\+|git:|https?:|file:)/i.test(spec);

  check(
    `Build dependency has safe package spec: ${name}`,
    !isUnsafeSpec,
    `${name} should use an npm semver range, not latest, wildcard, git, file or URL specs.`
  );

  check(
    `Build dependency is locked: ${name}`,
    Boolean(lockEntry?.version),
    `${name} should exist in frontend/package-lock.json with an exact resolved version.`
  );
}

check(
  "Root lockfile mirrors runtime dependencies",
  Object.keys(dependencies).every((name) => rootLockPackage.dependencies?.[name] === dependencies[name]),
  "The root package-lock entry should match frontend/package.json dependencies."
);

check(
  "Root lockfile mirrors build dependencies",
  Object.keys(devDependencies).every((name) => rootLockPackage.devDependencies?.[name] === devDependencies[name]),
  "The root package-lock entry should match frontend/package.json devDependencies."
);

const frontendDockerfile = exists("frontend/Dockerfile") ? read("frontend/Dockerfile") : "";
const frontendProdDockerfile = exists("frontend/Dockerfile.prod") ? read("frontend/Dockerfile.prod") : "";

check(
  "Development Dockerfile uses npm ci",
  frontendDockerfile.includes("RUN npm ci"),
  "frontend/Dockerfile should install from package-lock.json."
);

check(
  "Production Dockerfile uses npm ci",
  frontendProdDockerfile.includes("RUN npm ci"),
  "frontend/Dockerfile.prod should install from package-lock.json."
);

check(
  "Production Dockerfile copies package lock before install",
  includesAll(frontendProdDockerfile, ["COPY package*.json ./", "RUN npm ci"]),
  "Docker should copy package files before npm ci for deterministic image builds."
);

const gitStatusScript = exists("scripts/git-release-status.mjs") ? read("scripts/git-release-status.mjs") : "";
check(
  "Git release status treats package lock as important",
  gitStatusScript.includes('"frontend/package-lock.json"'),
  "check:git should catch an unstaged frontend lockfile change before push."
);

const docsText = [
  "docs/go-live-checklista.md",
  "docs/go-live-riskregister.md",
  "docs/release-evidence.md",
  "docs/roadmap-kvar.md"
]
  .filter(exists)
  .map(read)
  .join("\n");

check(
  "External npm audit command is documented",
  docsText.includes("npm run check:audit") && docsText.includes("npm audit --omit=dev --audit-level=critical"),
  "Go-live docs should mention the current online vulnerability audit that cannot be proven from the lockfile alone."
);

const failures = checks.filter((result) => !result.ok);

for (const result of checks) {
  console.log(`${result.ok ? "OK" : "FAIL"} - ${result.name}: ${result.detail}`);
}

console.log("");
console.log(`AliBooks dependency risk check: ${checks.length - failures.length}/${checks.length} required checks passed.`);

if (failures.length > 0) {
  console.error(`${failures.length} dependency risk check(s) failed.`);
  process.exit(1);
}
