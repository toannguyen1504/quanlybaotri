---
name: cross-service-contract-change
description: Plan, implement, or review a contract change spanning services, gateway routes, Angular clients, internal HTTP APIs, or RabbitMQ events.
---

# Cross-service contract change

Keep producers, consumers, routing, authentication, compatibility, and rollout behavior consistent across the repository.

## Map the contract

1. Identify the contract owner, every in-repository producer/caller and consumer, the gateway or RabbitMQ path, and any Angular model that exposes it.
2. Search by endpoint, DTO/record name, routing key, queue, and serialized field names. Inspect `contracts/events/` and `infra/rabbitmq/` for event changes.
3. State whether the change is backward compatible. For a breaking change, define a safe producer/consumer rollout or request an explicit product decision before implementation.

## HTTP contracts

- Keep public user traffic under `/api/**` and service traffic under `/internal/**`.
- Internal clients must obtain a short-lived service token with `scope=internal` and the target service audience. Forward user JWTs when the downstream decision depends on the current user.
- Preserve correlation IDs, bounded timeouts, and retry only GET or otherwise idempotent calls.
- Update the owning controller/DTO, all callers, gateway routing/security, Angular types, tests, and supported documentation together when applicable.

## Event contracts

- Use RabbitMQ only for after-commit events that need retry. Keep synchronous validation and lookup on HTTP.
- Publish from the aggregate-owning service and make consumers idempotent. Check retry, DLQ, duplicate delivery, and out-of-order implications.
- Update the event example/schema in `contracts/events/`, producer serialization, consumer deserialization, RabbitMQ topology, and contract-focused tests together.
- Do not revive removed projection event families or create a cross-service database projection as a shortcut.

## Handoff

Summarize the owner, affected participants, compatibility/rollout decision, and verification evidence. Use `$project-change-verification` across every changed module plus Compose configuration when routing or topology changed.

