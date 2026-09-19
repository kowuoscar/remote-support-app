-- manager-approves-requests ticket: Pending Approval and Rejected are two brand-new status values
-- on the existing lifecycle CHECK (request-types-and-flow spec, Lifecycle) -- a plain widen, no
-- narrow, mirroring replace-requests' V40 (net-new values, nothing to phase out). No Request that
-- exists today is ever PENDING_APPROVAL, so nothing here changes any existing row's status.
ALTER TABLE requests DROP CONSTRAINT requests_status_check;
ALTER TABLE requests ADD CONSTRAINT requests_status_check CHECK (
    status IN ('PENDING_APPROVAL', 'SUBMITTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REJECTED')
);

-- Rejected's own required reason (distinct from cancellation_reason -- CONTEXT.md "Rejected"),
-- mirroring V8's chk_requests_cancelled_has_reason.
ALTER TABLE requests ADD COLUMN rejection_reason TEXT;
ALTER TABLE requests ADD CONSTRAINT chk_requests_rejected_has_reason CHECK (
    (status = 'REJECTED' AND rejection_reason IS NOT NULL) OR (status <> 'REJECTED')
);

-- The decision (spec.md Manager approval: "records who decided and when") -- both null until a
-- Manager approves or rejects.
ALTER TABLE requests ADD COLUMN decided_by_user_id UUID REFERENCES users (id);
ALTER TABLE requests ADD COLUMN decided_at TIMESTAMPTZ;
