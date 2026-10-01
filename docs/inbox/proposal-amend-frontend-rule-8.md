---
id: proposal-amend-frontend-rule-8
type: proposal
status: open
blocks: []
created: 2026-10-01
---

<!-- sdlc:template inbox-item 1 -->

## Question

Fix Frontend rule 8 in `docs/agents/coding-standards.md`?

It requires classes built from tokens in `tailwind.config`, and a comment on
every arbitrary value. But:

- `tailwind.config` doesn't exist. The project is on Tailwind v4, and its
  tokens live in `frontend/app/globals.css` `@theme`.
- There are no font-size tokens.
- `app/` and `components/` hold 233 uncommented `text-[12px]` and
  `text-[13px]` uses.

As written, the rule blocks every UI change that follows the project's only
type-size pattern.

## Recommendation

Point the rule at `globals.css` `@theme`, and add the two type-scale tokens
(`text-meta` 12px, `text-caption` 13px) as a small enabler ticket. Then the
rule stays strict and becomes satisfiable. The cheaper alternative is to
exempt font sizes from the comment requirement.

## Blocks

Nothing.

## Meanwhile

Reviewers keep reading the rule as written.

## Answer
