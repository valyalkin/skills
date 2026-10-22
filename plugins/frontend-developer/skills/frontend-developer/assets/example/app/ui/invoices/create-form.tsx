'use client';

import Link from 'next/link';
import { useActionState } from 'react';
import { createInvoice, type State } from '@/app/lib/actions';
import type { CustomerField } from '@/app/lib/definitions';
import SubmitButton from '@/app/ui/invoices/submit-button';

export default function CreateInvoiceForm({
  customers,
}: {
  customers: CustomerField[];
}) {
  const initialState: State = { message: null, errors: {} };
  const [state, formAction] = useActionState(createInvoice, initialState);

  return (
    <form action={formAction}>
      <div className="rounded-md bg-gray-50 p-4 md:p-6">
        <div className="mb-4">
          <label htmlFor="customer" className="mb-2 block text-sm font-medium">
            Choose customer
          </label>
          <select
            id="customer"
            name="customerId"
            defaultValue=""
            aria-describedby="customer-error"
            className="block w-full cursor-pointer rounded-md border border-gray-200 py-2 pl-3 text-sm"
          >
            <option value="" disabled>
              Select a customer
            </option>
            {customers.map((customer) => (
              <option key={customer.id} value={customer.id}>
                {customer.name}
              </option>
            ))}
          </select>
          <div
            id="customer-error"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            {state.errors?.customerId?.map((error) => (
              <p className="mt-2 text-sm text-red-500" key={error}>
                {error}
              </p>
            ))}
          </div>
        </div>

        <div className="mb-4">
          <label htmlFor="amount" className="mb-2 block text-sm font-medium">
            Choose an amount
          </label>
          <input
            id="amount"
            name="amount"
            type="number"
            step="0.01"
            placeholder="Enter USD amount"
            aria-describedby="amount-error"
            className="block w-full rounded-md border border-gray-200 py-2 pl-3 text-sm"
          />
          <div
            id="amount-error"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            {state.errors?.amount?.map((error) => (
              <p className="mt-2 text-sm text-red-500" key={error}>
                {error}
              </p>
            ))}
          </div>
        </div>

        <fieldset>
          <legend className="mb-2 block text-sm font-medium">
            Set the invoice status
          </legend>
          <div className="flex gap-4 rounded-md border border-gray-200 bg-white px-3 py-3">
            <div className="flex items-center">
              <input
                id="pending"
                name="status"
                type="radio"
                value="pending"
                className="h-4 w-4 cursor-pointer"
              />
              <label htmlFor="pending" className="ml-2 text-xs font-medium">
                Pending
              </label>
            </div>
            <div className="flex items-center">
              <input
                id="paid"
                name="status"
                type="radio"
                value="paid"
                className="h-4 w-4 cursor-pointer"
              />
              <label htmlFor="paid" className="ml-2 text-xs font-medium">
                Paid
              </label>
            </div>
          </div>
          <div
            id="status-error"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            {state.errors?.status?.map((error) => (
              <p className="mt-2 text-sm text-red-500" key={error}>
                {error}
              </p>
            ))}
          </div>
        </fieldset>

        <div role="status" aria-live="polite" aria-atomic="true">
          {state.message ? (
            <p className="mt-2 text-sm text-red-500">{state.message}</p>
          ) : null}
        </div>
      </div>

      <div className="mt-6 flex justify-end gap-4">
        <Link
          href="/dashboard/invoices"
          className="flex h-10 items-center rounded-lg bg-gray-100 px-4 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-200"
        >
          Cancel
        </Link>
        <SubmitButton>Create invoice</SubmitButton>
      </div>
    </form>
  );
}
