---
name: project-change-verification
description: Select and run the repository's required checks before handing off backend, frontend, contract, infrastructure, or migration changes.
---

# Project change verification

Produce evidence for the changed scope without overwriting or cleaning unrelated worktree changes.

## Select checks

1. Inspect `git status --short` and the diff to identify changed modules and generated or unrelated files.
2. Start with focused tests for the affected behavior, then run the applicable project checks below.
3. Do not start the full Docker stack, external integrations, or destructive reset commands unless the task requires them and the user has authorized the side effects.

## Check matrix

- One backend module: `.\mvnw.cmd -s .\.mvn\settings-local.xml -pl services/<service> -am test`
- Multiple backend modules, parent build, or shared operational behavior: `.\mvnw.cmd -s .\.mvn\settings-local.xml test`
- Angular code, templates, styles, dependencies, or config: from `angular/first-angular-app`, run `npm test` and `npm run build`.
- Compose, environment mapping, ports, health checks, routing dependencies, or infrastructure config: `docker compose --env-file .env.example config --quiet`.
- S3 integration: run the documented `S3ObjectStorageIntegrationTest` only when the task touches object storage and its local prerequisites are available.

If a command fails, diagnose whether it is a product regression, an environment/prerequisite problem, or an unrelated pre-existing failure. Fix only failures within the requested scope unless the user expands it.

## Report

Return a compact verification summary containing:

- checks that passed;
- failures with the first actionable cause;
- checks not run and the reason;
- residual risk, especially for integration paths not exercised.

Do not claim the change is fully verified when a required check was skipped or failed.

