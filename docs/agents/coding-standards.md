# Coding standards

<!-- sdlc:template agents-coding-standards 1 -->

Read by every implementer before writing code, and by the reviewer when
judging a change. Four rules govern this file itself:

1. **Tool before text.** Anything a linter, formatter or type-checker can
   enforce goes into that tool's config, wired into `verify` — never
   written here as prose.
2. **A line earns its place by settling something.** It resolves a real
   choice between valid options, or records a lesson from a mistake this
   project actually made. A generality true of every codebase ("use
   meaningful names") does neither.
3. **A rule here is citable, and citable means blocking.** The reviewer
   turns a violation into a `rule-violated` finding by quoting the line it
   broke; an uncited claim is downgraded to a non-blocking smell. Write
   rules a reviewer can point at, not vibes.
4. **Keep it under ~60 lines.** It starts short and grows only when a
   recurring smell in `docs/tech-debt.md` earns promotion to a written
   rule; the loop proposes such promotions at epic closure, and the human
   decides.

## Rules

Two stacks live here: a Spring Boot backend (`backend/`) and a Next.js
frontend (`frontend/`). Read the section that matches the file you are
changing.

### Backend — Java / Spring Boot

Enforce via tooling, not here: formatting → Spotless (google-java-format);
static analysis → Checkstyle + SpotBugs/PMD, wired into the Maven build.
Present in this project today: Checkstyle (`backend/checkstyle.xml`, bound to
the `check` goal), Flyway, Testcontainers.

1. Controllers validate the request DTO with `@Valid` + Bean Validation
   annotations; a service method never re-validates what the controller
   boundary already checked.
2. Business rules live in the service layer; a controller translates
   HTTP <-> domain and never branches on domain state.
3. The transaction boundary is the service method (`@Transactional`), never
   the repository or the controller; a repository method is not itself
   `@Transactional` unless it composes multiple writes on its own.
4. Entities are never returned directly from a controller; map to a
   response DTO — this is what keeps JPA lazy-loading and JSON
   serialization from fighting each other.
5. Integration tests run against the real engine via Testcontainers;
   mocking the database (`@MockBean` on a repository) is banned in
   integration tests — it does not catch the SQL bugs that matter.
6. Unit tests (JUnit 5 + Mockito) target one class with its collaborators
   mocked; a test that needs a Spring context is an integration test, named
   and located accordingly, not a unit test.
7. Exceptions crossing the service→controller boundary are domain
   exceptions carrying `@ResponseStatus`, translated to a response body by a
   per-controller `@ExceptionHandler` when the body needs a machine-readable
   `code` — never a `try/catch` producing an ad hoc `ResponseEntity`. (Corrected
   2026-09-22: this rule previously prescribed one `@ControllerAdvice`, which
   exists nowhere in `backend/src/main`; four controllers use their own
   `@ExceptionHandler`, so the rule as written blocked every diff that followed
   the project's only pattern. The prohibition was always obeyed and is kept.)
8. A `@Transactional` method never calls an external HTTP or queue client
   inside the transaction; dispatch that call after commit (event listener,
   outbox) so a slow downstream never holds a database lock.
9. Migrations are Flyway scripts, one per change, forward-only; never edit
   a migration that already ran in any shared environment.
10. Constructor injection only in application beans, no field `@Autowired` —
    it makes required dependencies explicit and the class constructible in a
    plain unit test. Spring **test** classes are excepted: a `@SpringBootTest`
    base class exposing shared helpers cannot practically constructor-inject,
    and the whole existing suite uses `@Autowired` fields.
11. A JPA relationship defaults to `LAZY`; `EAGER` requires a comment naming
    the query pattern that needs it.
12. Table-driven tests (`@ParameterizedTest` + `@MethodSource`) for any
    method with more than two branch conditions, instead of copy-pasted
    per-case tests.
13. Package by feature (`authentication`, `fleet`, `requests`, `invoicing`),
    not by layer (`controllers`, `services`, `repositories`) — a feature's
    files stay next to each other. **This is the norm for new code**, decided
    by the human on 2026-09-22.
    The backend does not obey it yet: it is packaged by layer (`web`,
    `repository`, `dto`, `domain`, `security`, `logging`), and moving it is
    the `package-by-feature` epic, not something a feature ticket does on the
    side. Until that epic lands, a reviewer cites this rule only against a
    **new** package or module choosing the layered shape — never against a
    change that follows the existing layout, which has nowhere else to go.
14. Configuration is bound to typed `@ConfigurationProperties` classes,
    never scattered `@Value("${...}")` injections across unrelated beans.

### Frontend — TypeScript / Next.js

Enforce via tooling, not here: formatting → Prettier; lint (unused vars,
import order, hooks rules) → ESLint (`eslint-config-next`); type safety →
`tsc --strict` with `noUncheckedIndexedAccess`.
Present in this project today: ESLint (`eslint.config.mjs`), `tsc --strict`.
Not yet installed, so **not citable as a blocking rule until they are**:
Prettier, `noUncheckedIndexedAccess`, Zod (rule 2), MSW (rule 10), a
client-state library (rule 12). Installing one is a proposal for the inbox,
not something a ticket does on the side.

1. Default every component to a Server Component; add `"use client"` only
   when the file needs state, effects, or browser APIs — not to "be safe".
2. Validate all external input (form submissions, route handler bodies,
   search params) with a Zod schema at the boundary; never trust a type
   assertion past that boundary.
3. Data fetching happens in Server Components or Route Handlers, never
   inside a Client Component's `useEffect` — that reintroduces the
   waterfall the App Router exists to remove.
4. Mutations go through Server Actions; call `revalidatePath`/
   `revalidateTag` inside the action, not from the client after it resolves.
5. Cross a Server/Client boundary only with serializable props — no
   functions, class instances or `Date`s passed to a Client Component; pass
   ISO strings and format them client-side.
6. An error that should show UI uses `error.tsx` at the nearest route
   segment; an error that should crash to a boundary above is thrown, never
   swallowed and logged.
7. Environment variables consumed in the browser are prefixed
   `NEXT_PUBLIC_` and nowhere else; a secret read in a Client Component is a
   bug, not a config oversight.
8. Compose Tailwind classes from the tokens in `tailwind.config`; an
   arbitrary value (`w-[123px]`) needs a comment saying why it isn't a token.
9. Unit and component tests (Vitest + Testing Library) sit next to the file
   as `*.test.tsx`; end-to-end journeys (Playwright) sit under `e2e/`, one
   file per journey.
10. A test that needs the network mocks at the fetch boundary (MSW), never
    by stubbing an internal function — internals are allowed to change shape.
11. Loading UI uses `loading.tsx` / `<Suspense>` scoped to the slowest data
    dependency, not one spinner for the whole page.
12. Client-side global state (Zustand/Context) holds only UI state that must
    survive navigation; server state stays in the cache Next.js already
    manages — do not duplicate it into a store.
13. Route Handlers return typed JSON via one shared response helper, never
    an ad hoc shape per route.
14. `params`/`searchParams` are typed and parsed once at the top of the
    route or page; do not re-parse them further down the tree.
15. A third-party or long-running call inside a Server Component carries an
    explicit timeout/`AbortController` — an unbounded await there holds up
    the whole response.
