import type { Instrumentation } from 'next';
import { reportError, toErrorReport } from '@/app/lib/observability';

export const onRequestError: Instrumentation.onRequestError = (
  error,
  request,
  context,
) => {
  reportError(toErrorReport(error, request, context));
};
