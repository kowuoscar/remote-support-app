---
id: backend-bootstrap
title: Bootstrap the Spring Boot API, tenant-scoped schema and JWT auth
status: done
depends_on: []
labels: [backend, infra]
---

## Context

Establishes the walking-skeleton backend from the spec's Solution (Architecture) and Constraints: Java 21 / Spring Boot 3 / PostgreSQL 16, a tenant-scoped schema, the role model, and JWT auth. Nothing else can be built until this exists.

## Acceptance criteria

- [ ] A Spring Boot 3 (Java 21) project exists under `/backend` with a PostgreSQL 16 connection (Testcontainers for tests)
- [ ] Every core table carries a tenant reference; a Tenant entity exists
- [ ] A role model exists for Manager, Agent, Tester, SuperAdmin (SuperAdmin reserved, no dedicated behaviour yet)
- [ ] A login endpoint issues a JWT for a valid username/password; protected endpoints reject requests without a valid token
- [ ] A seed script/migration creates one Tenant and one Manager user usable for local development and tests

## Tests

Backend HTTP API seam (`MockMvc`/`RestAssured` + Testcontainers Postgres): login with valid credentials returns a token; login with invalid credentials is rejected; an authenticated request to a protected endpoint succeeds; an unauthenticated request is rejected with 401.

## Regression

N/A — project bootstrap, no prior behaviour.

## Observability

Structured request logging carrying tenant and user id on every authenticated request; a health-check endpoint.
