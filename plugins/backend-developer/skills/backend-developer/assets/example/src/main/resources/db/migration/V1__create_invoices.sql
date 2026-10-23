CREATE TABLE invoices (
    id           UUID        NOT NULL,
    customer_id  UUID        NOT NULL,
    amount_cents BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    -- Backs @Version on the entity: optimistic locking, so a concurrent update becomes a 409
    -- instead of a lost write.
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT ck_invoices_amount_positive CHECK (amount_cents > 0)
);

CREATE INDEX ix_invoices_customer_id ON invoices (customer_id);
