---
id: proposal-login-lifecycle-retro
type: proposal
status: open
blocks: []
created: 2026-10-06
---

<!-- sdlc:template inbox-item 1 -->

## Question

Which of these improvements should I make? They come from the retro on
`login-lifecycle` and the invoice features built in the same stretch. Answer
per item: yes, no, or later.

**In this repo**

1. **A lint rule for hand-written text sizes.** Every feature since the
   `text-label` tokens landed has had `text-[12px]`/`text-[13px]` flagged in
   review (edit-client-invoice-lines F2, deactivate-a-login F3,
   send-a-client-invoice-back F2), each costing a fix pass. A small ESLint
   rule would catch it at `verify` instead. It would flag only new lines, by
   failing on the files a branch touches.
2. **A busy mode for the shared button that keeps keyboard focus.** Three
   features in a row lost keyboard focus to the top of the page while a
   button was saving. Each was fixed locally, three different ways. Give
   `frontend/components/ui/button.tsx` one focusable busy mode
   (`aria-disabled` + `aria-busy` + spinner) and use it everywhere. The reset
   and login dialogs would get the fix too. That's a small refactor ticket.
3. **Reword rule Frontend 8.** It still says "once the type-scale tokens
   land". They landed weeks ago. New wording: new lines use
   `text-label-sm`/`text-label`, and leftover `text-[12px]`/`text-[13px]` in
   untouched lines are debt.
4. **Settle rule Backend 7.** The app's permission checks throw Spring
   Security's `AccessDeniedException` from services, which the rule forbids
   as written. Either allow Spring Security's own errors explicitly, or log
   the checks as debt. I recommend allowing them.
5. **The debt log is over its threshold** in both `backend` and `frontend`.
   The planned `package-by-feature` epic would pay much of the backend
   share. I suggest a small frontend clean-up epic after it:
   - the duplicated invoice layouts;
   - the dead routes;
   - the two date styles;
   - the three spinner styles.

**In the sdlc plugin (outside this repo)**

6. **The test guard flags production code.** It matched `.Disabled` in
   Spring's `DisabledException` and refused a correct merge. It should only
   look at test files.
7. **Implementers skipped "see the test fail first"** on 3 tickets out of
   22. The ones told explicitly to watch the test fail did so. The
   implementer's definition could say it in those words.
8. **The acceptance runner ran out of turns** on 2 of the last 3 features. Its turn
   limit is too low for walkthroughs of 20 or more steps, or the walkthrough
   should be split across two runs.
9. **Mergers twice returned "placeholder"** instead of their result, and had
   to be asked again.
10. **Migration numbers reserved in a spec go stale.** A spec reserved V57,
    but another feature shipped V58 first. Specs could say "the next free
    version at merge" instead of a number.

## Recommendation

Yes to 1, 2, 3 and 4. For 5: yes to `package-by-feature` next and to the
frontend clean-up after it. 6 to 10 are changes to the sdlc plugin itself,
for you to make there.

## Blocks

Nothing.

## Meanwhile

The next planned epic continues.

## Answer

