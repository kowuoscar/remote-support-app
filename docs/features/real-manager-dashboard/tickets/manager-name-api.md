---
id: manager-name-api
title: A Manager can read and correct their own name through the API
status: ready-for-agent
depends_on: []
labels: [backend]
stories: [30, 31, 33]
---

## Context

Backend half of the human's 2026-10-14 amendment: spec `## Solution`, "Backend: the Manager name"; `## Decisions taken`; `## Constraints`. One additive migration (`users.name`, nullable `varchar(100)`, next free Flyway version at merge), `User.name`, the `ManagerName` rule (trim, non-blank, at most 100), `name` on `GET /api/me` read from the row each call, and Manager-only `PUT /api/me/name` with coded `400`s and its own `@ExceptionHandler`, with a `SecurityConfig` matcher ahead of the `/api/me/**` fall-through. No backfill, no seeded name, no SuperAdmin entry. Consumed by `manager-changes-own-name`.

## Acceptance criteria

- `PUT /api/me/name` with "  Yazan Abuhassan  " as a Manager returns `204`; `GET /api/me` then returns `name` "Yazan Abuhassan" (trimmed) with the same `username`; the same token and the old password still work; a second `PUT` replaces the name.
- A blank or whitespace name gives `400 NAME_BLANK`, 101 characters gives `400 NAME_TOO_LONG`, 100 characters gives `204`; a refused write leaves the stored name as it was.
- `GET /api/me` omits `name` for an unnamed Manager and always for an Agent and a Tester.
- An Agent or a Tester gets `403` from `PUT /api/me/name`, no token gets `401`, a deactivated Manager gets `401`.
- A Manager's `PUT /api/me/name` changes only their own name; no other Login's name, `OtherTenantFixture`'s Managers included, changes.
- The migration adds a nullable `users.name` and writes no data: every existing row, the seeded Manager included, keeps no name.

## Tests

Seam: the HTTP API seam, `IntegrationTest` with MockMvc against real Postgres (spec `## Testing decisions` 2). New `ManagerNameApiTest`, prior art `ChangeOwnPasswordApiTest`, `ProtectedEndpointTest`, `DeactivatedLoginRefusedApiTest`. Cases: unnamed Manager and Agent/Tester `/api/me` have no `name`; trimmed write then read; replacement; blank, whitespace, 101 and 100 characters; refused write leaves name unchanged; Agent 403, Tester 403, no token 401, deactivated Manager 401; token and old password still work; `OtherTenantFixture` Managers' names untouched; migration test asserting the column is nullable and existing rows are null (prior art `SmartphoneOwnerMigrationTest`).

## Regression

At risk: every `/api/me` consumer and the password change under `/api/me/password` (matcher ordering). `ProtectedEndpointTest` and `ChangeOwnPasswordApiTest` must keep passing unmodified. No existing test is expected to change.

## Observability

N/A — a caller-own, non-sensitive correction; the spec asks for no audit trail.
