import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function fail(message) {
  console.error(`Docker config check failed: ${message}`);
  process.exit(1);
}

function requireIncludes(source, expected, label) {
  if (!source.includes(expected)) {
    fail(`${label} should include ${expected}.`);
  }
}

const frontendDockerfile = read("frontend/Dockerfile");
const frontendProdDockerfile = read("frontend/Dockerfile.prod");
const backendDockerfile = read("backend/Dockerfile");
const compose = read("docker-compose.yml");
const composeProd = read("docker-compose.prod.yml");
const nginx = read("frontend/nginx.conf");
const entrypoint = read("frontend/docker-entrypoint.sh");

requireIncludes(frontendDockerfile, "FROM node:24-alpine", "frontend/Dockerfile");
requireIncludes(frontendProdDockerfile, "FROM node:24-alpine", "frontend/Dockerfile.prod");
requireIncludes(frontendDockerfile, "npm ci", "frontend/Dockerfile");
requireIncludes(frontendProdDockerfile, "npm ci", "frontend/Dockerfile.prod");
requireIncludes(frontendDockerfile, "COPY public ./public", "frontend/Dockerfile");
requireIncludes(frontendProdDockerfile, "COPY public ./public", "frontend/Dockerfile.prod");
requireIncludes(frontendDockerfile, "COPY vite.config.js ./", "frontend/Dockerfile");
requireIncludes(frontendProdDockerfile, "COPY vite.config.js ./", "frontend/Dockerfile.prod");

requireIncludes(backendDockerfile, "maven:3.9.9-eclipse-temurin-21", "backend/Dockerfile");
requireIncludes(backendDockerfile, "eclipse-temurin:21-jre", "backend/Dockerfile");
requireIncludes(backendDockerfile, "EXPOSE 3000", "backend/Dockerfile");

requireIncludes(compose, "5157:5157", "docker-compose.yml");
requireIncludes(compose, "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/cloudshop", "docker-compose.yml");
requireIncludes(compose, "SPRING_JPA_HIBERNATE_DDL_AUTO=${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}", "docker-compose.yml");
requireIncludes(compose, "APP_CORS_ALLOWED_ORIGINS=${APP_CORS_ALLOWED_ORIGINS:-http://localhost:5157}", "docker-compose.yml");
requireIncludes(compose, "postgres:16", "docker-compose.yml");

requireIncludes(composeProd, "cloudshop-frontend", "docker-compose.prod.yml");
requireIncludes(composeProd, "cloudshop-backend", "docker-compose.prod.yml");
requireIncludes(composeProd, "SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL}", "docker-compose.prod.yml");
requireIncludes(composeProd, "SPRING_JPA_HIBERNATE_DDL_AUTO=${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}", "docker-compose.prod.yml");
requireIncludes(composeProd, "APP_CORS_LOCAL_DEV_ENABLED=${APP_CORS_LOCAL_DEV_ENABLED:-false}", "docker-compose.prod.yml");

requireIncludes(nginx, "proxy_pass http://backend:3000/;", "frontend/nginx.conf");
requireIncludes(entrypoint, "window.__ALIBOOKS_CONFIG__", "frontend/docker-entrypoint.sh");

console.log("Docker config check passed.");
