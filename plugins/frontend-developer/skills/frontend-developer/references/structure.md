# Project structure

The layout mirrors `vercel/next-learn/dashboard/final-example`, minus everything the backend
owns. Keep it; it is the convention this project is reviewed against.

```
app/
  layout.tsx                 # root layout: html/body, font class, metadata template
  page.tsx                   # landing page
  dashboard/
    layout.tsx               # sidebar + content shell
    (overview)/              # route group: scopes loading.tsx to the overview page only
      page.tsx
      loading.tsx
    <feature>/
      page.tsx               # list page: sync shell, Suspense'd sections
      error.tsx              # 'use client' error boundary for the segment
      create/page.tsx        # create form page
      [id]/edit/page.tsx     # edit form page
      [id]/edit/not-found.tsx
  ui/
    fonts.ts                 # next/font definitions, imported by name elsewhere
    global.css               # Tailwind entry, imported only by app/layout.tsx
    button.tsx               # shared primitives live flat in app/ui/
    search.tsx
    skeletons.tsx
    <feature>/
      table-section.tsx      # async boundary: awaits searchParams, renders the table
      table.tsx              # async server component that fetches via app/lib/data.ts
      create-form.tsx        # 'use client' form
      edit-form.tsx
      buttons.tsx            # small link/action buttons for the feature
      breadcrumbs.tsx
      pagination.tsx
      status.tsx
  lib/
    env.ts                   # validated server env ('server-only')
    api.ts                   # the single fetch wrapper ('server-only')
    data.ts                  # read functions, 'use cache' where cacheable
    actions.ts               # 'use server' write actions
    definitions.ts           # domain types
    utils.ts                 # pure helpers
e2e/
  <flow>.spec.ts
public/                      # static images only
mocks/
  handlers.ts                # MSW request handlers shared by tests
  server.ts                  # MSW node server for Vitest
```

Deliberately absent, because the backend owns it: `app/seed/`, `app/query/`, `auth.ts`,
`auth.config.ts`, `app/lib/placeholder-data.ts`, any `postgres`/ORM dependency, password
hashing. If a task seems to need one of these, the task belongs in the backend repo — say so
rather than adding it here.

Route handlers under `app/api/` are allowed **only** as a thin BFF edge: forwarding a webhook,
or proxying a call that must hide a token. They never contain business logic.

## Naming

- Files: kebab-case (`create-form.tsx`, `nav-links.tsx`). Directories: kebab-case.
- Components: default export, PascalCase identifier. The file name describes the thing
  (`table.tsx` under `app/ui/invoices/` is `InvoicesTable`'s home).
- Route files use Next's reserved names exactly: `page`, `layout`, `loading`, `error`,
  `not-found`, `template`, `route`.
- Dynamic segments: `[id]`. Route groups (no URL effect): `(overview)`.
- Imports always use the `@/` alias: `import Search from '@/app/ui/search'`. No `../../..`.
- Types: PascalCase, in `definitions.ts`. Prefer string unions (`'pending' | 'paid'`) over enums.

## Routing files, and what each is for

| File | Purpose |
| --- | --- |
| `layout.tsx` | Persistent chrome; does not re-render on navigation within its subtree |
| `page.tsx` | The route's content; async, fetches its own data |
| `loading.tsx` | Instant fallback while the segment's page resolves |
| `error.tsx` | Client component catching render/fetch errors in the segment |
| `not-found.tsx` | Rendered by `notFound()` from a page in that segment |

Put `loading.tsx` inside a route group when a sibling route should not inherit it — that is the
only reason `(overview)/` exists in the example.

## Worked example: adding a "customers" feature

1. `app/dashboard/customers/page.tsx` — `export const metadata = { title: 'Customers' }`; a
   **synchronous** `Page` that renders `<Suspense fallback={<SearchSkeleton />}><Search … /></Suspense>`
   and `<Suspense fallback={<CustomersTableSkeleton />}><CustomersTableSection searchParams={props.searchParams} /></Suspense>`.
   It must not await `searchParams` itself (see `data-layer.md`).
2. `app/dashboard/customers/error.tsx` — client error boundary with a `reset()` button.
3. `app/ui/customers/table-section.tsx` — small async component that awaits `searchParams` and
   renders the table with plain props.
4. `app/ui/customers/table.tsx` — async Server Component: `const customers = await fetchFilteredCustomers(query)`,
   renders a desktop `<table>` and a mobile card list (the example does both; keep that).
5. `app/lib/definitions.ts` — add `Customer` and the formatted row type.
6. `app/lib/data.ts` — `fetchFilteredCustomers(query)` declaring `'use cache'`,
   `cacheTag('customers')` and `cacheLife('minutes')`, then calling
   `apiFetch<Customer[]>('/customers?query=...')` and mapping money with `formatCurrency` from
   `app/lib/utils.ts`.
7. `app/ui/skeletons.tsx` — `CustomersTableSkeleton` with the same column count as the table.
8. `app/ui/dashboard/nav-links.tsx` — add `{ name: 'Customers', href: '/dashboard/customers', icon: UserGroupIcon }`.
9. Tests: `app/lib/data.test.ts` (MSW-mocked), `app/ui/customers/table.test.tsx` (RTL),
   `e2e/customers.spec.ts` (search narrows the list).
