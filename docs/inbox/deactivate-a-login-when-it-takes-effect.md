---
id: deactivate-a-login-when-it-takes-effect
type: question
status: answered
blocks: [deactivate-a-login]
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

When a Manager switches off someone's login while they are using the app, are
they thrown out at once, or only when their current session ends (within the
hour)?

Example: Ana, an Agent, leaves at 10:00 with the app still open on her phone.
You switch her login off at 10:05.

- **At once:** her next tap at 10:06 lands on the sign-in page.
- **At session end:** she keeps full use of the app (her Fleet, her Requests,
  submitting her invoice) until as late as 11:05, then cannot sign in again.

**Recommendation: at once.** Switching a login off is for people leaving,
sometimes on bad terms; an hour of access afterwards is the risk the feature
removes. The cost is one quick check per request, which nobody will notice.

Blocks `deactivate-a-login` (spec: `docs/features/deactivate-a-login/spec.md`,
Open questions 1). The client dashboard and the invoice work continue meanwhile.

## Answer

At once (2026-10-02): the recommendation.
