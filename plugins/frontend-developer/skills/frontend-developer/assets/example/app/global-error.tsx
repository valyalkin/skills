'use client';

import { useEffect } from 'react';
import { reportClientError } from '@/app/lib/report-client-error';

/**
 * Catches errors thrown by the root layout itself, which `error.tsx` cannot.
 * It replaces the whole document, so it must render its own <html>/<body>.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    reportClientError(error, 'global');
  }, [error]);

  return (
    <html lang="en">
      <body className="flex min-h-screen flex-col items-center justify-center gap-4 p-6">
        <h1 className="text-xl font-semibold">Something went wrong</h1>
        <p className="text-sm text-gray-600">
          The error has been reported. Try again in a moment.
        </p>
        <button
          onClick={() => reset()}
          className="rounded-md bg-blue-500 px-4 py-2 text-sm text-white hover:bg-blue-400"
        >
          Try again
        </button>
      </body>
    </html>
  );
}
