# Authentication: Auth0, with server-side token custody

Auth here follows the **BFF (token-handler) pattern**, and that is the only sense in which this
app is a BFF: it owns the user session, and tokens live in an encrypted, HttpOnly cookie that
the browser's JavaScript cannot read. Every API call carrying that token happens on the server.

```
browser ──session cookie──▶ Next server ──Bearer access token──▶ backend API
```

What this is *not*: a BFF layer that proxies or aggregates the API for the browser. Server
Components call the API directly and `app/lib/data.ts` shapes the result for the UI; there is
no `app/api/*` tier in between (see `structure.md`). The pattern is applied to tokens because
the alternative — a token in `localStorage` or readable JS memory — is exfiltrable by any XSS.
If a future requirement genuinely needs a browser-facing endpoint (a client component that must
fetch, or one call that fans out to several API calls), that is a thin route handler, added
deliberately, not the default shape of the app.

The SDK is `@auth0/nextjs-auth0` v4 (v5 of the API surface; `Auth0Client` + proxy mount). It
handles Universal Login, the callback, rolling sessions, refresh and logout. Do not hand-roll
any of that, and never put a client secret or token in client code.

## Files

| File | Role |
| --- | --- |
| `app/lib/auth0.ts` | The `Auth0Client` singleton, with `audience`/`scope` for the API |
| `proxy.ts` (repo root) | Mounts the SDK, serves `/auth/*`, gates protected segments |
| `app/lib/auth.ts` | `requireSession()` and `authedFetch()` used by the rest of the app |

`middleware.ts` is the Next ≤15 name for `proxy.ts`; on Next 16 use `proxy.ts` (the old name
still works under the Edge runtime but is deprecated for Node).

## The client

```ts
// app/lib/auth0.ts
import 'server-only';
import { Auth0Client } from '@auth0/nextjs-auth0/server';
import { env } from '@/app/lib/env';

export const auth0 = new Auth0Client({
  authorizationParameters: {
    scope: 'openid profile email offline_access',
    audience: env.AUTH0_AUDIENCE,
  },
});
```

- `audience` is what makes Auth0 issue a JWT **for your API** rather than an opaque userinfo
  token. Without it the backend cannot validate the token.
- `offline_access` buys a refresh token, so sessions survive access-token expiry.
- Config comes from env (`AUTH0_DOMAIN`, `AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`,
  `AUTH0_SECRET`, `APP_BASE_URL`, plus your `AUTH0_AUDIENCE`) and is validated in
  `app/lib/env.ts` like everything else.

## The proxy

```ts
// proxy.ts
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { auth0 } from '@/app/lib/auth0';

const PROTECTED_PREFIXES = ['/dashboard'];

export async function proxy(request: NextRequest) {
  const authResponse = await auth0.middleware(request);
  const { pathname, search } = request.nextUrl;

  if (pathname.startsWith('/auth/')) {
    return authResponse;
  }

  if (!PROTECTED_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return authResponse;
  }

  const session = await auth0.getSession(request);

  if (!session) {
    // A Server Action's POST follows 3xx at the fetch layer, so a redirect here
    // is swallowed and the action silently does nothing. Let it through and let
    // the action itself reject.
    if (request.headers.has('next-action')) {
      return authResponse;
    }

    const loginUrl = new URL('/auth/login', request.nextUrl.origin);
    loginUrl.searchParams.set('returnTo', `${pathname}${search}`);
    return NextResponse.redirect(loginUrl);
  }

  return authResponse;
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt).*)',
  ],
};
```

Why gate here rather than in a layout: the proxy runs **before** rendering, so protected pages
stay prerenderable. Reading the session in `app/dashboard/layout.tsx` instead would put
`cookies()` in the static shell and make every dashboard route dynamic (see `data-layer.md`).

- Always return `authResponse` on the pass-through paths — it carries the rolled session cookie.
  Returning a bare `NextResponse.next()` silently logs users out over time.
- Keep the broad matcher. It is what keeps rolling sessions and the SDK's security checks alive.
- The proxy is a convenience gate, not the security boundary. The backend must validate the JWT
  on every request; a proxy check alone is bypassable in ways the API cannot see.
- `auth0.withPageAuthRequired(Component, { returnTo })` is the SDK's co-located alternative for
  one-off protected pages. It makes that page dynamic, so prefer the proxy for whole segments.

## Session and API calls

