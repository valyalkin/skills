# Tooling and bootstrap

Package manager: **pnpm**. Node 20+.

Copy the files in `../assets/` into the project root as-is, then install the dependencies
listed in `../assets/package.scripts.json`. Pin nothing by hand — install with `pnpm add` so the
lockfile records real resolved versions, and check the installed major against this guide if
something behaves differently.

## New project

```sh
pnpm create next-app@latest <name> --ts --app --tailwind --eslint --use-pnpm
```

Then:

1. Delete the generated `src/` layout if the wizard produced one — this project uses `app/` at
   the root (`references/structure.md`).
2. Copy the assets: `tsconfig.json`, `next.config.ts`, `eslint.config.mjs`, `.prettierrc.json`,
   `.prettierignore`, `vitest.config.mts`, `vitest.setup.ts`, `playwright.config.ts`, and
   `global.css` → `app/ui/global.css` (delete the generated `app/globals.css` and update the
   import in `app/layout.tsx`).
3. Merge the `scripts` block from `package.scripts.json` into `package.json`, then install the
   `dependencies` and `devDependencies` it lists. `create-next-app` pins `@types/node@^20`,
   which Vitest 5 rejects — install `@types/node@^24`.
4. Create `app/lib/{env,api,data,actions,definitions,utils}.ts`, `app/ui/fonts.ts`,
   `mocks/{handlers,server}.ts`, `e2e/`, and `.env.example` with `API_BASE_URL`.
5. `pnpm exec playwright install --with-deps chromium`.
6. Verify: `pnpm typecheck && pnpm lint && pnpm test && pnpm build`.

## Scripts

Every project has exactly these, and they must all pass:

| Script | Command |
| --- | --- |
| `dev` | `next dev --turbopack` |
| `build` | `next build` |
| `start` | `next start` |
| `typecheck` | `tsc --noEmit` |
| `lint` | `eslint .` (Next 16+; `next lint` was removed) |
| `lint:fix` | same, with `--fix` |
| `format` | `prettier --write .` |
| `format:check` | `prettier --check .` |
| `test` | `vitest run` |
| `test:watch` | `vitest` |
| `test:e2e` | `playwright test` |

`next lint` was removed in Next 16, and `NextConfig` no longer accepts an `eslint` key — lint
is its own step, not part of `next build`.

## TypeScript

`assets/tsconfig.json` is the example's config with its teaching-only `include` entries
removed: `strict: true`, `noEmit`, `moduleResolution: 'bundler'`, the `next` plugin, and
`"@/*": ["./*"]`. Additions on top of the example, because they catch real bugs:
`noUncheckedIndexedAccess`, `noUnusedLocals`, `noUnusedParameters`.

Never relax `strict`, never add `any`, never `@ts-ignore`. `unknown` plus a narrowing check is
the correct escape hatch, and `satisfies` is better than a cast for literal objects.

## ESLint

`assets/eslint.config.mjs` is a flat config importing `eslint-config-next/core-web-vitals` and
`eslint-config-next/typescript` directly (Next 16+; on Next 15 go through `FlatCompat` from
`@eslint/eslintrc`), plus:

- `eslint-plugin-testing-library` on `**/*.test.tsx` — catches the RTL misuse that makes tests
  flaky.
- `@typescript-eslint/no-floating-promises` and `no-misused-promises` as errors (requires the
  type-aware config) — an unawaited `apiFetch` is a silent data bug.
- `no-restricted-imports` banning deep relative paths (`../../*`) in favor of `@/`. The
  server/client boundary is enforced by `import 'server-only'` in those modules instead — a
  lint glob cannot tell a server component in `app/ui/` from a client one.

Fix lint errors; do not add disable comments. A genuine exception gets a one-line comment saying
why, on that line only.

## Prettier

`assets/.prettierrc.json`: single quotes, trailing commas, 80 columns — matching the example's
formatting — plus `prettier-plugin-tailwindcss` so class order is mechanical. Prettier owns
formatting; ESLint owns correctness. Never argue with either in review.

## Tailwind v4

v4 is CSS-first: no `tailwind.config.ts`, no `postcss.config.js` with autoprefixer. With
Turbopack (the default), the CSS loader is wired in `next.config.ts` via `@tailwindcss/turbopack`
— `assets/next.config.ts` already has it. The entry file is `app/ui/global.css`
(`assets/global.css`), imported only by `app/layout.tsx`:

```css
@import 'tailwindcss';

@theme {
  --color-blue-400: #2589fe;
  --color-blue-500: #0070f3;
  --color-blue-600: #2f6feb;
  --grid-template-columns-13: repeat(13, minmax(0, 1fr));
  --animate-shimmer: shimmer 2s infinite;
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

@plugin '@tailwindcss/forms';
```

That `@theme` block is the v4 translation of the example's `tailwind.config.ts` extensions.
A non-Turbopack build instead needs `postcss.config.mjs` with the `@tailwindcss/postcss` plugin.

Design tokens go here, not into component class strings. Do not reintroduce a JS Tailwind
config; if a migration guide suggests one, the project is on v4 and uses `@theme`.

## Environment variables

`.env.local` (gitignored) for real values, `.env.example` committed with the keys and dummy
values. Server-only keys have no `NEXT_PUBLIC_` prefix and are read exclusively through
`app/lib/env.ts`, which validates them at startup. Anything prefixed `NEXT_PUBLIC_` is public —
treat it as published.

## CI

One workflow, one job, in this order so the cheapest check fails first:

```yaml
- run: pnpm install --frozen-lockfile
- run: pnpm typecheck
- run: pnpm lint
- run: pnpm format:check
- run: pnpm test
- run: pnpm build          # env: API_BASE_URL (app/lib/env.ts validates it at build time)
- run: pnpm exec playwright install --with-deps chromium
- run: pnpm test:e2e
```

Run it on pull requests and on `main`. A red CI is a blocking defect, not a flake to retry.

## Verified toolchain

The asset configs and every pattern in these references were smoke-tested end to end
(`typecheck`, `lint`, `format:check`, `test`, `build` all green) on 2026-10-19 against:

| Package | Version |
| --- | --- |
| next | 16.4.2 (Turbopack, Cache Components, Partial Prefetching) |
| react / react-dom | 19.3.0 |
| typescript | 5.9.3 |
| tailwindcss | 4.3.3 |
| zod | 4.6.7 |
| vitest | 5.0.5 |
| msw | 3.0.3 |
| @playwright/test | 1.64.1 |
| eslint / eslint-config-next | 9.39 / 16.4.2 |

If an installed major differs, trust the tool's own error message over this guide, fix the
pattern, and update these references — they are meant to stay current, not to be preserved.
