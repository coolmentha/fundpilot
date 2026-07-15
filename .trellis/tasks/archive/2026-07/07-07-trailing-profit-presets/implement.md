# Implementation Plan

## Preconditions

- Work on `feature/trailing-profit-presets`.
- Base branch is `main`.
- `main` contains `v0.5.14`.
- Do not push or commit without user confirmation.

## Checklist

- [ ] Add `TrailingProfitMode` enum.
- [ ] Extend `FundStrategyEntity` with mode and four-tier fields.
- [ ] Extend `StrategyConfigRequest` and `FundStrategyView`.
- [ ] Add `V15__add_trailing_profit_tiers.sql`.
- [ ] Implement backend preset generation and custom validation in `StrategyConfigService`.
- [ ] Update `DisciplineStrategyService` to use configured tiers before legacy fallback.
- [ ] Update `StrategyFormModal` from raw negative-number input to mode + tiers.
- [ ] Update `FundStrategyTab` active strategy and table display.
- [ ] Add backend tests for configured tiers and legacy fallback.
- [ ] Add service tests for preset generation and custom validation.

## Validation

- `git diff --check`
- `cd backend; $env:JAVA_HOME='C:\Users\kpy\.jdks\ms-25.0.3'; ./mvnw -B -DskipTests test-compile`
- `cd backend; $env:JAVA_HOME='C:\Users\kpy\.jdks\ms-25.0.3'; ./mvnw -B -Dtest=DisciplineStrategyServiceTest test`
- `cd backend; $env:JAVA_HOME='C:\Users\kpy\.jdks\ms-25.0.3'; ./mvnw -B -Dtest=StrategyConfigServiceTest test`
- `cd frontend; npm run build`

## Known Local Environment Constraints

- Backend requires JDK 25; local default Java may be 21.
- `StrategyConfigServiceTest` needs Postgres at `localhost:5432`; CI provides this through the GitHub Actions service
  container.
- Frontend build may emit an existing large chunk warning.

## Review Gates

- Confirm generated tiers match PRD preset values.
- Confirm custom validation happens server-side.
- Confirm frontend does not send tier values for preset modes.
- Confirm old `stopLossPullbackPercent` remains as compatibility fallback.
- Confirm no unrelated generated artifacts are included.

## Rollback Points

- Before database migration reaches production, full revert is straightforward.
- After database migration, prefer forward fix; dropping columns requires explicit approval.
