import { Suspense } from 'react';
import type { Metadata } from 'next';
import Search from '@/app/ui/search';
import InvoicesTableSection from '@/app/ui/invoices/table-section';
import { InvoicesTableSkeleton, SearchSkeleton } from '@/app/ui/skeletons';

export const metadata: Metadata = {
  title: 'Invoices',
};

export default function Page(props: {
  searchParams: Promise<{ query?: string; page?: string }>;
}) {
  return (
    <div className="w-full">
      <h1 className="text-2xl">Invoices</h1>
      <div className="mt-4 flex items-center justify-between gap-2 md:mt-8">
        <Suspense fallback={<SearchSkeleton />}>
          <Search placeholder="Search invoices..." />
        </Suspense>
      </div>
      <Suspense fallback={<InvoicesTableSkeleton />}>
        <InvoicesTableSection searchParams={props.searchParams} />
      </Suspense>
    </div>
  );
}
