export function FormSkeleton() {
  return (
    <div className="animate-pulse rounded-md bg-gray-50 p-6" aria-hidden="true">
      <div className="mb-4 h-10 w-full rounded bg-gray-100" />
      <div className="mb-4 h-10 w-full rounded bg-gray-100" />
      <div className="h-16 w-full rounded bg-gray-100" />
    </div>
  );
}

export function UserMenuSkeleton() {
  return (
    <div
      className="h-5 w-28 animate-pulse rounded bg-gray-100"
      aria-hidden="true"
    />
  );
}

export function SearchSkeleton() {
  return (
    <div
      className="h-10 w-full animate-pulse rounded-md bg-gray-100"
      aria-hidden="true"
    />
  );
}

export function InvoicesTableSkeleton() {
  return (
    <div className="mt-6 animate-pulse space-y-4" aria-hidden="true">
      {Array.from({ length: 6 }, (_, index) => (
        <div key={index} className="h-10 w-full rounded bg-gray-100" />
      ))}
    </div>
  );
}
