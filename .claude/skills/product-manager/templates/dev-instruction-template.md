# Dev Instructions: [Feature Name]

| Field | Value |
|---|---|
| Author | |
| Status | Draft / Ready for dev / In progress |
| Date | YYYY-MM-DD |
| Related PRD | |
| Related SRS | |
| Assignee(s) | |
| Target sprint / milestone | |

---

## 1. Context and goal

<!-- One paragraph. What is being built, why it matters, and the single most important constraint to keep in mind. -->

---

## 2. Scope of changes

<!-- List the packages, modules, files, or services expected to change. Sets the blast radius. -->

**Backend:**
- `pkg/...`
- `pkg/...`

**Frontend:**
- `src/...`
- `src/...`

**Database:**
- Migration: `migrations/YYYYMMDD_description.sql`

**Infrastructure / config:**
- 

---

## 3. Architecture and approach

<!-- High-level design decision. Why this approach? What alternatives were considered and why rejected? -->

### Chosen approach
<!-- Describe the implementation strategy -->

### Alternatives considered

| Alternative | Reason not chosen |
|---|---|
| | |

### Architecture diagram / flow
<!-- ASCII diagram, Mermaid, or link to external diagram -->

```
[Component A] → [Component B] → [DB]
```

---

## 4. Data model changes

### 4.1 New / modified tables

```sql
-- Migration: add_xxx_to_yyy
ALTER TABLE yyy ADD COLUMN xxx TYPE NOT NULL DEFAULT '';
CREATE INDEX idx_yyy_xxx ON yyy (xxx);
```

**Migration safety:** Online / Requires maintenance window
**Rollback:** `ALTER TABLE yyy DROP COLUMN xxx;`

### 4.2 Schema impact

| Table | Change | Notes |
|---|---|---|
| | | |

---

## 5. API changes

### 5.1 New endpoints

**`[METHOD] /api/v1/[path]`**
- Auth: required / optional
- Request: `{ field: type }`
- Response (200): `{ field: type }`
- Errors: 400 on validation failure, 403 on insufficient permission

### 5.2 Modified endpoints

| Endpoint | Change | Backward compatible? |
|---|---|---|
| | | |

---

## 6. Business logic walkthrough

<!-- Step-by-step through the core logic. Call out where transactions, idempotency checks, RBAC, events, and audit logging are required. -->

**Step 1: [Name]**
<!-- What happens, why, and any constraints -->

**Step 2: [Name]**
<!-- What happens -->

**Key invariants to preserve:**
- [ ] Idempotency: check for existing record with same key before executing
- [ ] Transaction boundary: steps X–Y must be atomic
- [ ] RBAC: verify `env.RoleID` has permission before step Z
- [ ] Audit: log every state transition with actor, timestamp, and before/after values

---

## 7. Integration points

| Integration | Direction | Protocol | Failure behavior |
|---|---|---|---|
| | Inbound / Outbound | HTTP / Kafka / MQTT | Retry / DLQ / Fail fast |

---

## 8. Testing guidance

### Unit tests (must have)
- [ ] Happy path: valid input → expected output
- [ ] Validation: invalid input → correct error
- [ ] Edge case: [describe]

### Integration tests (must have for these paths)
- [ ] [Critical path 1]
- [ ] [Critical path 2]

### Manual test checklist
- [ ] [Scenario to verify manually]

---

## 9. Definition of done

- [ ] Code changes complete and self-reviewed
- [ ] Migration written, tested in dev, and reviewed
- [ ] Unit tests passing, coverage on new paths
- [ ] Integration tests added for critical paths
- [ ] API response matches SRS contract
- [ ] Structured logging added for key operations
- [ ] Error cases return the correct status codes and messages
- [ ] RBAC enforced on all new endpoints
- [ ] PR description links to this document and the related tickets
- [ ] Feature flag applied (if staged rollout required)

---

## 10. Known constraints and gotchas

<!-- Things that will waste time if discovered mid-implementation. Ordering dependencies, locked patterns, prior bugs, performance-sensitive paths. -->

- ⚠️ 
- ⚠️ 
