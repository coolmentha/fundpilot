# Trailing Profit Presets

## Goal

Improve the sell-side trailing profit strategy so users can choose understandable take-profit presets instead of editing
one raw pullback number. The current version only changes SELL trailing-profit behavior.

## Background

- `main` includes production tag `v0.5.14`; this task targets `main` from branch `feature/trailing-profit-presets`.
- Existing strategy UI exposes `stopLossPullbackPercent` directly, with unclear negative-number input.
- Existing signal logic only emits `NONE` or `SELL`; buy/add logic has already been removed.
- Existing SELL priority is logic stop-loss before trailing profit.

## Confirmed Scope

- Add trailing profit modes:
    - Conservative
    - Standard
    - Loose
    - Custom
- Show and store four trailing-profit tiers.
- Each tier has:
    - pullback percent from holding-period peak NAV
    - sell ratio of current holding shares
- Keep existing SELL priority: logic stop-loss remains before trailing profit.
- Preserve old strategies by deriving four standard tiers from `stopLossPullbackPercent` when new fields are missing.

## Out Of Scope

- Stop-loss tuning or new stop-loss UI.
- Automatic parameter optimization.
- Buy/build/add strategy behavior.
- Changing signal confirmation or transaction execution semantics.
- Changing CI/CD or deployment configuration.

## Version Plan

- This version: trailing profit presets and custom tiers.
- Next version: automatic optimization.
- Following version: stop-loss improvements.

## Requirements

- R1: Users can create or edit a strategy using one of four trailing profit modes.
- R2: Preset modes are generated on the backend, not trusted from frontend-submitted tier values.
- R3: Custom mode accepts four pullback percentages and four sell ratios.
- R4: Custom pullback percentages must be positive and strictly increasing.
- R5: Custom sell ratios must be in `(0, 1]` and strictly increasing.
- R6: Existing strategies without new tier fields must continue to produce the same trailing-profit SELL result as
  before.
- R7: The strategy list and active strategy panel must display the mode and four-tier summary.
- R8: This change must remain compatible with existing `stopLossPullbackPercent` API payloads where possible.

## Acceptance Criteria

- [ ] Creating a standard preset strategy stores four tiers equivalent to 8%, 16%, 24%, 32% pullback and 25%, 50%, 75%,
  100% sell ratios.
- [ ] Conservative preset uses 5%, 10%, 15%, 20% pullback and 25%, 50%, 75%, 100% sell ratios.
- [ ] Loose preset uses 10%, 20%, 30%, 40% pullback and 25%, 50%, 75%, 100% sell ratios.
- [ ] Custom mode rejects missing, non-positive, or non-increasing tier values.
- [ ] Signal engine selects the deepest matching tier and sells `holdingShares * tierSellRatio`.
- [ ] When tier fields are absent, signal engine falls back to old `stopLossPullbackPercent` logic.
- [ ] Frontend build succeeds.
- [ ] Backend compile succeeds, and trailing-profit signal tests pass.
- [ ] Service integration tests are expected to run in CI with Postgres service container.

## Risks

- Database migration touches `fund_strategy`; rollback requires dropping new trailing-profit columns.
- Cross-layer field additions must stay aligned between entity, request DTO, view DTO, frontend form, and display.
- Preset constants duplicated in frontend preview and backend generation can drift; backend remains the source of truth.

## Open Questions

- None blocking for this confirmed version.
