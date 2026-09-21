#!/usr/bin/env bash
# Runs the frontend e2e suite (frontend/tests/e2e, via playwright.e2e.isolated.config.ts) against a
# throwaway Postgres + backend + frontend stack that this run creates and destroys — never the
# developer's docker-compose stack on 5432/8080/3000 (scripts/run-backend-for-e2e.sh's flow, still
# reachable by hand through `npm run test:e2e`). This is what `verify` (docs/agents/sdlc.json) runs.
#
# Ports and the Postgres container name are picked fresh every invocation, by asking the OS for a
# currently-free ephemeral port (bind to port 0, read back what it chose, release it) rather than a
# fixed number: this is what keeps two runs on the same machine — two implementers' worktrees, or
# this script invoked twice in a row — from ever colliding with each other or with the developer's
# stack, with no coordination between them needed.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

free_port() {
  node -e '
    const net = require("node:net");
    const server = net.createServer();
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      server.close(() => console.log(port));
    });
  '
}

export E2E_ISOLATED_PG_PORT
E2E_ISOLATED_PG_PORT="$(free_port)"
export E2E_ISOLATED_BACKEND_PORT
E2E_ISOLATED_BACKEND_PORT="$(free_port)"
export E2E_ISOLATED_FRONTEND_PORT
E2E_ISOLATED_FRONTEND_PORT="$(free_port)"
export E2E_ISOLATED_PG_CONTAINER="remote-support-e2e-pg-$$-${RANDOM}"

echo "Isolated e2e stack: postgres container ${E2E_ISOLATED_PG_CONTAINER} on port ${E2E_ISOLATED_PG_PORT}, backend on port ${E2E_ISOLATED_BACKEND_PORT}, frontend on port ${E2E_ISOLATED_FRONTEND_PORT}."

cd "$ROOT_DIR/frontend"
exec npx playwright test --config=playwright.e2e.isolated.config.ts
