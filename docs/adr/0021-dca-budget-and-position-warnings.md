# ADR-0021: DCA budget and position warnings replace hard capital limits

## Status

Accepted. Supersedes ADR-0020.

## Context

The V20 capital pool and per-fund limit were implemented as purchase-confirmation
guards. They made a configured cash-flow preference block `INCREASE`, `INVEST`,
`TRANSFER_IN`, existing-position onboarding, and even a transfer-in leg that
otherwise had all required NAV data. The product need is visibility into monthly
DCA cash flow and concentration, not an automated trading veto.

## Decision

- Remove `user_config.total_capital`, external-deposit APIs, `PositionLimitService`,
  and all purchase-confirmation checks derived from them.
- Store an optional `user_config.monthly_dca_budget`. It is a direct replacement
  value, not an accumulating cash balance. A null value keeps monthly DCA amounts
  visible without a progress or over-budget state.
- Expose `GET /api/dca/budget-summary`. It counts all non-cancelled `INVEST`
  transactions in the Shanghai calendar month and predicts only enabled,
  effective DCA plans on their remaining actual execution dates. The shared DCA
  schedule rules own the 14:55 and cross-month deferral semantics.
- Rename each fund's old limit to `position_warning_ratio`, preserve existing
  values, and add `position_warning_enabled`. The default warning is enabled at
  30%, accepts 1% through 100%, and compares only current confirmed holding
  market value with total current holding market value.
- Budget and concentration are display-only. They must never throw a business
  error, pause a plan, or affect buying, transfers, onboarding, NAV confirmation,
  lot accounting, or status reconciliation.

## Consequences

- Users set a monthly budget deliberately; historical capital is not inferred.
- A budget overage is rendered as a progress/text warning and transactions still
  proceed normally.
- Existing per-fund thresholds survive the migration as warning preferences.
- Reverting requires a later forward migration; Flyway does not roll back V22.
