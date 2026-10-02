---
id: deactivate-a-login-sign-in-message
type: question
status: answered
blocks: [deactivate-a-login]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

When a switched-off person signs in with their **correct** password, what
does the sign-in page say?

Example: Ana tries to sign in the week after she left.

- **Say so:** "This login has been deactivated. Ask your Manager if you need
  access again."
- **Say nothing:** "Incorrect email or password.", the same as a typo.

**Recommendation: say so, but only when the password is correct.** A wrong
password still gets "Incorrect email or password.", so a stranger learns
nothing. Ana stops retrying and doesn't ask you for a reset that wouldn't let
her in anyway.

Blocks `deactivate-a-login` (spec: `docs/features/deactivate-a-login/spec.md`,
Open questions 2).

## Answer

Say so, only when the password is correct (2026-10-02): the recommendation.
