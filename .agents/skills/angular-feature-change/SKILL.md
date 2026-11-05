---
name: angular-feature-change
description: Implement or review Angular application changes under angular/first-angular-app, including pages, components, client services, routing, styles, and unit tests.
---

# Angular feature change

Deliver the requested UI behavior using the application's existing Angular patterns and backend contracts.

## Establish scope

1. Inspect the route, page/component, client service, models, shared UI, global styles, and closest `*.spec.ts` files involved.
2. Confirm the backend endpoint and response model before changing client types. If the backend or gateway contract must change, also use `$cross-service-contract-change`.
3. Preserve unrelated frontend edits already present in the working tree.

## Implement

- Reuse established standalone-component, template, form, state, and styling patterns in adjacent features.
- Keep authentication tokens and service credentials out of source and browser-visible configuration. Browser calls go through the gateway's public HTTP or WebSocket surface.
- Treat role-based rendering as presentation only; backend authorization remains authoritative. Handle forbidden, not-found, validation, loading, empty, and retry states when the feature can reach them.
- Keep TypeScript models aligned with the actual API payload. Avoid broad `any` types that hide contract drift.
- Add or update focused Vitest/Angular specs for meaningful behavior. Avoid tests that only assert static text or implementation details.
- Check production bundle budgets when styles or dependencies grow.

## Handoff

Use `$project-change-verification` to run the relevant Angular tests and production build. Report any check skipped because dependencies or browser/runtime prerequisites are unavailable.

