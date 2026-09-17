import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

/**
 * Component seam (spec.md Testing decisions, seam 3): client components rendered in jsdom with
 * Testing Library, `fetch` and Next.js navigation mocked — no backend, no browser. Only
 * `*.test.tsx` files next to the component they cover; the Playwright suites under tests/visual
 * and tests/e2e use `*.spec.ts` and are never picked up here.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./", import.meta.url)) },
  },
  test: {
    environment: "jsdom",
    include: ["components/**/*.test.tsx", "lib/**/*.test.tsx", "app/**/*.test.tsx"],
    setupFiles: ["./tests/component/setup.ts"],
    restoreMocks: true,
    unstubGlobals: true,
  },
});
