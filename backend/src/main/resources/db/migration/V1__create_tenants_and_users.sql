-- Tenant-scoped schema foundation. Every core table (users included) carries a tenant_id, even
-- though the MVP seeds and runs exactly one tenant (see spec.md Constraints).
CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Role model: MANAGER, AGENT, TESTER are active this iteration; SUPER_ADMIN is reserved with no
-- dedicated behaviour yet (spec.md Goals/Non-goals).
CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('MANAGER', 'AGENT', 'TESTER', 'SUPER_ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username)
);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);
