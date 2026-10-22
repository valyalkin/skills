import clsx from 'clsx';
import type { InvoiceStatus as Status } from '@/app/lib/definitions';

export default function InvoiceStatus({ status }: { status: Status }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2 py-1 text-xs',
        {
          'bg-gray-100 text-gray-500': status === 'pending',
          'bg-green-500 text-white': status === 'paid',
        },
      )}
    >
      {status}
    </span>
  );
}
