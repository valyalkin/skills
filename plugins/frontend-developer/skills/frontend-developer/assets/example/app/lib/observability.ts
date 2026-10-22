import 'server-only';

export type ErrorReport = {
  message: string;
  digest?: string;
  stack?: string;
  path?: string;
  method?: string;
  routePath?: string;
  routerKind?: string;
  renderSource?: string;
};

/**
 * Builds the report from what Next hands `onRequestError`.
 *
 * Deliberately field-by-field: `request.headers` carries the session cookie and
 * any Authorization header, so it must never be copied into a log or sent to a
 * third party. Add fields explicitly, never spread the request.
 */
export function toErrorReport(
  error: unknown,
  request: { path?: string; method?: string },
  context: { routePath?: string; routerKind?: string; renderSource?: string },
): ErrorReport {
  return {
    message: error instanceof Error ? error.message : String(error),
    digest:
      typeof error === 'object' && error !== null && 'digest' in error
        ? String((error as { digest: unknown }).digest)
        : undefined,
    stack: error instanceof Error ? error.stack : undefined,
    path: request.path,
    method: request.method,
    routePath: context.routePath,
    routerKind: context.routerKind,
    renderSource: context.renderSource,
  };
}

/**
 * The single place server errors leave the app. Swap the body for a vendor SDK
 * (Sentry's captureException, an OTel exporter) without touching callers.
 */
export function reportError(report: ErrorReport): void {
  console.error(JSON.stringify({ level: 'error', ...report }));
}
