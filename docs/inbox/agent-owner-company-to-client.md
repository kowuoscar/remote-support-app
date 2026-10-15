---
id: agent-owner-company-to-client
type: question
status: open
blocks: [agent-corrects-the-fleet]
created: 2026-10-15
---

<!-- sdlc:template inbox-item 1 -->

## Question

May the Agent change a Smartphone's owner from Company to Client? A Return of a Company-owned phone waits for the Manager's approval; a Client-owned one does not. An Agent who switches the owner can send the phone back without the Manager approving. See docs/features/agent-corrects-the-fleet/spec.md, Open questions 1.

## Recommendation

Client to Company only for the Agent; Company to Client stays with the Manager. *Reason:* the approval protects the company's phones; the person it checks should not be able to lift it. Alternative: both directions, audit-logged.

## Blocks

Ticket `move-corrections-to-the-agent`.

## Meanwhile

The other questions of this spec gate; nothing is built until the specs are approved.

## Answer
