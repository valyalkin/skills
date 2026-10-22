import { fetchCustomers } from '@/app/lib/data';
import CreateInvoiceForm from '@/app/ui/invoices/create-form';

export default async function CreateInvoiceSection() {
  const customers = await fetchCustomers();

  return <CreateInvoiceForm customers={customers} />;
}
