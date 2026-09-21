<!-- sdlc:map:start -->
# Remote Support App

<!-- sdlc:template agent-map 1 -->

A shared system of record for the phones, SIM cards, support requests and two monthly invoicing cycles of a company that works through in-country local-support agents.
For that company's Managers, its local support Agents, and its Clients' Testers.

Verify command: see `docs/agents/sdlc.json`.

| You need | Read |
|---|---|
| Who and why | `PRODUCT.md` |
| Where code lives, module boundaries, entry points | `ARCHITECTURE.md` |
| Domain vocabulary | `CONTEXT.md` |
| The visual world (UI work only) | `DESIGN.md` |
| Planned work, epic order | `docs/roadmap/` |
| A feature's spec and tickets | `docs/features/` |
| Past irreversible decisions | `docs/adr/` |
| Open questions, approvals, alerts awaiting a human | `docs/inbox/` |

Every rule an agent must follow is indexed in `docs/agents/README.md` — read
it before writing code, a spec or a ticket.

Feature work runs through the `sdlc` skill: it reads the state of this repository and takes the next step.
<!-- sdlc:map:end -->

## Skill arbitration

The engineering skills in `~/.agents/skills/` overlap with superpowers. Prefer
`grilling` over `superpowers:brainstorming`, `tdd` over
`superpowers:test-driven-development`, `diagnosing-bugs` over
`superpowers:systematic-debugging`, and let `implement-spec` manage its own
worktrees rather than `superpowers:using-git-worktrees` or
`subagent-driven-development`. Superpowers keeps everything else.
