---
name: backend-service-change
description: Implement or review behavior changes inside a Spring Boot service under services/**, including its API, application, domain, persistence, security, and service-local tests.
---

# Backend service change

Make a cohesive change in the service that owns the affected business aggregate while preserving service boundaries and authorization behavior.

## Establish scope

1. Read the relevant service entry in `SYSTEM_OVERVIEW.md`, its `pom.xml`, `application.yml`, nearby production code, and nearby tests.
2. Identify the owning service, affected actor roles, customer/site scope, persistence impact, and any callers or consumers outside the service.
3. If an API, gateway route, or event contract crosses a service boundary, also use `$cross-service-contract-change`. If persistence shape changes, also use `$flyway-schema-change`.

## Implement

- Keep domain state and repository access in the owning service. Store foreign aggregate identities as UUIDs; do not add cross-service JPA relationships or projection tables.
- Follow the service's existing package boundaries (`api`, `application`, `domain`, `persistence`, `config`, and `shared`) rather than introducing a new architecture for one change.
- Enforce authorization and tenant/site scope on list, detail, mutation, attachment, and presigned-URL paths. Do not rely on Angular visibility checks.
- Preserve the distinction between user JWTs on `/api/**` and audience-scoped service JWTs on `/internal/**`.
- Add or update focused tests at the narrowest useful layer. Cover permission boundaries and invalid state transitions when the change affects them.
- Update `README.md` or `SYSTEM_OVERVIEW.md` only when the supported behavior, contract, setup, or operational model actually changes.

## Handoff

Use `$project-change-verification` with the owning Maven module and report the behavior changed, tests added or updated, checks run, and any cross-service follow-up.

