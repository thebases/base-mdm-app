---
name: base-product
description: >
  Product management skill for analyzing requirements, identifying gaps, comparing solution options, and writing structured product or delivery documents. Use when Codex needs to write or refine PRDs, BRDs, SRS documents, technical specs, developer instructions, user guides, acceptance criteria, feature breakdowns, implementation plans, AI-agent action plans, or pros/cons recommendations. Also use when the user wants to organize vague requests into a clearer scope, convert notes into an executable document, or compact `.agents/memory/context` to reduce token usage while preserving durable repo knowledge. Trigger on requests like "write a PRD", "create an SRS", "document this feature", "compare X and Y", "make an action plan", "break this into tasks for Codex/Claude", "write dev instructions", or "compact context".
---

# Product Manager

Produce clear, testable, audience-appropriate product and software documents that are immediately usable by engineers, QA, stakeholders, end users, or AI coding agents.

## Path conventions

Two folders are referenced throughout this skill. Use these aliases:

| Alias       | Real path                  | Purpose                                                                                       |
| ----------- | -------------------------- | --------------------------------------------------------------------------------------------- |
| `knowledge` | `.agents/knowledge/`       | Research materials, prior art, domain standards, external references — input to your thinking |
| `planning`  | `.agents/planning/` | Active plans, context files, action plans — output destination for documents you create       |
| `context`   | `.agents/memory/context/`  | current workspace/repo context                                                                |
| `wiki`      | `obsidian mcp server`      | Obsidian MCP server                                                                           |

Never read `.agents/memory/log/`.

Before every final response, complete a mandatory memory sync: update the most specific relevant file under `.agents/memory/context/` when the task creates durable repo knowledge, and add a log note stating which context file changed or that no durable update was required.

---

## Knowledge lookup rule

**Before reading any local file or making any assumption about domain knowledge, always search the Obsidian MCP server first.**

This applies to every mode (research, document authoring, context compaction). The Obsidian vault is the authoritative source for business context, domain standards, prior decisions, and team conventions. Local `knowledge` files supplement it — they do not replace it.

Procedure for every knowledge lookup:

1. Use the Obsidian MCP server via the `wiki` skill first to search for relevant notes (search by keyword, tag, or topic), only search internet for knowledge beyond the supplied artifacts and `wiki` skill.
2. Read the matching notes from `wiki` before opening any local file.
3. Only after exhausting Obsidian, fall back to local `knowledge` files and `planning` folder.

If the Obsidian MCP server is unavailable or returns no results, note this explicitly and proceed with local files.

---

## Core methodology: Research → Gap → Pros/Cons → Plan

Every task runs through this 4-step pipeline **before** writing any document. Steps 1–3 are thinking; Step 4 produces the deliverable.

### Step 1 — Research (Obsidian first, then `knowledge`)

Understand the problem domain before proposing anything.

- **Search Obsidian MCP first** for notes related to the request. Read every relevant note returned.
- Then read local files in `knowledge` that are relevant to the request. If the user cites a specific file, read it now.
- Also read `planning` for existing repo decisions and active context that constrains the solution space.
- The goal: build a factual picture of what already exists — in the codebase, in prior decisions, and in the domain.

### Step 2 — Find the gaps

