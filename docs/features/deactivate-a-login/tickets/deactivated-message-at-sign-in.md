---
id: deactivated-message-at-sign-in
title: Show the deactivated message at sign-in
status: done
depends_on: [deactivated-login-refused]
labels: [frontend]
stories: [11]
---

## Context

Fifth slice of `spec.md` (`## Execution order`; `## Solution`, "What deactivated means" door 1 and "BFF"). The BFF session route (`frontend/app/api/session/route.ts`) maps a backend `401` with `code: LOGIN_DEACTIVATED` to the error "This login has been deactivated. Ask your Manager if you need access again."; any other non-OK answer keeps "Incorrect email or password." `login-form.tsx` already renders the route's `error`. Modules: `frontend/app/api`, `frontend/components/login`.

## Acceptance criteria

- [ ] Signing in with a deactivated Login's right password shows "This login has been deactivated. Ask your Manager if you need access again."
- [ ] A wrong password, or any other failure, still shows "Incorrect email or password."

## Tests

- **Vitest component tests (spec `## Testing decisions`):** new test for the session route's mapping (`login-deactivated-code-gives-deactivated-message`, `other-401-gives-incorrect-email-or-password`) and a login form test showing each message.
- No new e2e here; the end-to-end proof is in `deactivate-an-agents-login-ui`.

## Regression

- At risk: sign-in for every role, and the login page's visual surface.
- Protected by `frontend/tests/e2e/login.spec.ts` and the visual surfaces, unedited. No existing test is expected to change.

## Observability

N/A — presentation only; the backend refusal is logged by `deactivated-login-refused`.
