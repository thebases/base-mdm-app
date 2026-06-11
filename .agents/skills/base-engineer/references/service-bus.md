# Service Bus — MQTT/EMQX, WebSocket, Firebase Messaging

## Channel contract

MQTT topics and WebSocket channel names follow the same normalization: `{tenant}/{resource_type}/{resource_id}` or the Odoo-compatible `res.partner/{id}` form.
Always normalize channel names server-side before any publish, subscribe, or DB insert — never trust raw client-supplied channel strings.
The three transports (WebSocket polling, MQTT/EMQX, Firebase FCM) must implement the same channel contract so the client can switch transports without changing business logic.
The `/bus/transport_config` endpoint tells the client which transport to use. Keep this decision server-controlled.

## MQTT / EMQX

QoS 1 (at-least-once) is the default for service bus notifications.
QoS 0 is acceptable only for ephemeral presence heartbeats where a lost message is harmless.
QoS 2 is too expensive for high-frequency notification traffic — avoid it.
Use retained messages only for last-known-state topics (e.g., device online/offline). Do not retain notification payloads; clients poll on reconnect instead.
Topic ACLs must be enforced at the EMQX broker level. Do not rely on the application layer alone for topic isolation.

## WebSocket polling (base_service_bus)

Use the `base_service_bus` table as the durable notification store.
The `last_notification_id` cursor lets clients detect missed messages across reconnects and page loads.
The `/websocket/peek_notifications` endpoint returns rows since the client's last ID. The client drives the poll interval.
Update the client's high-water mark only after it confirms receipt — do not advance the cursor on the server before the client has processed.

## Firebase Cloud Messaging (FCM)

Send only the channel name and `last_id` in the FCM push payload — never embed sensitive data, PII, or full record content.
The FCM notification wakes the client; the client then fetches the full payload from the bus poll endpoint.
FCM delivery is not guaranteed (device offline, token expired). Always have a pull-based fallback (WebSocket poll) for any message that must be seen.
Rotate FCM tokens when they are refreshed by the device and update the stored token immediately.

## Fan-out safety

Bound the recipient set for any fan-out operation. A single event must not trigger unbounded channel lookups or publishes.
When notifying a group (e.g., all users in a role), resolve the recipient list before publishing and log the count.
Use a worker pool for fan-out publishes — never spawn a goroutine per recipient.
