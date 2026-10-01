---
id: reset-a-testers-password-ui
title: Reset a Tester's password from the Client's page
status: ready-for-agent
depends_on: [reset-a-testers-password-api, reset-an-agents-password-ui]
labels: [frontend]
stories: [2, 5, 13, 17, 19, 25, 26, 27]
---

## Context

The Tester half (spec `## Solution` → Frontend → "Client's page"; `## Design direction`). `ManagerTestersView`'s
table gains a trailing column with a screen-reader-only "Actions" header and, per row, a **Reset password**
row action whose accessible name includes that Tester's email. One reset dialog instance per view, opened for
the chosen Tester (dialog title uses the email), reusing the dialog from `reset-an-agents-password-ui`. Adds the
new BFF proxy for `POST /api/clients/{clientId}/testers/{testerId}/password` (forwarding `no-store`) and the Tester
half of `manager-resets-a-password.spec.ts`. `TableScroll` handles the narrow viewport.

Modules touched: `frontend/components` → `manager`; `frontend/app` → `api`.

## Acceptance criteria

- Every Tester row on the Client's page shows a **Reset password** action with a distinct accessible name carrying
  that Tester's email, and the DOM holds one reset dialog, with no `<form>` in it while closed.
- Confirming shows the reveal naming the Tester's email; the Tester signs in with it and the previous password is refused.
- Closing returns focus to the row's trigger and the status says "Password reset for {email}." without the password.
- By keyboard alone: Tab to the row action, open, confirm, Tab to **Copy password** and activate it, close with Done;
  open again and close with Escape; focus returns to the trigger each time.
- At the mobile breakpoint the row action is reachable and the dialog, the password field and the copy button sit inside
  the viewport.

## Tests

Seam: Vitest + Testing Library with the BFF stubbed at `fetch`; the isolated e2e stack; the stub-backend visual suite.

- component tests for `ManagerTestersView`: an action per row with the email in its accessible name; one dialog
  instance for many rows; the dialog opens for the chosen row; `404` renders "no longer exists";
- e2e named case: the Tester reset POST response's `cache-control` header contains `no-store`, asserted with `page.waitForResponse`;
- e2e `manager-resets-a-password.spec.ts`, Tester half (steps 2-4 for a Tester added through `addTester`), including a
  keyboard-only pass and a mobile-viewport pass; add a Tester, close the reveal with Escape without copying, then reset
  that Tester and sign in;
- visual: new goldens for the reset dialog's confirm step on the Manager's Client page, per theme and breakpoint, with the
  stub backend extended to serve that page.

## Regression

At risk: the Client's page Testers table (`ManagerTestersView`, `CreateTesterDialog`). Guarded by
`create-tester-dialog.test.tsx`, `manager-entity-setup.spec.ts`, `fleet-management.spec.ts` and `tester-request-submission.spec.ts`,
all unedited by this ticket. Existing files modified: `manager-resets-a-password.spec.ts` (created by the Agent ticket; gains the
Tester half, Agent cases keep passing unmodified), `frontend/tests/visual/stub-backend.mjs` and `surfaces.spec.ts` (gain the Client page
and its reset route; every existing surface keeps its golden). The visual suite has no Manager Client page golden today, so no existing
golden moves; any other golden moving is a finding.

## Observability

N/A — frontend only; the audit line is `reset-a-testers-password-api`'s. The password is never logged, put in a URL or stored.
