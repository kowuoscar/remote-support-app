-- Request: a support action raised against one Contract, attributed to the Tester it's raised
-- for (spec.md Solution; tester-request-submission ticket). `status` lists every value the
-- eventual state machine needs (agent-request-fulfillment ticket adds the transition rules), not
-- just the one this ticket ever writes, so no later migration has to widen this CHECK.

CREATE TABLE requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    contract_id UUID NOT NULL REFERENCES contracts (id),
    tester_id UUID NOT NULL REFERENCES testers (id),
    type VARCHAR(32) NOT NULL CHECK (
        type IN ('REBOOT', 'TOPUP', 'SIM_SWAP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'REPAIR')
    ),
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED' CHECK (
        status IN ('SUBMITTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_requests_tenant_id ON requests (tenant_id);
CREATE INDEX idx_requests_contract_id ON requests (contract_id);
CREATE INDEX idx_requests_tester_id ON requests (tester_id);
