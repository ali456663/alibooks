import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const backendSourceRoot = path.join(repoRoot, "backend", "src");

function fail(message) {
  console.error(`Backend wiring check failed: ${message}`);
  process.exit(1);
}

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const fullPath = path.join(dir, name);
    return statSync(fullPath).isDirectory() ? walk(fullPath) : [fullPath];
  });
}

function read(file) {
  return readFileSync(file, "utf8");
}

function findMatchingParen(source, openIndex) {
  let depth = 0;
  let quote = "";
  let escaped = false;

  for (let index = openIndex; index < source.length; index += 1) {
    const character = source[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (character === "\\") {
        escaped = true;
      } else if (character === quote) {
        quote = "";
      }
      continue;
    }

    if (character === "\"" || character === "'") {
      quote = character;
      continue;
    }

    if (character === "(") {
      depth += 1;
    } else if (character === ")") {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  return -1;
}

function countTopLevelItems(source) {
  const trimmed = source.trim();
  if (!trimmed) {
    return 0;
  }

  let depth = 0;
  let angleDepth = 0;
  let quote = "";
  let escaped = false;
  let count = 1;

  for (const character of source) {
    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (character === "\\") {
        escaped = true;
      } else if (character === quote) {
        quote = "";
      }
      continue;
    }

    if (character === "\"" || character === "'") {
      quote = character;
      continue;
    }

    if (character === "(" || character === "[" || character === "{") {
      depth += 1;
    } else if (character === ")" || character === "]" || character === "}") {
      depth -= 1;
    } else if (character === "<") {
      angleDepth += 1;
    } else if (character === ">" && angleDepth > 0) {
      angleDepth -= 1;
    } else if (character === "," && depth === 0 && angleDepth === 0) {
      count += 1;
    }
  }

  return count;
}

function invocationArgumentCounts(source, className) {
  const counts = [];
  const pattern = new RegExp(`new\\s+${className}\\s*\\(`, "g");
  let match;

  while ((match = pattern.exec(source)) !== null) {
    const openIndex = source.indexOf("(", match.index);
    const closeIndex = findMatchingParen(source, openIndex);
    if (closeIndex < 0) {
      fail(`Could not parse constructor call for ${className}.`);
    }

    counts.push(countTopLevelItems(source.slice(openIndex + 1, closeIndex)));
    pattern.lastIndex = closeIndex + 1;
  }

  return counts;
}

function declarationCounts(source, regex) {
  const counts = new Map();
  let match;

  while ((match = regex.exec(source)) !== null) {
    const className = match[1];
    const openIndex = source.indexOf("(", match.index);
    const closeIndex = findMatchingParen(source, openIndex);
    if (closeIndex < 0) {
      fail(`Could not parse declaration for ${className}.`);
    }

    counts.set(className, countTopLevelItems(source.slice(openIndex + 1, closeIndex)));
    regex.lastIndex = closeIndex + 1;
  }

  return counts;
}

const javaFiles = walk(backendSourceRoot).filter((file) => file.endsWith(".java"));
const mainFiles = javaFiles.filter((file) => file.includes(`${path.sep}main${path.sep}`));
const allSource = javaFiles.map(read).join("\n");
const mainSource = mainFiles.map(read).join("\n");

const controllerConstructors = declarationCounts(mainSource, /public\s+([A-Z][A-Za-z0-9]*Controller)\s*\(/g);
const records = declarationCounts(mainSource, /public\s+record\s+([A-Z][A-Za-z0-9]*)\s*\(/g);
const problems = [];

for (const [className, expectedCount] of controllerConstructors) {
  const counts = invocationArgumentCounts(allSource, className);
  const mismatches = counts.filter((count) => count !== expectedCount);

  if (mismatches.length > 0) {
    problems.push(`${className} expects ${expectedCount} constructor argument(s), found call(s) with ${unique(mismatches).join(", ")}.`);
  }
}

for (const [recordName, expectedCount] of records) {
  const counts = invocationArgumentCounts(allSource, recordName);
  const mismatches = counts.filter((count) => count !== expectedCount);

  if (mismatches.length > 0) {
    problems.push(`${recordName} expects ${expectedCount} record component(s), found call(s) with ${unique(mismatches).join(", ")}.`);
  }
}

function unique(values) {
  return [...new Set(values)].sort((first, second) => first - second);
}

if (problems.length > 0) {
  fail(`\n- ${problems.join("\n- ")}`);
}

console.log(`Backend wiring check passed: ${controllerConstructors.size} controller constructor(s), ${records.size} record(s).`);
