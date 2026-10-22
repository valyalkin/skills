import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import Search from '@/app/ui/search';

const replace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  usePathname: () => '/dashboard/invoices',
  useSearchParams: () => new URLSearchParams('query=old'),
}));

it('pushes the typed term into the query string', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  render(<Search placeholder="Search invoices..." />);

  await user.clear(screen.getByRole('textbox', { name: /search/i }));
  await user.type(screen.getByRole('textbox', { name: /search/i }), 'acme');
  await vi.advanceTimersByTimeAsync(300);

  expect(replace).toHaveBeenCalledWith('/dashboard/invoices?query=acme&page=1');
  vi.useRealTimers();
});
