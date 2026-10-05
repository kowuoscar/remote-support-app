-- deactivate-a-login: a Login is switched off, never deleted. NULL means active, so every
-- existing Login stays active without a data rewrite.
ALTER TABLE users ADD COLUMN deactivated_at timestamptz NULL;
