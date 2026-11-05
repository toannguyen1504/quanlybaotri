# Domain event contracts

All messages published to the durable `domain.events` topic exchange use
`domain-event.schema.json`. Routing keys equal `eventType`.

Supported event types:

- `maintenance.ticket.changed`
- `maintenance.system-feedback.changed`
- `inventory.part.used`
- `inventory.part.low-stock`
- `notification.chat.message-created`
- `notification.chat.read-changed`

Identity, organization and asset lookup data is resolved through internal HTTP APIs;
it is not replicated through events.

Consumers must ignore unknown fields inside `data`, deduplicate by `eventId`,
and dead-letter messages with an unsupported `schemaVersion`.

The two `notification.chat.*` events are internal delivery events emitted through
the notification outbox. Their payload contains the recipient IDs and a stable
socket event containing message or read-state identifiers, so duplicate broker
delivery remains safe for clients to collapse.

`maintenance.ticket.changed` schema version 2 includes `customerId`, `siteId` and
the immutable `warrantyStatus` snapshot. Notification recipients remain limited
to the requester, current assignee and the relevant internal management group.
