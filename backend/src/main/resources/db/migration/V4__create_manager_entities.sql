-- Client/Tester/Agent/Contract, the entity set the Manager stands up before anything else can
-- happen (spec.md Solution/Core entities and relationships; manager-entity-setup ticket).

CREATE TABLE clients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clients_tenant_id ON clients (tenant_id);

-- Country fixes currency deterministically (spec.md: "a country (which fixes their currency)").
-- currency is stored denormalized from country at creation time so it can be queried/joined
-- without re-deriving it, and so a Contract can copy it without a lookup at read time.
CREATE TABLE agents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(255) NOT NULL,
    country VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    salary_amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agents_tenant_id ON agents (tenant_id);

-- A Tester is a login (one row in `users`, role TESTER) plus the Client it belongs to and
-- whether it is that Client's primary contact. At most one primary contact per Client is
-- enforced both here (defense in depth) and in the service layer, which returns a clean 409
-- instead of a raw constraint-violation error.
CREATE TABLE testers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    client_id UUID NOT NULL REFERENCES clients (id),
    user_id UUID NOT NULL REFERENCES users (id),
    is_primary_contact BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_testers_user_id UNIQUE (user_id)
);

CREATE INDEX idx_testers_tenant_id ON testers (tenant_id);
CREATE INDEX idx_testers_client_id ON testers (client_id);
CREATE UNIQUE INDEX uq_testers_one_primary_contact_per_client
    ON testers (client_id)
    WHERE is_primary_contact;

-- A Contract links exactly one Client and one Agent, but neither is unique on its own: a Client
-- can hold several Contracts (e.g. one per country/Agent) and an Agent can hold several
-- Contracts (spec.md Core entities and relationships). currency is copied from the Agent's
-- currency at creation time, not re-derived on read.
CREATE TABLE contracts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    client_id UUID NOT NULL REFERENCES clients (id),
    agent_id UUID NOT NULL REFERENCES agents (id),
    currency VARCHAR(8) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contracts_tenant_id ON contracts (tenant_id);
CREATE INDEX idx_contracts_client_id ON contracts (client_id);
CREATE INDEX idx_contracts_agent_id ON contracts (agent_id);
