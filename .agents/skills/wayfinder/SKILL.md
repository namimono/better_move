---
name: wayfinder
description: Plan work that is too large or uncertain for one agent session. Create a shared planning issue with decision tickets in the issue tracker, then resolve those tickets one at a time until the work is ready for a specification or implementation plan.
disable-model-invocation: true
---

Use this skill when an effort is too large or uncertain to plan in one agent session.

The skill creates a **map**, which is a shared planning issue in the repository's issue tracker. The map contains child **decision tickets**. Each decision ticket answers one planning question; it is not an implementation slice.

First define the **destination**: the concrete result this planning effort must make possible. The destination might be a specification, a decision that must be settled before implementation planning, or a change performed directly, such as a data-structure migration. The destination sets the scope and determines which tickets belong on the map. This structure can be used for engineering work, course content, or any other large planning effort.

## Plan by default

By default, Wayfinder produces decisions rather than deliverables. Each ticket resolves one decision. The map is complete when no planning decisions remain before another person or agent can execute the work.

If resolving a ticket starts to become implementation work, stop and hand off to the appropriate implementation workflow. The map's **Notes** section may explicitly allow implementation, but without that exception, produce decisions only.

## Refer to issues by title

In all human-readable text, refer to maps and tickets by their titles rather than by a bare id, number, or slug. Include the id or URL by linking the title. For example, use `[Choose the storage model](link)` rather than `#42`.

## The map

The map is a single issue labeled `wayfinder:map`. It is the canonical index for the planning effort. Its decision tickets are child issues.

Keep detailed decisions in their tickets. In the map, add only a one-line summary and a link to each resolved ticket. Do not duplicate the full decision in both places.

The configured issue tracker determines how to represent the map, child tickets, dependencies, and queries for ready work. Run `/setup-matt-pocock-skills` if the tracker configuration is missing, then follow the tracker's **Wayfinding operations** instructions. If no tracker is configured, use the local Markdown tracker.

### The map body

Load the map once per session as a concise overview. Do not list open tickets in the map body; find them by querying its open child issues.

```markdown
## Destination

<the specification, decision, or change this planning effort must make possible; one or two lines that every session reads before choosing a ticket>

## Notes

<domain; skills every session should consult; standing preferences for this effort>

## Decisions so far

<!-- one line per closed ticket: summarize the answer and link to the ticket for details -->

- [<closed ticket title>](link) — <one-line gist of the answer>

## Not yet specified

<!-- in-scope questions that are not yet precise enough to become tickets -->

## Out of scope

<!-- work explicitly excluded from this planning effort -->
```

### Tickets

Each ticket is a child issue of the map and uses the tracker's issue id as its identity. Size its question so one agent session of up to approximately 100K tokens can resolve it.

```markdown
## Question

<the decision or investigation this ticket resolves>
```

