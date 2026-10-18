-- carrier-invoice-retention: a Carrier Invoice File's bytes are deleted from the object store 30
-- days after its Client Invoice is approved; the row stays. deleted_at is null while the bytes are
-- kept, is set once when retention deleted them and is never cleared. storage_path keeps the
-- object key for the record.
ALTER TABLE carrier_invoice_files ADD COLUMN deleted_at TIMESTAMPTZ NULL;

-- Serves the retention job's join to client_invoices; it shrinks as files are deleted.
CREATE INDEX idx_carrier_invoice_files_not_deleted
  ON carrier_invoice_files (client_invoice_id)
  WHERE deleted_at IS NULL;
