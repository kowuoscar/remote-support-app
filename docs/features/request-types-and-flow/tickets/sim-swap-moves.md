---
id: sim-swap-moves
title: A SIM Swap Request moves or exchanges SIM Cards when completed
status: done
depends_on: [provision-request-details]
labels: [backend, frontend, requests, fleet]
---

## Context

Implements `spec.md` Solution (Details and Fleet changes for SIM Swap) and user stories 6, 7 and 31.

## Acceptance criteria

- [x] A SIM Swap Request is either one move (an Active SIM Card into an Active Smartphone of the same Contract) or an exchange of two SIM Cards installed in two different Smartphones
- [x] Submission is refused if applying the moves would leave a Smartphone with more than two SIM Cards, or if a move changes nothing
- [x] Completing the Request applies the moves with no Agent input, through the Installed-in module
- [x] Completion re-checks against the Fleet as it is then and is refused with a clear message if the moves no longer fit
- [x] The Requests lists describe the moves in words ("SIM … into …")
- [x] A SIM Swap Request created before this ticket completes without changing the Fleet

## Tests

- **API seam:** move and exchange on both paths; third-SIM refusal; no-op refusal; other Contract and retired units refused; completion applies both moves atomically; stale-at-completion refusal; legacy no-op.
- **Component seam:** the exchange picker offers only SIM Cards that are installed, and the second one only from a different Smartphone.
- **E2E:** a Tester submits an exchange between two Smartphones; the Agent completes it; both Fleet rows swap.

## Regression

The Installed-in rules and Request completion. `sim-installed-in-smartphone`'s API tests must pass unchanged.

## Observability

The installed and uninstalled audit events carry the Request id.
