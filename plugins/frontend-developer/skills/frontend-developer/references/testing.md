# Testing

Three layers, each with a clear job:

| Layer | Tool | Covers |
| --- | --- | --- |
| Unit | Vitest | `app/lib/utils.ts`, Zod schemas, `app/lib/data.ts`, `app/lib/auth.ts` and action logic with the API mocked |
| Component | Vitest + React Testing Library | Client components: rendered output, interaction, aria wiring |
| End to end | Playwright | Whole flows through Server Components and Server Actions, API mocked |

Tests are colocated: `app/lib/utils.test.ts`, `app/ui/invoices/pagination.test.tsx`. Playwright
specs live in `e2e/`. MSW handlers are shared from `mocks/`.

Four environment facts that will otherwise cost an afternoon:

- Server-only modules start with `import 'server-only'`, which throws outside a Server
  Component. `vitest.config.mts` replaces it with an empty module (see the `stubServerOnly`
  plugin in `../assets/vitest.config.mts`) — keep that plugin.
- `'use cache'` is inert under Vitest, but `cacheTag()`/`cacheLife()` throw without the Next
  runtime. Mock them in any test that touches a cached data function:
  `vi.mock('next/cache', () => ({ cacheTag: vi.fn(), cacheLife: vi.fn() }))`. Keep `e2e/` in
  `tsconfig.json` so type-aware lint can parse the specs, and exclude it in Vitest's `exclude`.
- `app/lib/env.ts` validates at import time, so **every required variable must be set in
  `vitest.setup.ts`**. Miss one and importing any server module fails with a ZodError that
  names the variable, not the test.
- `vi.mock` is hoisted above the imports. A factory that closes over a plain top-level `const`
  throws "Cannot access '…' before initialization" — create those spies with `vi.hoisted`:

  ```ts
  const { getAccessToken, redirect } = vi.hoisted(() => ({
    getAccessToken: vi.fn(async () => ({ token: 'test-token' })),
    redirect: vi.fn(() => {
      throw new Error('NEXT_REDIRECT');
    }),
  }));

  vi.mock('@/app/lib/auth0', () => ({ auth0: { getAccessToken } }));
  vi.mock('next/navigation', () => ({ redirect }));
  ```

Setup files to copy are in `../assets/` (`vitest.config.mts`, `vitest.setup.ts`,
`playwright.config.ts`); see `tooling.md`.

## Unit tests

Pure helpers are the cheapest coverage — test the branches, not the happy path only:

```ts
import { describe, expect, it } from 'vitest';
import { formatCurrency, generatePagination } from '@/app/lib/utils';

describe('generatePagination', () => {
  it('lists every page when there are seven or fewer', () => {
    expect(generatePagination(1, 5)).toEqual([1, 2, 3, 4, 5]);
  });

  it('collapses the middle when the current page is in the first three', () => {
    expect(generatePagination(2, 10)).toEqual([1, 2, 3, '...', 9, 10]);
  });

  it('collapses both sides when the current page is in the middle', () => {
    expect(generatePagination(6, 10)).toEqual([1, '...', 5, 6, 7, '...', 10]);
  });
});

it('formats cents as USD', () => {
  expect(formatCurrency(2500)).toBe('$25.00');
});
```

## Data functions and actions, with MSW

Mock at the network boundary, not by stubbing `apiFetch` — that way the wrapper's URL building,
headers and error mapping are under test too.

`mocks/handlers.ts`:

```ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('http://api.test/invoices', () =>
    HttpResponse.json([{ id: '1', name: 'Acme', amount: 2500, status: 'paid' }]),
  ),
];
```

`mocks/server.ts` (MSW v3 — `server.listen({ onUnhandledFrame: 'error' })` in the setup file;
on MSW v2 the option is `onUnhandledRequest`):

```ts
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
```

A data test then overrides per case:

```ts
import { http, HttpResponse } from 'msw';
import { expect, it, vi } from 'vitest';
import { server } from '@/mocks/server';
import { fetchInvoiceById } from '@/app/lib/data';

// 'use cache' is inert here, but cacheTag/cacheLife need the Next runtime.
vi.mock('next/cache', () => ({ cacheTag: vi.fn(), cacheLife: vi.fn() }));

it('returns the invoice', async () => {
  await expect(fetchInvoiceById('1')).resolves.toMatchObject({ id: '1' });
});

it('throws ApiError on a server failure', async () => {
  server.use(
    http.get('http://api.test/invoices/1', () => new HttpResponse(null, { status: 500 })),
  );
  await expect(fetchInvoiceById('1')).rejects.toThrow('API request failed with 500');
});
```

For actions, call the exported function directly with a `FormData` and assert the returned
`State`:

