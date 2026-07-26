---
name: to-spec
description: Synthesize the current conversation and codebase context into a specification, then publish it to the project issue tracker. Use when the user wants an existing discussion turned into a spec or PRD. Do not conduct a new requirements interview.
disable-model-invocation: true
---

Create a specification, also called a PRD, from decisions and requirements already present in the conversation and codebase context.

Do NOT reopen requirements discovery or conduct a new interview. Synthesize what is already known. The only confirmation allowed is the testing-interface check in step 2.

The issue tracker and triage labels should already be configured. Run `/setup-matt-pocock-skills` if they are missing.

## Process

1. If the codebase has not been explored yet, inspect the relevant parts before writing. Use terms from the project's domain glossary throughout the specification and follow relevant ADRs.

2. Identify the public interfaces through which tests will verify the feature. The codebase-design vocabulary calls each such interface a **seam**.

   - Prefer existing test interfaces over new ones.
   - Use the highest-level interface that can verify the behavior end to end.
   - Propose a new interface only when no suitable one exists.
   - Minimize the number of test interfaces; one is ideal when it can cover the required behavior.

   Present the proposed test interfaces and ask the user to confirm that they match expectations. Do not use this checkpoint to restart the requirements interview.

3. Write the specification using the template below. Publish it to the project issue tracker and apply the `ready-for-agent` triage label. Do not run any additional triage workflow.

<spec-template>

## Problem Statement

Describe the user's problem from the user's perspective.

## Solution

Describe the proposed result from the user's perspective.

## User Stories

Write a comprehensive numbered list of user stories. Cover every relevant actor and all important aspects of the feature, including success paths, failure cases, permissions, and lifecycle behavior when applicable.

Use this format:

1. As an <actor>, I want a <feature>, so that <benefit>

<user-story-example>
1. As a mobile bank customer, I want to see balance on my accounts, so that I can make better informed decisions about my spending
</user-story-example>

Make the list detailed enough that omitted behavior is easy to notice during review.

## Implementation Decisions

List the implementation decisions already made in the conversation or established by the codebase. Include relevant items such as:

- The modules that will be built/modified
- The interfaces of those modules that will be modified
- Technical clarifications
- Architectural decisions
- Schema changes
- API contracts
- Specific interactions

Do NOT include specific file paths or code snippets because they become outdated quickly.

Exception: if a prototype produced a short snippet that captures a decision more precisely than prose, such as a state machine, reducer, schema, or type shape, include it with the relevant decision and state that it came from a prototype. Include only the decision-relevant part, not a complete working demo.

## Testing Decisions

List the testing decisions already made. Include:

- The rule that tests verify externally observable behavior rather than implementation details
- The public interfaces and modules that will be tested
- Similar existing tests in the codebase that should be used as examples

## Out of Scope

List the work explicitly excluded from this specification.

## Further Notes

Record relevant information that does not fit the sections above.

</spec-template>
