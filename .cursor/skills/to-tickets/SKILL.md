---
name: to-tickets
description: Turn a plan, specification, or conversation into small end-to-end implementation tickets. Use when the user wants work split into tickets, with each ticket's dependencies recorded and the result published to the configured local or external issue tracker.
disable-model-invocation: true
---

# To Tickets

Break a plan, specification, or conversation into **tickets**. Each ticket must deliver a small but complete path through the system and must list the other tickets that must finish before it can start.

The issue tracker and triage labels should already be configured. Run `/setup-matt-pocock-skills` if they are missing.

## Process

### 1. Gather context

Use the information already in the conversation. If the user provides a specification path, issue number, or URL, fetch it and read the full body and comments.

### 2. Explore the codebase (optional)

If the codebase has not been explored yet, inspect it before drafting tickets. Use terms from the project's domain glossary in ticket titles and descriptions, and follow relevant ADRs.

Identify any preparatory refactoring that would make the feature easier and safer to implement. Schedule that work before the tickets that depend on it.

### 3. Draft vertical slices

Break the work into **vertical slices**: small tickets that each deliver one complete, end-to-end behavior.

<vertical-slice-rules>

- Each slice covers every required layer for one behavior, such as schema, API, UI, and tests. Do not create separate layer-only tickets unless the wide-refactor exception below applies.
- Each completed slice can be demonstrated or verified independently.
- Each slice fits in one fresh agent context window.
- Complete required preparatory refactoring first.

</vertical-slice-rules>

For each ticket, list its **dependencies**: the tickets that must complete before it can start. A ticket with no dependencies can start immediately.

**Wide refactors are the exception to vertical slicing.** A wide refactor is one mechanical change, such as renaming a database column or changing a widely used shared type, that affects too many call sites for any individual migration ticket to pass CI.

Use an **expand–contract** sequence with an explicit migration phase:

1. **Expand:** Add the new form beside the old one without breaking existing callers.
2. **Migrate:** Update callers in batches sized by the affected area, such as one package or directory per ticket. Each migration ticket depends on the expand ticket, and the old form remains available so CI can stay green.
3. **Contract:** Remove the old form after every migration ticket is complete. This ticket depends on all migration tickets.

If individual migration batches still cannot pass CI, use a shared integration branch. Make all migration tickets dependencies of one final integration-and-verification ticket; only that final ticket is required to restore green CI.

### 4. Quiz the user

Present the proposed tickets as a numbered list. For each ticket, show:

- **Title**: short descriptive name
- **Blocked by**: the tickets, if any, that must complete first
- **What it delivers**: the end-to-end behaviour this ticket makes work

Ask the user:

- Is each ticket the right size, or is it too large or too small?
- Are the dependencies correct? Each ticket should depend only on work that genuinely prevents it from starting.
- Should any tickets be merged or split further?

Iterate until the user approves the breakdown.

### 5. Publish the tickets to the configured tracker

Publish the approved tickets using the tracker configured by `/setup-matt-pocock-skills`. Ticket content stays the same; only the representation of dependencies changes.

- **Local files** → Write one file per ticket under `.scratch/<feature-slug>/issues/<NN>-<slug>.md`. Number files from `01` in dependency order, with prerequisites first. In each file, **Blocked by** lists the numbers and titles of its dependencies. Use the per-ticket template below. Never combine several tickets in one file.
- **External issue tracker (GitHub, Linear, and similar)** → Publish one issue per ticket in dependency order so each dependency can reference an existing issue identifier. Use the platform's native blocking or sub-issue relationship when available; otherwise list the dependencies under **Blocked by**. Apply the `ready-for-agent` triage label unless instructed otherwise.

The **frontier** is the set of tickets whose dependencies are all complete. These tickets are ready to implement. For a linear dependency chain, work from top to bottom.

Do NOT close or modify any parent issue.

<local-ticket-template>

# <NN> — <Ticket title>

**What to build:** the end-to-end behaviour this ticket makes work from the user's perspective, not a layer-by-layer implementation list.

**Blocked by:** the numbers/titles of the tickets that gate this one, or "None — can start immediately".

**Status:** ready-for-agent

- [ ] Acceptance criterion 1
- [ ] Acceptance criterion 2

</local-ticket-template>

<issue-template>

## Parent

A reference to the parent issue on the tracker (if the source was an existing issue, otherwise omit this section).

## What to build

The end-to-end behaviour this ticket makes work from the user's perspective, not a layer-by-layer implementation list.

## Acceptance criteria

- [ ] Criterion 1
- [ ] Criterion 2

## Blocked by

- A reference to each blocking ticket, or "None — can start immediately".

</issue-template>

In either format, avoid specific file paths and code snippets because they become outdated quickly. Make an exception when a prototype produced a short snippet that captures an important decision more precisely than prose, such as a state machine, reducer, schema, or type shape. Include only the decision-relevant part and note that it came from a prototype; do not include a complete working demo.
