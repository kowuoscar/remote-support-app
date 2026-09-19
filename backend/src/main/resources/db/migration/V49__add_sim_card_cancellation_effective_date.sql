-- manager-decides-return-disposition ticket: a cancelled SIM Card (Disposition CANCELLED, spec.md
-- Solution's Completion table) keeps the effective cancellation date the Agent gives at completion
-- ("the SIM Card is retired and keeps the date" — CONTEXT.md "Disposition"), past or future.
-- Nullable: only ever set on a SIM Card retired this way, never on an ordinary retirement.
ALTER TABLE sim_cards ADD COLUMN cancellation_effective_date DATE;
