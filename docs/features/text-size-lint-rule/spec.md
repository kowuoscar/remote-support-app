---
feature: text-size-lint-rule
epic: frontend-cleanup
status: approved
date: 2026-10-09
---

<!-- sdlc:template spec 1 -->

# A lint rule for the two label sizes

## Problem

The frontend has two size tokens for small text, `text-label-sm` (12px) and
`text-label` (13px). Frontend 8 says a new or changed line uses them and never
the arbitrary `text-[12px]`/`text-[13px]`. Nothing checks that rule except a
reviewer reading the diff. Review flagged hand-written sizes on
edit-client-invoice-lines F2, deactivate-a-login F3 and
send-a-client-invoice-back F2. Each one cost a fix pass for a one-word change.

The rule is hard to follow by hand because the old values are everywhere:
203 lines in 79 files still write `text-[12px]` (78) or `text-[13px]` (126).
An implementer copies a neighbouring line and the old value comes with it.
The rule is also hard to check by hand: "a new or changed line" means reading
the diff, and nothing in the repository computes one.

The people who feel this are the owner, who pays for the fix passes, and the
implementers and reviewers, who spend turns on it. No Manager, Agent or
Tester sees any of it, and none of them should see this feature either.

## Journeys

This advances `frontend-cleanup` (`docs/roadmap/frontend-cleanup.md`). It is
the first feature of the epic, and it gives the epic the first of its three
closing proofs: on `main`, `verify` fails on a branch that adds `text-[13px]`.

No journey in `docs/journeys.md` moves. Every screen must look exactly as it
does today.

## Goals / Non-goals

Goals:

- Every existing `text-[12px]` becomes `text-label-sm`, and every
  `text-[13px]` becomes `text-label`, across the frontend sources. The change
  is pixel-identical.
- An ESLint rule, part of the existing `npm run lint`, fails on
  `text-[12px]`/`text-[13px]` in any frontend source file. Its message names
  the token to use.
- A test pins the rule's behaviour against the real ESLint configuration.
- Frontend 8 is reworded: the check is now mechanical, and the "known debt"
  clause is gone because no debt is left.

Non-goals. Each is something a reasonable agent would otherwise do:

- **No diff-scoped or file-scoped check.** Nothing computes "the files a
  branch touches" or "changed lines". The rule holds on the whole frontend,
  which the migration makes possible (see Decisions taken).
- **11px, 15px and 28px are left alone.** `text-[11px]` (32 places),
  `text-[15px]` (top bar) and `text-[28px]` (stat card) keep their arbitrary
  values. No token is created for them. The rule does not flag them.
- **No new token, no renamed token, no change to `globals.css` or
  `DESIGN.md`.** That `text-label` is a misleading name (tech-debt,
  `globals.css`) is not paid here. The comment in `globals.css` that writes
  the literals to record the equivalence stays as it is.
- **No contrast fix.** The 13px "Open queue" and "Open Invoices" links that
  fail AA in dark mode (tech-debt) need a colour token change. A size swap
  does not fix them.
- **No visual change of any kind.** No golden is updated. If the swap moves a
  pixel, that is a bug in the swap, not a reason to re-capture.
- **No other class-name cleanup.** Other arbitrary values (`w-[…]`,
  `max-w-[…]`, `min-w-[720px]`) and the requirement that they carry a
  comment are not linted. Nothing else on the touched lines is reformatted or
  reordered.
- **No autofix and no ESLint plugin package.** The migration is done once, by
  the first ticket, and the rule only reports.
- **No change to `verify`, to the `lint` script or to CI.** The rule runs
  because `verify` already runs `npm run lint`.
- **No new npm dependency.**
- **No guard against `eslint-disable` comments.** A suppression is visible
  in review, and Constraints forbid adding one for this rule.
- **The epic's other features are not touched,** including the button busy
  mode (`busy-button-keeps-focus`), even where the same files are edited.

## User stories

"Owner" is the human who accepted the epic. "Implementer" and "reviewer" are
the agents that build and review features. The "user" is a Manager, an Agent
or a Tester using the app.

