import { fetchFilteredInvoices } from '@/app/lib/data';
import { formatCurrency } from '@/app/lib/utils';
import InvoiceStatus from '@/app/ui/invoices/status';

export default async function InvoicesTable({
  query,
  currentPage,
}: {
  query: string;
  currentPage: number;
}) {
  const invoices = await fetchFilteredInvoices(query, currentPage);

  return (
    <table className="mt-6 min-w-full text-gray-900">
      <thead className="text-left text-sm font-normal">
        <tr>
          <th scope="col" className="px-4 py-5">
            Customer
          </th>
          <th scope="col" className="px-3 py-5">
            Amount
          </th>
          <th scope="col" className="px-3 py-5">
            Status
          </th>
        </tr>
      </thead>
      <tbody className="bg-white">
        {invoices.map((invoice) => (
          <tr key={invoice.id} className="border-b text-sm">
            <td className="px-4 py-3">{invoice.name}</td>
            <td className="px-3 py-3">{formatCurrency(invoice.amount)}</td>
            <td className="px-3 py-3">
              <InvoiceStatus status={invoice.status} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
