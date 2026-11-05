---
name: flyway-schema-change
description: Create or review a Flyway schema or data migration for one service-owned PostgreSQL database under services/*/src/main/resources/db/migration.
---

# Flyway schema change

Create an append-only migration owned by the service whose aggregate or operational data is changing.

## Prepare

1. Read the owning domain entity, repository queries, existing migrations, and database configuration.
2. Find the highest existing migration version in that service and add the next version. Do not fill an old numbering gap and never edit an applied migration.
3. Confirm that every affected table belongs to this service. A migration must not read or write another service's database.

## Design

- Prefer additive, backward-compatible steps when old and new application versions can overlap during deployment.
- Define nullability, defaults, constraints, indexes, and backfill behavior explicitly. Account for existing rows and realistic table size.
- Use deterministic, rerunnable statements only where the repository's migration pattern requires them; Flyway versioned migrations themselves remain immutable.
- Separate destructive cleanup from the compatibility phase. Do not drop or rewrite material data without explicit authorization, a recovery path, and validated backups.
- For data backfills, define how row counts, orphan records, and failures will be checked. Avoid application-dependent values that can change during migration.

## Verify and report

- Add or update repository/service tests that exercise the new shape or constraint.
- Use `$project-change-verification` for the owning service and Compose model. Run integration migration checks when their dependencies are available.
- Report the migration file, compatibility assumptions, backfill/locking risk, validation performed, and rollback or recovery approach.

