# Slice 3 Result: ProductCatalog

## Status

ProductCatalog code, API, frontend cutover, static/unit verification, and the clean-schema database
gate are complete. PostgreSQL 16 applied all 34 migrations through V36, Hibernate schema validation
completed, and the required reconciliation queries passed on the isolated `fundpilot_test` schema.
A production-copy rehearsal remains a deployment gate, not a prerequisite for starting the next
development slice.

## Delivered

- Added the global `FundProduct` aggregate and `fund_product` persistence ownership.
- Added deterministic legacy backfill plus `fund_product_migration_conflict` reporting.
- Added product search, ensure, catalog synchronization, and default discipline suggestion contracts.
- Added `FundFeeSchedule` ownership over the existing `fund_fee` table, including HTML parsing,
  persistence, cached reads, on-demand refresh, rate limiting, and the 02:30 scheduled refresh.
- Changed legacy transaction confirmation to read cached rates through `FundFeeApi` while preserving
  zero-fee degradation when no schedule exists.
- Switched the frontend to `/api/products`, `/api/products/{fundCode}/fees`, and
  `/api/admin/products/catalog/sync`.
- Removed legacy dictionary and fee Controller/Service/Job/JPA ownership. The `fund_dict` table and
  legacy `fund` product columns remain untouched for rollback.

## Database Reconciliation

Run after applying V36:

```sql
SELECT count(*) AS missing_product_reference
FROM fund
WHERE fund_code IS NOT NULL AND trim(fund_code) <> '' AND product_id IS NULL;

SELECT fund_code, count(*) AS active_rows
FROM fund_product
WHERE deleted_date IS NULL
GROUP BY fund_code
HAVING count(*) > 1;

SELECT field_name, count(*) AS conflicts
FROM fund_product_migration_conflict
GROUP BY field_name
ORDER BY field_name;

SELECT count(*) AS mismatched_reference
FROM fund f
JOIN fund_product p ON p.id = f.product_id
WHERE trim(f.fund_code) <> p.fund_code;
```

Required results: missing references, active duplicate codes, and mismatched references are all zero.
Conflict counts are reviewed, not required to be zero.

## Verification

- Backend compile: passed.
- ProductCatalog, fee parser/persistence, transaction fee regression, and architecture tests: passed.
- Frontend tests, ESLint, and production build: passed.
- PostgreSQL 16 clean-schema Flyway migration: passed; 34 migrations applied through V36.
- Hibernate schema validation against the migrated schema: passed.
- Clean-schema reconciliation: missing product references = 0, active duplicate codes = 0,
  mismatched references = 0, reported conflicts = 0.
- Production-copy migration and conflict review: not run; remains a pre-deployment gate.
- Full backend `clean verify`: passed; 584 tests executed with no failures, errors, or skips. Legacy
  user-scoped integration fixtures now create a real test administrator, bind an explicit
  `CurrentActor`, and assign matching owner IDs without adding a production fallback identity.

## Rollback Point

Before V36 is applied, revert the ProductCatalog code and frontend routes. After V36 is applied,
the application may roll back without dropping `fund_product`, `fund_product_migration_conflict`, or
`fund.product_id`; the legacy columns and `fund_dict` remain readable. Schema removal requires a later,
separately approved contract migration.
