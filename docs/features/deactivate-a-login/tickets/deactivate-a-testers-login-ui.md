---
id: deactivate-a-testers-login-ui
title: Let a Manager deactivate and reactivate a Tester's Login from the Client's page
status: in-progress
depends_on: [deactivate-a-testers-login-api, deactivate-an-agents-login-ui]
labels: [frontend]
stories: [2, 4, 5, 6, 7, 17, 24, 25]
---

## Context

Seventh slice of `spec.md` (`## Execution order`; `## Solution`, "Frontend: Client's page", `## Design direction`). Adds the two BFF proxies (`frontend/app/api/clients/[clientId]/testers/[testerId]/deactivate/route.ts` and `.../reactivate/route.ts`), and in `ManagerTestersView` the **Deactivated** tag in the email cell (the same neutral tag as the Agent's page) and, in the trailing Actions column, **Reset password** then **Deactivate login** or **Reactivate login**, each with an accessible name that includes that Tester's email. It reuses the two dialogs from `deactivate-an-agents-login-ui` (one instance of each per view, opened for the chosen row; {name} is the Tester's email) and the same announcement and focus rules. `TesterListItem` in `frontend/lib/api/types.ts` gains an optional `deactivatedAt?: string | null` so existing fixtures still typecheck. The stub backend gains one Tester with a deactivated Login. The Tester half of `deactivate-a-login.spec.ts` lands here. Modules: `frontend/app/api`, `frontend/components/manager`, `frontend/lib/api`.

## Acceptance criteria

- [ ] Each Tester row shows **Reset password** and **Deactivate login**, or **Reset password** and **Reactivate login** when deactivated, each named with that Tester's email; a deactivated Tester's email cell carries the **Deactivated** tag with its date.
- [ ] Deactivating (dialog names the Tester's email, **Cancel** sends nothing) closes the dialog, announces "Login deactivated for {email}.", returns focus to the row's position and shows the tag; reactivating removes it and announces "Login reactivated for {email}."
- [ ] The Tester's Client, Primary contact badge and email are unchanged on the row after either action.
- [ ] In a second browser context signed in as that Tester, the next navigation lands on sign-in after deactivation, signing in shows the deactivated message, and after reactivation the same password reaches the Client console.
- [ ] By keyboard alone: Tab to the row action, open, cancel with Escape, open again, confirm, with a visible focus ring at each stop and focus returning.
- [ ] At the mobile breakpoint the tag, both row actions and each dialog fit inside the viewport.

## Tests

- **Vitest component tests (spec `## Testing decisions`):** add cases to `testers-view.test.tsx`: `deactivated-tester-shows-tag-in-email-cell-and-reactivate`, `active-tester-shows-reset-and-deactivate-named-with-email`, `deactivate-confirm-calls-the-tester-route-closes-and-announces`, `reactivate-confirm-calls-the-tester-route`, `cancel-sends-nothing`, `404-shows-no-longer-exists-message`.
- **e2e:** extend `frontend/tests/e2e/deactivate-a-login.spec.ts` (created by `deactivate-an-agents-login-ui`) with the Tester half, using `helpers.ts` `addTester` on its own Client: `manager-deactivates-tester-second-context-lands-on-sign-in-then-reactivate-signs-in`, and the keyboard and mobile-viewport walk.
- **Visual:** new goldens for the deactivate confirm step on the Client's page, per theme and breakpoint, in a new spec file beside `reset-tester-password-dialog.spec.ts`, against the stub backend's deactivated Tester.

## Regression

- At risk: the Testers table and its Actions column, the single reset dialog, the Client page layout at the mobile breakpoint (recorded horizontal-scroll debt must get no worse in kind).
- Existing tests expected to change: `frontend/components/manager/testers-view.test.tsx` gains the cases above, and its dialog-count assertion (`expect(dialogs).toHaveLength(2)`, "Add tester + the one reset dialog") must become 4 because the view now mounts the two new dialogs; every other pre-existing case keeps passing unmodified. `frontend/tests/e2e/deactivate-a-login.spec.ts` is extended by this ticket (it is new in the previous one). `frontend/tests/visual/stub-backend.mjs` gains one deactivated Tester and leaves existing fixtures untouched. Existing goldens expected to move, recaptured here: `reset-tester-password-dialog-confirm-*` and any golden of the Manager's Client page; a golden moving on any other surface is a finding.
- `frontend/tests/e2e/manager-resets-a-password.spec.ts` is not modified.

## Observability

N/A — presentation only; the audit lines are written by `deactivate-a-testers-login-api`, and the confirmation is announced in the page's status region.
