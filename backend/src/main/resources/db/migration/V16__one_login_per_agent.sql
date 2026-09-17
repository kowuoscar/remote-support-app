-- agent-login-on-creation: an Agent has at most one login, ever. The nullable users.agent_id link
-- (V5) stays where it is; this makes it unique among the rows that set it. Enforced here as
-- defence in depth and in the service layer, which returns a clean 409 first — the same pattern
-- as V4's one-primary-contact-per-Client index. Replaces V5's plain lookup index on the column.
DROP INDEX idx_users_agent_id;

CREATE UNIQUE INDEX uq_users_one_login_per_agent
    ON users (agent_id)
    WHERE agent_id IS NOT NULL;
