---
id: one-time-password-reveal
title: Add the shared one-time reveal of a generated password
status: ready-for-agent
depends_on: []
labels: [enabler, frontend]
stories: []
---

## Context

Enables `creation-dialogs-reveal-generated-password` (three consumers) and
`reset-an-agents-password-ui` (the reset dialog's second step). One new client component
in `frontend/components/manager`, built from shipped tokens and primitives, exactly as the
spec's `## Solution` → Frontend → "The one-time reveal" and `## Design direction` describe.
It has no consumer yet, so it is exercised by component tests only. Adds the one short
`DESIGN.md` entry the spec names; no token change. Read `docs/agents/frontend.md` first.

Modules touched: `frontend/components` → `manager`.

## Acceptance criteria

- Given an email, a password and a mode (creation or reset), it renders the heading and
  line from the spec ("{email} can now sign in with this password." / "…with this new
  password. Their old one no longer works."), the value in a read-only **Generated
  password** input (`autoComplete="off"`, `spellCheck={false}`, `.tnum`, wide tracking) that
  receives focus with its text selected.
- **Copy password** writes the exact value with `navigator.clipboard.writeText` and a polite
  status says "Copied"; if the clipboard rejects, the status says "Couldn't copy — select
  the password and copy it yourself".
- The warning line "This password won't be shown again…" is present, and **Done** is the
  only pill, calling the close callback.
- The password is held in component state only: nothing in the URL, `localStorage` or
  `sessionStorage`.
- `DESIGN.md` has the short entry for the reveal composition.

## Tests

Seam: Vitest + Testing Library, colocated `generated-password-reveal.test.tsx` (the component being `generated-password-reveal.tsx`) (the spec's
frontend component-test shape), clipboard stubbed at `navigator.clipboard`.

- value shown read-only, focused and selected on mount;
- Copy success announces "Copied" and the clipboard received the exact value;
- Copy rejection announces the fallback;
- Done calls the close callback;
- both heading variants (creation, reset);
- `localStorage` and `sessionStorage` empty after render.

## Regression

N/A — a new component with no consumer and no route; no existing test or golden is
touched. (`DESIGN.md` gains an entry; nothing in it changes.)

## Observability

N/A — presentational only; the password must never be logged, and the component logs nothing.
