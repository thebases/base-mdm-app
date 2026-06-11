# Requirement Analysis

## Purpose

Requirement analysis transforms raw input (user requests, business goals, pain points, stakeholder interviews) into structured, unambiguous, testable requirements that a team can act on. The output feeds PRDs, SRS documents, and dev instructions.

## Input sources to consider

- Stakeholder interviews or meeting notes
- User research findings, support tickets, or feedback logs
- Existing product behavior (reverse-engineer what is)
- Competitor analysis or benchmark features
- Business constraints (regulatory, budget, timeline, technical)

## Analysis steps

**1. Gather and organize raw input**
Collect everything in one place. Do not filter yet — preserve original language because wording often reveals intent.

**2. Identify actors and goals**
For each requirement, answer: who wants this, what do they want to accomplish, and why does it matter to the business? Requirements without a clear actor and goal are usually solutions disguised as needs.

**3. Separate problems from solutions**
Raw requests are often framed as solutions ("add a filter button"). Rewrite them as problems first ("users cannot find records by date range"), then describe the solution separately. This preserves design flexibility.

**4. Classify requirements**
- **Functional**: what the system must do (behaviors, inputs, outputs)
- **Non-functional**: how the system must perform (speed, reliability, security, accessibility, scalability)
- **Constraint**: what limits the solution space (existing tech stack, regulatory rules, budget, timeline)
- **Out of scope**: explicitly named exclusions — prevents scope creep

**5. Write testable requirements**
A requirement is testable when you can answer yes or no to "does the system meet this?" Vague: "the system should be fast." Testable: "search results must return in under 500ms at p95."

**6. Prioritize**
Use MoSCoW or a simple stack-rank:
- **Must have**: launch blocker; the product does not ship without this
- **Should have**: high value, strong workaround exists if missing
- **Could have**: nice to have; cut first under time pressure
- **Won't have (this release)**: explicitly deferred — document to manage expectations

**7. Surface open questions**
List every decision that requires input from a stakeholder, subject matter expert, or another team. Unresolved questions left implicit become bugs.

## Quality checklist before handing off

- Each requirement has a unique ID for traceability
- Each requirement is atomic (one thing per statement)
- No requirement contains "and/or" — split them
- Every functional requirement has at least one acceptance criterion
- Non-functional requirements have measurable thresholds
- Out-of-scope items are explicitly listed
- Open questions are listed with an owner and due date
