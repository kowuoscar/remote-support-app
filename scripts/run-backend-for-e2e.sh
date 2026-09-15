#!/usr/bin/env bash
# Starts local Postgres (docker compose) and the Spring Boot backend against it, in the
# foreground, so the frontend E2E Playwright suite (frontend/tests/e2e) can log in against a
# real backend instead of demo data. Playwright's webServer starts this and polls its health
# URL (http://127.0.0.1:8080/api/health) before running tests.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose -f "$ROOT_DIR/docker-compose.yml" up -d postgres

echo "Waiting for postgres to accept connections..."
until docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T postgres pg_isready -U remote_support >/dev/null 2>&1; do
  sleep 1
done

# Lombok's annotation processing needs a JDK the pinned Lombok version supports; the machine's
# default `java` may be newer. Override JAVA_HOME before calling this script if 21 lives
# somewhere else.
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$ROOT_DIR/backend"
exec mvn -q spring-boot:run
