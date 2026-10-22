# Reference implementation

A complete, working slice of the architecture this skill describes. Every file here was run:
`pnpm typecheck`, `pnpm lint`, `pnpm format:check`, `pnpm test` (17 tests) and `pnpm build` all
pass, and the dashboard routes build as partially prerendered (`◐`).

Copy from it rather than reassembling the patterns from prose. The domain (invoices, customers)
is from the next-learn dashboard example; replace it with yours and keep the shapes.

## What each file demonstrates

| File | Pattern |
| --- | --- |
| `proxy.ts` | Auth0 mount, protected-segment gating, the `next-action` pass-through |
| `instrumentation.ts` | `onRequestError` wired to the reporting seam |
| `app/lib/env.ts` | Zod-validated server env, failing at startup |
| `app/lib/api.ts` | The one `fetch` wrapper: transport and `ApiError` only |
| `app/lib/auth0.ts`, `auth.ts` | Client singleton; `requireSession`, `authedFetch`, 401 → login |
| `app/lib/api-schema.d.ts` | **Generated** from `contracts/api.yaml` — never edited |
| `app/lib/definitions.ts` | Readable aliases over the generated schemas, plus UI-only types |
| `app/lib/data.ts` | Cached public reads (`'use cache'` + `cacheTag` + `cacheLife`) beside an uncached per-user read |
| `app/lib/actions.ts` | `'use server'`, Zod `safeParse`, `State`, `updateTag`, `redirect` outside `try` |
| `app/lib/observability.ts` | Error reports built field-by-field so headers cannot leak |
| `app/lib/report-client-error.ts` | The browser-side reporting seam |
| `app/lib/utils.ts` | Pure helpers, fully unit-tested |
| `app/dashboard/invoices/page.tsx` | Sync page passing `searchParams` into a Suspense'd section |
| `app/ui/invoices/table-section.tsx` | The async boundary that awaits request data |
| `app/dashboard/invoices/[id]/edit/page.tsx` | Same shape for `params`, plus `generateMetadata` |
| `app/dashboard/invoices/create/page.tsx` | Why a per-user read stays dynamic (see below) |
| `app/dashboard/layout.tsx` | Static shell with the session read isolated inside `<Suspense>` |
| `app/global-error.tsx` | Root-layout failures; renders its own `<html>`/`<body>` |
| `app/dashboard/invoices/error.tsx` | Segment boundary that *reports* rather than `console.error` |
| `app/ui/dashboard/user-menu.tsx` | Session read; plain `<a>` for `/auth/login` |
| `app/ui/invoices/create-form.tsx` | `useActionState`, labels, `role="status"` live regions, `useFormStatus` button |
| `app/ui/search.tsx` vs `search-form.tsx` | Debounced live search vs the `next/form` default |
| `app/ui/invoices/pagination.tsx` | URL-as-state, `clsx`, the `as Route` cast |
| `app/ui/web-vitals.tsx` | `useReportWebVitals`, mounted once in the root layout |
| `app/sitemap.ts`, `robots.ts` | `'use cache'` sitemap; authed paths disallowed |
| `app/lib/*.test.ts`, `app/ui/**/*.test.tsx` | Unit, data (MSW), action, component and redaction tests |
| `mocks/` | MSW handlers shared by every test |

## Three findings baked into this code

**A cached read with no request dependency runs at build time.** `next build` prerenders it, so
the API must be reachable from CI — or the build fails with `ECONNREFUSED`. That is why
`fetchCustomers` is an uncached `authedFetch` read behind `<Suspense>`: customers are per-user
anyway, and keeping it dynamic takes the API out of the build. Cache the reads that are
genuinely shared and public; expect those to need a reachable API at build time.

**Request data in a page body blocks prerendering.** `searchParams`, `params`, `cookies()` and
an uncached fetch all do it, which is why every page here is synchronous and hands the promise
to a small async section component.

**`onRequestError` receives the request headers**, including the session cookie and bearer
token. `app/lib/observability.ts` copies fields explicitly and never touches `headers`;
`observability.test.ts` asserts neither value can appear in a report. Keep that test if you
swap in a vendor SDK.

## Not included

`app/ui/global.css` (use `../global.css`), `package.json` (use `../package.scripts.json` and
install current versions), the root config files (`../tsconfig.json`, `../next.config.ts`,
`../eslint.config.mjs`, `../vitest.config.mts`, `../vitest.setup.ts`,
`../playwright.config.ts`, `../.prettierrc.json`, `../.lintstagedrc.json`,
`../husky/pre-commit`), and `.env.local` (see `../env.example`).
