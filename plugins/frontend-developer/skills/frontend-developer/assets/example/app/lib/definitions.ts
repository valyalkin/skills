import type { components } from '@/app/lib/api-schema';

/**
 * UI-facing names for the API's generated schemas. Add an alias here per type
 * the UI renders; never redeclare the shape by hand - regenerating the schema
 * is what catches a backend change at compile time.
 */
type Schemas = components['schemas'];

export type InvoiceStatus = Schemas['InvoiceStatus'];
export type Invoice = Schemas['Invoice'];
export type InvoicesTableRow = Schemas['InvoicesTableRow'];
export type Customer = Schemas['Customer'];

/** A view type the API does not define: just what the <select> needs. */
export type CustomerField = Pick<Customer, 'id' | 'name'>;
