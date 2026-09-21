# Delivery report — second-tenant-test-seam

<!-- sdlc:template delivery 1 -->

## What was built

The test suite can now seed a **second Tenant with a working login** and sign
in against it — and one deliberately disposable test records what today's
tenant-blind sign-in actually does. **This feature fixes nothing**; the fix is
the next feature, `globally-unique-usernames`.

- **Stories 1, 2, 6** — `OtherTenantFixture.managerLoginInAnotherTenant(username, password)` creates a brand-new Tenant, a `MANAGER` login hashed by the context's own `PasswordEncoder`, and a Client in that Tenant, returning `OtherTenantLogin(tenantId, username, password, clientId)`. Written straight through the repositories, because there is no API for creating a Tenant. Ticket: `second-tenant-login-fixture`.
- **Story 3** — `IntegrationTest.tenantIdOf(token)` reads the `tenantId` claim through the `JwtService` bean, so a test can assert *which Tenant a sign-in resolved to* without decoding a JWT itself. Deliberately not by adding a field to the login response, which would have been a user-visible API change in a feature that promises none. Ticket: `second-tenant-login-fixture`.
- **Stories 4, 5** — `SecondTenantSignInApiTest`: a login existing only in a second Tenant signs in, and its token names that Tenant, not the seeded one; that Manager then reads only its own Tenant's Client. Ticket: `second-tenant-login-fixture`, strengthened by the fix pass (see F1).
- **Stories 7, 8** — `CollidingUsernameSignInApiTest`, in its own class so the next feature deletes a file rather than editing a surviving one. Ticket: `pin-colliding-username-sign-in`.
- **Stories 9, 10** — the seam tests assert only properties that stay true after the fix, and nothing a user can see changed: no `backend/src/main`, no schema, V55 still free.

**The headline result is that the epic's prediction was wrong.** It said a
username present in two Tenants "authenticates against whichever row is found
first". Observed twice before pinning: **401 Unauthorized with an empty body**.
`UserRepository.findByUsername`'s derived `Optional` query fails on two
matching rows, and Spring Security turns that into an ordinary authentication
failure inside the filter chain — no exception reaches the controller.

So the defect is a **lockout, not a cross-tenant leak**. Nobody reaches
another Tenant's data through it; the affected user simply cannot sign in and
cannot tell that from a mistyped password. The epic has been corrected, and
`globally-unique-usernames` should be written against that real symptom.

One ticket was added at delivery that no story asked for:
`isolate-e2e-database-for-verify`, an `enabler` — see **Decisions taken
alone**.

## Acceptance walkthrough

1. Run the new sign-in test class alone and show every test name and a green result — played — evidence: `evidence/step-1.txt`
2. The second-Tenant login signs in 200 and the token's Tenant id is the fixture's, not `11111111-…` — played — evidence: `evidence/step-2.txt`
3. That Manager reads only its own Tenant's Client — played — evidence: `evidence/step-3.txt` *(replayed after the F1 fix; the first capture described the weaker test)*
4. The colliding username's real response, with the non-colliding control — played — evidence: `evidence/step-4.txt`
5. The pinning test names what deletes it and the assertion that replaces it — played — evidence: `evidence/step-5.txt`
6. Full `verify` green, named regression classes green by name — played — evidence: `evidence/step-6.txt`
7. The diff touches only `backend/src/test`; no `src/main`, no migration, V55 unused — played — evidence: `evidence/step-7.txt`
8. Confirm the pinning test pins the intended defect, and that deleting it later is right — **yours**

## Decisions taken alone

From the spec's `## Decisions taken` (twelve entries, all test-shape choices):
extend `OtherTenantFixture` rather than add a new fixture; return a record
rather than a bare id, since a sign-in assertion needs the Tenant to compare
against and the plaintext password, which cannot be recovered from a hash; one
method serving both the clean and the colliding case; a `MANAGER`-role login,
needing no companion Agent row; hashes from the `PasswordEncoder` bean rather
than a pasted literal; the Tenant read from the token's claim rather than from
a new response field; no migration test; no prefactoring of the
`repository.count()` assertions, which were checked and are delta assertions
over repositories this fixture never writes to; the pinning test named for the
property rather than the mechanism; and the split into two tickets so the
disposable test has its own commit.

**(after review) `isolate-e2e-database-for-verify`.** The merge gate came back
`ok: false` with **no blocking finding** and `verify: failed`. The failing
stage was the e2e suite, and the cause was environmental: `verify` ended in
`npm run test:e2e`, whose config defaults `E2E_DATABASE_URL` to the
**developer's live docker-compose Postgres**, so every gate run drove
Playwright against it and mutated it. `agent-invoice-submission-and-approval`
failed on exactly the staleness `implementer-notes.md` already warns about —
`getByText('Draft')` not found, because an earlier run had already sent and
approved that invoice. This feature's diff touches no frontend file, so it
could not have caused it.

The human chose an isolated database per gate run over dropping e2e from
`verify` or resetting the shared one. `verify` now ends in
`npm run test:e2e:isolated`, which takes three OS-assigned free ports and a
uniquely named throwaway Postgres. Proof, demonstrated rather than asserted:
per-table row counts and an order-independent content checksum
(`ddb09e7c3bb82c124c76299cabcff688`) of the docker-compose database, unchanged
across four isolated runs; and a deliberately failed run leaving no container
and no process behind.

**(after review) `ARCHITECTURE.md` gained three lines** — the two new scripts
as entry points and the isolated Playwright config under Tests — applied by
the merger, which disagreed with the implementer's empty harness declaration
on the grounds that the sibling `scripts/run-backend-for-e2e.sh` is documented
the same way.

## Debt recorded

- `frontend/playwright.e2e.isolated.config.ts` · smell: Incomplete teardown · `gracefulShutdown` is set on the backend `webServer` entry but not the frontend one, so a SIGKILL can orphan a `next start` grandchild (F7).

Four smells found in lines this feature changed were **fixed** in the one fix
pass rather than recorded: duplicated login calls that `IntegrationTest.loginAs`
already owns, an unnamed seeded-Tenant UUID literal, a dead `userId` record
component, and a duplicated Client-building block.

One finding is deliberately left open and unfixed, **F2**: this feature's diff
carries the loop's own bookkeeping — the answered handover-approval inbox item
was deleted and `docs/inbox/.gitkeep` added. No story asked for it. It rides
along because the loop never pushes `main`, so its records travel inside a
feature branch.

## How to undo

```
git revert -m 1 159df08
```

Reverts code only. This feature added no migration — V55 remains free.

Note that reverting takes the `verify` change with it, restoring an e2e stage
that runs against your docker-compose database.

## What happens next

`globally-unique-usernames`, the second feature of `tenant-scoped-sign-in`: a
username becomes unique across the whole deployment, rejected at creation if
already taken in any Tenant, so the sign-in lookup resolves to exactly one
user. It deletes `CollidingUsernameSignInApiTest` and replaces it with a test
that creating such a login is refused.

If you veto a decision above, it reopens as an open question on that feature's
spec. The one most worth your eye is the `verify` change, since it altered how
every future merge is checked.
