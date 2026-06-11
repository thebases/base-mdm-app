# Product Manager Skill Guide

Load this guide after `product-manager` triggers when you need the detailed writing stance, document workflow, or reference/template selection map.

## Core stance

- Clarify intent before writing. A requirement that can be interpreted two ways will be implemented the wrong way.
- Calibrate language, depth, and terminology to the document's real audience.
- Requirements must be testable. If you cannot write an acceptance criterion, the requirement is not ready.
- Trace features back to user needs or business goals.
- Read any provided document, notes, or constraints fully before proceeding.

## Path aliases (canonical)

| Alias | Real path | Role in the workflow |
| --- | --- | --- |
| `knowledge` | `.agents/knowledge/` | Step 1 research input — read before writing anything |
| `planning` | `.agents/memory/context/` | Step 4 output destination — save finished documents here |

## Workflow details

- Complete the 4-step Research → Gap → Pros/Cons → Plan pipeline (defined in `SKILL.md`) before selecting a document template.
- If input material is provided, read it fully before starting — it belongs to Step 1.
- Ask clarifying questions when ambiguity would change scope, acceptance criteria, or decisions. This is part of Step 2 (gap-finding).
- Use the matching template from `templates/` when one exists; do not draft from scratch unnecessarily.
- Write assumptions, open questions, and out-of-scope items explicitly.
- End by summarizing what was written, what was assumed, and what still needs stakeholder input.

## Reference and template selection

Load only the file needed for the task:

| Task / keyword | Reference file | Template file |
| --- | --- | --- |
| Analyze requirements, gather needs, break down features | `references/requirement-analysis.md` | — |
| PRD, product requirements, product spec, feature spec | `references/prd.md` | `templates/prd-template.md` |
| SRS, software requirements, technical spec, functional spec | `references/srs.md` | `templates/srs-template.md` |
| BRD, business requirements, business case, stakeholder requirements | `references/brd.md` | `templates/brd-template.md` |
| Guideline, standard, best-practice doc, coding standard, process guide | `references/guideline.md` | `templates/guideline-template.md` |
| Dev instructions, coding guide, implementation spec, handoff doc | `references/dev-instruction.md` | `templates/dev-instruction-template.md` |
| User guide, user manual, help doc, how-to, end-user doc | `references/user-guide.md` | `templates/user-guide-template.md` |
| Compare two systems/approaches, pros/cons, recommend between options | No reference — write directly. Structure: Executive Summary → Architecture overview (both sides) → Feature-by-feature comparison table → Pros/Cons per option → Recommendation with rationale → Open questions. | — |
| Action plan for AI agents, task breakdown for Codex/Claude, phased dev handoff | Use `references/dev-instruction.md` + `templates/dev-instruction-template.md` as the base, then apply the **AI agent audience rules** below. | `templates/dev-instruction-template.md` |

Also use:

- `knowledge` (`.agents/knowledge/`) for shared domain standards, prior art, or referenced input documents — Step 1 research source.
- `planning` (`.agents/memory/context/`) for repo-specific decisions — Step 1 context, and Step 4 output destination.
- `.agents/memory/log/` only to append a new change note after finishing.

---

## AI agent audience rules

When the document audience is an AI coding agent (Codex, Claude, or similar), apply these rules on top of the dev-instruction format:

### Stop-and-ask markers — `[ASK]`

Insert an `[ASK]` marker at every point where:

- A schema or API shape could be implemented two non-equivalent ways and the plan doesn't specify which.
- A new file or table is being added and the naming/migration mechanism hasn't been confirmed.
- A new dependency (library, external service) is required and hasn't been approved.
- The execution changes existing behavior rather than adding new behavior (risky path).

Format: `**[ASK]** One sentence describing exactly what to ask, with options if applicable.`

### Verify-before-touching markers — `[VERIFY]`

Insert a `[VERIFY]` marker whenever the plan assumes the shape of an existing file, function signature, or DB column. The agent must read the named file before writing code that depends on it.

Format: `**[VERIFY]** Read the named file and confirm the expected shape before continuing.`

### Phase gates

Group tasks into numbered phases. Start each phase 2+ with: `> Do not start Phase N until Phase N-1 is complete and reviewed by the human.`

This prevents the agent from running ahead on later phases before the earlier design is validated.

### Consolidated open questions section

End the document with a "Questions to ask before starting" section that lists every `[ASK]` in one place, numbered. The agent should surface these to the human in a single message before writing any code.

### Specificity over prose

For AI agents, every step must name the exact file path, the exact struct/function name, and what change to make. General guidance like "add appropriate error handling" is useless. `Return 422 with error: unknown_trigger_type when trigger_type is not one of http, kafka, mqtt` is actionable.