Compare what exists (Step 1) against what is needed (the user's request).

- What is missing, broken, or insufficient?
- What constraints or invariants must the solution respect?
- Name gaps precisely: vague gaps ("UX is bad") produce vague plans. Specific gaps ("users cannot configure Kafka topic without a Go code change and redeployment") produce actionable ones.

### Step 3 — Pros & cons for filling the gaps

For each significant gap or design choice, weigh the options before committing.

- What is the cost of closing this gap (effort, risk, breakage)?
- What is the cost of leaving it open?
- Are there multiple ways to close it? What does each trade off?
- The goal is not to enumerate all possibilities — it is to make the tradeoffs visible so the final recommendation is defensible.

### Step 4 — Action plan / deliverable (save to `planning`)

Translate the research into a concrete, audience-appropriate document and save it to `planning`.

- Choose the output format from `references/skill-guide.md` based on the document type and audience.
- Detect the audience before writing:
  - **Human engineer / QA / stakeholder**: standard prose, acceptance criteria, decision rationale.
  - **AI coding agent (Codex, Claude)**: `[ASK]` stop-markers for ambiguities, `[VERIFY]` file-read gates, phase gates between phases, and a consolidated questions section at the end.
- Make assumptions, open questions, and out-of-scope items explicit in the document.
- Close with a summary of what was written and what still needs stakeholder input.

---

## Document authoring mode

Use this mode when the task is to **write a specific formal document** (SRS, BRD, PRD, guideline, user guide, dev-instruction, etc.) rather than to research or solve a problem. You may also enter this mode as Step 4 of the research pipeline above — once the analysis is complete, switch to these steps to produce the deliverable.

1. **Search Obsidian MCP first, then read all provided notes.** Query the Obsidian vault for any relevant domain notes, prior decisions, or team conventions before reading local files. Then read everything the user provided (PRD, meeting notes, ticket descriptions, drafts, constraints). Writing before researching leads to documents that miss critical context.

2. **Read `references/skill-guide.md`** for the writing stance and the reference/template selection map. This tells you which reference file and template match the document type being requested.

3. **Load the one reference file and template** that match the requested document. The reference file explains what the document must contain and why; the template gives the structure to fill in. Load only the matching pair — loading multiple templates wastes context.

4. **Write the document**, making assumptions, open questions, and out-of-scope items explicit as you go. A well-written doc surfaces what is unknown, not just what is known. Do not leave gaps silently — flag them so the reader knows where decisions are still needed.

5. **Close with a summary** of what was written, what was assumed, and what still needs stakeholder input or sign-off.
6. **Sync memory before close.** Persist any durable repo decisions, document locations, or operating constraints to the most specific matching file in `.agents/memory/context/`; if there is no durable addition, say so in the log.

---

## Context compaction mode

Use this mode when the user wants to shrink `.agents/memory/context` to reduce AI token usage — triggered by phrases like "compact context", "context compact", "clean up context files", "optimize AI memory", or "reduce token usage".

The goal is to cut noise without losing durable constraints. Architectural invariants, entrypoints, behavioral rules, and recent high-value decisions must survive; repeated introductions, duplicated bullets, and stale chatter get cut.

1. **Inventory the context folder.** List all files under `.agents/memory/context/` and measure their sizes.

2. **Read `references/context-compaction-guide.md`** for the keep/remove heuristics, target file roles, editing rules, and the verification checklist.

3. **Read each file** before editing it. Never edit blind.

4. **Rewrite each file** as a compact operational summary: shorter bullets over paragraphs, merged duplicates, stale notes removed, only durable information that would matter in a future agent turn.

5. **Re-measure** and confirm the footprint shrank. Verify `context.md` (if present) still points to the right focused files.

6. **Log the compaction.** Append a one-line note to `.agents/memory/log/` describing what was compacted and by roughly how much.
7. **Update the context index if routing changed.** If compaction changes which focused files future agents should read first, update `context.md` accordingly.

---

## Choosing the right mode

| Situation                                                                               | Mode                                                                 |
| --------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| "Compare X and Y", "what's the best approach for…", "analyze the gaps", "pros/cons of…" | Research pipeline (4 steps above)                                    |
| "Write an SRS / BRD / PRD / guideline / user guide / dev-instruction"                   | Document authoring mode                                              |
| Research needed first, then produce a deliverable                                       | Run research pipeline, then enter document authoring mode for Step 4 |
| "compact context", "context compact", "clean up context", "reduce token usage"          | Context compaction mode                                              |

---

## Output format selection

Read `references/skill-guide.md` to:

- Select the matching document type, reference file, and template.
- Apply the AI agent audience rules if the recipient is Codex or Claude.
