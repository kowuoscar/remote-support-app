---
id: question-tester-open-requests-pending-approval
type: question
status: answered
blocks: [real-client-dashboard]
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

On a Tester's dashboard, should the **Open Requests** card count Requests at
**Pending Approval**, waiting on the Manager, as well as Submitted and In
Progress?

The Agent's dashboard leaves Pending Approval out, because the Agent can't
start such a Request yet. The demo page doesn't settle it: its status type
has no Pending Approval value.

## Recommendation

**Yes.** Count Pending Approval, Submitted and In Progress, with the meta
"Pending Approval, Submitted or In Progress". From the Tester's side, a
Request waiting on the Manager isn't done. The Agent's reason for excluding
it doesn't apply to a Tester.

## Blocks

`real-client-dashboard`: the spec stays `draft`, and its approval waits for
this answer.

## Meanwhile

Nothing else can move without you: every next feature waits on a spec
approval or on this.

## Answer

Yes (human, 2026-10-01): count Pending Approval, Submitted and In Progress, with the meta "Pending Approval, Submitted or In Progress".
