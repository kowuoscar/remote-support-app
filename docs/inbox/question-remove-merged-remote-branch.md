---
id: question-remove-merged-remote-branch
type: question
status: answered
blocks: []
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Remove the merged remote branch `feature/real-agent-dashboard` from GitHub?
Removing a remote branch is out of bounds for an agent here (escalation case
5), so the loop left it.

## Recommendation

Remove it: PR #19 is merged and nothing else uses the branch. Either use the
"Delete branch" button on PR #19, or turn on the repository setting
"Automatically delete head branches" so future merges clean up after
themselves.

## Blocks

Nothing.

## Meanwhile

The loop carries on.

## Answer

Keep it (human, 2026-10-01): leave merged feature branches on GitHub. Close the item.
