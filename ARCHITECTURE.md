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
- `backend/src/main/java/com/remotesupport/backend/security/LoginState.java` · the per-request check that a userId is an active Login, used by `JwtAuthenticationFilter`.
- `backend/src/main/java/com/remotesupport/backend/security/LoginDeactivatedException.java` · the sign-in refusal for a deactivated Login.
- `backend/src/main/resources/db/migration/V58__add_users_deactivated_at.sql` · the migration behind deactivating a Login.
- `backend/src/main/resources/db/migration/V59__add_client_invoice_sent_back.sql` · the migration behind sending a Client Invoice back to draft.
- `backend/src/main/java/com/remotesupport/backend/domain/ManagerName.java` · the one rule for a valid Manager name (trim, non-blank, at most 100 chars).
- `backend/src/main/java/com/remotesupport/backend/web/ManagerNameController.java` · `PUT /api/me/name`, Manager-only, writes the caller's own name.
- `backend/src/main/resources/db/migration/V60__add_users_name.sql` · the migration behind a Manager's name.
- `backend/src/main/resources/db/migration/V61__add_carrier_invoice_file_sim_card.sql` · the migration behind a Carrier Invoice File belonging to one SIM line.
- `backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceErrors.java` · the refused-request bodies both Client Invoice controllers' `@ExceptionHandler`s return: conflicts as `{code, message}`, invalid requests as `{message}`.
- `backend/src/main/java/com/remotesupport/backend/web/TenantTotalsController.java` · `GET /api/tenant-totals`, Manager-only.
- `backend/src/main/java/com/remotesupport/backend/web/TenantTotalsService.java` · selects and sums this month's billed (Client Invoices SENT/APPROVED) and payout (Agent Invoices APPROVED/PAID) per currency from frozen totals.
- `backend/src/main/java/com/remotesupport/backend/repository/AgentInvoiceTotal.java` · the single JPQL expression for an Agent Invoice's frozen total, shared by the Review Queue and Tenant totals queries.
- `backend/src/main/java/com/remotesupport/backend/web/SmartphoneCorrectionService.java` · the transactional Correction of a Smartphone's fields on a Contract's Fleet.
- `backend/src/main/java/com/remotesupport/backend/web/SimCardCorrectionService.java` · the transactional Correction of a SIM Card's number and Postpaid plan on a Contract's Fleet.
- `backend/src/main/java/com/remotesupport/backend/web/UnusablePlanException.java` · the coded 400 (PLAN_NOT_ON_CARRIER, PLAN_ARCHIVED) for a Postpaid Plan a SIM cannot take.
- `backend/src/main/java/com/remotesupport/backend/dto/SimCardPostpaidPlanUpdateRequest.java` · the request body of a SIM Card's plan Correction.
- `backend/src/main/java/com/remotesupport/backend/web/InvalidCorrectionException.java` · the coded 400 for a Correction the Fleet refuses.
- `backend/src/main/java/com/remotesupport/backend/web/CorrectionConflictException.java` · the coded 409 for a Fleet Correction that clashes with the unit's state.
- `backend/src/main/java/com/remotesupport/backend/web/ObjectStoreProperties.java` · typed `app.object-store` settings (endpoint, region, bucket, credentials, path style, create bucket).
- `backend/src/main/java/com/remotesupport/backend/web/ObjectStoreConfig.java` · builds the single S3 client for a non-AWS store and validates settings; never fails the context.
- `backend/src/main/java/com/remotesupport/backend/web/ObjectStoreAvailability.java` · cached, on-demand, single-flight answer to whether file storage is available, plus the never-fatal startup bucket probe.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceFileStorage.java` · key, put, read and delete of Carrier Invoice File bytes in the object-store bucket.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceFileService.java` · non-transactional attach (upload, record row under lock, undo on failure) and download.
- `backend/src/main/java/com/remotesupport/backend/web/ObjectStoreUnavailableException.java` · the 503 for a failed or unusable object store.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceFileName.java` · pure function building a Carrier Invoice File's computed name `{Client} - {Month Year} - {Carrier} - {SIM number}{.ext}`.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceFileMissingException.java` · the 404 for a row whose object is missing.
- `backend/src/main/resources/db/migration/V62__add_carrier_invoice_file_deleted_at.sql` · the migration behind retention marking a Carrier Invoice File's bytes deleted.
- `backend/src/main/resources/db/migration/V63__add_client_invoice_first_sent_at.sql` · the migration behind a Client Invoice remembering when it was first sent, kept through a send-back.
- `backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceDrafts.java` · the one get-or-create of a Contract's DRAFT Client Invoice for a billing month, shared by the invoice controller and the month send.
- `backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceMonthService.java` · the non-transactional half of the month send: month check, completing each Contract's current-month draft, then handing to `ClientInvoiceService`.
- `backend/src/main/java/com/remotesupport/backend/web/ClientInvoiceMonthController.java` · the Agent-only `POST /api/me/client-invoice-months/{YYYY-MM}/send`.
- `backend/src/main/java/com/remotesupport/backend/dto/ClientInvoiceMonthSendResponse.java` · the month send's answer: billing month, sent time, invoice ids.
- `backend/src/main/java/com/remotesupport/backend/dto/ClientInvoiceMonthSendRequest.java` · the month send's optional body naming the invoice ids.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceRetention.java` · deletes due Carrier Invoice File bytes 30 days after approval and marks the rows `deleted_at`, stopping at the first object-store failure.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceRetentionProperties.java` · typed `app.carrier-invoice-retention` settings (enabled, cron).
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceRetentionScheduling.java` · the app's only scheduling configuration: the UTC cron and the startup run of retention.
- `backend/src/main/java/com/remotesupport/backend/web/CarrierInvoiceFileDeletedException.java` · the 410 for downloading a file retention deleted.
- `backend/src/main/java/com/remotesupport/backend/repository/DueCarrierInvoiceFile.java` · projection of a Carrier Invoice File due for retention (id, storage path).

### Frontend — `frontend/app`

- `agent` · route segments of the Agent Console.
- `manager` · route segments of the Manager Console.
- `client` · route segments of the Client Portal.
- `login` · the login route.
- `api` · the BFF route handlers; the only place the browser's data reaches the backend.
- `fonts` · font assets served by the app.
- `frontend/app/api/agents/[agentId]/login/password/route.ts` · BFF pass-through for the Manager's password reset of an Agent, forwarding `Cache-Control: no-store`.
- `frontend/app/api/clients/[clientId]/testers/[testerId]/password/route.ts` · BFF pass-through for the Manager's password reset of a Tester, forwarding `Cache-Control: no-store`.
- `frontend/app/api/me/route.ts` · BFF GET proxy for the caller's own read, so the browser can read the caller's own name.
- `frontend/app/api/me/name/route.ts` · BFF PUT proxy for the caller's own-name write.
- `frontend/app/api/contracts/[contractId]/smartphones/[smartphoneId]/model/route.ts` · BFF PATCH proxy for a Smartphone's model Correction, via `forwardJson`.
- `frontend/app/api/contracts/[contractId]/smartphones/[smartphoneId]/owner/route.ts` · BFF PATCH proxy for a Smartphone's owner Correction, via `forwardJson`.
- `frontend/app/api/contracts/[contractId]/sim-cards/[simCardId]/number/route.ts` · BFF PATCH proxy for a SIM Card's number Correction, via `forwardJson`.
- `frontend/app/api/contracts/[contractId]/sim-cards/[simCardId]/postpaid-plan/route.ts` · BFF PATCH proxy for a Postpaid SIM Card's plan Correction, via `forwardJson`.
- `frontend/app/client/(dashboard)` · the /client dashboard page and its error.tsx.
- `frontend/app/api/me/client-invoice-months/[month]/send/route.ts` · BFF proxy for the whole-month send through forwardJson.
- `frontend/app/agent/client-invoices/[invoiceId]` · the Agent's by-id Client Invoice page, guarded by `requireAgent`.

### Frontend — `frontend/components`

- `app-shell` · application shell, layout and navigation.
- `ui` · the generic reusable primitives (buttons, dialogs, forms, tables).
- `theme` · theme provider, theme script and design tokens.
- `login` · the login flow's components.
- `agent` · components specific to the Agent Console.
- `manager` · components specific to the Manager Console.
- `client` · components specific to the Client Portal.
- `frontend/components/app-shell/change-name-dialog.tsx` · the dialog behind the account menu's Change name item: edits the caller's own name, never signs out.
- `frontend/components/manager/reset-password-dialog.tsx` · the two-step (confirm, then one-time reveal) dialog a Manager uses to reset an Agent's or Tester's password.
- `frontend/components/agent/agent-client-invoice-card.tsx` · the Agent's Client Invoice card.
- `frontend/components/agent/send-client-invoice-month-control.tsx` · the Agent's month bar: unsent, sent and incomplete states and the confirm-and-send flow.
- `frontend/components/agent/attach-carrier-invoice-dialog.tsx` · the attach form: pick a Postpaid SIM line and one file.
- `frontend/components/agent/remove-carrier-invoice-file-control.tsx` · the Agent's Remove on one Carrier Invoice File of an open draft, with an inline confirm.
- `frontend/components/ui/side-list.tsx` · the shared SideList (named nav landmark of links with name, detail, badge, mark and status slots) and SideListLayout (page body: column at xl, dropdown and header below xl, with lead and detailRef slots).
- `frontend/components/ui/tabs.tsx` · the shared WAI-ARIA tablist with roving tabindex, automatic activation and one rendered panel; switches between the tables of one record, never scopes a Contract.
- `frontend/components/ui/carrier-invoice-files-section.tsx` · the shared Carrier invoice files section for the Agent card, Manager invoice detail and Tester Invoices page, including the paused state (names listed, Download disabled, one notice).
- `frontend/components/ui/carrier-invoice-check.tsx` · the shared CarrierInvoiceCheck: a non-interactive mark showing a Postpaid SIM line's carrier invoice is attached, used by the Agent card and the Manager detail.
- `frontend/components/agent/invoice-line-table.tsx` · the Agent Client Invoice lines as one markup: a table at xl, one-column grid rows below it, with explicit ARIA table roles; exports `LINE_AREA` for parts to name their grid area.
- `frontend/components/manager/login-activation-dialog.tsx` · the shared confirm dialog a Manager uses to deactivate or reactivate a Login.
- `frontend/components/manager/send-back-client-invoice-control.tsx` · inline reason form that POSTs a send-back to a given endpoint and hands the returned invoice to its caller.
- `frontend/components/fleet/fleet-tabs-card.tsx` · the Fleet card whose header row is the "Fleet" tablist (SIM Cards, Smartphones) with per-tab action and empty state, and the `?tab=` parser; used by the Agent and Tester Fleet.
- `frontend/components/manager/contracts-view.tsx` · the Manager's one-page Contracts view: Clients side list, Agent select, invoice summary and tabbed Fleet; a pick is router.replace in a transition.
- `frontend/components/fleet/smartphone-model-control.tsx` · the inline control that corrects a Smartphone's model.
- `frontend/components/fleet/smartphone-owner-control.tsx` · the inline control that corrects a Smartphone's owner.
- `frontend/components/fleet/sim-card-number-control.tsx` · the inline control that corrects a SIM Card's number.
- `frontend/components/fleet/sim-card-plan-control.tsx` · the inline control that corrects a Postpaid SIM Card's plan, showing the fee before Save.
- `fleet` · smartphones, SIM cards, installation and serial editing.
- `carriers` · the carrier catalog and its pickers.
- `requests` · the request and provisioning workflow.
- `stock` · agent stock and inventory views.

### Frontend — `frontend/lib`

- `api` · typed callers for the BFF routes.
- `auth` · session and token handling.
- `demo` · demo data generation for development and the visual goldens.
- `frontend/lib/manager-contracts.ts` · groups the Manager's Contracts by Client and resolves the selected Contract from `?contract=`.
- `frontend/lib/api/client-invoice.ts` · the Agent's by-id Client Invoice read.
- `frontend/lib/api/client-invoice-month.ts` · clientInvoiceMonth: derives the month bar's state from the page's loaded invoices.

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
- `docker-compose.yml` · local Postgres and the SeaweedFS object store (`seaweedfs`, no host port); the e2e scripts start a throwaway SeaweedFS.
- `scripts/run-backend-for-e2e.sh` · starts docker-compose Postgres and the backend on 8080 for the e2e suite.
- `scripts/run-backend-for-e2e-isolated.sh` · starts a throwaway Postgres container and the backend against it for the isolated e2e stage, torn down by exact container name and PID on exit.
- `scripts/run-e2e-isolated.sh` · runs `frontend/tests/e2e` against that throwaway stack, driven by `frontend/playwright.e2e.isolated.config.ts`; what `verify`'s e2e stage calls.

## Tests

- `backend/src/test/java/com/remotesupport/backend/web` · integration tests at the HTTP seam, driven through MockMvc against a real PostgreSQL started by Testcontainers. The base class and its fixture helpers are `backend/src/test/java/com/remotesupport/backend/support/IntegrationTest.java`.
- `backend/src/test/java/com/remotesupport/backend/support/CarrierInvoiceTestSupport.java` · attaches a file per Postpaid SIM line before a send (by invoice id and current month); no-op when not a draft, no Postpaid line, or already covered.
- `backend/src/test/java/com/remotesupport/backend/demo/DemoDataLoaderStoreDownTest.java` · demo loader with the store refusing writes: completes, one WARN, invoice stays a draft.
- `backend/src/test/java/com/remotesupport/backend/migration` · migration tests, each migrating a throwaway database to a fixed version and asserting what that migration did.
- `frontend/components` · Vitest component tests, colocated with the component as `*.test.tsx`.
- `frontend/tests/visual` · Playwright visual goldens, rendered against the stub backend named in `frontend/playwright.config.ts` — no database.
- `frontend/tests/e2e` · Playwright end-to-end journeys against a real backend and Postgres, configured by `frontend/playwright.e2e.config.ts`. Which database they write to is set by `E2E_DATABASE_URL`; see `docs/agents/implementer-notes.md` before running them.
- `frontend/playwright.e2e.isolated.config.ts` · same `frontend/tests/e2e` specs run against a throwaway Postgres/backend/frontend stack that the run creates and destroys, never the docker-compose database.

## Invariants

- A controller returns a DTO, never a `domain` entity.
- Access control goes through a guard class in `security` (`FleetAccessGuard`, `ContractAccessGuard`, and their siblings), never inline in a controller.
- The browser reaches the backend only through `frontend/app/api`; a client component never calls the backend host directly.
- Migrations are forward-only Flyway scripts in `backend/src/main/resources/db/migration`, one per change, never edited once applied — every file there is `V<n>__*.sql`, with no undo or repeatable scripts.
