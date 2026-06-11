# BRD — Business Requirements Document

## Purpose and audience

A BRD captures what a business needs to achieve — in business terms, not technical terms. It is written before the SRS or PRD and is the bridge between executive/stakeholder intent and the engineering team. The primary readers are business stakeholders, product managers, and project sponsors. Engineering reads it to understand the business context, not to derive implementation specs.

The BRD answers: why are we doing this, who benefits, what must the outcome be, and what constraints exist — without specifying how the system will be built.

## When to write a BRD

- At the start of a new initiative, before any technical design begins.
- When a business unit is requesting a system change and needs to justify the investment.
- When multiple stakeholder groups must align on scope before engineering work can be scoped.
- When regulatory, compliance, or procurement requirements must be documented formally.

## Key sections

**Executive summary**
One paragraph: the problem, the proposed solution, the expected business outcome, and the timeline. Written for a reader who will only read one paragraph.

**Business objectives**
What specific business outcomes must this project achieve? Frame objectives as measurable results (revenue impact, cost reduction, error rate, user adoption) not as features to build.

**Stakeholder register**
Who has a stake in this project? For each: name/role, interest (what they need), influence (high/medium/low), and communication preference.

**Current state and problem statement**
Describe the current process or system and the specific problems, pains, or opportunities it creates. Use data where available (error rates, manual hours, cost). Avoid jumping to solutions here.

**Proposed solution overview**
A high-level description of the proposed change — in business terms, not technical. What new capability will exist? What process will change? What will users be able to do that they cannot do today?

**Business requirements**
Each requirement is a statement of what the business needs, not how the system will implement it. Format:

- ID: BR-001
- Statement: "The system shall allow finance to reconcile daily transactions without manual spreadsheet work."
- Rationale: Why this requirement exists (links to objective)
- Priority: Must / Should / Could / Won't (MoSCoW)
- Acceptance criterion: How stakeholders will verify this requirement is met

**Assumptions and constraints**
Assumptions the BRD is written under (budget, timeline, technology availability). Constraints that must not be violated (regulatory, data residency, existing contracts).

**Out of scope**
Explicitly name what is NOT included in this initiative. This is as important as what is in scope — it prevents scope creep and sets expectations.

**Success metrics**
How will the business know this project succeeded? Define metrics and target values for each business objective.

**Risks and dependencies**
Business-level risks (budget, adoption, regulatory change) and dependencies on other projects, vendors, or teams.

## Writing discipline

- Keep language accessible to non-technical stakeholders. Avoid system jargon.
- Every requirement must trace to a business objective. If it doesn't, ask why it's there.
- MoSCoW prioritization is mandatory — unstated priority means everything is equal, which leads to scope creep.
- An acceptance criterion that requires a demo or UAT sign-off is acceptable here; precise technical tests belong in the SRS.
