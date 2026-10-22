import { http, HttpResponse } from 'msw';
import { expect, it, vi } from 'vitest';
import { server } from '@/mocks/server';
import { fetchInvoiceById, fetchInvoicesPages } from '@/app/lib/data';

// 'use cache' is inert under Vitest, but cacheTag/cacheLife need the Next runtime.
vi.mock('next/cache', () => ({ cacheTag: vi.fn(), cacheLife: vi.fn() }));

it('returns the invoice', async () => {
  await expect(fetchInvoiceById('1')).resolves.toMatchObject({ id: '1' });
});

it('throws ApiError on a server failure', async () => {
  server.use(
    http.get(
      'http://api.test/invoices/1',
      () => new HttpResponse(null, { status: 500 }),
    ),
  );
  await expect(fetchInvoiceById('1')).rejects.toThrow(
    'API request failed with 500',
  );
});

it('derives the page count', async () => {
  await expect(fetchInvoicesPages('')).resolves.toBe(3);
});
