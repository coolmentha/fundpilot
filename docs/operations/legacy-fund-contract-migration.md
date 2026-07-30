# Legacy Fund Contract Migration

Run this checklist only against a restored production backup. The local empty
PostgreSQL container is not evidence for a contract migration.

## Preconditions

- Keep a restorable `pg_dump -Fc` backup and validate it with `pg_restore --list`.
- Deploy and observe at least one stable version using `portfolio_fund_id` read
  paths.
- Confirm `event_publication` has no incomplete rows.
- Stop immediately if any query below returns a nonzero difference.

## Read-only Reconciliation

```sql
-- Every legacy fund still has exactly one tracked or voided portfolio mapping.
SELECT count(*) AS unmapped_legacy_funds
FROM fund f
LEFT JOIN portfolio_fund pf ON pf.legacy_fund_id = f.id
WHERE pf.id IS NULL;

-- No ledger row can lose its portfolio owner during the contract cutover.
SELECT count(*) AS unmapped_transactions
FROM fund_transaction t
LEFT JOIN portfolio_fund pf ON pf.id = t.portfolio_fund_id
WHERE pf.id IS NULL;

SELECT count(*) AS unmapped_lots
FROM fund_lot l
LEFT JOIN portfolio_fund pf ON pf.id = l.portfolio_fund_id
WHERE pf.id IS NULL;

-- Position facts migrated from fund must still agree before the old columns go.
SELECT count(*) AS position_differences
FROM accounting_position p
JOIN portfolio_fund pf ON pf.id = p.portfolio_fund_id
JOIN fund f ON f.id = pf.legacy_fund_id
WHERE p.owner_id <> pf.owner_id
   OR p.opened_at IS DISTINCT FROM f.opened_at
   OR p.cost_per_share IS DISTINCT FROM f.cost_per_share
   OR p.status <> CASE f.status
       WHEN 'HOLDING' THEN 'OPEN'
       WHEN 'CLEARED' THEN 'CLEARED'
       ELSE 'EMPTY'
   END;
```

## Authorization Gate

Only after the reconciliation is zero, the backup restore is proven, and the
user explicitly approves contract migration may a new forward-only Flyway
migration remove legacy columns, tables, read paths, and dual writes. Do not
mix that migration with unrelated behavior changes.
