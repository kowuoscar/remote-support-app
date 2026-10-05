-- send-a-client-invoice-back: a Manager returns a sent Client Invoice to draft with a reason.
-- Nullable, so every existing invoice stays "never sent back" with no data rewrite. The reason is
-- its own column, like requests.cancellation_reason / rejection_reason. (Not V57: V58 shipped
-- first, and Flyway rejects an out-of-order version on an already-migrated database.)
ALTER TABLE client_invoices ADD COLUMN sent_back_at timestamptz NULL;
ALTER TABLE client_invoices ADD COLUMN sent_back_reason varchar(1000) NULL;
