'use server';

import { updateTag } from 'next/cache';
import { redirect } from 'next/navigation';
import { z } from 'zod';
import { apiFetch, ApiError } from '@/app/lib/api';

const FormSchema = z.object({
  customerId: z.string().min(1, { message: 'Please select a customer.' }),
  amount: z.coerce
    .number()
    .gt(0, { message: 'Please enter an amount greater than $0.' }),
  status: z.enum(['pending', 'paid'], {
    message: 'Please select an invoice status.',
  }),
});

export type State = {
  errors?: {
    customerId?: string[];
    amount?: string[];
    status?: string[];
  };
  message?: string | null;
};

export async function createInvoice(
  _prevState: State,
  formData: FormData,
): Promise<State> {
  const validatedFields = FormSchema.safeParse({
    customerId: formData.get('customerId'),
    amount: formData.get('amount'),
    status: formData.get('status'),
  });

  if (!validatedFields.success) {
    return {
      errors: z.flattenError(validatedFields.error).fieldErrors,
      message: 'Missing fields. Failed to create invoice.',
    };
  }

  const { customerId, amount, status } = validatedFields.data;

  try {
    await apiFetch('/invoices', {
      method: 'POST',
      body: JSON.stringify({
        customer_id: customerId,
        amount: Math.round(amount * 100),
        status,
      }),
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 422) {
      return { message: 'Please fix the errors above.' };
    }
    return { message: 'Something went wrong. Failed to create invoice.' };
  }

  updateTag('invoices');
  redirect('/dashboard/invoices');
}
