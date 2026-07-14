# ADR-0020: Capital pool as a portfolio risk budget

## Status

Superseded by [ADR-0021](0021-dca-budget-and-position-warnings.md).

## Decision

External deposits only increase the singleton user's `totalCapital`. They do
not belong to a fund and do not create a transaction. The value is a stable
portfolio risk-budget denominator, not a cash balance that purchases consume.

Each fund stores `maxPositionRatio`, defaulting to 30%. Users may lower the
ratio, but both the database and service layer reject values outside `(0, 30%]`.
Every purchase confirmation, including DCA, transfers into a fund and existing
position onboarding, locks the fund row and verifies:

`current confirmed holding value + purchase amount <= totalCapital * maxPositionRatio`

Holding value uses unit NAV. V20 preserves existing holdings and does not infer
historical total capital. Until the first external deposit is recorded, new
purchase confirmations are rejected while sales remain available.

## Consequences

- Concurrent confirmations for the same fund serialize on the fund row.
- A lower per-fund ratio can make a current holding temporarily over target;
  it blocks further purchases but does not rewrite or liquidate history.
- The capital pool restores portfolio risk control only. It does not restore
  retired pyramid-add or automatic allocation behavior.
