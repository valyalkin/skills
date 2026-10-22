import { expect, it } from 'vitest';
import { toErrorReport } from '@/app/lib/observability';

const request = {
  path: '/dashboard/invoices',
  method: 'GET',
  headers: {
    cookie: '__session=secret-session-value',
    authorization: 'Bearer secret-token',
  },
};
const context = { routePath: '/dashboard/invoices', routerKind: 'App Router' };

it('reports the error without leaking headers', () => {
  const report = toErrorReport(new Error('boom'), request, context);

  expect(report.message).toBe('boom');
  expect(report.path).toBe('/dashboard/invoices');
  expect(report.routerKind).toBe('App Router');

  // The session cookie and bearer token must not appear anywhere in the report.
  const serialized = JSON.stringify(report);
  expect(serialized).not.toContain('secret-session-value');
  expect(serialized).not.toContain('secret-token');
  expect(serialized).not.toContain('authorization');
});

it('carries the digest React attaches to server component errors', () => {
  const error = Object.assign(new Error('boom'), { digest: '1234567890' });

  expect(toErrorReport(error, request, context).digest).toBe('1234567890');
});
