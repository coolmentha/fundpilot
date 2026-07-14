# Design

## Architecture

The change spans database, backend strategy configuration, backend signal evaluation, and frontend strategy management.

Data flow:

```text
StrategyFormModal
  -> StrategyConfigRequest
  -> StrategyConfigService validation / preset generation
  -> FundStrategyEntity / fund_strategy
  -> FundStrategyView
  -> FundStrategyTab display
  -> DisciplineStrategyService signal evaluation
```

## Data Model

Keep the legacy field:

- `stopLossPullbackPercent`

Add new fields:

- `trailingProfitMode`
- `trailingTier1PullbackPercent`
- `trailingTier2PullbackPercent`
- `trailingTier3PullbackPercent`
- `trailingTier4PullbackPercent`
- `trailingTier1SellRatio`
- `trailingTier2SellRatio`
- `trailingTier3SellRatio`
- `trailingTier4SellRatio`

Add enum:

- `TrailingProfitMode`: `CONSERVATIVE`, `STANDARD`, `LOOSE`, `CUSTOM`

## Migration

Create `V15__add_trailing_profit_tiers.sql`.

For existing rows:

- Set mode to `STANDARD`.
- Use `ABS(COALESCE(stop_loss_pullback_percent, 0.08))` as tier 1 pullback.
- Derive tier 2 through tier 4 by multiplying tier 1 by 2, 3, 4.
- Backfill sell ratios to 0.25, 0.50, 0.75, 1.00.

## Backend Contract

`StrategyConfigRequest` accepts both:

- legacy `stopLossPullbackPercent`
- new mode and tier fields

Backend rules:

- If only legacy field is provided, derive standard tiers from it.
- If a preset mode is provided, ignore submitted tier values and generate tiers server-side.
- If `CUSTOM` is provided, validate all tier fields.
- Keep `stopLossPullbackPercent` synced to tier 1 for compatibility.

`FundStrategyView` returns both legacy and new fields so old display/fallback paths remain possible.

## Signal Logic

`DisciplineStrategyService` keeps SELL priority:

1. Logic stop-loss
2. Trailing profit

Trailing profit:

- Compute `pullback = (holdingPeriodPeakNav - currentNav) / holdingPeriodPeakNav`.
- Match deepest configured tier where `pullback >= tierPullbackPercent`.
- Sell `holdingShares * tierSellRatio`.
- If no configured tier is available, fall back to old `stopLossPullbackPercent` four-step logic.

## Frontend

`StrategyFormModal`:

- Use segmented mode control.
- Preset modes show disabled tier preview.
- Custom mode enables tier inputs.
- Submit only mode for preset strategies; backend generates exact values.

`FundStrategyTab`:

- Display mode label.
- Display four-tier summary as `pullback / sell ratio`.
- Display legacy fallback when tier fields are absent.

## Compatibility

- Existing rows are backfilled.
- Existing API clients that still submit `stopLossPullbackPercent` continue to work.
- Existing signal behavior remains available via fallback.

## Rollback

Rollback shape:

- Revert frontend form/display changes.
- Revert backend entity/DTO/service/signal changes.
- Drop V15 columns if migration has been applied in a disposable environment.

Production rollback after migration should prefer a forward fix rather than destructive column drops unless explicitly
approved.
