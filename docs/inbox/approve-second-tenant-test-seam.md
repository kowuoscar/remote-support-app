---
id: approve-second-tenant-test-seam
type: approval
status: open
blocks: [second-tenant-test-seam]
created: 2026-09-21
---

## Question

Approve the spec for `second-tenant-test-seam`, the first feature of the
`tenant-scoped-sign-in` epic:
`docs/features/second-tenant-test-seam/spec.md`.

Read the **user stories**, the **non-goals**, `## Decisions taken` and the
**acceptance walkthrough**. `## Solution` is yours to skip.

In one line: the test suite gains the ability to seed a second Tenant *with a
login* and sign in against it, plus one deliberately disposable test recording
that a username present in two Tenants does not sign the caller in to their
own Tenant. It fixes nothing — the fix is the next feature.

Three things worth your eye:

1. **It is smaller than the epic assumed.** `OtherTenantFixture` already
   exists, already builds second Tenants through the repositories, and is
   already used by five test classes. Its only gap is that it creates no login
   rows. This feature closes that gap; it builds nothing new.
2. **The defect may not be what the epic predicted.** The epic's feature line
   says a colliding username "authenticates against whichever row is found
   first". `UserRepository.findByUsername` returns `Optional<User>` from a
   derived query, which on two matching rows raises an incorrect-result-size
   failure rather than choosing one — so a failed sign-in is the likelier
   outcome. The ticket observes what really happens and pins exactly that,
   naming the test for the property rather than the mechanism. The epic's
   wording gets corrected once we know. Nothing is blocked either way: the
   next feature removes both outcomes.
3. **One test is written to be deleted.** Once `globally-unique-usernames`
   adds the global unique index, the colliding insert fails before the test
   can assert anything. It lives in its own ticket and commit so the next
   feature can revert it cleanly, and its replacement — a login refused
   because the username is taken in another Tenant — belongs to that feature.

Touches no production source, no schema, no frontend. No migration; V55 stays
free.

## Recommendation

Approve as written. The decisions are all test-shape choices, cheap to undo,
and `## Open questions` is `None` honestly rather than by omission — I checked
each of the twelve `## Decisions taken` against the five escalation cases and
none of them is a *what*.

## Blocks

`second-tenant-test-seam` stays `draft` and is not cut into tickets until this
is answered. That in turn holds `globally-unique-usernames`, and so the whole
`tenant-scoped-sign-in` epic.

## Meanwhile

Nothing else is ready to work: the other three epics are `planned` but
unplanned into features, and each would need its own exploration. The loop
pauses here.

Separately and independently of this item: Docker is still down
(`docker-not-running`), so no feature can pass the merge gate until it is
started.

## Answer
