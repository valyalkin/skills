---
name: frontend-developer
description: This skill should be used for any frontend work in a Next.js App Router project — when the user asks to "build a Next.js app", "scaffold a frontend", "add a page", "add a route", "add a component", "fetch data in a server component", "add a form", "write a server action", "add a dashboard", or to set up TypeScript, Tailwind, tests or linting for a Next.js frontend. It fixes the project structure, the data-fetching and mutation patterns, and the mandatory TypeScript/test/lint gates.
version: 0.1.0
---

# Frontend Developer (Next.js App Router)

Build and extend Next.js frontends structured like the
[next-learn dashboard example](https://github.com/vercel/next-learn/tree/main/dashboard/final-example):
`app/` holds routes, `app/ui/` holds components, `app/lib/` holds data access and types.

**The backend is a separate service.** This project renders UI and talks to that service over
HTTP. Never add a database client, an ORM, SQL, a seed route, or password hashing here.

## Non-negotiables

- **TypeScript only.** `strict: true`, no `any`, no `@ts-ignore`, no new `.js`/`.jsx` files.
  Types for API payloads live in `app/lib/definitions.ts`.
- **Tests ship with the code.** Every new component, util and data function gets a test; every
  new user-visible flow gets a Playwright spec. A change with no test is not done.
- **Lint and format are gates, not suggestions.** ESLint flat config + Prettier.
- **Every change leaves this green:**
  ```sh
  pnpm typecheck && pnpm lint && pnpm test
  ```
  Run it before reporting the work finished. If a command does not exist yet, set it up
  (`references/tooling.md`).
- **Server-first.** Data is fetched on the server through `app/lib/data.ts`; `'use client'` is
  an exception that must be justified.
- **Caching is explicit.** Cacheable reads declare `'use cache'` with `cacheTag` + `cacheLife`;
  everything else is dynamic and lives inside `<Suspense>`.

## Where code goes

| Path | Holds | Rules |
| --- | --- | --- |
| `app/<segment>/page.tsx` | One route | Server Component, default export `Page`, exports `metadata`; does not await `searchParams` itself |
| `app/<segment>/layout.tsx` | Shared chrome for a subtree | Takes `{ children }` |
| `app/<segment>/loading.tsx` | Route-level fallback | Reuse a skeleton from `app/ui/skeletons.tsx` |
| `app/<segment>/error.tsx` | Error boundary | `'use client'`, takes `{ error, reset }` |
| `app/<segment>/not-found.tsx` | 404 for that segment | Paired with `notFound()` in the page |
| `app/ui/<feature>/<component>.tsx` | Feature components | kebab-case file, default-exported component |
| `app/ui/<shared>.tsx` | Cross-feature UI | e.g. `button.tsx`, `search.tsx`, `skeletons.tsx`, `fonts.ts` |
| `app/lib/definitions.ts` | Domain types | Hand-written; the single source of payload shapes |
| `app/lib/api.ts` | The one `fetch` wrapper | Base URL, headers, `ApiError`; starts with `import 'server-only'` |
| `app/lib/data.ts` | Read functions | The only place pages read data from |
| `app/lib/actions.ts` | Server Actions (writes) | `'use server'`, Zod validation |
| `app/lib/utils.ts` | Pure helpers | Formatting, pagination maths — trivially unit-testable |
| `e2e/*.spec.ts` | Playwright specs | One per user flow |

A new feature means a route segment **plus** an `app/ui/<feature>/` directory. Do not invent a
top-level `components/`, `hooks/`, `services/` or `src/` directory — this layout is the convention.

## Workflow: add a page

1. Create the segment: `app/<feature>/page.tsx`. Export `metadata` with a `title`.
2. Add the read function to `app/lib/data.ts`, returning a type from `app/lib/definitions.ts`.
   It calls `apiFetch<T>()` — never `fetch` directly, never from the component body. Cacheable
   reads declare `'use cache'` with `cacheTag` + `cacheLife`; per-request reads stay dynamic.
3. Keep the page **synchronous** and never await `searchParams` or `params` in its body — under
   Cache Components that blocks prerendering of the whole route. Pass the promise down instead:
   `<Suspense fallback={<XSkeleton />}><Section searchParams={props.searchParams} /></Suspense>`,
   and `await` it inside that small async section component.
4. Wrap every dynamic child in `<Suspense>` with a skeleton, including client components that
   call `useSearchParams`. The static shell then streams instantly.
5. Put the data-dependent part in a child component under `app/ui/<feature>/`, and the skeleton
   in `app/ui/skeletons.tsx` mirroring its real layout.
6. Add `error.tsx` for the segment, and `not-found.tsx` if a lookup can miss.
7. Add the nav entry to `app/ui/dashboard/nav-links.tsx` if it belongs in the sidebar.
8. Tests: unit-test the data function with a mocked API, RTL-test any client component,
   Playwright-test the flow.

## Workflow: add a mutation

1. Define the Zod schema and the action in `app/lib/actions.ts` (`'use server'` at the top).
2. The action `safeParse`s `FormData`, returns `{ errors, message }` on failure
   (the `State` type), calls the API on success, then `updateTag(...)` and `redirect(...)`.
3. The form is a small `'use client'` component in `app/ui/<feature>/` using
   `useActionState(action, initialState)` and a `useFormStatus` submit button.
4. Wire accessibility: every input has a `<label htmlFor>`, `aria-describedby="<field>-error"`,
   and an `aria-live="polite" aria-atomic="true"` error container.
5. The form must work without JavaScript — no `onSubmit` handler, use `<form action={formAction}>`.
6. Tests: unit-test the action's validation branches, RTL-test that field errors render and are
   announced, Playwright-test the happy path.

See `references/forms-and-actions.md` for the full pattern.

## Server/client boundary

Default to Server Components. Add `'use client'` only for: React state or effects, event
handlers, browser APIs, or hooks like `usePathname`/`useSearchParams`/`useActionState`.

- Push the directive to the **smallest leaf**. A page should not become a client component
  because one button needs `onClick`.
- Never pass secrets, functions or class instances across the boundary — props must serialize.
- Keep state in the URL (`?query=`, `?page=`) rather than in React state when it should survive
  reload and be shareable. See `references/components.md`.
- Only `NEXT_PUBLIC_*` env vars exist in client code. API base URLs and tokens are server-only.

## Definition of done

- [ ] `pnpm typecheck && pnpm lint && pnpm test` green; `pnpm build` green for structural changes.
- [ ] New data access goes through `app/lib/data.ts` → `app/lib/api.ts`; no stray `fetch`.
- [ ] Loading and error states exist for anything that awaits data.
- [ ] Labels, focus states and keyboard paths work; errors are announced.
- [ ] No backend concerns (DB, ORM, SQL, secrets) added to this repo.

## References

| File | Read it when |
| --- | --- |
| `references/structure.md` | Laying out a new project or unsure where a file belongs |
| `references/data-layer.md` | Writing `api.ts`/`data.ts`, choosing caching, handling API errors |
| `references/forms-and-actions.md` | Any write path, form validation, or action error mapping |
| `references/components.md` | Building components, URL state, styling, accessibility |
| `references/testing.md` | Writing tests or setting up Vitest/RTL/MSW/Playwright |
| `references/tooling.md` | Bootstrapping configs, scripts, Tailwind v4, ESLint, CI |

Config templates to copy verbatim live in `assets/` — see `references/tooling.md`.
