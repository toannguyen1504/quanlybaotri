# Domain event contracts

All messages published to the durable `domain.events` topic exchange use
`domain-event.schema.json`. Routing keys equal `eventType`.

Supported event types:

- `maintenance.ticket.changed`
- `inventory.part.used`
- `inventory.part.low-stock`

Identity, organization and asset lookup data is resolved through internal HTTP APIs;
it is not replicated through events.

Consumers must ignore unknown fields inside `data`, deduplicate by `eventId`,
and dead-letter messages with an unsupported `schemaVersion`.

`maintenance.ticket.changed` schema version 2 includes `customerId`, `siteId` and
the immutable `warrantyStatus` snapshot. Notification recipients remain limited
to the requester, current assignee and the relevant internal management group.
