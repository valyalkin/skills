/**
 * Client-side error reporting seam. Server errors are captured by
 * `onRequestError` in instrumentation.ts; this covers errors that happen while
 * rendering in the browser, which the server never sees.
 *
 * Swap the body for a vendor SDK's browser client. Never include anything
 * user-identifying beyond what that provider is already allowed to hold.
 */
export function reportClientError(
  error: Error & { digest?: string },
  boundary: string,
): void {
  console.error(
    JSON.stringify({
      level: 'error',
      source: 'client',
      boundary,
      message: error.message,
      digest: error.digest,
    }),
  );
}
