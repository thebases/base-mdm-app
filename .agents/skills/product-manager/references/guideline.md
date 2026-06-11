# Guideline / Standard Document

## Purpose and audience

A guideline (also called a standard, coding standard, process guide, or best-practice document) tells practitioners how to do something consistently and correctly. Unlike a PRD or SRS, it is not about a single feature — it is durable reference material that applies across many situations.

The audience is practitioners: engineers, designers, QA, or operators who will consult the document while doing their work. The writing goal is clarity and actionability, not completeness for its own sake.

## When to write a guideline

- When the same decision keeps being made inconsistently across the team.
- When onboarding new members who need to learn the team's standards.
- When a pattern or practice has proven itself and needs to be codified before institutional knowledge is lost.
- When a compliance or audit requirement mandates documented procedures.

## Key sections

**Purpose and scope**
One paragraph: what this guideline covers, who it applies to, and what it does not cover. Scope boundaries prevent the document from being used in situations it was not designed for.

**Background and rationale**
Why does this guideline exist? What problem does it solve? What was happening without it? Understanding the "why" helps practitioners apply the guideline correctly in edge cases that the rules don't explicitly cover.

**Definitions**
Any terms, abbreviations, or concepts that are specific to this guideline or used in a non-obvious way. Keep this short — only define what would genuinely confuse a reader otherwise.

**The guidelines / rules**
The core content. Each guideline:
- States the rule clearly in the imperative ("Do X", "Avoid Y", "Always Z").
- Explains the reason briefly — practitioners who understand the why apply the rule better.
- Includes an example where the rule is non-obvious.
- Notes exceptions explicitly. A rule with hidden exceptions is a trap.

Organize rules into logical groups. Use a numbered hierarchy (1.1, 1.2, 2.1) so rules can be cited by ID in code reviews, tickets, and audits.

**Examples**
Concrete before/after or good/bad examples. Examples do more teaching than prose rules — include at least one per major section.

**Anti-patterns**
Common mistakes to avoid. Name them explicitly so practitioners can recognize them in code review.

**Enforcement and tooling**
How is this guideline enforced? Linter rule, CI check, code review checklist, or convention-only? If tooling exists, name it and link to the config. If it is convention-only, say so.

**Exceptions and escalation**
How should a practitioner handle a situation where following the guideline would produce a worse outcome than breaking it? Define the process: document it, get sign-off from X, or open a discussion.

**Changelog**
When was the guideline created, and what changed in each version? Standards that evolve without a changelog create confusion about which version is current.

## Writing discipline

- Write for the reader who will consult this mid-task, not the reader studying it in full. Prioritize scannability: headings, numbered rules, and examples over dense paragraphs.
- Distinguish "must" (mandatory) from "should" (strongly recommended) from "may" (optional). Mixed mandatoriness is a major source of inconsistency.
- Every rule that lacks a "why" will be ignored or cargo-culted. Explain the reasoning.
- A guideline that tries to cover every edge case becomes unreadable. Describe the principle; trust practitioners to apply judgment at the edges.
