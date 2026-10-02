---
id: proposal-rule-backend-6-test-naming
type: proposal
status: open
blocks: []
created: 2026-10-02
---

<!-- sdlc:template inbox-item 1 -->

## Question

**Backend 6** says a test that needs the Spring context is an integration
test, "named and located accordingly", but no naming or location is written
down. Spring-context tests named `*Test` sit beside unit tests, so the rule
can't be applied.

## Recommendation

Write the convention down as it already is: Spring-context tests extend
`IntegrationTest`, keep the `*Test` name and sit beside unit tests. No renames
needed.

## Blocks

Nothing.

## Meanwhile

The loop carries on.

## Answer
