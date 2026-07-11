# ADR-0019: Unit NAV for accounting, accumulated NAV for analysis

## Status

Accepted.

## Decision

Real transactions, shares, fees, lots, holding market value and total PnL use
`fund_nav_history.nav` (unit NAV). `accumulated_nav` is reserved for adjusted
return analysis such as daily change, drawdown, moving highs and MACD.

Purchase shares are `(amount - fee) / unitNav`, while purchase cost is the full
cash contribution: `amount / shares`. Lot acquisition and redemption holding
periods use the business `tradeDate`, not the later confirmation timestamp.

V17 schedules a one-time transactional replay of all active CONFIRMED
transactions. The replay preserves onboarding cost evidence and historical
redemption-rate evidence, rebuilds FIFO lots and redemption details, and resets
take-profit runtime cycles without changing user strategy parameters or signal
history. Any failure rolls back the entire replay and prevents normal startup.

## Consequences

- Accounting values remain reconcilable with fund-company transaction records.
- Adjusted analysis remains continuous across dividend distributions.
- DCA creation is protected by a Beijing-calendar-day database unique index.
- Deployments upgrading existing data require a verified database backup.
