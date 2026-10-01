---
id: dialog-shell-mounts-when-open
title: Mount DialogShell children only while the dialog is open
status: ready-for-agent
depends_on: []
labels: [enabler, frontend]
stories: [14]
---

## Context

Prefactoring; pays the `docs/tech-debt.md` entry for
`frontend/components/manager/dialog-shell.tsx` (renders children while closed). It
enables `creation-dialogs-reveal-generated-password` and the two reset UI tickets, which
put a second closed dialog on the Agent's page and the Client's page and need a shown
password to leave the DOM the moment its dialog closes. See the spec's `## Solution` →
"Prefactoring: `DialogShell` mounts its children only while open". The change-password
dialog's consumer-side deferral stays as it is.

Modules touched: `frontend/components` → `manager` only (its consumers in `carriers`,
`app-shell` and `manager` are not edited).

## Acceptance criteria

- With a `DialogShell` that has not been opened, its children are not in the DOM; after
  `open()` they are present and focusable; after `close()` they are gone again.
- Every existing dialog (Add agent, Create login, Add tester, the carrier dialogs, the
  change-password dialog) still opens, validates, submits and resets its state as before.
- Escape-blocked-while-`submitting` and backdrop-click-to-close behave as before.

## Tests

Seam: Vitest + Testing Library component tests, colocated (`*.test.tsx`).

- New `dialog-shell.test.tsx` in `frontend/components/manager`: closed → a child `<form>`
  is absent; open → present and an input inside takes focus; closed again → absent;
  Escape is refused while `submitting`; backdrop click closes when not submitting.
- Every existing dialog test stays green unedited.

## Regression

At risk: all `DialogShell` consumers — `create-agent-dialog`, `create-agent-login-dialog`,
`create-tester-dialog`, `change-password-dialog`, and the three carrier dialogs. Guarded by
`create-tester-dialog.test.tsx`, `change-password-dialog.test.tsx`,
`frontend/tests/e2e/manager-entity-setup.spec.ts`, `create-agent-with-login.spec.ts`,
`create-login-for-existing-agent.spec.ts`, `change-password.spec.ts`,
`carrier-catalog.spec.ts` and `agent-standing-amounts-and-invoice-generation.spec.ts`
(whose `locator("form").nth(0)` is the recorded casualty), plus
`frontend/tests/visual/change-password-dialog.spec.ts`. None is edited. No golden moves.

## Observability

N/A — pure frontend mounting change; no event or log line.
