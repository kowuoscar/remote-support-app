-- Carrier invoice files attached to a Client Invoice draft (spec.md Solution: "plus attached
-- carrier-invoice files (opaque supporting documents, not modeled entities)"; client-invoice-
-- generation ticket AC: "Agent can attach one or more carrier invoice files"). The file bytes
-- themselves live on the filesystem (CarrierInvoiceFileStorage), not in this table -- see its
-- Javadoc for the filesystem-vs-DB-blob trade-off. `storage_path` is an opaque handle the app
-- never exposes to a client; `filename`/`content_type`/`size_bytes` are the metadata a download
-- response needs without touching disk.
CREATE TABLE carrier_invoice_files (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    client_invoice_id UUID NOT NULL REFERENCES client_invoices (id),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_carrier_invoice_files_tenant_id ON carrier_invoice_files (tenant_id);
CREATE INDEX idx_carrier_invoice_files_client_invoice_id ON carrier_invoice_files (client_invoice_id);
