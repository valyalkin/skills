import 'server-only';

import { cacheLife, cacheTag } from 'next/cache';
import { notFound } from 'next/navigation';
import { apiFetch, ApiError } from '@/app/lib/api';
import { authedFetch } from '@/app/lib/auth';
import type {
  Customer,
  CustomerField,
  Invoice,
  InvoicesTableRow,
} from '@/app/lib/definitions';

const ITEMS_PER_PAGE = 6;

export async function fetchFilteredInvoices(
  query: string,
  currentPage: number,
) {
  'use cache';
  cacheTag('invoices');
  cacheLife('minutes');

  const params = new URLSearchParams({
    query,
    offset: String((currentPage - 1) * ITEMS_PER_PAGE),
    limit: String(ITEMS_PER_PAGE),
  });

  return apiFetch<InvoicesTableRow[]>(`/invoices?${params}`);
}

export async function fetchInvoicesPages(query: string) {
  'use cache';
  cacheTag('invoices');
  cacheLife('minutes');

  const { total } = await apiFetch<{ total: number }>(
    `/invoices/count?query=${encodeURIComponent(query)}`,
  );

  return Math.ceil(total / ITEMS_PER_PAGE);
}

/**
 * Per-user read: uncached, so it runs at request time rather than during the
 * build. A 'use cache' version would be executed by `next build` and would
 * need the API reachable from CI.
 */
export async function fetchCustomers(): Promise<CustomerField[]> {
  const customers = await authedFetch<Customer[]>('/customers');

  return customers.map(({ id, name }) => ({ id, name }));
}

export async function fetchInvoiceById(id: string) {
  try {
    return await apiFetch<Invoice>(`/invoices/${id}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

/** Per-user read: no 'use cache', because the access token is per request. */
export async function fetchMyInvoiceSummary() {
  return authedFetch<{ outstanding: number; paid: number }>(
    '/me/invoices/summary',
  );
}
