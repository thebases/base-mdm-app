# Kafka — Consumer and Producer Patterns

## Consumer idempotency

Every consumer must be idempotent — the same message may arrive twice, out of order, or after a long delay.
Design for at-least-once delivery. Check for a prior record with the same `idempotency_key` or `event_id` before executing any state mutation.

## Offset management

Commit offsets only after the message has been fully processed and persisted.
Never commit before processing — this converts at-least-once into at-most-once delivery and loses events silently.
Use manual offset commit (not auto-commit) for any consumer touching financial or audit-sensitive state.

## Dead-letter and error handling

Route messages that fail after N retries to a dead-letter topic.
Log the failure reason, original offset, partition, and raw message key before forwarding to the DLQ.
Monitor DLQ depth as an operational alert — a growing DLQ means silent data loss if not investigated.

## Consumer groups

Consumer group IDs must be stable and unique per logical consumer role.
Changing a group ID causes the consumer to reprocess from the beginning of the retention window — treat this as a migration event.
Scale consumers to match partition count; adding more consumers than partitions leaves extras idle.

## Message schema

Keep schemas backward compatible: add optional fields, never remove or rename deployed fields without a migration window.
For financial events include: `idempotency_key`, `event_id`, `occurred_at`, `aggregate_id`, `aggregate_type`, `sequence`.
Partition by the natural key of the resource being mutated (wallet ID, account ID) to preserve per-resource ordering within a partition.

## Producer patterns

Use synchronous sends (wait for broker ack) for financial mutations. Async fire-and-forget is only acceptable for metrics and logging events.
Set `acks=all` and `min.insync.replicas=2` for topics that carry financial events.
Include the sender's `service_name` and `instance_id` in message headers for debugging.
