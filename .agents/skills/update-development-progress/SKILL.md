---
name: update-development-progress
description: Update a project's Markdown development-progress document after implementing, debugging, reviewing, or validating code. Use when a coding session should record completed work, verification evidence, open risks, acceptance status, follow-up tasks, or changed files; also use when creating a new progress document from an established project plan.
---

# Update Development Progress

Maintain a concise, factual Markdown record of the project's current development state. Treat the document as a handoff and acceptance artifact, not as a diary or a copy of the code diff.

## Locate the document

1. Use the progress document named by the user.
2. Otherwise search `docs/` for names containing `progress`, `开发进度`, `status`, or `进展`.
3. If several candidates exist, choose the one linked from the relevant plan or feature documentation. Ask only if the choice would materially change the record.
4. If no document exists and the user requests one, create it from [the standard layout](references/standard-layout.md).

## Gather evidence before writing

Read the existing progress document and the feature plan, troubleshooting notes, and review notes it links to. Inspect the implementation and the session's relevant `git diff` / `git status`; run or review the relevant tests when they are available and in scope.

Base every status change on evidence. Distinguish these states precisely:

| State | Meaning |
|---|---|
| 已完成 | Implementation and required verification for the item are complete. |
| 基本完成 / 部分完成 | A usable portion exists, but an identified requirement, verification, or cleanup remains. State the gap. |
| 代码已实现，待验证 | Code exists, but manual, integration, device, or performance validation has not been completed. |
| 未开始 / 待办 | No in-scope implementation evidence exists. |
| 已阻塞 | Progress depends on a named external decision, access, defect, or prerequisite. |

Never promote a task to “已完成” only because code was written. Never mark a checkbox complete for an intended fix, an unrun test, or a result inferred without evidence.

## Update the record

Preserve the document's existing structure, links, terminology, ordering, and useful history. Make the smallest complete edit that reflects the session.

1. Update the date to the actual update date.
2. Update the overall-progress table and summary judgment when the feature or phase status changed.
3. Add or revise only the affected capabilities and architecture entries. Include file paths and behavioral consequences where they help a future reader.
4. Move completed work out of “待办” or check its existing checkbox; leave unverified follow-up items visible.
5. Record defects and their fixes in the existing review/fix section, including severity when the document uses it.
6. Update the acceptance matrix so each item says whether it is implemented, verified, partially complete, blocked, or still pending.
7. Update the changed-files section only for files materially changed in this iteration. Do not list generated noise or unrelated worktree changes.
8. Keep “out of scope” items separate from unfinished work.

Use concise language focused on observable behavior, scope, and remaining risk. Prefer “关闭窗口会终止该窗全部子进程，待人工回归” over implementation-only claims such as “已调整 ProcessHandler”.

## Record verification faithfully

For each important change, capture the strongest evidence available:

- Automated checks: test command and whether it passed, failed, or was not run.
- Manual checks: scenario and result; retain any cases still awaiting confirmation.
- Review findings: severity, impact, and the applied correction.
- Known limitations: exact condition and next action.

If the document does not have a suitable verification section, add a compact entry near the affected feature or acceptance item. Do not invent test results, measurements, dates, or file changes.

## New documents

When creating a document, adapt the standard layout to the feature and link to the plan, review, and troubleshooting documents that actually exist. Omit empty sections rather than filling them with placeholders. Keep a clear acceptance matrix and an actionable, prioritized backlog.

## Final check

Before handing off, reread the edited sections and confirm:

- The date, status labels, checkboxes, and summary agree.
- “Done” claims have implementation and verification evidence.
- Remaining work is concrete, prioritized, and not duplicated.
- Paths, links, and changed-file lists are accurate.
- The document says what changed for users or the system, not merely which symbols changed.
