# Slice 5 Result: MarketData Ownership

## Completed

- V39 backfills product-owned NAV and indicator records, adds `market_watched_index`, and validates ownership before proceeding.
- MarketData now owns published NAV, trading calendar, indicator, index K-line, and watched-index responsibilities through its four-layer packages and APIs.
- Realtime refresh fetches the de-duplicated union of user watched indices while request reads are owner-filtered.
- The Settings UI uses the MarketData watched-index API. `UserConfig` now owns only the monthly DCA budget.
- `FundNavHistorySource`, `FundCatalogSource`, and `IndexKlineSource` split external data capabilities. `MarketDataSource` remains a deprecated migration bridge; the fallback chain is the primary implementation.

## Verification

- Focused MarketData, cache, ownership, user-isolation, and architecture suites passed.
- Fresh-schema Flyway ran through V39 and Hibernate validation passed in PostgreSQL integration tests.

## Stop Point

Legacy foreign keys and `user_config.watched_indices` remain for data compatibility only. Contract deletion requires a later approved migration after a stable production version and reconciliation.
