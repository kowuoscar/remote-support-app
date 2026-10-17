---
id: approve-carrier-invoice-object-storage
type: approval
status: open
blocks: [carrier-invoice-object-storage]
created: 2026-10-17
---

<!-- sdlc:template inbox-item 1 -->

## Question

Approve the revised spec `docs/features/carrier-invoice-object-storage/spec.md`,
with your amendment applied? Read the user stories, the non-goals, the
decisions taken and the walkthrough.

Your amendment, as written into the spec:

1. The backend always starts, whatever the store's state or settings; it logs
   one error naming the endpoint and bucket.
2. It checks the bucket at startup and every 30 seconds (each check capped at
   about 3s), and a failed upload or download marks it unavailable at once.
   It recovers by itself; the log says once when it goes down and once when
   it comes back.
3. The invoice pages learn it from the invoice itself (`filesAvailable`), with
   no extra request. While unavailable, for the Agent, the Manager and the
   Tester: the file names still show; Attach and every Download are disabled;
   one notice in the files section:
   - "File storage is unavailable right now. Attaching and downloading are
     paused." where Attach is shown;
   - "File storage is unavailable right now. Downloading is paused."
     elsewhere.
   The controls come back on the next page load after the store returns.
4. Send, send-back and approve are unaffected here; the send gate (next
   feature) will decide what happens while storage is down.
5. Three tickets in one PR: the storage move with the availability check, the
   exact download name, the paused state on screen.

## Recommendation

Approve.

## Blocks

`carrier-invoice-object-storage`: no tickets until approved.

## Meanwhile

`text-size-lint-rule` stays held at your request.

## Answer
