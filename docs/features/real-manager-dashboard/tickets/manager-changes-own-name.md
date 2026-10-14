---
id: manager-changes-own-name
title: A Manager can change their name from the account menu and see it on the chip
status: in-progress
depends_on: [manager-name-api, manager-dashboard-identity-and-counts]
labels: [frontend]
stories: [1, 29, 30, 31, 32, 33]
---

## Context

Frontend half of the 2026-10-14 amendment: spec `## Solution`, "Frontend: changing the name from the account menu"; `## Decisions taken`. Needs the BFF routes `frontend/app/api/me` (GET) and `frontend/app/api/me/name` (PUT), which do not exist today (only `app/api/me/password/route.ts` does); "Change name" as an optional first item of `ViewerMenu` shown when `onChangeName` is passed; `ChangeNameDialog`; and `viewerCanChangeName` through `SurfacePage`/`TopBar`/`ViewerActions`, passed by every Manager page. Only the Dashboard chip shows the name. Depends on `manager-name-api` (the endpoints) and `manager-dashboard-identity-and-counts` (the chip, and the shared page, stub and goldens).

## Acceptance criteria

- Every Manager page's account menu lists "Change name", "Change password", "Log out" in that order, reachable by arrow keys; Agent and Tester menus still hold only "Change password" and "Log out".
- "Change name" opens a "Name" field holding the current name (empty when none or the read fails); a blank submit shows "Enter your name." with no request sent; 101 characters shows "Your name can be at most 100 characters." with the text kept; any other failure shows "Couldn't change your name. Try again.".
- On success the dialog closes and the Dashboard chip reads "Yazan Abuhassan · Manager" with no sign-in in between, and still after reload; the user is not signed out.
- Other Manager pages (e.g. `/manager/invoices`) keep the plain "Manager" chip after the name is set.
- `manager-menu-open-*` is recaptured with three items and `change-name-dialog-open-*` is added, prefilled from the stubbed name.

## Tests

Seams: vitest component tests, Playwright e2e, the stubbed visual suite (spec `## Testing decisions` 3, 4, 5). `ViewerMenu` with `onChangeName` shows three items cycled by arrow keys, without it exactly two. New `ChangeNameDialog` test beside `change-password-dialog.test.tsx`: prefilled from the read, empty when unset or when the read fails, blank refused with no request sent, each code mapped to its message with the typed value kept, success closes and refreshes with no sign-out. E2E in `tests/e2e/manager-dashboard.spec.ts`: open the item, save a run-unique name, chip reads `<name> · Manager`, kept after reload, and `/manager/invoices` still shows the plain "Manager" chip. New `change-name-dialog.spec.ts` golden; `manager-menu-open-*` deleted then recaptured.

## Regression

Existing tests expected to change, each only by addition with every pre-existing case passing unmodified: `frontend/components/app-shell/viewer-menu.test.tsx` (new three-item and arrow-key cases); `tests/visual/viewer-menu.spec.ts` (golden recaptured with the third item); `tests/e2e/manager-dashboard.spec.ts` (name step added to the file created earlier). At risk: Agent and Tester menus and the change-password flow (`change-password.spec.ts`, `change-password-dialog.spec.ts`).

## Observability

N/A — a caller-own, non-sensitive correction; no audit trail is asked for.
