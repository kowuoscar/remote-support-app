---
id: demo-story-loader
title: Build the demo story on demand under a demo profile
status: in-progress
depends_on: [trim-seed-to-test-baseline]
labels: [backend, seed-data, docs]
---

## Context

Implements `spec.md` Solution (Demo story, Separate databases, The story, Retired, Documentation) and user stories 1-8, 10 and 11.

## Acceptance criteria

- [ ] A component active only under the `demo` profile builds the story at startup when its marker is absent, and writes nothing when it is present
- [ ] The story contains everything listed in `spec.md` The story, with every unit, Request, Fee and invoice linked to the Clients, Contracts, Testers and Agents it belongs to
- [ ] Current-month activity goes through the application's services; past months are written directly and match what those services would have stored, including frozen snapshots
- [ ] docker-compose has a second Postgres service for the demo with its own volume; the compose backend runs with the `demo` profile against it; the committed e2e flow keeps using the existing `postgres` service and never sees demo data
- [ ] `scripts/seed-demo-invoice-review.sh` is removed
- [ ] The docker-compose header and the README list every login with its password and what to look at, and how to reset the demo alone (remove the demo volume, then `docker compose up --build`)
- [ ] A fresh `docker compose up --build` shows the story for every role

## Tests

- **API seam:** a backend integration test boots the `demo` profile on a fresh database and asserts the story's shape (every Request type and status, a Stock unit, a cancelled Postpaid SIM, a sent Client Invoice and Agent Invoice, frozen totals equal to their snapshots), then starts it again and asserts nothing was added.
- **Manual:** a real-browser walkthrough of each documented login against the demo stack, run on isolated ports, never against the user's running stack.
- **E2E and visual:** full suites green on a fresh isolated database, proving the demo never leaks into them.

## Regression

The e2e flow's database and every existing suite. The full backend, e2e and visual suites protect it.

## Observability

The loader logs whether it built the story or skipped it, and counts per entity when it builds.
