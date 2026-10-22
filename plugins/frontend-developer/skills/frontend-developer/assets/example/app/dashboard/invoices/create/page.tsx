import { Suspense } from 'react';
import type { Metadata } from 'next';
import CreateInvoiceSection from '@/app/ui/invoices/create-section';
import { FormSkeleton } from '@/app/ui/skeletons';

export const metadata: Metadata = {
  title: 'Create invoice',
  robots: { index: false },
};

export default function Page() {
  return (
    <main>
      <h1 className="mb-4 text-2xl">Create invoice</h1>
      {/* The customer list is per-user, so it streams in rather than being
          prerendered - which also keeps the API out of the build. */}
      <Suspense fallback={<FormSkeleton />}>
        <CreateInvoiceSection />
      </Suspense>
    </main>
  );
}
