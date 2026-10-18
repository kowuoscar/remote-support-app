-- agent-sends-the-month: when a Client Invoice was first sent. Set at its first send, never
-- changed, never cleared by a send-back, so "the Agent's month is sent" and its date are derived
-- from the invoices. Backfilled for invoices sent before this column existed: a sent-back draft
-- takes its send-back time (the closest record left; its first send was earlier), a DRAFT never
-- sent stays NULL.
ALTER TABLE client_invoices ADD COLUMN first_sent_at TIMESTAMPTZ;

UPDATE client_invoices
   SET first_sent_at = COALESCE(sent_at, approved_at, sent_back_at, created_at)
 WHERE status <> 'DRAFT' OR sent_back_at IS NOT NULL;
