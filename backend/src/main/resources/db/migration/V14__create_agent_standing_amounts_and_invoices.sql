-- Agent standing amounts: a small history/versioning table for an Agent's standing monthly
-- salary and standing Rollout Advance (spec.md Solution's Agent Invoice entity; CONTEXT.md
-- "Rollout Advance"; agent-standing-amounts-and-invoice-generation ticket). Modeled as an
-- append-only history of (agent, amount_type, amount, effective_month, set_by, set_at) rows
-- rather than a single mutable column on `agents`, because "what was the standing salary/advance
-- in effect for month X" must be answerable for both the current invoice being built and any past
-- month, and a Manager's change must take effect starting *next* month's invoice, never the
-- current one already in progress (spec.md user stories 5-6). Resolving "the amount in effect for
-- month X" = the most recent row (by effective_month, then set_at as a same-month tiebreaker)
-- with effective_month <= X -- see AgentStandingAmount's Javadoc / StandingAmountService.
CREATE TABLE agent_standing_amounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    agent_id UUID NOT NULL REFERENCES agents (id),
    amount_type VARCHAR(24) NOT NULL CHECK (amount_type IN ('SALARY', 'ROLLOUT_ADVANCE')),
    amount NUMERIC(12, 2) NOT NULL,
    effective_month DATE NOT NULL,
    set_by_user_id UUID NOT NULL REFERENCES users (id),
    set_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_standing_amounts_tenant_id ON agent_standing_amounts (tenant_id);
CREATE INDEX idx_agent_standing_amounts_agent_type_month
    ON agent_standing_amounts (agent_id, amount_type, effective_month DESC, set_at DESC);

-- Backfill: the one Agent that already existed before this ticket (the seeded dev Agent "Jordan
-- Ellis", V5 migration) never went through AgentController#create's new "write the initial SALARY
-- row too" step, so it would otherwise resolve to 0/absent for every month. Every Agent created
-- from here on gets its initial SALARY row written in the same request that creates it -- this is
-- a one-time backfill for pre-existing data, not a general mechanism.
INSERT INTO agent_standing_amounts (id, tenant_id, agent_id, amount_type, amount, effective_month, set_by_user_id, set_at)
SELECT
    '66666666-6666-6666-6666-666666666666',
    a.tenant_id,
    a.id,
    'SALARY',
    a.salary_amount,
    date_trunc('month', a.created_at)::date,
    '22222222-2222-2222-2222-222222222222',
    a.created_at
FROM agents a
WHERE a.id = '55555555-5555-5555-5555-555555555555';

-- Agent Invoice: one per Agent per calendar month (spec.md Solution;
-- agent-standing-amounts-and-invoice-generation ticket). No snapshot columns yet -- every line
-- item is computed live while DRAFT, the only status this ticket ever writes (see AgentInvoice's
-- Javadoc). `status` is declared wide enough for the whole spec.md lifecycle (draft -> sent ->
-- approved -> paid) even though this ticket only ever writes DRAFT -- the
-- agent-invoice-submission-and-approval ticket needs SENT/APPROVED/PAID without a schema change,
-- mirroring client_invoices' V11 precedent.
--
-- The unique (agent_id, billing_month) pair makes "get or create this month's draft" safe under
-- concurrent first access, exactly like client_invoices' (contract_id, billing_month) constraint.
CREATE TABLE agent_invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    agent_id UUID NOT NULL REFERENCES agents (id),
    billing_month DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'APPROVED', 'PAID')),
    currency VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_agent_invoices_agent_billing_month UNIQUE (agent_id, billing_month)
);

CREATE INDEX idx_agent_invoices_tenant_id ON agent_invoices (tenant_id);
CREATE INDEX idx_agent_invoices_agent_id ON agent_invoices (agent_id);
