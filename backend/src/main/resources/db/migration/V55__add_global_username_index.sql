-- A username is unique across the whole deployment, not merely within one Tenant
-- (globally-unique-usernames spec.md Solution "The database is what makes the rule true";
-- global-username-index ticket). uq_users_tenant_username (V1) stays exactly as it is -- it is
-- subsumed but harmless, and dropping it is schema churn with no defect behind it.
--
-- Before creating the index, a pre-check stops the migration -- writing nothing -- if any
-- normalized username (lower(btrim(username))) already exists under more than one users row,
-- naming each offending username, its row count and the Tenant ids holding it. This is
-- deliberately louder than letting CREATE UNIQUE INDEX fail on its own, whose error names one
-- arbitrary duplicated key and no Tenant. Reconciled against the data that exists (V2/V3 seeds,
-- DemoDataLoader): no username appears under two Tenants today, so this only ever fires against a
-- developer's own hand-made rows.
DO $$
DECLARE
    offenders text;
BEGIN
    SELECT string_agg(
               format(
                   '%s (%s rows, tenants: %s)',
                   normalized_username,
                   row_count,
                   tenant_ids),
               '; ' ORDER BY normalized_username)
    INTO offenders
    FROM (
        SELECT
            lower(btrim(username)) AS normalized_username,
            count(*) AS row_count,
            string_agg(DISTINCT tenant_id::text, ', ' ORDER BY tenant_id::text) AS tenant_ids
        FROM users
        GROUP BY lower(btrim(username))
        HAVING count(*) > 1
    ) duplicates;

    IF offenders IS NOT NULL THEN
        RAISE EXCEPTION
            'V55 cannot create uq_users_username_global: the following normalized username(s) '
            'exist under more than one users row and must be resolved by hand first -- %',
            offenders;
    END IF;
END $$;

CREATE UNIQUE INDEX uq_users_username_global ON users (lower(btrim(username)));
