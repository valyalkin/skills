import { fetchInvoiceById } from '@/app/lib/data';

export default async function EditInvoiceSection({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const invoice = await fetchInvoiceById(id);

  return <p className="mt-4 text-sm">Invoice {invoice.id} loaded.</p>;
}