```ts
// app/lib/auth.ts
import 'server-only';
import { redirect } from 'next/navigation';
import { apiFetch, ApiError } from '@/app/lib/api';
import { auth0 } from '@/app/lib/auth0';

export async function requireSession() {
  const session = await auth0.getSession();

  if (!session) {
    redirect('/auth/login');
  }

  return session;
}

/**
 * apiFetch with the caller's access token attached. Reads the session cookie,
 * so it must never be called from a 'use cache' scope.
 */
export async function authedFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const { token } = await auth0.getAccessToken();

  try {
    return await apiFetch<T>(path, {
      ...init,
      headers: { ...init.headers, Authorization: `Bearer ${token}` },
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      redirect('/auth/login');
    }
    throw error;
  }
}
```

Rules:

- `app/lib/api.ts` stays pure transport and imports nothing from Auth0. Auth lives in
  `app/lib/auth.ts`. That keeps public-data tests free of session mocking.
- **Per-user reads are never cached.** `authedFetch` reads cookies, which is forbidden inside
  `'use cache'`. Put per-user data in its own component inside `<Suspense>`; cache only
  data that is identical for every user.
- `getAccessToken()` in a Server Component cannot persist a refreshed token (components cannot
  set cookies). The proxy's broad matcher refreshes it on the preceding request, which is why
  the matcher matters. If a Server Component still sees an expired token, refresh in the proxy
  rather than working around it in the component.
- 401 from the API means the token is no longer good: redirect to login. 403 is an
  authorization failure — show a "you do not have access" state, do not bounce to login.
- Using DPoP or wanting the SDK to manage attachment for you? `auth0.createFetcher(request, {
  baseUrl })` returns a `fetchWithAuth` that does the same job; wrap it in `authedFetch` rather
  than calling it from components.

## Login, logout and user display

The SDK serves these routes from the proxy — do not write page or route files for them:
`/auth/login`, `/auth/logout`, `/auth/callback`, `/auth/profile`, `/auth/access-token`.

```tsx
// app/ui/dashboard/user-menu.tsx - a server component, rendered inside <Suspense>
import { auth0 } from '@/app/lib/auth0';

export default async function UserMenu() {
  const session = await auth0.getSession();

  if (!session) {
    return <a href="/auth/login">Log in</a>;
  }

  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="font-medium">{session.user.name}</span>
      <a href="/auth/logout">Log out</a>
    </div>
  );
}
```

- Use a plain `<a>` (or `<Link prefetch={false}>`) for `/auth/login`. A `prefetch={true}` Link
  starts a real login transaction during prefetch and leaks transaction cookies — the SDK
  cannot tell that request apart from a navigation.
- There is no login *form* to build: Universal Login is hosted by Auth0. Brand it there.
- `session.user` carries the ID-token claims (`sub`, `name`, `email`, `picture`). It is for
  display. Authorization decisions belong to the API, which validates the access token.
- With `typedRoutes` enabled, the `/auth/*` routes are virtual and not in the generated route
  union: `redirect('/auth/login' as Route)` with `import type { Route } from 'next'`.

## Server Actions

The proxy lets action requests through (above), so each action re-checks for itself:

```ts
'use server';

export async function createInvoice(_prevState: State, formData: FormData): Promise<State> {
  const session = await auth0.getSession();

  if (!session) {
    return { message: 'Your session expired. Reload the page and sign in again.' };
  }
  // ... validate, then call the API through authedFetch
}
```

Returning a message beats redirecting: the user keeps their typed input. Never trust a user id
from `formData` — take it from the session, or let the API derive it from the token.

## Testing

Mock the Auth0 client, not the SDK internals:

```ts
const { getAccessToken } = vi.hoisted(() => ({
  getAccessToken: vi.fn(async () => ({ token: 'test-token' })),
}));
vi.mock('@/app/lib/auth0', () => ({ auth0: { getAccessToken } }));
```

Assert that `authedFetch` sends `Authorization: Bearer …` (MSW can read the request headers)
and that a 401 redirects. `app/lib/env.ts` validates the Auth0 variables at import time, so
`vitest.setup.ts` must define them — see `testing.md`. For Playwright, either stub
`**/auth/**` or run against a dedicated Auth0 test tenant with a seeded user; do not point E2E
at a real tenant with real credentials.

## What stays in the backend

Password storage, user records, roles and permissions, token validation, and any
`client_credentials` machine-to-machine flow. This app proves who the user is and forwards that
proof. If a task needs a database or a password hash, it belongs in the API.
