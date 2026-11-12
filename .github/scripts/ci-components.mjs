#!/usr/bin/env node

import { readFileSync } from "node:fs";

const backendNames = [
  "api-gateway",
  "identity-service",
  "organization-service",
  "asset-service",
  "maintenance-service",
  "payment-service",
  "inventory-service",
  "notification-service",
  "ai-service",
];

const components = Object.fromEntries(
  backendNames.map((name) => [
    name,
    {
      name,
      context: ".",
      dockerfile: `./services/${name}/Dockerfile`,
    },
  ]),
);
components.web = {
  name: "web",
  context: "./angular/first-angular-app",
  dockerfile: "./angular/first-angular-app/Dockerfile",
};

function affectedComponents(paths, forceAll) {
  if (forceAll) return new Set(Object.keys(components));

  const selected = new Set();
  for (const rawPath of paths) {
    const path = rawPath.trim().replaceAll("\\", "/").replace(/^\.\//, "");
    if (!path) continue;

    if (
      path.startsWith(".github/workflows/") ||
      path.startsWith(".github/scripts/")
    ) {
      return new Set(Object.keys(components));
    }

    if (
      ["pom.xml", "mvnw", "mvnw.cmd", ".dockerignore"].includes(path) ||
      path.startsWith(".mvn/")
    ) {
      backendNames.forEach((name) => selected.add(name));
      continue;
    }

    if (path.startsWith("angular/first-angular-app/")) {
      selected.add("web");
      continue;
    }

    const parts = path.split("/");
    if (parts[0] === "services" && backendNames.includes(parts[1])) {
      selected.add(parts[1]);
    }
  }
  return selected;
}

function matrix(names) {
  return {
    include: Object.values(components).filter(({ name }) => names.has(name)),
  };
}

const args = process.argv.slice(2);
const forceAll = args.includes("--all");
const pathsIndex = args.indexOf("--paths-file");
if (!forceAll && pathsIndex === -1) {
  console.error("--paths-file is required unless --all is used");
  process.exit(2);
}

const paths =
  pathsIndex === -1
    ? []
    : readFileSync(args[pathsIndex + 1], "utf8").split(/\r?\n/);
const changed = affectedComponents(paths, forceAll);
const unchanged = new Set(
  Object.keys(components).filter((name) => !changed.has(name)),
);

console.log(`changed=${JSON.stringify(matrix(changed))}`);
console.log(`unchanged=${JSON.stringify(matrix(unchanged))}`);
console.log(`has_changed=${changed.size > 0}`);
console.log(`has_unchanged=${unchanged.size > 0}`);
