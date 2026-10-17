-- A Carrier Invoice File belongs to one Postpaid SIM Card of its Client Invoice
-- (carrier-invoice-per-sim). Nullable: a row from before this feature keeps NULL, and Postgres lets
-- any number of NULLs pass the unique constraint. Every row written from now on carries its SIM.
-- Bytes live in the S3-compatible object store (V12's header comment is stale; migrations are never edited).
ALTER TABLE carrier_invoice_files ADD COLUMN sim_card_id UUID REFERENCES sim_cards (id);
ALTER TABLE carrier_invoice_files
  ADD CONSTRAINT uq_carrier_invoice_files_invoice_sim UNIQUE (client_invoice_id, sim_card_id);