1. As the owner, I want `verify` to fail when any frontend source contains `text-[12px]` or `text-[13px]`, so that the size is caught by a command, not by a review fix pass.
2. As an implementer, I want the lint error to name the token that replaces the value (`text-label-sm` for 12px, `text-label` for 13px) and cite Frontend 8, so that I can fix it in one edit without opening the standards.
3. As an implementer, I want the rule to catch the value however the class string is written: a JSX `className` string, a template literal, a string in an object map, or an argument to `cn`, so that no form slips through.
4. As an implementer, I want the rule to catch the value with a variant prefix, an important mark or a line-height modifier (`sm:text-[13px]`, `!text-[12px]`, `text-[13px]/5`), so that a decorated form does not slip through.
5. As an implementer, I want the rule to leave alone the tokens themselves and other arbitrary sizes (`text-[11px]`, `text-[15px]`, `text-[28px]`), so that it fails only on what Frontend 8 forbids.
6. As the owner, I want every existing `text-[12px]` and `text-[13px]` replaced by its token, so that the rule can hold on the whole frontend from the day it lands.
7. As a user, I want every screen to look exactly as it did, in light and dark themes, on desktop and mobile, so that a cleanup changes nothing I see.
8. As an implementer, I want the lint result not to depend on which files my branch touches, so that editing an old file never fails on lines I did not write and no diff tooling is needed.
9. As a reviewer, I want Frontend 8 to say that lint enforces the two sizes everywhere and that no old values remain, so that I stop checking them by eye and cite the rule correctly.
10. As the owner, I want a test that fails if the rule is dropped or weakened in the ESLint configuration, and that Vitest really collects and runs, so that the guard cannot vanish silently.
11. As the owner, I want the rule built from ESLint's own features, with no new dependency, so that there is nothing extra to maintain or upgrade.

## Solution

Two steps: first replace every old value with its token, then add a rule
that keeps them out.

### 1. Replace every existing value (prefactoring)

Every `text-[12px]` in the frontend's TypeScript and JavaScript sources
becomes `text-label-sm`, and every `text-[13px]` becomes `text-label`. Only
that token in the class string changes. A variant prefix or modifier stays
where it is (none is in use today). The forms the explorer found are all
covered: JSX string literals (most), the three template literals (the
requests status filter tabs, the review queue view, the top bar) and the
Fleet picker size map, whose values go through `cn`.

This is pixel-identical by construction. The tokens are declared in the
`@theme` block with no paired line-height, so Tailwind emits a font-size
declaration only, exactly like the arbitrary value. `cn` is a plain join with
no `tailwind-merge`, so no class is dropped by a merge that does not know the
custom tokens. No line today pairs one of these values with a second
font-size utility, so the cascade order between two size utilities cannot
change on those lines. The tokens are already in use in 11 files and passed
their goldens there.

### 2. The rule

The ESLint flat configuration gets one more entry, applied to every file
ESLint lints in the frontend. It uses ESLint's built-in `no-restricted-syntax`
with two selectors:

- a string literal whose value matches the pattern below;
- a template literal part whose raw text matches the same pattern.

The pattern is the substring `text-[12px]` or `text-[13px]`, so prefixes and
modifiers match too, and `text-[11px]` and the tokens do not. There are two
entries, one per size, so each message names its own token:

```
text-[12px] → "Use text-label-sm, not text-[12px] (Frontend 8)."
text-[13px] → "Use text-label, not text-[13px] (Frontend 8)."
```

It matches any string, not only a `className` attribute, because class
strings travel through `cn`, object maps and template literals. Comments are
not strings, so a comment that mentions the value is not flagged.

No other configuration entry sets `no-restricted-syntax` today. A flat
configuration entry that sets the rule again replaces its selector list
rather than adding to it, so the configuration keeps every restricted-syntax
selector in this one entry. Constraints make this binding.

### 3. Frontend 8

The second sentence of Frontend 8 is reworded to:

> The two small type sizes are the tokens `text-label-sm` and `text-label`;
> `text-[12px]`/`text-[13px]` are never written, and `npm run lint` fails on
> them anywhere in the frontend. (Amended by the human, 2026-10-01 and
> 2026-10-08; enforced by lint since `text-size-lint-rule`.)

The first sentence, about arbitrary values needing a comment, is unchanged.
The "Present in this project today" line above the frontend rules already
names ESLint, so it does not change.

### Interfaces touched

- The frontend's ESLint flat configuration: one added entry.
- The Vitest configuration: its `include` list gains one entry for the lint
  test (see Testing decisions). Nothing else in it changes.
- About 79 frontend source files: class strings only.
- `docs/agents/coding-standards.md`: Frontend 8, as above.

No backend, schema, API, route or `ARCHITECTURE.md` change. No module is
added, moved or renamed.

## Design direction

N/A — no user interface change. The migration is pixel-identical and conforms
to `DESIGN.md`'s Label role (500, 12–13px) as it stands.

## Constraints

- **Pixel-identical.** `npm run test:visual` passes with no golden updated,
  added or deleted. Run it on an idle machine; the suite is known to fail
  under load. Its 0.005 pixel tolerance can hide a small shift (tech-debt), so
  the compiled CSS is also checked: the `text-label-sm` and `text-label`
  utilities each declare `font-size` and nothing else.
- **Exact mapping:** `text-[12px]` → `text-label-sm`, `text-[13px]` →
  `text-label`. Prefixes and modifiers stay. No other character on a touched
  line changes, apart from what the formatter already does on its own.
