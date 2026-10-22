import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import CreateInvoiceForm from '@/app/ui/invoices/create-form';

// useActionState calls the action on submit; return the shape the form renders.
const { createInvoice } = vi.hoisted(() => ({
  createInvoice: vi.fn(async () => ({
    errors: { customerId: ['Please select a customer.'] },
    message: 'Missing fields. Failed to create invoice.',
  })),
}));
vi.mock('@/app/lib/actions', () => ({ createInvoice }));

const customers = [{ id: 'c1', name: 'Acme' }];

it('labels every control and wires its error container', () => {
  render(<CreateInvoiceForm customers={customers} />);

  const select = screen.getByLabelText('Choose customer');
  expect(select).toHaveAttribute('aria-describedby', 'customer-error');
  expect(screen.getByLabelText('Choose an amount')).toHaveAttribute(
    'aria-describedby',
    'amount-error',
  );
  expect(
    screen.getByRole('group', { name: 'Set the invoice status' }),
  ).toBeInTheDocument();
});

it('announces the validation errors the action returns', async () => {
  render(<CreateInvoiceForm customers={customers} />);

  await userEvent.click(screen.getByRole('button', { name: 'Create invoice' }));

  await screen.findByText('Please select a customer.');

  // The message must sit in a live region, or a screen reader never hears it.
  const liveRegions = screen.getAllByRole('status');
  expect(
    liveRegions.some((region) =>
      region.textContent?.includes('Please select a customer.'),
    ),
  ).toBe(true);
});
