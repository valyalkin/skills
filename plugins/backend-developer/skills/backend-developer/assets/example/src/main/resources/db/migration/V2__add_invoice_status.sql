-- Additive and backwards compatible: the column has a default, so the previous version of the
-- application keeps working against this schema while the rollout is in progress. That property
-- is what makes the deployment order irrelevant - see the migration rules in persistence.md.
ALTER TABLE invoices
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_status CHECK (status IN ('PENDING', 'PAID'));

-- One open invoice per customer and amount; this is what DuplicateException maps onto when the
-- service's pre-check loses a race and Postgres rejects the insert.
CREATE UNIQUE INDEX ux_invoices_customer_amount_pending
    ON invoices (customer_id, amount_cents)
    WHERE status = 'PENDING';