- After the migration, no TypeScript or JavaScript source under `frontend/`
  (outside `node_modules` and `.next`) contains `text-[12px]` or
  `text-[13px]`. The comment in `globals.css` is the only remaining
  occurrence, and it is CSS, which ESLint does not lint.
- **One restricted-syntax entry.** Every `no-restricted-syntax` selector in
  the frontend configuration lives in a single configuration entry.
- **Severity is `error`,** so `npm run lint` exits non-zero and `verify`
  fails.
- **The rule covers every file `npm run lint` lints**, tests included. No
  file or directory is excluded from it.
- **No `eslint-disable` comment for this rule** is added anywhere.
- **No new dependency.** The `dependencies` and `devDependencies` in
  `package.json` are unchanged.
- **`verify`** (`docs/agents/sdlc.json`) is run unchanged and passes. Maven
  needs JDK 21 (`docs/agents/implementer-notes.md`).
- **Files allowed to change:** frontend sources that hold the two values, the
  ESLint configuration, the Vitest configuration, the new lint test,
  `docs/agents/coding-standards.md`, and this feature's folder.

## Testing decisions

**Seam: ESLint's Node API, driven with the project's real configuration.**
A Vitest test builds an `ESLint` instance from the frontend's own flat
configuration. It lints small source snippets with `lintText` under a
`.tsx` file path inside the frontend, and asserts on the reported messages.
This is the highest automated seam that proves the behaviour: it tests the
configuration as `npm run lint` loads it, not the rule in isolation. A test
built on `RuleTester` would test `no-restricted-syntax` (which ESLint already
tests) with a copy of the selectors, and would pass even if the configuration
lost the entry.

Cases, each an external behaviour:

- each form from stories 3 and 4 is reported, with the message naming the
  right token (stories 1–4);
- `text-label-sm`, `text-label`, `text-[11px]`, `text-[15px]`, `text-[28px]`,
  and a comment that mentions `text-[13px]` are not reported (story 5);
- the 12px and 13px messages differ and each names its own token (story 2).

**Where it lives, and why Vitest's include changes.** The file under test is
the ESLint configuration at the frontend root. Frontend 9 puts a unit test
next to its file as `*.test.tsx`, so the test sits beside the configuration
under that name. Vitest's `include` covers only `components/`, `lib/` and
`app/`, so a test at the root would be skipped without any warning. The
include gains exactly one entry for this test's file. The test opts into
Vitest's `node` environment with a per-file directive, because ESLint does
not need jsdom. The ticket shows the test failing before the configuration
entry exists, which also proves Vitest collects it (story 10).

**Pixel-identical proof (story 7)** is not a new test. It is the existing
visual suite (`npm run test:visual`, committed goldens per surface × theme ×
breakpoint) run without update, plus the compiled-CSS check in Constraints.
`verify` does not run the visual suite (tech-debt), so the ticket and the
acceptance run it by hand.

**Whole-codebase proof (stories 6 and 8)** is `npm run lint` itself: once the
rule is in, a single missed occurrence fails it.

Prior art: no test in the repository covers the ESLint configuration today,
and none uses `RuleTester`. The closest precedent for the shape is the
component seam described in the Vitest configuration's header: a Vitest file
next to the thing it covers. The visual suite under `tests/visual` is reused
as it is.

## Decisions taken

- **The rule holds on the whole frontend, after a pixel-identical migration;
  it is not scoped to the files or lines a branch touches.** The tokens
  render exactly as the old values, so replacing all 203 lines changes no
  pixel. Then a plain whole-codebase rule needs no diff machinery, which the
  repository does not have (no CI, nothing computes changed files). A
  file-scoped rule would also fail on the old lines of every touched file.
  The epic's wording, "in the files a branch touches", is still met, because
  every file is covered. Undo: delete the entry.
- **Migration first, rule second, in two tickets.** The migration is a large
  mechanical diff and the rule is a small one. Reviewing them apart keeps the
  rule's diff readable. The rule cannot land first, because lint would fail
  on 203 lines.
- **Built-in `no-restricted-syntax`, not a local plugin or custom rule.** It
  needs no new file type, no plugin registration and no dependency, and its
  message can name the token. A custom rule would only add an autofix, and
  the one-time migration makes an autofix unnecessary.
- **Match any string literal and template part, not only `className`.**
  Class strings reach the DOM through `cn`, object maps (the Fleet pickers)
  and template literals. The values are specific enough that a false
  positive is unlikely.
- **Two selectors per size, one message each.** The message is the fix
  (story 2), so each size names its own token.
