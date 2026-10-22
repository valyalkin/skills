import Form from 'next/form';

/**
 * Submit-based search. A server component: no 'use client', no hooks, and it
 * works before hydration. next/form prefetches the target route and does a
 * client-side navigation on submit.
 */
export default function SearchForm({
  action,
  placeholder,
  defaultQuery,
}: {
  action: string;
  placeholder: string;
  defaultQuery?: string;
}) {
  return (
    <Form action={action} className="relative flex flex-1">
      <label htmlFor="query" className="sr-only">
        Search
      </label>
      <input
        id="query"
        name="query"
        defaultValue={defaultQuery}
        placeholder={placeholder}
        className="peer block w-full rounded-md border border-gray-200 py-2 pl-10 text-sm"
      />
      <button type="submit" className="sr-only">
        Search
      </button>
    </Form>
  );
}
