#!/usr/bin/env bash
# Starts a throwaway Postgres (a plain `docker run`, never docker-compose.yml) and the Spring Boot
# backend against it, in the foreground, for the isolated e2e stack
# (frontend/playwright.e2e.isolated.config.ts, isolate-e2e-database-for-verify ticket). Never
# touches the developer's docker-compose `postgres` service on 5432 — this container gets its own
# name and host port, both chosen by the caller (scripts/run-e2e-isolated.sh) fresh per run.
#
# Called as: run-backend-for-e2e-isolated.sh <container-name> <postgres-host-port> <backend-port>
#
# Playwright's webServer stops this script with SIGTERM (see the isolated config's
# `gracefulShutdown`), never SIGKILL — SIGKILL would skip the trap below and leave the Postgres
# container running as an orphan, since `docker run -d` detaches it from this process's own
# group. The trap is what makes teardown happen even when the e2e suite fails: whether Playwright
# stops this script because every test finished or because one failed, the same signal arrives.
set -euo pipefail

CONTAINER_NAME="$1"
PG_PORT="$2"
BACKEND_PORT="$3"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MVN_PID=""

cleanup() {
  if [[ -n "$MVN_PID" ]]; then
    kill "$MVN_PID" >/dev/null 2>&1 || true
    wait "$MVN_PID" 2>/dev/null || true
  fi
  # Exact container name, never a pattern: `docker stop` on a container started with `--rm` also
  # removes it, so a failed run leaves neither a running container nor a stopped one behind.
  docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup TERM INT EXIT

docker run -d --rm --name "$CONTAINER_NAME" \
  -p "127.0.0.1:${PG_PORT}:5432" \
  -e POSTGRES_DB=remote_support \
  -e POSTGRES_USER=remote_support \
  -e POSTGRES_PASSWORD=remote_support \
  postgres:16-alpine >/dev/null

echo "Waiting for isolated postgres ($CONTAINER_NAME, port $PG_PORT) to accept connections..."
until docker exec "$CONTAINER_NAME" pg_isready -U remote_support >/dev/null 2>&1; do
  sleep 0.5
done

# Lombok's annotation processing needs a JDK the pinned Lombok version supports; the machine's
# default `java` may be newer. Override JAVA_HOME before calling this script if 21 lives
# somewhere else.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

# application.yml:5-7 already reads these three; no backend source change needed.
export DB_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/remote_support"
export DB_USERNAME="remote_support"
export DB_PASSWORD="remote_support"
export SERVER_PORT="${BACKEND_PORT}"

cd "$ROOT_DIR/backend"
# Not `exec`: this script must stay alive to receive the signal above and run `cleanup` (an
# `exec`'d mvn would replace this process, losing the trap and orphaning the Postgres container).
mvn -q spring-boot:run &
MVN_PID=$!
wait "$MVN_PID"
