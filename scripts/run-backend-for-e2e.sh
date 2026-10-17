#!/usr/bin/env bash
# Starts local Postgres (docker compose) and the Spring Boot backend against it, in the
# foreground, so the frontend E2E Playwright suite (frontend/tests/e2e) can log in against a
# real backend instead of demo data. Playwright's webServer starts this and polls its health
# URL (http://127.0.0.1:8080/api/health) before running tests.
#
# Carrier Invoice Files need an object store (ADR 0007): this also starts a throwaway SeaweedFS
# (a plain `docker run`, own exact name, a free host port, a trap that removes it). It does not use
# docker-compose's `seaweedfs` service, which publishes no host port.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# One tag everywhere: docker-compose.yml, ObjectStoreTestSupport and run-backend-for-e2e-isolated.sh.
SEAWEEDFS_IMAGE="chrislusf/seaweedfs:4.48"
OBJECT_STORE_DEV_ACCESS_KEY="dev-only-access-key"
OBJECT_STORE_DEV_SECRET_KEY="dev-only-secret-key-change-me"
SEAWEEDFS_CONTAINER_NAME="remote-support-e2e-seaweedfs-$$-${RANDOM}"

MVN_PID=""

cleanup() {
  if [[ -n "$MVN_PID" ]]; then
    kill "$MVN_PID" >/dev/null 2>&1 || true
    wait "$MVN_PID" 2>/dev/null || true
  fi
  # Exact container name, never a pattern; `--rm` removes it on stop.
  docker stop "$SEAWEEDFS_CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup TERM INT EXIT

docker compose -f "$ROOT_DIR/docker-compose.yml" up -d postgres

echo "Waiting for postgres to accept connections..."
until docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T postgres pg_isready -U remote_support >/dev/null 2>&1; do
  sleep 1
done

SEAWEEDFS_PORT="$(node -e '
  const net = require("node:net");
  const server = net.createServer();
  server.listen(0, "127.0.0.1", () => {
    const { port } = server.address();
    server.close(() => console.log(port));
  });
')"

docker run -d --rm --name "$SEAWEEDFS_CONTAINER_NAME" \
  -p "127.0.0.1:${SEAWEEDFS_PORT}:8333" \
  -e AWS_ACCESS_KEY_ID="$OBJECT_STORE_DEV_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$OBJECT_STORE_DEV_SECRET_KEY" \
  "$SEAWEEDFS_IMAGE" server -dir=/data -s3 >/dev/null

echo "Waiting for seaweedfs ($SEAWEEDFS_CONTAINER_NAME, port $SEAWEEDFS_PORT) to answer on its S3 port..."
# Any HTTP answer (an unauthenticated request is a 403) proves the S3 gateway is up.
until curl -s -o /dev/null "http://127.0.0.1:${SEAWEEDFS_PORT}/"; do
  sleep 0.5
done

# Lombok's annotation processing needs a JDK the pinned Lombok version supports; the machine's
# default `java` may be newer. Override JAVA_HOME before calling this script if 21 lives
# somewhere else.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

export OBJECT_STORE_ENDPOINT="http://127.0.0.1:${SEAWEEDFS_PORT}"
export OBJECT_STORE_ACCESS_KEY="$OBJECT_STORE_DEV_ACCESS_KEY"
export OBJECT_STORE_SECRET_KEY="$OBJECT_STORE_DEV_SECRET_KEY"

cd "$ROOT_DIR/backend"
# Not `exec`: this script must stay alive to receive the signal and run `cleanup`.
mvn -q spring-boot:run &
MVN_PID=$!
wait "$MVN_PID"
