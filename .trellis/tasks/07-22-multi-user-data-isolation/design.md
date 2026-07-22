# Technical Design

## Authentication

- Add a `site_user` table with username, password hash, role (`USER`/`ADMIN`), enabled flag and audit timestamps.
- Replace the single API-key session identity with a signed session cookie containing the user id; retain the deployment API key only as a controlled bootstrap/migration credential.
- Hash passwords with the JDK PBKDF2 implementation; never store plaintext passwords.
- Resolve the current user in a request-scoped authentication context. Services use that context, never a request-supplied user id.

## Data Boundaries

- User-owned: `fund`, `fund_strategy`, `fund_strategy_activation`, `signal_log`, `fund_transaction`, `strategy_backtest`, `fund_lot`, `fund_lot_redemption`, `fund_dca_plan`, `fund_group`, `fund_group_item`, portfolio snapshots and `user_config`.
- Shared: fund product identity, `fund_nav_history`, market indicator snapshots, index K-lines, trading calendar and fund dictionary.
- Keep one shared fund product per fund code; user-specific fund rows reference the shared product and hold user-only attributes.

## Authorization

- Business APIs scope queries and writes to the authenticated user.
- Admin controllers require `ADMIN`; they can list, enable/disable, and change a user's role.
- Frontend obtains the authenticated user view, hides `/admin` navigation for `USER`, and backend remains authoritative for direct URL/API access.

## Migration

- Create the initial admin from environment-provided username/password once.
- Assign all legacy single-user rows to that admin.
- Split shared fund data without duplicating NAV/history rows; preserve foreign keys and unique constraints.

## Risks and Rollback

- Migration is schema/data changing: back up PostgreSQL first; deploy with Flyway validation and rollback via database backup.
- Temporary compatibility for the existing API-key login is allowed only during migration and must resolve to the initial admin.
