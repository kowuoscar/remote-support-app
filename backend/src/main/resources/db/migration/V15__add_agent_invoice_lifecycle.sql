-- Agent Invoice send/override/approve/paid lifecycle (agent-invoice-submission-and-approval
-- ticket; CONTEXT.md "Agent Invoice" snapshot-on-send decision; ADR 0003). Mirrors V13's Client
-- Invoice precedent almost exactly, with one addition: Agent Invoice has a fourth status (PAID,
-- already allowed by V14's CHECK constraint) and four snapshot columns instead of one, because
-- every one of its four line items (Local Support Fees, Salary, Rollout Advance repayment, new
-- advance) is computed live while DRAFT (AgentInvoiceController's Javadoc) and all four must
-- freeze together at send -- there is no single "base amount" the way Client Invoice has.
--
-- `sent_at`/`approved_at`/`paid_at` record when each transition happened (nullable -- null until
-- that transition happens, mirroring V13's sent_at/approved_at).
--
-- The four `snapshot_*` columns freeze the figures the Agent actually saw when they sent the
-- invoice. They stay NULL for a DRAFT (still computed live) and are populated exactly once, at
-- DRAFT -> SENT. `snapshot_salary` and `snapshot_rollout_advance_new_advance` (only the new
-- advance, never the repayment line -- see CONTEXT.md's "Agent Invoice" entry for why) are the
-- two a Manager may subsequently overwrite via AgentInvoiceController#override, while the invoice
-- is still SENT -- an edit to *this invoice's own snapshot only*, never to `agent_standing_amounts`
-- (the table future invoices actually resolve against), so the standing rate used by every other
-- invoice is untouched.
ALTER TABLE agent_invoices
    ADD COLUMN sent_at TIMESTAMPTZ,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN snapshot_local_support_fees NUMERIC(12, 2),
    ADD COLUMN snapshot_salary NUMERIC(12, 2),
    ADD COLUMN snapshot_rollout_advance_repayment NUMERIC(12, 2),
    ADD COLUMN snapshot_rollout_advance_new_advance NUMERIC(12, 2);
