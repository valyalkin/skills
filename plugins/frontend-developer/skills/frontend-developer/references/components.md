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

Keep genuine UI-only state (a menu open/closed, an optimistic row) in React. `useOptimistic` is
the right tool for instant feedback on an action; `useTransition` for non-urgent updates.

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

- Tailwind utility classes in JSX. No CSS modules, no styled-components, no inline `style`
  except for a genuinely computed value (chart bar heights in the example).
- `app/ui/global.css` is imported once, by `app/layout.tsx`. Design tokens (brand colors,
  custom grids, keyframes) go in its `@theme` block — see `references/tooling.md`.
- Mobile first: unprefixed classes are the small screen, then `md:`/`lg:` override. The example
  renders a card list on mobile and a `<table>` on desktop for the same data; keep that
  approach rather than a horizontally scrolling table.
- Let `prettier-plugin-tailwindcss` sort classes — never hand-order them.

## Images, fonts, links

- `next/image` with explicit `width`/`height` and meaningful `alt`; `alt=""` only for
  decoration. Use the `hidden md:block` / `block md:hidden` pair for art-directed variants.
- Fonts come from `app/ui/fonts.ts` via `next/font/google`, applied as
  `className={inter.className}` — no `<link>` tags, no font CSS imports.
- Internal navigation is always `next/link`, never `<a href>` — `<a>` loses client-side
  navigation and prefetching.

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
