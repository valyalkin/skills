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
  API_TOKEN: z.string().min(1).optional(),
});

export const env = serverEnvSchema.parse({
  API_BASE_URL: process.env.API_BASE_URL,
  API_TOKEN: process.env.API_TOKEN,
});
```

Server-only — importing it from a client component is a bug (the module would leak the token
into the bundle). Anything the browser legitimately needs is a separate `NEXT_PUBLIC_*` schema.
Keep `.env.example` in sync and never commit `.env.local`.

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
      ...(env.API_TOKEN ? { Authorization: `Bearer ${env.API_TOKEN}` } : {}),
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
- The wrapper does transport only — no caching options. Caching is declared one level up, in
  `app/lib/data.ts`, with `'use cache'` (see below).
- Non-2xx throws `ApiError`. Callers decide: `notFound()` on 404, field errors on 422, rethrow
  otherwise so `error.tsx` catches it.
- Validate the payload with Zod at this boundary when the API is not under your control or its
  shape is unstable. For an API you own with a shared contract, the type assertion is enough —
  do not scatter runtime validation through `data.ts`.

## `app/lib/data.ts` — read functions

```ts
import 'server-only';
import { cacheLife, cacheTag } from 'next/cache';
import { notFound } from 'next/navigation';
import { apiFetch, ApiError } from '@/app/lib/api';
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
```

- One exported function per read a page needs. Name them `fetch<Thing>` as the example does.
- Shape data for the UI here (money formatting, row mapping), so components stay presentational.
- Fetch independent things in parallel: `const [cards, revenue] = await Promise.all([...])`.
  Sequential `await`s that do not depend on each other are a performance bug.
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
  Anything genuinely per-user is usually better left uncached.
- Everything the cached function returns and receives must be serializable.

| Situation | What to do |
| --- | --- |
| Shared, slowly changing data | `'use cache'` + `cacheTag` + `cacheLife('minutes'/'hours')` |
| Per-user or per-request data | no directive — leave it dynamic, inside `<Suspense>` |
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
"Next.js encountered uncached or runtime data during prerendering".

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
- Never render a raw error message or stack to the user; log the error and show a sentence.
