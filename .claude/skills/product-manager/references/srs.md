# SRS — Software Requirements Specification

## Purpose and audience

An SRS translates the PRD into precise, implementation-ready requirements for the engineering team, QA, and architects. Where the PRD says "users can filter by date", the SRS says what the filter input looks like, what values it accepts, how errors are handled, and what the API contract is.

The SRS is the contract between product and engineering. Both sides sign off on it before significant implementation work begins.

## When to write an SRS

- After the PRD is approved and before sprint planning begins
- When the feature has non-trivial logic, external integrations, or cross-team dependencies
- When QA needs precise behavior descriptions to write test cases
- When a feature will be maintained by a team that did not build it

## Key sections

**Introduction**
Scope, purpose, intended audience, and a list of abbreviations or domain terms used in the document.

**System overview**
A short description of the system context — what this component does and how it fits into the larger architecture. Include a context diagram if the integration surface is complex.

**Functional requirements**
Each requirement has:
- Unique ID (SRS-F-001)
- Title and description (precise, not vague)
- Inputs: what data arrives and from where
- Processing: what the system does with it
- Outputs: what the system returns or persists
- Error conditions: what happens when inputs are invalid, dependencies are unavailable, or constraints are violated
- Priority: Must / Should / Could

Write requirements at the boundary of the system — what enters, what exits, what state changes. Do not specify internal implementation (which class, which algorithm) unless there is a real constraint.

**Non-functional requirements**
- Performance: response time thresholds (p50, p95, p99), throughput (requests/sec), batch processing time
- Reliability: uptime target, acceptable error rate, recovery time objective (RTO)
- Security: authentication, authorization, data encryption at rest and in transit, audit logging
- Scalability: expected data volume, user concurrency, growth horizon
- Accessibility: WCAG level, assistive technology support

**Data requirements**
Entity definitions, field names, types, validation rules, and constraints. Reference the data model or schema migration if it already exists. Define what is stored, how long it is retained, and who can access it.

**API and interface contracts**
Endpoint names, HTTP methods, request/response shapes (with example payloads), error codes and messages, and rate limits. For event-driven interfaces, define the message schema and delivery guarantees.

**Constraints**
Technology stack, regulatory requirements, backward compatibility rules, deployment environment limitations.

**Assumptions and dependencies**
What must be true for these requirements to hold? List external services, infrastructure, third-party APIs, and upstream teams.

**Open questions**
Decisions still pending, with an owner and resolution date.

## Quality checklist

- Every functional requirement is testable
- Every error condition is specified (not just the happy path)
- All API contracts have example request/response pairs
- Non-functional requirements have measurable thresholds
- Data fields have types, validation rules, and nullability stated
- Unique IDs are assigned so QA can trace test cases back to requirements
