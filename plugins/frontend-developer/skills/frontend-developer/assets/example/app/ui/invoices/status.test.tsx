import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import InvoiceStatus from '@/app/ui/invoices/status';

it('renders the paid state', () => {
  render(<InvoiceStatus status="paid" />);
  expect(screen.getByText('paid')).toBeInTheDocument();
});
