# Metadata, icons and SEO

Next's Metadata API renders `<head>` for you. There are two halves: the `metadata` export (or
`generateMetadata`) in a layout or page, and file conventions in `app/`.

## Root layout: template and base

```tsx
// app/layout.tsx
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: {
    template: '%s | Acme Dashboard',
    default: 'Acme Dashboard',
  },
  description: 'Invoicing and customers for Acme.',
  metadataBase: new URL(process.env.APP_BASE_URL ?? 'http://localhost:3000'),
};
```

- `title.template` means every page exports only its own name (`title: 'Invoices'`) and gets
  ` | Acme Dashboard` appended. `default` covers routes that export no title.
- `metadataBase` is what makes relative Open Graph and canonical URLs resolve. Without it, OG
  images silently come out as relative paths that crawlers cannot fetch.
- Set `description` once here; override per page only when the page is genuinely different.
- Do not add `keywords` — search engines ignore it.

## Per page

```tsx
// app/dashboard/invoices/page.tsx
export const metadata: Metadata = { title: 'Invoices' };
```

Static metadata is a plain export. Dynamic routes compute it:

```tsx
export async function generateMetadata(props: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await props.params;
  const invoice = await fetchInvoiceById(id);

  return {
    title: `Edit invoice ${invoice.id}`,
    robots: { index: false },
  };
}
```

- `generateMetadata` may await `params` — the prerendering restriction that applies to the page
  body (see `data-layer.md`) does not apply here.
- Call the same `app/lib/data.ts` function the page uses; the request is deduped, and a
  `'use cache'` function is simply served from cache.
- Never both `metadata` and `generateMetadata` in one file — Next rejects it.
- Keep titles under ~60 characters and make them distinct per route; two pages sharing a title
  is an SEO bug and a confusing browser-tab list.

## Authenticated areas

Anything behind the proxy gate should tell crawlers not to index it, belt and braces with
`app/robots.ts`:

```tsx
export const metadata: Metadata = { robots: { index: false, follow: false } };
```

Put it on the `app/dashboard/layout.tsx` so it applies to the whole subtree.

## File conventions

| File | Produces |
| --- | --- |
| `app/favicon.ico` | the classic favicon |
| `app/icon.png` / `app/icon.tsx` | `<link rel="icon">`, generated or static |
| `app/apple-icon.png` | the iOS home-screen icon |
| `app/opengraph-image.png` / `.tsx` | `og:image` for the whole app |
| `app/<segment>/opengraph-image.tsx` | a segment-specific OG image |
| `app/sitemap.ts` | `/sitemap.xml` |
| `app/robots.ts` | `/robots.txt` |
| `app/manifest.ts` | the web manifest |

Next adds the `og:image` meta tags, dimensions and type automatically from the file — no
hand-written `<meta property="og:image">`.

A generated OG image is a route that returns an `ImageResponse`:

```tsx
// app/opengraph-image.tsx
import { ImageResponse } from 'next/og';

export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const alt = 'Acme Dashboard';

export default async function Image() {
  return new ImageResponse(
    (
      <div
        style={{
          display: 'flex',
          height: '100%',
          width: '100%',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#0070f3',
          color: 'white',
          fontSize: 72,
        }}
      >
        Acme Dashboard
      </div>
    ),
    size,
  );
}
```

`ImageResponse` supports only flexbox and a subset of CSS — no grid, no Tailwind classes. Keep
it to 1200×630.

## sitemap and robots

```ts
// app/sitemap.ts
import type { MetadataRoute } from 'next';

const baseUrl = process.env.APP_BASE_URL ?? 'http://localhost:3000';

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  'use cache';

  return [
    { url: baseUrl, lastModified: new Date(), changeFrequency: 'monthly', priority: 1 },
  ];
}
```

```ts
// app/robots.ts
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: '*', allow: '/', disallow: ['/dashboard/', '/auth/'] }],
    sitemap: `${baseUrl}/sitemap.xml`,
  };
}
```

A sitemap that reads env or data is dynamic (`ƒ` in the build output) unless you mark it
`'use cache'`, which makes it a prerendered `○` route with a cache profile. List only public,
indexable routes — never the authenticated dashboard.

## Checklist for a new route

- [ ] `title` exported (static) or returned from `generateMetadata` (dynamic).
- [ ] `robots: { index: false }` if it sits behind auth.
- [ ] A segment `opengraph-image` if the route is shared publicly and deserves its own card.
- [ ] Added to `app/sitemap.ts` if it is public.
