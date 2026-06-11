# PRD — Product Requirements Document

## Purpose and audience

A PRD defines what to build and why. Its primary audience is the product team, engineering leads, designers, and business stakeholders. It answers: what problem are we solving, for whom, what does success look like, and what are the boundaries of this effort?

A PRD is not a technical specification. It describes behavior and goals, not implementation. Engineers will use it as input to write the SRS or dev instructions.

## When to write a PRD

- Starting a new feature or product area
- Scoping a significant change to existing behavior
- Aligning multiple teams (design, engineering, marketing, legal) before work begins
- Documenting a decision after a discovery process

## Key sections and what goes in each

**Overview / Problem statement**
One paragraph. What is broken or missing? Who is affected? What is the cost of not solving it? Write this for an executive who has 30 seconds.

**Goals and success metrics**
What does "done well" look like? Include measurable KPIs (conversion rate, task completion time, error rate reduction). If you cannot define a metric, you cannot tell whether you succeeded.

**Non-goals**
Explicitly list what this effort will not address. This prevents the team from solving adjacent problems and protects scope.

**User personas and scenarios**
Who uses this feature? Describe the user types affected and 2–3 narrative scenarios showing the feature in use. Scenarios make abstract requirements concrete and expose edge cases early.

**Requirements**
Functional requirements stated as user-facing behaviors. Each requirement gets a unique ID (e.g., PRD-F-001) and a priority (Must/Should/Could). Avoid implementation details — describe what the user experiences, not how the code works.

**Acceptance criteria**
For each requirement, define the conditions that prove it is implemented correctly. Use Given/When/Then format where helpful.

**Out of scope**
Explicit list of things that were considered but deferred. Include a brief reason so the team does not relitigate them.

**Dependencies and risks**
What must be true before this can ship? What could go wrong? Include technical dependencies, third-party integrations, legal reviews, and timeline risks.

**Open questions**
Unresolved decisions with an owner and target resolution date.

**Appendix**
Supporting research, mockups links, competitive analysis, data, or prior art.

## Writing discipline

- Write requirements in the present tense: "The system allows the user to…" not "The system will allow…"
- Use active voice. Passive voice hides who is responsible.
- Avoid hedge words: "should be able to", "ideally", "if possible". If it is a requirement, state it plainly. If it is optional, mark it Could.
- Link to design mockups and research artifacts — do not embed screenshots inline, link to the source of truth.
