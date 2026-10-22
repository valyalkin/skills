# Observability

Three seams, all vendor-neutral. Each is one function body away from Sentry, Datadog or an
OpenTelemetry exporter, so swapping provider never touches a caller.

| File | Catches |
| --- | --- |
| `instrumentation.ts` | Every error the Next **server** captures — RSC renders, data fetches, Server Actions, route handlers |
| `app/lib/observability.ts` | Where those reports are built and shipped |
| `app/lib/report-client-error.ts` | Errors that happen while rendering in the **browser**, which the server never sees |
| `app/global-error.tsx` | Errors thrown by the root layout itself, which `error.tsx` cannot catch |
| `app/ui/web-vitals.tsx` | LCP, CLS, INP and friends from real users |

## Server errors

```ts
// instrumentation.ts (repo root)
import type { Instrumentation } from 'next';
import { reportError, toErrorReport } from '@/app/lib/observability';

export const onRequestError: Instrumentation.onRequestError = (
  error,
  request,
  context,
) => {
  reportError(toErrorReport(error, request, context));
};
```

This fires for errors anywhere on the server, including ones `error.tsx` already showed the user
— the boundary renders the fallback, `onRequestError` records what happened. You do not need to
log inside every `catch`.

**The header trap.** Next hands `onRequestError` the full `request.headers`, which includes the
`__session` cookie and any `Authorization` header. Spreading that object into a log line or a
POST body ships the user's credentials to your logging provider. So `toErrorReport` copies
fields **explicitly** and never touches `headers` — and there is a test asserting a bearer token
and session value cannot appear in the output. Keep that test.

```ts
export function toErrorReport(error, request, context): ErrorReport {
  return {
    message: error instanceof Error ? error.message : String(error),
    digest: /* React attaches this to Server Component errors */,
    stack: error instanceof Error ? error.stack : undefined,
    path: request.path,
    method: request.method,
    routePath: context.routePath,
    routerKind: context.routerKind,
    renderSource: context.renderSource,
  };
}
```

`digest` matters: for an error thrown during Server Component rendering, the instance React hands
you may not be the original, and `digest` is the id that correlates it with what the user saw in
`error.tsx`. Keep it in the report, and show it in the error UI if you want support to be able
to match them up.

If `register` is also exported, it runs once per server instance before any request — that is
where an OTel SDK gets initialised (`registerOTel('app-name')` from `@vercel/otel`).

## Client errors

`error.tsx` and `global-error.tsx` call `reportClientError` from a `useEffect`, rather than
`console.error`:

```tsx
useEffect(() => {
  reportClientError(error, 'dashboard/invoices');
}, [error]);
```

`global-error.tsx` is easy to forget and is the only thing that catches a root-layout failure.
It replaces the whole document, so it must render its own `<html>` and `<body>`, and it cannot
use anything from the layout it is replacing.

Send nothing user-identifying beyond what the provider is already entitled to hold, and never a
token — the rules in `auth.md` apply to telemetry too.

## Web Vitals

`app/ui/web-vitals.tsx` is a client component mounted once in the root layout that returns
`null`. `useReportWebVitals` gives you real-user LCP/CLS/INP rather than a lab score. Keep the
callback non-blocking (`navigator.sendBeacon` or a fire-and-forget fetch); never `await` inside
it.

## What not to do

- Do not log in every `catch`. Catch to *handle* — convert to a `State` message, call
  `notFound()`, redirect — and let `onRequestError` do the recording.
- Do not log request headers, cookies, tokens, or request bodies that may contain them.
- Do not render a raw message or stack to the user. Show a sentence, a retry, and at most the
  `digest`.
- Do not add a vendor SDK until someone will actually read the dashboard. The seams exist so
  that decision stays cheap and reversible.
