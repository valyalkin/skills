# Data layer

The backend is a separate HTTP service. Three modules, in strict dependency order:

```
app/lib/env.ts   →   app/lib/api.ts   →   app/lib/data.ts   →   pages / server components
                                      →   app/lib/actions.ts (writes)
```

Nothing outside `api.ts` calls `fetch`. Nothing outside `data.ts`/`actions.ts` calls `apiFetch`.

## `app/lib/env.ts` — fail fast on misconfiguration

```ts
import 'server-only';
import { z } from 'zod';

const serverEnvSchema = z.object({
  // Zod 4. On Zod 3 this is z.string().url().
  API_BASE_URL: z.url(),
  AUTH0_AUDIENCE: z.string().min(1),
});

export const env = serverEnvSchema.parse({
  API_BASE_URL: process.env.API_BASE_URL,
  AUTH0_AUDIENCE: process.env.AUTH0_AUDIENCE,
});
```

Add every variable the app needs here, including the Auth0 ones the SDK reads
(`AUTH0_DOMAIN`, `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `AUTH0_SECRET`, `APP_BASE_URL`), so
a missing value fails at startup instead of at the first login.

Server-only — importing it from a client component is a bug (the module would leak secrets into
the bundle). Anything the browser legitimately needs is a separate `NEXT_PUBLIC_*` schema.
Keep `.env.example` in sync, never commit `.env.local`, and remember `vitest.setup.ts` must
define the same required variables (`testing.md`).

## `app/lib/api.ts` — the one wrapper

```ts
import 'server-only';
import { env } from '@/app/lib/env';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`API request failed with ${status}`);
    this.name = 'ApiError';
  }
}

