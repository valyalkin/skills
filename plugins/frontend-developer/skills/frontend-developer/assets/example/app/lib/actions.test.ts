import { expect, it, vi } from 'vitest';
import { createInvoice } from '@/app/lib/actions';

vi.mock('next/cache', () => ({ updateTag: vi.fn(), revalidateTag: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: vi.fn(() => {
    throw new Error('NEXT_REDIRECT');
  }),
}));

it('reports field errors for an empty form', async () => {
  const formData = new FormData();
  formData.set('customerId', '');
  formData.set('amount', '0');

  const state = await createInvoice({ message: null, errors: {} }, formData);

  expect(state.errors?.customerId).toContain('Please select a customer.');
  expect(state.errors?.amount).toContain(
    'Please enter an amount greater than $0.',
  );
  expect(state.errors?.status).toContain('Please select an invoice status.');
});

it('redirects after a successful create', async () => {
  const formData = new FormData();
  formData.set('customerId', 'c1');
  formData.set('amount', '25');
  formData.set('status', 'paid');

  await expect(
    createInvoice({ message: null, errors: {} }, formData),
  ).rejects.toThrow('NEXT_REDIRECT');
});
