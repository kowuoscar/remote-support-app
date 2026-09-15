---
id: auth-login-flow
title: Log in through the real UI and reach a role-appropriate shell
status: in-progress
depends_on: [backend-bootstrap, design-system]
labels: [frontend, backend, auth]
---

## Context

The walking skeleton connecting the Next.js app to the backend's JWT auth from `backend-bootstrap`, inside the committed design system. Implements user story 29 and the login precondition every later story assumes.

## Acceptance criteria

- [ ] A user of any role can log in from the Next.js app with their credentials
- [ ] On success, they land on a role-appropriate empty shell page (Manager Console / Agent Console / Client Portal)
- [ ] An invalid login shows an error and does not grant access
- [ ] A logged-in user can log out, ending their session client-side
- [ ] Visiting a protected route while unauthenticated redirects to login

## Tests

Frontend browser seam (`playwright-cli`, accessibility-tree level): successful login for each role reaches its own shell; invalid login is rejected; logout then a protected route redirects to login.

## Regression

N/A — first user-facing flow.

## Observability

Login success/failure events logged with tenant, user id and role.
