import { Suspense } from 'react';
import type { Metadata } from 'next';
import { fetchInvoiceById } from '@/app/lib/data';
import EditInvoiceSection from '@/app/ui/invoices/edit-section';
import { InvoicesTableSkeleton } from '@/app/ui/skeletons';

export async function generateMetadata(props: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await props.params;
  const invoice = await fetchInvoiceById(id);

  return {
    title: `Edit invoice ${invoice.id}`,
    robots: { index: false },
  };
}

export default function Page(props: { params: Promise<{ id: string }> }) {
  return (
    <main>
      <h1 className="text-2xl">Edit invoice</h1>
      <Suspense fallback={<InvoicesTableSkeleton />}>
        <EditInvoiceSection params={props.params} />
      </Suspense>
    </main>
  );
}
