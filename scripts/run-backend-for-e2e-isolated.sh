#!/usr/bin/env bash
# Starts a throwaway Postgres (a plain `docker run`, never docker-compose.yml) and the Spring Boot
# backend against it, in the foreground, for the isolated e2e stack
# (frontend/playwright.e2e.isolated.config.ts, isolate-e2e-database-for-verify ticket). Never
# touches the developer's docker-compose `postgres` service on 5432 — this container gets its own
# name and host port, both chosen by the caller (scripts/run-e2e-isolated.sh) fresh per run.
#
# Also starts a throwaway SeaweedFS the same way (the object store for Carrier Invoice Files,
# ADR 0007): its own exact container name and host port, passed in, on the same pinned tag as
# docker-compose.yml and the backend tests, with the same dev-only credentials.
#
# Called as: run-backend-for-e2e-isolated.sh <container-name> <postgres-host-port> <backend-port>
#                                            <seaweedfs-container-name> <seaweedfs-host-port>
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
SEAWEEDFS_CONTAINER_NAME="$4"
SEAWEEDFS_PORT="$5"

# One tag everywhere: docker-compose.yml, ObjectStoreTestSupport and scripts/run-backend-for-e2e.sh.
SEAWEEDFS_IMAGE="chrislusf/seaweedfs:4.48"
OBJECT_STORE_DEV_ACCESS_KEY="dev-only-access-key"
OBJECT_STORE_DEV_SECRET_KEY="dev-only-secret-key-change-me"

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
  docker stop "$SEAWEEDFS_CONTAINER_NAME" >/dev/null 2>&1 || true
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

docker run -d --rm --name "$SEAWEEDFS_CONTAINER_NAME" \
  -p "127.0.0.1:${SEAWEEDFS_PORT}:8333" \
  -e AWS_ACCESS_KEY_ID="$OBJECT_STORE_DEV_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$OBJECT_STORE_DEV_SECRET_KEY" \
  "$SEAWEEDFS_IMAGE" server -dir=/data -s3 >/dev/null

echo "Waiting for isolated seaweedfs ($SEAWEEDFS_CONTAINER_NAME, port $SEAWEEDFS_PORT) to answer on its S3 port..."
# Any HTTP answer (an unauthenticated request is a 403) proves the S3 gateway is up.
until curl -s -o /dev/null "http://127.0.0.1:${SEAWEEDFS_PORT}/"; do
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
# application.yml's app.object-store block reads these; the rest of its defaults match.
export OBJECT_STORE_ENDPOINT="http://127.0.0.1:${SEAWEEDFS_PORT}"
export OBJECT_STORE_ACCESS_KEY="${OBJECT_STORE_DEV_ACCESS_KEY}"
export OBJECT_STORE_SECRET_KEY="${OBJECT_STORE_DEV_SECRET_KEY}"

cd "$ROOT_DIR/backend"
# Not `exec`: this script must stay alive to receive the signal above and run `cleanup` (an
# `exec`'d mvn would replace this process, losing the trap and orphaning the Postgres container).
mvn -q spring-boot:run &
MVN_PID=$!
wait "$MVN_PID"
