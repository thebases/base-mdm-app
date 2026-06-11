# SRS: [Feature / Component Name]

| Field | Value |
|---|---|
| Author | |
| Status | Draft / In Review / Approved |
| Version | 1.0 |
| Date | YYYY-MM-DD |
| Related PRD | |
| Reviewers | |

---

## 1. Introduction

### 1.1 Purpose
<!-- What this document specifies and who should read it. -->

### 1.2 Scope
<!-- The system or component being described. What is included and excluded. -->

### 1.3 Definitions and abbreviations

| Term | Definition |
|---|---|
| | |

### 1.4 References
- PRD:
- Data model / schema:
- API contract:
- Architecture diagram:

---

## 2. System overview
<!-- Short description of the component and how it fits into the larger system. Include a context diagram if the integration surface is complex. -->

---

## 3. Functional requirements

### Requirement format
Each requirement includes: ID · Title · Description · Inputs · Processing · Outputs · Error conditions · Priority.

---

### SRS-F-001: [Requirement title]

**Description:**
<!-- Precise behavioral statement. What the system does, not how. -->

**Inputs:**
| Input | Source | Type | Validation rules |
|---|---|---|---|
| | | | |

**Processing:**
<!-- Step-by-step description of what the system does with the inputs. -->
1. 
2. 

**Outputs:**
| Output | Destination | Type | Description |
|---|---|---|---|
| | | | |

**Error conditions:**
| Condition | System behavior | Error response |
|---|---|---|
| | | |

**Acceptance criteria:**
- [ ] 
- [ ] 

**Priority:** Must / Should / Could

---

### SRS-F-002: [Requirement title]
<!-- Copy the block above for each requirement -->

---

## 4. Non-functional requirements

### 4.1 Performance

| Metric | Threshold | Measurement conditions |
|---|---|---|
| Response time (p95) | | |
| Throughput | | |
| Batch processing | | |

### 4.2 Reliability

| Attribute | Target |
|---|---|
| Uptime | |
| Error rate | |
| RTO (Recovery Time Objective) | |
| RPO (Recovery Point Objective) | |

### 4.3 Security

| Requirement | Detail |
|---|---|
| Authentication | |
| Authorization / RBAC | |
| Data encryption at rest | |
| Data encryption in transit | |
| Audit logging | |
| PII handling | |

### 4.4 Scalability

| Dimension | Current | 12-month target |
|---|---|---|
| Data volume | | |
| Concurrent users | | |
| Requests per second | | |

### 4.5 Accessibility
<!-- WCAG level, assistive technology requirements -->

---

## 5. Data requirements

### 5.1 Data model changes

| Table / Collection | Change type | Description |
|---|---|---|
| | New / Altered / Deleted | |

### 5.2 Field definitions

**Table: [table_name]**

| Field | Type | Nullable | Default | Validation | Description |
|---|---|---|---|---|---|
| | | | | | |

### 5.3 Data retention and access

| Data type | Retention period | Who can access | Deletion method |
|---|---|---|---|
| | | | |

---

## 6. API and interface contracts

### 6.1 [Endpoint name]

| Attribute | Value |
|---|---|
| Method | GET / POST / PUT / DELETE / PATCH |
| Path | `/api/v1/...` |
| Auth | Required / Optional — type: |
| Rate limit | |

**Request:**
```json
{
  "field": "type — description"
}
```

**Response (200):**
```json
{
  "field": "type — description"
}
```

**Error responses:**

| Status | Code | When |
|---|---|---|
| 400 | `validation_error` | |
| 401 | `unauthorized` | |
| 403 | `forbidden` | |
| 404 | `not_found` | |
| 422 | `unprocessable` | |
| 500 | `internal_error` | |

---

## 7. Constraints

| Constraint | Description |
|---|---|
| Technology stack | |
| Backward compatibility | |
| Regulatory / compliance | |
| Deployment environment | |

---

## 8. Assumptions and dependencies

### 8.1 Assumptions
<!-- What must be true for these requirements to hold -->
- 

### 8.2 Dependencies

| Dependency | Type | Owner | Status |
|---|---|---|---|
| | Internal / External / Infrastructure | | |

---

## 9. Open questions

| # | Question | Owner | Due date | Status |
|---|---|---|---|---|
| 1 | | | | Open |
