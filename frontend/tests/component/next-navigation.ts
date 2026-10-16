import { vi } from "vitest";

export const mockRouter = {
  refresh: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  forward: vi.fn(),
  prefetch: vi.fn(),
};

export const nextNavigationMock = {
  useRouter: () => mockRouter,
  usePathname: () => "/",
  // Reads the address the way Next does once it has applied a write (a restore): a render after one shows it.
  useSearchParams: () => new URLSearchParams(window.location.search),
};
