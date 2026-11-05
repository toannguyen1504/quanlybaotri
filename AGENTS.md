# Project guidance

## Project overview

- This repository is a Java 17/Spring Boot microservice platform with an Angular application under `angular/first-angular-app`.
- Each service owns its database aggregates. Cross-service references are UUIDs; do not introduce cross-service JPA relations or duplicate projection tables.
- Read `SYSTEM_OVERVIEW.md` for service ownership and interaction diagrams. Read the relevant part of `README.md` for local operations and verification commands.
- Preserve unrelated working-tree changes. Never copy secrets from `.env` into source, tests, logs, documentation, or frontend code.

## Skill routing

- Use `$backend-service-change` for behavior changes under `services/**`.
- Use `$angular-feature-change` for application changes under `angular/first-angular-app/**`.
- Use `$cross-service-contract-change` before changing public/internal APIs, gateway routes, RabbitMQ events, or a contract used by more than one service.
- Use `$flyway-schema-change` whenever adding or reviewing a database schema/data migration.
- Use `$project-change-verification` before handing off code, configuration, contract, or migration changes.
- Use `$project-skill-authoring` when adding or substantially changing repository-local skills.
- Combine the applicable skills for work spanning multiple areas. Documentation-only edits do not require code verification unless they change executable examples or operational commands.

## Repository invariants

- `/api/**` accepts user JWTs. `/internal/**` accepts short-lived service JWTs with the required `scope=internal` and target audience.
- Use synchronous HTTP for validation, lookup, and synchronous actions. Use RabbitMQ only for after-commit events that need retry; consumers must be idempotent.
- Authorization must be enforced in backend services. Frontend role checks are presentation behavior, not a security boundary.
- Never edit an already-applied Flyway migration. Add the next migration version in the owning service.
- Do not run destructive Docker/database commands or `docker compose down -v` unless the user explicitly asks to reset data.

## Standard verification

- Backend: `.\mvnw.cmd -s .\.mvn\settings-local.xml test`
- One backend module: `.\mvnw.cmd -s .\.mvn\settings-local.xml -pl services/<service> -am test`
- Compose model: `docker compose --env-file .env.example config --quiet`
- Frontend (from `angular/first-angular-app`): `npm run build` and `npm test`

