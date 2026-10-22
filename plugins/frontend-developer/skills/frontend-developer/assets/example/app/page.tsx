import Link from 'next/link';

export default function Page() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-6">
      <h1 className="text-3xl font-semibold">Acme</h1>
      <p className="text-sm text-gray-600">Invoicing, minus the spreadsheet.</p>
      <Link
        href="/dashboard/invoices"
        className="flex h-10 items-center rounded-lg bg-blue-500 px-4 text-sm font-medium text-white hover:bg-blue-400"
      >
        Go to dashboard
      </Link>
    </main>
  );
}