export async function apiFetch<T>(
  path: string,
  { headers, ...init }: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${env.API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await response.json().catch(() => null));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
```

Rules:

- Generic in the response type; callers pass a type from `app/lib/definitions.ts`. Never `any`.
- The wrapper does transport only — no caching options and no auth. Caching is declared one
  level up in `app/lib/data.ts` with `'use cache'`; the user's token is attached by
  `authedFetch` in `app/lib/auth.ts` (`auth.md`). Keeping Auth0 out of this module is what
  lets public-data tests run without session mocking.
- Non-2xx throws `ApiError`. Callers decide: `notFound()` on 404, field errors on 422, rethrow
  otherwise so `error.tsx` catches it.
- Validate the payload with Zod at this boundary when the API is not under your control or its
  shape is unstable. For an API you own with a shared contract, the type assertion is enough —
  do not scatter runtime validation through `data.ts`.

## Types come from the API's OpenAPI schema

The backend owns the contract, so the frontend **generates** its types instead of hand-writing
them. Hand-written types silently drift; a generated one turns a backend rename into a compile
error — verified: renaming a field in the schema fails `pnpm typecheck` at the component that
reads it.

```sh
pnpm add -D openapi-typescript
pnpm generate:api   # openapi-typescript contracts/api.yaml -o app/lib/api-schema.d.ts
```

Point it at a committed copy of the spec (`contracts/api.yaml`) or the live
`$API_BASE_URL/openapi.json` — a committed copy is preferable, because then the contract the
build used is in git and CI needs no network.

`app/lib/api-schema.d.ts` is generated: never edit it, and commit it so CI and editors agree.
`app/lib/definitions.ts` becomes a thin alias layer giving the UI readable names:

```ts
import type { components } from '@/app/lib/api-schema';

type Schemas = components['schemas'];

export type InvoiceStatus = Schemas['InvoiceStatus'];
export type Invoice = Schemas['Invoice'];
export type InvoicesTableRow = Schemas['InvoicesTableRow'];

/** A view type the API does not define: the UI's own concern. */
export type CustomerField = { id: string; name: string };
```

Rules:

- One alias per type the UI renders. Never redeclare a shape by hand "to keep it simple" —
  that is the drift this exists to prevent.
- Response types for `apiFetch<T>` come from these aliases, or straight from
  `paths['/invoices']['get']['responses'][200]['content']['application/json']` when a response
  has no named schema.
- Types the API has no opinion about (form view-models, derived display rows) are declared by
  hand in `definitions.ts`, clearly separated from the aliases.
- Regenerate whenever the backend ships a change, and in CI: run `pnpm generate:api` then
  `git diff --exit-code app/lib/api-schema.d.ts`, so a stale checked-in schema fails the build
  rather than producing types that quietly disagree with production.
- `openapi-typescript` emits types only — no runtime, no client. Transport stays `apiFetch`.

## `app/lib/data.ts` — read functions

```ts
import 'server-only';
import { cacheLife, cacheTag } from 'next/cache';
import { notFound } from 'next/navigation';
import { apiFetch, ApiError } from '@/app/lib/api';
import { authedFetch } from '@/app/lib/auth';
import type { Invoice, InvoicesTable } from '@/app/lib/definitions';

const ITEMS_PER_PAGE = 6;

export async function fetchFilteredInvoices(query: string, currentPage: number) {
  'use cache';
  cacheTag('invoices');
  cacheLife('minutes');

  const params = new URLSearchParams({
    query,
    offset: String((currentPage - 1) * ITEMS_PER_PAGE),
    limit: String(ITEMS_PER_PAGE),
  });

  return apiFetch<InvoicesTable[]>(`/invoices?${params}`);
}

export async function fetchInvoiceById(id: string) {
  try {
    return await apiFetch<Invoice>(`/invoices/${id}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

/** Per-user read: no 'use cache', because the token and the data are per request. */
export async function fetchMyInvoiceSummary() {
  return authedFetch<{ outstanding: number; paid: number }>('/me/invoices/summary');
}
```

- One exported function per read a page needs. Name them `fetch<Thing>` as the example does.
- Shape data for the UI here (money formatting, row mapping), so components stay presentational.
- Fetch independent things in parallel: `const [cards, revenue] = await Promise.all([...])`.
  Sequential `await`s that do not depend on each other are a **request waterfall** — each one
  waits for the previous round trip for no reason. A waterfall is only correct when a later
  request genuinely needs an earlier result.
- Fetch in the component that needs the data, not in a parent that drills props.
- `env.ts`, `api.ts` and `data.ts` each start with `import 'server-only'`, so importing one from
  a client component fails the build instead of leaking the token into the bundle. Tests stub
  that module — see `testing.md`.
- If a client component needs data, pass it as a prop from a server parent, or expose a
  Server Action.
- `notFound()` and `redirect()` must not be called inside a `'use cache'` scope — keep the
  cached part to the fetch, and branch in the uncached caller as above.

## Caching (Cache Components)

New Next apps are created with `cacheComponents: true` and `partialPrefetching: true`, so
**nothing is cached unless you say so**. The legacy `fetch(url, { next: { tags, revalidate } })`
model does not apply; caching is declared with the `'use cache'` directive:

```ts
import { cacheLife, cacheTag } from 'next/cache';

export async function fetchFilteredInvoices(query: string, currentPage: number) {
  'use cache';
  cacheTag('invoices');
  cacheLife('minutes');

  const params = new URLSearchParams({ query, page: String(currentPage) });
  return apiFetch<InvoicesTable[]>(`/invoices?${params}`);
}
```

Rules:

- `'use cache'` goes at the top of the **function** (or component) body, which must be `async`.
  Arguments become part of the cache key, so each `(query, page)` pair caches separately.
- Pair every `'use cache'` with a `cacheLife` profile (`'seconds'`, `'minutes'`, `'hours'`,
  `'days'`, `'max'`, or a custom profile). Omitting it falls back to an implicit default — be
  explicit instead.
- Tag with the resource name, and the item id for single-item reads:
  `cacheTag('invoices', 'invoice:' + id)`. Tags are how a mutation invalidates precisely.
- A cached scope **cannot read `cookies()`, `headers()` or `draftMode()`**. Read per-request
  values in the caller and pass them in as arguments — that also keeps them in the cache key.
- **Per-user data is never cached.** It is fetched with `authedFetch` from `app/lib/auth.ts`,
  which reads the session cookie; put it in its own component inside `<Suspense>`. Caching it
  would serve one user's data to another. See `auth.md`.
- Everything the cached function returns and receives must be serializable.

| Situation | What to do |
| --- | --- |
| Shared, slowly changing data | `'use cache'` + `cacheTag` + `cacheLife('minutes'/'hours')` |
| Per-user data (`authedFetch`) | no directive, ever — dynamic, inside `<Suspense>` |
| Other per-request data | no directive — dynamic, inside `<Suspense>` |
| Just written by an action | `updateTag('invoices')` in the action |
| Stale-while-revalidate is fine | `revalidateTag('invoices')` (also valid from a Route Handler) |

`updateTag` is the right call after a user-triggered mutation: it purges immediately so the
next read is fresh (read-your-own-writes). `revalidateTag` allows serving stale content while
the refresh happens in the background. Both come from `next/cache`.

An uncached async read makes its subtree dynamic, which is fine — wrap it in `<Suspense>` and
the static shell still streams instantly. Streaming is the answer to "it is slow"; disabling
caching is not a performance problem to fix.

**On an older project with `cacheComponents` off**, the legacy model applies instead: pass
`next: { tags: [...], revalidate: n }` to `fetch` in `apiFetch`, use `cache: 'no-store'` for
per-request data, and `revalidateTag`/`revalidatePath` after writes. Check
`next.config.ts` before writing caching code, and do not mix the two models.

## Streaming, searchParams and prerendering

Under Cache Components a route is prerendered as a static shell with dynamic holes. Reading
request data (`searchParams`, `params`, `cookies()`, `headers()`) or awaiting an uncached fetch
**outside** a `<Suspense>` boundary blocks that, and the build fails with
"Next.js encountered uncached or runtime data during prerendering". This applies equally to a
dynamic route's `params`, and to a session read in a layout — which is why protection lives in
`proxy.ts` and the user menu sits inside its own boundary.

So the page stays synchronous and hands the promise down:

```tsx
export default function Page(props: {
  searchParams: Promise<{ query?: string; page?: string }>;
}) {
  return (
    <div className="w-full">
      <h1 className="text-2xl">Invoices</h1>
      <Suspense fallback={<SearchSkeleton />}>
        <Search placeholder="Search invoices..." />
      </Suspense>
      <Suspense fallback={<InvoicesTableSkeleton />}>
        <InvoicesTableSection searchParams={props.searchParams} />
      </Suspense>
    </div>
  );
}
```

```tsx
// app/ui/invoices/table-section.tsx — the async boundary that reads the URL
export default async function InvoicesTableSection({
  searchParams,
}: {
  searchParams: Promise<{ query?: string; page?: string }>;
}) {
  const { query = '', page } = await searchParams;
  return <InvoicesTable query={query} currentPage={Number(page) || 1} />;
}
```

- A client component calling `useSearchParams` also needs its own `<Suspense>` boundary, for the
  same reason.
- A dynamic route does the same with `params`: sync page → `<Suspense>` → async section that
  awaits it. `generateMetadata` is exempt and may await `params` directly (`metadata.md`).
- `loading.tsx` covers the whole segment; per-panel `<Suspense>` gives a faster shell and is
  preferred for pages with several independent panels.
- Skeletons mirror the real layout (same column count, same card grid) and are `aria-hidden`.
- `export const instant = false` makes a route block on its data instead. It exists for
  migrations; do not reach for it to silence the build error.
- A page that awaits all of its data before rendering anything is a mistake to fix, not a style.

## Error handling

- `ApiError` propagates to the nearest `error.tsx`, which must be a client component taking
  `{ error, reset }` and offering a retry.
- 404 → `notFound()` → the segment's `not-found.tsx`.
- 401 → the token is stale: `authedFetch` redirects to `/auth/login` (see `auth.md`). 403 is an
  authorization failure — render a "no access" state rather than bouncing to login.
- Never render a raw error message or stack to the user; log the error and show a sentence.
