---
id: accept-deactivate-a-login
type: acceptance
status: answered
blocks: []
created: 2026-10-05
---

<!-- sdlc:template inbox-item 1 -->

## Question

Does switching a login off and on do what you wanted?
`docs/features/deactivate-a-login/delivery.md`.

A Manager can switch an Agent's or a Tester's login off from their page or
row, and back on. The person is signed out at once. They see "This login has
been deactivated. Ask your Manager if you need access again." when they type
the right password. Nothing about them is deleted. Agents played 20 of 23
steps, with evidence. Three are yours:

21. On a phone, sign in as a real Agent. As the Manager, switch them off and
    watch the phone's next tap land on sign-in. Read the sign-in message as
    that person would, and the dialog wording as the Manager. Do both say the
    right thing, without suggesting anything was deleted?
22. Switch them back on. Are you happy that their old password works again,
    with **Reset password** beside it if you want a fresh one?
23. Open that Agent's page, their invoices and a request they raised, and a
    switched-off Tester's Client page. Is everything you need still there?

## Recommendation

Accept, or tell me what's off and it becomes a follow-up.

## Blocks

Nothing.

## Meanwhile

The login epic closes, and the loop moves on to `send-a-client-invoice-back`.

## Answer

Accepted (2026-10-05): steps 21–23 checked.