```ts
const formData = new FormData();
formData.set('customerId', '');
formData.set('amount', '0');

const state = await createInvoice({ message: null, errors: {} }, formData);

expect(state.errors?.customerId).toContain('Please select a customer.');
expect(state.errors?.amount).toContain('Please enter an amount greater than $0.');
```

Mock `next/cache` and `next/navigation` when testing an action's success path, since
`updateTag` and `redirect` need a request scope:

```ts
vi.mock('next/cache', () => ({ updateTag: vi.fn(), revalidateTag: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: vi.fn(() => {
    throw new Error('NEXT_REDIRECT');
  }),
}));
```

Set `API_BASE_URL=http://api.test` in the Vitest setup so the wrapper and the handlers agree.

## Component tests

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import Search from '@/app/ui/search';

const replace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  usePathname: () => '/dashboard/invoices',
  useSearchParams: () => new URLSearchParams('query=old'),
}));

it('pushes the typed term into the query string', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  // user-event needs the timer shim, or typing hangs under fake timers.
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  render(<Search placeholder="Search invoices..." />);

  await user.type(screen.getByRole('textbox', { name: /search/i }), 'acme');
  await vi.advanceTimersByTimeAsync(300);

  expect(replace).toHaveBeenCalledWith('/dashboard/invoices?query=acme&page=1');
  vi.useRealTimers();
});
```

Rules:

- Query by role, label or text — what a user perceives. `getByTestId` is a last resort, and
  querying by class name or tag is never acceptable.
- Interact with `@testing-library/user-event`, not `fireEvent`.
- Assert the accessibility contract: the error text appears, and the input's
  `aria-describedby` points at the container holding it.
- `await screen.findBy…` for anything async; no arbitrary `waitFor` sleeps.
- Async Server Components are not RTL's job — cover them with Playwright. Test the client leaves
  and the data functions separately.

## End-to-end tests

```ts
import { expect, test } from '@playwright/test';

test('searching narrows the invoice list', async ({ page }) => {
  await page.goto('/dashboard/invoices');
  await page.getByRole('textbox', { name: /search/i }).fill('acme');
  await expect(page).toHaveURL(/query=acme/);
  await expect(page.getByRole('row')).toHaveCount(2);
});

test('creating an invoice shows a validation error for an empty customer', async ({ page }) => {
  await page.goto('/dashboard/invoices/create');
  await page.getByRole('button', { name: /create invoice/i }).click();
  await expect(page.getByText('Please select a customer.')).toBeVisible();
});
```

- One spec per user flow, in `e2e/`. Role-based locators, web-first assertions
  (`await expect(locator)…`), never `page.waitForTimeout`.
- Stub the backend so runs are deterministic: `page.route('**/api/**', …)`, or point
  `API_BASE_URL` at a mock server started by Playwright's `webServer`.
- Run against a production build (`next build && next start`) so caching, streaming and Server
  Actions behave as they will in production. That is also the only place the prerendering rules
  from `data-layer.md` are enforced, so the E2E run doubles as a check on them.
- Add an axe accessibility assertion (`@axe-core/playwright`) on each main page.

## Authenticated paths

Mock `@/app/lib/auth0` (the project's singleton), never the SDK's internals, and assert the
contract rather than the plumbing:

```ts
it('attaches the access token as a bearer header', async () => {
  let seen: string | null = null;
  server.use(
    http.get('http://api.test/me/summary', ({ request }) => {
      seen = request.headers.get('authorization');
      return HttpResponse.json({ outstanding: 1 });
    }),
  );

  await expect(authedFetch('/me/summary')).resolves.toMatchObject({ outstanding: 1 });
  expect(seen).toBe('Bearer test-token');
});

it('redirects to login when the API rejects the token', async () => {
  server.use(
    http.get('http://api.test/me/summary', () => new HttpResponse(null, { status: 401 })),
  );

  await expect(authedFetch('/me/summary')).rejects.toThrow('NEXT_REDIRECT');
  expect(redirect).toHaveBeenCalledWith('/auth/login');
});
```

For E2E, stub `**/auth/**` or use a dedicated Auth0 test tenant with a seeded user. Never point
Playwright at a tenant holding real users.

## What not to test

Tailwind class strings, Next internals (routing, caching), the shape of a skeleton, or snapshot
blobs of whole trees. Test behavior a user or caller depends on. A test that must change
whenever markup is restyled is a liability.

## Coverage expectation

`app/lib/` is the critical path: aim for full branch coverage there. Components get a test per
behavior they own. No coverage-percentage gate in CI — a missing test for a new behavior is
caught in review, and chasing a number produces tests that assert nothing.
