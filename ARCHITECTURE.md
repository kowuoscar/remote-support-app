# Architecture

<!-- sdlc:template architecture 1 -->

This file **points**; it never **describes**. A route, a schema, a function
signature is read in the code, never copied here — a copy goes stale, a
pointer does not. It changes in the same merge as the code it maps: an
implementer who moves or adds a module updates this file in that ticket, or
declares the change for the merger to apply. Every path cited here is checked
mechanically by `sdlc-check-harness`; a path that stops existing is a build
error, not a nitpick.

## Modules

### Backend — `backend/src/main/java/com/remotesupport/backend`

- `domain` · JPA entities for the core concepts (Agent, Client, Contract, Request, Fee, invoices, fleet items).
- `dto` · request and response shapes crossing the HTTP boundary.
- `repository` · Spring Data repositories, the only data access to `domain`.
- `security` · JWT authentication, the per-resource access guards, and security configuration.
- `web` · HTTP controllers, the services holding business rules, and the domain factories they share.
- `logging` · the audit trail written when an entity is created or changes state.
- `demo` · the `demo` profile's seed loader, used for development and demonstration.

### Frontend — `frontend/app`

- `agent` · route segments of the Agent Console.
- `manager` · route segments of the Manager Console.
- `client` · route segments of the Client Portal.
- `login` · the login route.
- `api` · the BFF route handlers; the only place the browser's data reaches the backend.
- `fonts` · font assets served by the app.

### Frontend — `frontend/components`

- `app-shell` · application shell, layout and navigation.
- `ui` · the generic reusable primitives (buttons, dialogs, forms, tables).
- `theme` · theme provider, theme script and design tokens.
- `login` · the login flow's components.
- `agent` · components specific to the Agent Console.
- `manager` · components specific to the Manager Console.
- `client` · components specific to the Client Portal.
- `fleet` · smartphones, SIM cards, installation and serial editing.
- `carriers` · the carrier catalog and its pickers.
- `requests` · the request and provisioning workflow.
- `stock` · agent stock and inventory views.

### Frontend — `frontend/lib`

- `api` · typed callers for the BFF routes.
- `auth` · session and token handling.
- `demo` · demo data generation for development and the visual goldens.

## Dependency direction

Backend: `domain` has no outbound dependencies; `repository` and `dto` depend
only on `domain`; `web` depends on `repository`, `domain`, `dto`, `security`
and `logging` — never the reverse. `security` depends on `domain`,
`repository` and `logging`. `demo` sits above everything and may depend on any
layer; nothing depends on `demo`.

Frontend: route segments and components depend on `lib`, never the reverse.
The browser reaches the backend only through `frontend/app/api`; Server
Components may call the backend directly through
`frontend/lib/api/backend.ts`.

## Entry points

- `backend/src/main/java/com/remotesupport/backend/BackendApplication.java` · the Spring Boot main class.
- `backend/src/main/java/com/remotesupport/backend/web` · the HTTP controllers, all mapped under the `api` URL prefix.
- `frontend/app/login`, `frontend/app/agent`, `frontend/app/manager`, `frontend/app/client` · the four browser surfaces.
- `frontend/app/api` · the BFF route handlers, one subtree per backend resource.
- `frontend/proxy.ts` · the request proxy in front of the app.
- `scripts/run-backend-for-e2e.sh` · starts docker-compose Postgres and the backend on 8080 for the e2e suite.

## Tests

- `backend/src/test/java/com/remotesupport/backend/web` · integration tests at the HTTP seam, driven through MockMvc against a real PostgreSQL started by Testcontainers. The base class and its fixture helpers are `backend/src/test/java/com/remotesupport/backend/support/IntegrationTest.java`.
- `backend/src/test/java/com/remotesupport/backend/migration` · migration tests, each migrating a throwaway database to a fixed version and asserting what that migration did.
- `frontend/components` · Vitest component tests, colocated with the component as `*.test.tsx`.
- `frontend/tests/visual` · Playwright visual goldens, rendered against the stub backend named in `frontend/playwright.config.ts` — no database.
- `frontend/tests/e2e` · Playwright end-to-end journeys against a real backend and Postgres, configured by `frontend/playwright.e2e.config.ts`. Which database they write to is set by `E2E_DATABASE_URL`; see `docs/agents/implementer-notes.md` before running them.

## Invariants

- A controller returns a DTO, never a `domain` entity.
- Access control goes through a guard class in `security` (`FleetAccessGuard`, `ContractAccessGuard`, and their siblings), never inline in a controller.
- The browser reaches the backend only through `frontend/app/api`; a client component never calls the backend host directly.
- Migrations are forward-only Flyway scripts in `backend/src/main/resources/db/migration`, one per change, never edited once applied — every file there is `V<n>__*.sql`, with no undo or repeatable scripts.
