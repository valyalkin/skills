# Components, state and styling

## Server or client?

Start every component as a Server Component. Add `'use client'` only when the component needs:

- `useState`, `useReducer`, `useEffect`, `useRef` on a DOM node
- event handlers (`onClick`, `onChange`, …)
- `usePathname`, `useSearchParams`, `useRouter`, `useActionState`, `useFormStatus`
- browser APIs (`window`, `localStorage`, `IntersectionObserver`)
- a third-party library that uses any of the above

Then push the directive to the smallest leaf. In the example only `search.tsx`,
`pagination.tsx`, `nav-links.tsx`, the forms, the delete button and `error.tsx` are client
components; the tables, cards and pages stay on the server.

Patterns that keep the boundary small:

- **Server parent, client leaf.** The page fetches, a client child handles interaction.
- **Children as props.** A client component can render server-rendered `children` — pass the
  server content in rather than importing it inside the client component.
- Props crossing the boundary must be serializable: no functions, class instances, `Date` with
  methods relied on, or `Map`/`Set` round-trips. Pass plain data.
- A client component can never import `app/lib/env.ts`, `app/lib/api.ts` or `app/lib/data.ts`.
  Those modules declare `import 'server-only'`, so doing it fails the build.
- A client component that calls `useSearchParams` needs a `<Suspense>` boundary around it, or
  the route cannot be prerendered — see `data-layer.md`.

## Search and filter forms: `next/form`

For anything the user **submits**, use `next/form`. It is a server component — no `'use client'`,
no hooks — it prefetches the target route, does a client-side navigation on submit, and works
before hydration:

```tsx
import Form from 'next/form';

export default function SearchForm({
  action,
  placeholder,
  defaultQuery,
}: {
  action: string;
  placeholder: string;
  defaultQuery?: string;
}) {
  return (
    <Form action={action} className="relative flex flex-1">
      <label htmlFor="query" className="sr-only">
        Search
      </label>
      <input id="query" name="query" defaultValue={defaultQuery} placeholder={placeholder} />
      <button type="submit" className="sr-only">
        Search
      </button>
    </Form>
  );
}
```

With a string `action`, the field names become the query string (`?query=acme`) — which is
exactly the URL-as-state model below, with no client JavaScript at all.

**Choose between the two:**

| Need | Use |
| --- | --- |
| Submit-based search or filters | `next/form` — fewer moving parts, progressive enhancement |
| Results that update as the user types | the debounced client component below |

Live-as-you-type search genuinely needs a client component and therefore JavaScript; that is a
deliberate trade, not the default. Reach for it when the interaction demands it, and prefer
`next/form` otherwise.

## State belongs in the URL

Anything a user would expect to survive reload, share or bookmark — search text, page number,
filters, selected tab — lives in the query string, not in React state.

```tsx
'use client';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDebouncedCallback } from 'use-debounce';

export default function Search({ placeholder }: { placeholder: string }) {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const { replace } = useRouter();

  const handleSearch = useDebouncedCallback((term: string) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', '1');
    if (term) params.set('query', term);
    else params.delete('query');
    replace(`${pathname}?${params.toString()}`);
  }, 300);

  return (
    <input
      defaultValue={searchParams.get('query')?.toString()}
      onChange={(event) => handleSearch(event.target.value)}
      placeholder={placeholder}
    />
  );
}
```

- `replace`, not `push`, for filter changes — typing should not fill the history stack.
- Debounce text input (300ms) with `use-debounce`; never debounce by hand with `setTimeout`.
- Reset `page` to 1 whenever a filter changes.
- The input is uncontrolled with `defaultValue` from the URL; the URL is the state.
- The server side reads the same params by awaiting the `searchParams` promise inside a
  Suspense'd async section (never in the page body), so the rendered result matches the URL.
- **Which API to reach for:** read params from a client component with the `useSearchParams`
  hook (no server round trip); read them on the server from the `searchParams` prop. Never
  thread them from the server into a client component that could read the hook itself.