Give each ticket one `wayfinder:<type>` label: `research`, `prototype`, `grilling`, or `task`. See [Ticket types](#ticket-types).

Before doing any work, **claim** the ticket by assigning it to the developer working on the map. Other concurrent sessions must skip claimed tickets. An open, unassigned ticket is unclaimed.

Use the tracker's native dependency relationship so ready tickets are visible in its UI. Only use a text convention in the ticket body when the tracker has no native dependency feature. A ticket is **unblocked** when every ticket it depends on is closed. The **frontier** means all child tickets that are open, unblocked, and unclaimed; these are the tickets available to work on now.

Do not put the answer in the ticket body. Record it when resolving the ticket, as described in [Work through the map](#work-through-the-map). Link assets created during the work instead of pasting them into the issue.

## Ticket types

Every ticket is either **HITL** (human in the loop) or **AFK** (the agent can complete it without live human participation). A HITL ticket requires a real exchange with the human. The agent must never invent the human's answers.

- **Research** (AFK): Read documentation, third-party APIs, or local knowledge bases to find a fact needed for a decision. Resolve it with a `/research` subagent. Use this type when the required information is outside the current working directory.
- **Prototype** (HITL): Create a cheap, rough artifact that the human can evaluate, such as an outline, draft, stub, or UI/logic prototype made with `/prototype`. Link the artifact from the ticket. Use this type when the key question is how something should look or behave.
- **Grilling** (HITL): Use `/grilling` and `/domain-modeling` to ask the human one question at a time. This is the default ticket type.
- **Task** (HITL or AFK): Complete manual work required before a decision can be made, such as creating a service account, provisioning access, or moving data for inspection. Use this type only to unblock a later decision, not to deliver the destination itself. The agent completes the work alone when possible (AFK); otherwise, provide the human with a precise checklist (HITL). Resolve the ticket when the work is complete, and record both what was done and any facts later tickets need, such as credential locations, URLs, or row counts.

## Questions not ready for tickets

The map is intentionally incomplete. Do not create a ticket for a question that cannot yet be stated precisely. Some in-scope questions depend on unresolved decisions and can only be described roughly. Record these questions in **Not yet specified**.

After resolving a ticket, review **Not yet specified**. If a question can now be stated precisely, remove it from that section and create one or more tickets for it. Repeat until the destination is clear and no decision tickets remain.

Use this test:

- **Create a ticket** when you can state the question precisely, even if another ticket currently blocks it.
- **Keep it in Not yet specified** when you cannot yet state the exact question. Do not guess how many future tickets it will require; one rough area may later become several tickets or none.

Do not put resolved decisions, existing tickets, or out-of-scope work in **Not yet specified**.

## Out of scope

The destination defines the scope. Put work that is outside that destination in the map's **Out of scope** section, not in **Not yet specified**.

Out-of-scope work never becomes a ticket in this map. If the destination later changes to include it, start a new planning effort rather than resuming the excluded work in this map.

If an existing ticket turns out to be outside the destination, close it and add one line to **Out of scope** that summarizes the work, explains why it is excluded, and links to the closed ticket. Do not add it to **Decisions so far**, because no planning decision for the destination was resolved.

## Invocation

Wayfinder has two modes. In either mode, never resolve more than one non-research ticket per session. Research tickets are the only exception.

### Chart the map

Use this mode when the user provides a large, loosely defined idea.

1. **Define the destination.** Run `/grilling` and `/domain-modeling` to state the specification, decision, or change this map must make possible. Define it first because it sets the scope.
2. **Identify planning questions across the full scope.** Use another breadth-first interview: cover the whole effort before exploring any one question deeply. Identify the precise questions that can become tickets now and the rough areas that belong in **Not yet specified**. If every question is already clear and the entire effort fits in one session, do not create a map. Stop and ask the user how to proceed.
3. **Create the map** with the `wayfinder:map` label. Fill in **Destination** and **Notes**, leave **Decisions so far** empty, and record imprecise in-scope questions under **Not yet specified**.
4. **Create all tickets that can be stated precisely** as child issues. After every ticket has an id, add dependency links in a second pass. The dependency links determine which tickets are ready now and which are blocked.
5. **Start research subagents.** For each new `research` ticket, start a `/research` subagent in parallel. Save its findings on a temporary `research/<name>` branch and add a context pointer to the ticket.
6. Stop after creating the map and its tickets. Do not manually resolve a ticket in the same session.

### Work through the map

Use this mode when the user provides a map URL or number. The user may optionally name a ticket. If they do not, choose the next available ticket.

1. Load the map as an overview. Do not load every ticket body.
2. Choose a ticket. Use the ticket named by the user, or otherwise choose the first ticket in the frontier. Assign it to yourself before doing any work.
3. Resolve that ticket. Fetch related or closed ticket bodies only when needed. Invoke every skill named in the map's `## Notes` section. If no more specific process applies, use `/grilling` and `/domain-modeling`.
4. Record the result as a resolution comment, close the ticket, and append a one-line linked summary to the map's **Decisions so far** section.
5. Create any newly identified tickets, then add their dependency links. Move any newly precise question out of **Not yet specified** and into a ticket so it exists in only one place. If this or another ticket is outside the destination, handle it using the **Out of scope** rules instead of resolving it. Update or delete tickets invalidated by the decision.

The user may work on several unblocked tickets in parallel. Expect concurrent changes to the issue tracker.
