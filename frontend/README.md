This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Tests

Three suites, each at its own seam:

| Suite | Command | Needs | Covers |
|---|---|---|---|
| Component | `npm test` (`npm run test:watch` while iterating) | Nothing — jsdom, `fetch` and Next.js navigation are mocked | Client components: rendered states, pending, inline errors |
| Visual | `npm run test:visual` (`npm run test:visual:update` to re-baseline) | Chromium via Playwright; builds and serves the app on port 4173 with demo data, no backend | Golden screenshots of the Manager, Agent and Client surfaces |
| E2E | `npm run test:e2e` | Docker (PostgreSQL) and the backend, both started by the config | Golden-path journeys against the real API |

Component tests are Vitest + Testing Library files named `*.test.tsx`, placed next to the component they cover. `tests/component/setup.ts` mocks `next/navigation` for every test; assert refreshes and navigation through `mockRouter` from `tests/component/next-navigation.ts`, and fake the API with `stubFetch` / `stubPendingFetch` from `tests/component/fetch.ts`. Assert what a user can observe — roles, names, text, disabled state — never component internals. Async server components are not rendered here; the E2E suite covers them.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