Keep genuine UI-only state (a menu open/closed, an optimistic row) in React. `useOptimistic` is
the right tool for instant feedback on an action; `useTransition` for non-urgent updates.

**Do not hand-write `useMemo`, `useCallback` or `React.memo`.** The React Compiler is enabled
(`reactCompiler: true`), so memoization is applied automatically and correctly. Manual
memoization is now noise at best and a stale-dependency bug at worst. If profiling shows a real
problem the compiler missed, fix it deliberately and say why in a comment.

## Composition

- One component, one job. If a file mixes fetching, layout and interaction, split it:
  server fetcher → presentational table → client controls.
- Co-locate single-use subcomponents in the same file (the example's `pagination.tsx` keeps
  `PaginationNumber` and `PaginationArrow` private, and that is correct). Export only what other
  files use.
- Lift shared primitives to `app/ui/` (`button.tsx`) only once a second feature needs them.
- Props are explicit and typed inline for small components
  (`{ status }: { status: 'pending' | 'paid' }`); reach for a named type in `definitions.ts`
  when the shape is a domain object.
- Lists: a stable `key` from the data's id. Never the array index.
- Conditional classes use `clsx`, never string concatenation:

  ```tsx
  className={clsx('flex items-center rounded-full px-2 py-1 text-xs', {
    'bg-gray-100 text-gray-500': status === 'pending',
    'bg-green-500 text-white': status === 'paid',
  })}
  ```

## Styling

- Tailwind utility classes in JSX. No styled-components and no inline `style` except for a
  genuinely computed value (chart bar heights in the example). CSS Modules work in Next and are
  a reasonable choice in general — this project standardises on Tailwind so that every
  component reads the same way, so do not mix the two.
- `app/ui/global.css` is imported once, by `app/layout.tsx`. Design tokens (brand colors,
  custom grids, keyframes) go in its `@theme` block — see `references/tooling.md`.
- Mobile first: unprefixed classes are the small screen, then `md:`/`lg:` override. The example
  renders a card list on mobile and a `<table>` on desktop for the same data; keep that
  approach rather than a horizontally scrolling table.
- Let `prettier-plugin-tailwindcss` sort classes — never hand-order them.

## Images, fonts, links

- `next/image` with explicit `width`/`height` and meaningful `alt`; `alt=""` only for
  decoration. The dimensions are what prevent layout shift, so never omit them (or use `fill`
  with a sized parent). Add `sizes` for responsive images and `priority` for the one
  above-the-fold hero. Images served from the API's domain need an `images.remotePatterns`
  entry in `next.config.ts`.
- Use the `hidden md:block` / `block md:hidden` pair for art-directed desktop/mobile variants.
- Fonts come from `app/ui/fonts.ts` via `next/font/google`, applied as
  `className={inter.className}` — no `<link>` tags, no font CSS imports. `next/font` downloads
  and self-hosts at build time, which removes a network round trip and the font swap that
  causes **cumulative layout shift**. Two exports is the usual shape: a body font on `<body>`
  in the root layout, and a display font used per heading
  (`className={`${lusitana.className} text-2xl`}`).
- Internal navigation is always `next/link`, never `<a href>`. Next code-splits per route
  segment and `<Link>` prefetches the segments in view, so navigation stays instant and a
  broken page cannot take down the rest of the app; a plain `<a>` throws that away with a full
  reload. The exception is `/auth/login` and `/auth/logout` (see `auth.md`).

## Accessibility baseline

Non-negotiable for every component:

- Every input has a visible `<label>` or an `sr-only` one; icon-only buttons have an `sr-only`
  text label.
- Semantic elements: `<table>` for tabular data, `<button>` for actions, `<nav>`/`<main>`
  landmarks, one `<h1>` per page and a sensible heading order.
- Focus is visible (keep Tailwind's focus ring; `outline-2` in the example's inputs) and every
  interactive element is keyboard reachable in a logical order.
- Validation and async results are announced via `aria-live` regions.
- Loading states are skeletons that mirror layout, so content does not jump.