- **11px, 15px and 28px are out of scope.** Frontend 8 names only 12px and
  13px. There is no token for the others, and 11px sits below `DESIGN.md`'s
  12–13px Label role, so making a token for it would be a design change.
  Those values stay under Frontend 8's first sentence (an arbitrary value
  needs a comment), which review checks.
- **The `globals.css` comment keeps its literals.** It records why the swap
  is safe, and ESLint does not lint CSS.
- **Test seam: ESLint's Node API with the real configuration, beside the
  configuration, collected through one added Vitest include entry.** It is
  the highest seam, and it fails if the entry is removed. `RuleTester` would
  not. The explicit include entry fixes the silent-skip trap the explorer
  found.
- **The test file is named `*.test.tsx`, per Frontend 9, even though it has
  no JSX.** This keeps to the rule's letter rather than adding an exception
  for one file.
- **Pixel identity is checked by the visual suite plus compiled CSS, not by
  goldens alone.** The suite's 0.005 tolerance can hide a small shift.
- **Frontend 8 is reworded in the rule ticket, keeping the human's
  attribution and adding an enforcement note.** The "known debt" clause
  becomes false the day the migration lands. The new wording strengthens the
  human's intent from 2026-10-08 ("a lint rule catches them at `verify`")
  rather than changing it.
- **The epic's feature line is not edited.** It stays true as written.

## Open questions

None.

Nothing here can be seen by a Manager, Agent or Tester, by construction, and
everything is undone by a revert. The epic said "the files a branch touches",
and this spec chooses the whole codebase instead. That is a *how*, recorded
above, and the human may veto it.

## Acceptance walkthrough

1. [agent] Search the frontend's TypeScript and JavaScript sources (outside `node_modules` and `.next`) for `text-[12px]` and `text-[13px]`, and show there are no matches. Show the one remaining occurrence is the `globals.css` comment. Show `text-label-sm` and `text-label` now appear in about 90 files, up from 11. (stories: 6)
2. [agent] Run `npm run build`, open the compiled CSS, and show that the `.text-label-sm` and `.text-label` rules each declare only `font-size` (12px and 13px through their variables), with no line-height. (stories: 7)
3. [agent] On an idle machine, run `npm run test:visual` with no update flag. Show every golden passes, and that `git status` shows no golden added, changed or deleted. (stories: 7)
4. [agent] In a scratch edit to a file this feature did not otherwise touch, add `className="text-[13px]"` and run `npm run lint`. Show it exits non-zero, gives the file and line, and says "Use text-label, not text-[13px] (Frontend 8)." Repeat with `text-[12px]` and show the message names `text-label-sm`. (stories: 1, 2, 8)
5. [agent] In the same scratch file, add a template literal holding `text-[12px]`, an object map value holding `text-[13px]`, `sm:text-[13px]`, `!text-[12px]` and `text-[13px]/5`. Show `npm run lint` reports each one. (stories: 3, 4)
6. [agent] Replace them with `text-label-sm`, `text-label`, `text-[11px]`, `text-[15px]`, `text-[28px]`, and a comment that mentions `text-[13px]`. Show `npm run lint` passes. Revert the scratch file. (stories: 5)
7. [agent] Run the lint test alone with Vitest and show it is collected and passes. Remove the rule's configuration entry, re-run it, and show it fails. Restore the entry. (stories: 10)
8. [agent] Show `package.json` dependencies are unchanged, the ESLint configuration holds one `no-restricted-syntax` entry built only from ESLint's own rules, and no `eslint-disable` for it exists in the frontend. Show that `git diff` against the merge base touches only the files allowed in Constraints. (stories: 11)
9. [agent] Show the amended Frontend 8 text: the "known debt" clause gone, lint enforcement stated, the human's 2026-10-01 and 2026-10-08 attribution kept. Run `verify` from `docs/agents/sdlc.json` and show it green. (stories: 1, 9)
10. [human] Open the Agent's log-fee dialog, the Client's Invoices page and the Manager's Carriers page in light and dark themes, on desktop and on a phone width. Confirm small text looks as it did before. (stories: 7)
11. [human] Read the amended Frontend 8 as a reviewer would, and confirm it says what you meant on 2026-10-08. (stories: 9)

## Execution order

1. `label-size-tokens-everywhere` — every `text-[12px]`/`text-[13px]` in the frontend sources replaced by `text-label-sm`/`text-label`. Visual suite unchanged, compiled CSS checked. Labels: `frontend`, `enabler`. Depends on nothing. (stories: 6, 7)
2. `lint-fails-on-arbitrary-label-sizes` — the `no-restricted-syntax` entry with its two messages, the ESLint Node API test and its Vitest include entry, and the Frontend 8 rewording. Labels: `frontend`. Depends on `label-size-tokens-everywhere`. (stories: 1, 2, 3, 4, 5, 8, 9, 10, 11)
