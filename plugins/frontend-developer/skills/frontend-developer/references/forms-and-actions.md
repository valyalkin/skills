# Forms and Server Actions

Writes go through Server Actions in `app/lib/actions.ts`. The action validates with Zod, calls
the API through `apiFetch`, invalidates the cache, and redirects. The form is a small client
component driven by `useActionState`.

This is the example's pattern with the SQL replaced by an API call.

## The action

```ts
'use server';

import { z } from 'zod';
import { updateTag } from 'next/cache';
import { redirect } from 'next/navigation';
import { apiFetch, ApiError } from '@/app/lib/api';

const FormSchema = z.object({
  customerId: z.string().min(1, { message: 'Please select a customer.' }),
  amount: z.coerce.number().gt(0, { message: 'Please enter an amount greater than $0.' }),
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

export async function createInvoice(_prevState: State, formData: FormData): Promise<State> {
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
      return { errors: toFieldErrors(error.body), message: 'Please fix the errors above.' };
    }
    return { message: 'Something went wrong. Failed to create invoice.' };
  }

  updateTag('invoices');
  redirect('/dashboard/invoices');
}
```

Rules:

- `'use server'` at the top of the file. Actions are HTTP endpoints — validate every input,
  never trust a hidden field, and check authorization inside the action rather than in the UI.
- Signature is `(prevState, formData)` so it plugs into `useActionState`. For an action needing
  an id, bind it: `updateInvoice.bind(null, id)`, taking `(id, prevState, formData)`.
- Return a `State`; do not throw for user-correctable problems. Throw only for genuinely
  unexpected failures, so `error.tsx` catches them.
- `updateTag` before `redirect`. `redirect` throws internally, so it must be the last
  statement and must sit **outside** the `try` block. Use `updateTag` after a user-triggered
  write so the next read is fresh; `revalidateTag` only when serving stale data meanwhile is
  acceptable. On a project with `cacheComponents` off, use `revalidateTag`/`revalidatePath`.
- Money: the UI takes decimal input, the wire format is whatever the API specifies (cents in the
  example). Convert in the action, not in the component.
- Map API 422 responses onto the same `errors` shape as Zod, so the form renders both
  identically (`toFieldErrors` is a small local helper).

## The form

```tsx
'use client';

import { useActionState } from 'react';
import { createInvoice, type State } from '@/app/lib/actions';
import { Button } from '@/app/ui/button';

export default function Form({ customers }: { customers: CustomerField[] }) {
  const initialState: State = { message: null, errors: {} };
  const [state, formAction] = useActionState(createInvoice, initialState);

  return (
    <form action={formAction}>
      <label htmlFor="customer" className="mb-2 block text-sm font-medium">
        Choose customer
      </label>
      <select id="customer" name="customerId" defaultValue="" aria-describedby="customer-error">
        <option value="" disabled>
          Select a customer
        </option>
        {customers.map((customer) => (
          <option key={customer.id} value={customer.id}>
            {customer.name}
          </option>
        ))}
      </select>

      <div id="customer-error" aria-live="polite" aria-atomic="true">
        {state.errors?.customerId?.map((error) => (
          <p className="mt-2 text-sm text-red-500" key={error}>
            {error}
          </p>
        ))}
      </div>

      <div aria-live="polite" aria-atomic="true">
        {state.message ? <p className="mt-2 text-sm text-red-500">{state.message}</p> : null}
      </div>

      <Button type="submit">Create Invoice</Button>
    </form>
  );
}
```

Rules:

- `<form action={formAction}>` — no `onSubmit`, no `preventDefault`, no manual `fetch`. The form
  then works before hydration.
- Every control: an `id`, a `name` matching the Zod key, a `<label htmlFor>`, and
  `aria-describedby` pointing at its error container.
- Each error container is `aria-live="polite" aria-atomic="true"` so screen readers announce
  validation results, plus one container for the form-level `message`.
- Select/radio groups use `defaultValue=""` with a disabled placeholder; wrap radios in a
  `<fieldset>` with a `<legend>`.
- The browser's own `required` / `type="email"` / `min` attributes are a free first line of
  feedback and are fine to add, but they are never the validation: the Zod schema in the action
  is the single source of truth, since anything client-side can be bypassed.
- Pending state comes from `useFormStatus` inside the submit button, never from `useState`:

  ```tsx
  'use client';
  import { useFormStatus } from 'react-dom';

  export function SubmitButton({ children }: { children: React.ReactNode }) {
    const { pending } = useFormStatus();
    return (
      <Button type="submit" aria-disabled={pending} disabled={pending}>
        {pending ? 'Saving…' : children}
      </Button>
    );
  }
  ```
- Edit forms take the record as a prop from their server page and set `defaultValue` on each
  input — uncontrolled, not mirrored into state.
- Destructive actions (delete) are a `<form action={deleteInvoice.bind(null, id)}>` with an
  icon submit button and an `sr-only` label, as in the example's `buttons.tsx`.

## What not to do

- No client-side form libraries (react-hook-form, Formik) unless a form genuinely needs
  per-keystroke dependent fields. The Zod + `useActionState` path covers the rest and keeps
  validation authoritative on the server.
- No duplicated validation rules in the client. If inline feedback is wanted, import the same
  Zod schema rather than rewriting the rules.
- No `router.refresh()` to "make the list update" — that is what `updateTag` is for.
