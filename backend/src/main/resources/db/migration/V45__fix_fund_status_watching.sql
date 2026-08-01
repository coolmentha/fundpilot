-- FundStatus enum only accepts PENDING_HOLDING/HOLDING/CLEARED. Legacy createLegacyBridge
-- wrote the invalid 'WATCHING' value; Hibernate fails to map it on any legacy fund read.
-- Normalize dirty rows to the equivalent valid initial state (PENDING_HOLDING).
UPDATE fund
SET status = 'PENDING_HOLDING', updated_date = now()
WHERE deleted_date IS NULL AND status = 'WATCHING';
