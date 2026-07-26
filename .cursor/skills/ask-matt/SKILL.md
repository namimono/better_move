---
name: ask-matt
description: Recommend the skill or workflow that best fits the user's situation. Use this as a guide to the other skills in this repository.
disable-model-invocation: true
---

# Ask Matt

Use this guide when you are unsure which skill to run.

A **workflow** is an ordered sequence of skills. Most feature work follows the recommended workflow below. Some situations have a different starting point and join it later. Other skills are standalone tools or shared terminology references.

## Recommended workflow: idea → implementation

Use this workflow when the user has an idea and wants it implemented.

1. **`/grill-with-docs`** — clarify the idea through an interview. Use it when a codebase exists. It records what it learns in `CONTEXT.md` and ADRs. If there is no codebase, use `/grill-me` instead; both use `/grilling`, but only `/grill-with-docs` writes project documentation.
2. **Decide whether conversation alone can answer every design question.** If a question needs executable code—for example, to test state, business logic, or a visual UI—use a prototype in a separate session:
   - Run **`/handoff`** and open a fresh session using the generated file.
   - Run **`/prototype`** to answer the question with temporary code.
   - Run **`/handoff`** again to carry the result back, then reference it from the original idea task.
3. **Decide whether implementation will require multiple sessions.**
   - **Yes** → Run **`/to-spec`** to turn the conversation into a specification. Then run **`/to-tickets`** to create small end-to-end tickets and record which tickets depend on others. For a local tracker, each ticket is a file under `.scratch/<feature>/issues/`. For a real tracker, use its native dependency links. Run **`/implement`** once per ready ticket, with a fresh context for each ticket.
   - **No** → **`/implement`** right here, in the same context window.

   In either case, **`/implement`** uses **`/tdd`** to implement one test-first increment at a time. Before committing, it runs **`/code-review`** to check both repository standards and the specification. Use **`/tdd`** directly when the user wants to implement a specific behavior test-first without a full specification. Use **`/code-review`** directly to review a branch or PR against a fixed point.

### Keep context focused

Keep steps 1–3 in the same context window. Do not compact or clear the conversation until `/to-tickets` is complete, so the interview, specification, and tickets use the same reasoning and decisions. Start each `/implement` ticket in a fresh context.

If the session approaches the model's effective context limit (roughly 120k tokens on current high-capacity models) before `/to-tickets`, run `/handoff` and continue in a fresh task instead of continuing with degraded context.

## Alternative starting points

Use these skills when the work begins with an existing issue, a difficult bug, or an effort too large to plan in one session. Each one eventually joins the recommended workflow.

- **Unprocessed bugs and requests** → **`/triage`**. It classifies and verifies incoming issues, then produces issues that **`/implement`** can execute.

  Use triage only for raw issues created by someone else, such as bug reports and incoming feature requests. Do not triage tickets created by `/to-tickets`; they are already ready for an agent.

- **A difficult bug or performance regression** → **`/diagnosing-bugs`**. Use it for intermittent failures, regressions between known-good states, or bugs that are not obvious from inspection. It first creates a fast, repeatable command that reproduces this specific failure. It then fixes the bug and adds a regression test. If the bug is hard to test because the code lacks a suitable public interface, follow up with **`/improve-codebase-architecture`**.

- **A project too large or uncertain to plan in one session** → **`/wayfinder`**. It creates a shared planning issue and a set of decision tickets, then resolves those decisions one at a time. Use it for large greenfield projects or major features whose planning questions are not yet clear. Do not use it for a well-scoped feature.

  `/wayfinder` plans the work but does not implement it. When the decisions are complete, run **`/to-spec`** to combine the linked decisions into one buildable specification. Then run `/to-tickets` and `/implement`. Go directly to `/implement` only if the effort turns out to be genuinely small.

## Codebase maintenance

Use this for maintenance rather than feature delivery.

- **`/improve-codebase-architecture`** — identify areas where a simpler interface could hide more implementation complexity. Choose one result and take it into `/grill-with-docs` as a new improvement idea. Use **`/codebase-design`** to design the selected module.

## Shared terminology

These two reference skills define terminology used by the other engineering skills. Use them directly when the problem is unclear language or module design, or let another skill invoke them when needed.

- **`/domain-modeling`** — clarify the project's domain terms, resolve words that have several meanings, and record hard-to-reverse decisions as ADRs. `/grill-with-docs` uses it to maintain the glossary in `CONTEXT.md`.
- **`/codebase-design`** — define how to design modules with a small public interface and substantial behavior behind it. It provides the terms module, interface, depth, seam, adapter, leverage, and locality. `/tdd` and `/improve-codebase-architecture` use this vocabulary.

## Moving between sessions

- **`/handoff`** — summarize the current conversation into a Markdown file, then open a new task and reference that file. Use it when you need a fresh context but must preserve the current conversation, including when moving into or back from a `/prototype` session.
- **`/compact`** (built-in) — continue in the same conversation after summarizing earlier turns. Use it only at a deliberate break between phases, when losing the exact earlier wording is acceptable. Do not compact in the middle of a phase.

## Standalone

These skills can be used independently of the recommended workflow.

- **`/grill-me`** — run the same detailed interview as `/grill-with-docs` when no codebase exists. It does not save local state or create `CONTEXT.md`.
- **`/prototype`** — build a small, temporary program to answer one design question about logic, state, behavior, or UI. Keep the result and discard the prototype code.
- **`/research`** — ask a background agent to investigate a question using primary sources and save a cited Markdown report in the repository. Use the report as input to later planning; it does not replace `/grill-with-docs`.
- **`/teach`** — learn a concept over multiple sessions, using the current directory as a stateful workspace.
- **`/writing-great-skills`** — reference for writing and editing skills well.

## Precondition

Run **`/setup-matt-pocock-skills`** before the first engineering workflow. It configures the issue tracker, triage labels, and documentation layout required by the other skills. Custom issue trackers are supported.
