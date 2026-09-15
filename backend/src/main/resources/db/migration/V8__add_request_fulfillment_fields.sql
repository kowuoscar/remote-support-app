-- Request status transitions, cancellation, and Agent-authored/proactive logging
-- (agent-request-fulfillment ticket).
--
-- `raised_by_user_id` records the login that actually created the row — the Tester themselves
-- for a Tester-authored Request, the Agent's own login for an Agent-authored one — distinct from
-- `tester_id`, which always names whose behalf the Request is raised on (unchanged from V7).
-- `agent_authored` makes that provenance directly queryable without joining back through
-- `raised_by_user_id` to `users.role` (fee-logging-and-provisioning ticket will filter on this to
-- know whether a Request was logged proactively).
ALTER TABLE requests ADD COLUMN raised_by_user_id UUID REFERENCES users (id);

-- Backfill: every Request written before this migration was Tester-authored (V7 only ever wrote
-- SUBMITTED from a Tester), so its raiser is that Tester's own login.
UPDATE requests
SET raised_by_user_id = (SELECT user_id FROM testers WHERE testers.id = requests.tester_id)
WHERE raised_by_user_id IS NULL;

ALTER TABLE requests ALTER COLUMN raised_by_user_id SET NOT NULL;

ALTER TABLE requests ADD COLUMN agent_authored BOOLEAN NOT NULL DEFAULT FALSE;

-- Required only when cancelling (agent-request-fulfillment ticket AC: "Agent can cancel a
-- Request ... with a reason"); NULL the rest of the time.
ALTER TABLE requests ADD COLUMN cancellation_reason TEXT;

ALTER TABLE requests ADD CONSTRAINT chk_requests_cancelled_has_reason CHECK (
    (status = 'CANCELLED' AND cancellation_reason IS NOT NULL) OR (status <> 'CANCELLED')
);

CREATE INDEX idx_requests_raised_by_user_id ON requests (raised_by_user_id);
