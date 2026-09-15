## Engineering workflow

Feature work runs through `/sdlc <idea>`, or `/sdlc-implement <feature-slug>`
to resume one whose tickets already exist.

`docs/agents/issue-tracker.md` defines where specs and tickets live, what shape
they take, the status lifecycle and the Definition of Done. Read it before
writing a spec, a ticket, or a commit that closes one.

The engineering skills in `~/.agents/skills/` overlap with superpowers. Prefer
`grilling` over `superpowers:brainstorming`, `tdd` over
`superpowers:test-driven-development`, `diagnosing-bugs` over
`superpowers:systematic-debugging`, and let `implement-spec` manage its own
worktrees rather than `superpowers:using-git-worktrees` or
`subagent-driven-development`. Superpowers keeps everything else.

Maintain the domain model as you go with `domain-modeling`: update `CONTEXT.md`
the moment a term settles, and record an ADR only when a decision is hard to
reverse, surprising without context, and a genuine trade-off.

Before any UI work, read `~/.claude/sdlc/frontend.md`. `impeccable` is the only
design system active during implementation; `DESIGN.md` is committed before the
first line of UI, and every visual change either conforms to it or changes it in
the same commit.
