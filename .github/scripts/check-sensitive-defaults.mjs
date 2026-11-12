#!/usr/bin/env node

import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const placeholder = /\$\{([A-Z][A-Z0-9_]*)(?::([^{}]*))?}/g;
const sensitiveEnvironment =
  /(?:_PASSWORD|_SECRET(?:_KEY)?|_TOKEN|_API_KEY|_ACCESS_KEY|_PRIVATE_KEY)$/;
const sensitiveKey =
  /(?:password|secret(?:-key)?|bot-token|api-key|access-key|private-key)\s*$/i;
const violations = [];

for (const service of readdirSync("services", { withFileTypes: true })) {
  if (!service.isDirectory()) continue;
  const resources = join("services", service.name, "src", "main", "resources");
  let entries;
  try {
    entries = readdirSync(resources, { withFileTypes: true });
  } catch {
    continue;
  }

  for (const entry of entries) {
    if (
      !entry.isFile() ||
      !/^application.*\.(?:ya?ml|properties)$/.test(entry.name)
    )
      continue;
    const path = join(resources, entry.name);
    const lines = readFileSync(path, "utf8").split(/\r?\n/);

    lines.forEach((line, index) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) return;

      placeholder.lastIndex = 0;
      for (const match of line.matchAll(placeholder)) {
        const [, variable, defaultValue] = match;
        if (
          sensitiveEnvironment.test(variable) &&
          defaultValue !== undefined &&
          defaultValue !== ""
        ) {
          violations.push([path, index + 1, variable]);
        }
      }

      const yamlSeparator = line.indexOf(":");
      const propertySeparator = line.indexOf("=");
      const separator =
        yamlSeparator === -1 ? propertySeparator : yamlSeparator;
      if (separator !== -1) {
        const key = line.slice(0, separator);
        const value = line.slice(separator + 1).trim();
        if (sensitiveKey.test(key) && value && !value.includes("${")) {
          violations.push([path, index + 1, key.trim()]);
        }
      }
    });
  }
}

if (violations.length === 0) {
  console.log(
    "Packaged Spring configuration contains no non-empty credential defaults.",
  );
  process.exit(0);
}

for (const [path, line, name] of violations) {
  console.error(`${path}:${line}: remove the packaged default for ${name}`);
}
process.exit(1);
