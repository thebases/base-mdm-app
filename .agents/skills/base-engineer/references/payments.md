# Payments — Wallet, Ledger, and Reconciliation Patterns

## Lifecycle states

Distinguish authorization, capture, settlement, refund, reversal, fee, and reconciliation as separate lifecycle states.
Never collapse unrelated states into a single status string — each state transition has different invariants and audit requirements.
Distinguish internal ledger state from provider-reported state. They can diverge (provider timeout, delayed callback) and must reconcile explicitly.

## Idempotency

Every externally triggered money movement requires an idempotency key.
The handler must check for a prior record with the same key before executing. Return the prior result if found — do not execute twice.
Idempotency keys must be stored with the transaction record and indexed for fast lookup.

## Audit trail

Preserve a full audit trail for every balance mutation: amount, direction, before/after balance, actor, timestamp, idempotency key, provider reference ID.
Store the raw callback payload hash alongside the parsed fields so the original can be replayed for audit.
Never delete ledger or callback records, even after reconciliation. Archive instead.

## Failure compensation

Design explicit compensation paths for:
- Provider timeout — the payment may have succeeded; poll or wait before marking failed.
- Duplicate success callback — deduplicate by provider reference ID before crediting.
- Late failure callback — reverse the credit if the window allows; otherwise flag for manual review.
- Internal/provider state mismatch — surface as a reconciliation discrepancy, not a silent correction.

## Reconciliation

Reconciliation is a first-class workflow, not a cleanup script.
Run on a schedule; report discrepancies with enough detail to reproduce the cause.
Do not silently correct mismatches — log them, alert, and require human sign-off for corrections above a threshold.

## Compliance

Flag any code that touches PII, card data, bank credentials, or transaction amounts above compliance thresholds — these have regulatory implications.
Secrets (API keys, signing keys) must come from environment variables or a secrets manager, never from source code or the database.
