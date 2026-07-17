# Existing Position Onboarding

## 1. Scope / Trigger

Applies when `POST /api/funds` creates a fund with an existing confirmed position.

## 2. Signatures

`FundCreateRequest` uses:

```text
initialHoldingShares: BigDecimal?
costPerShare: BigDecimal?
openedAt: Instant?
```

## 3. Contracts

- `initialHoldingShares = null`: create a `PENDING_HOLDING` fund without a transaction.
- `initialHoldingShares > 0`: create an `INCREASE / CONFIRMED` transaction using the shares unchanged.
- Transaction `nav` is the latest confirmed unit NAV.
- Transaction `amount = initialHoldingShares * nav`; amount is accounting market value, not user cost.
- `costPerShare` preserves the user's cost basis; when absent, it defaults to the same confirmed unit NAV.
- Current market value remains derived from confirmed shares and current NAV/estimate.

## 4. Validation & Error Matrix

| Condition | Result |
| --- | --- |
| Shares are null | Observation fund, no position transaction |
| Shares are zero or negative | `INITIAL_HOLDING_SHARES_INVALID` |
| Cost per share is zero or negative | `COST_PER_SHARE_INVALID` |
| Confirmed unit NAV is unavailable | `NAV_HISTORY_EMPTY`, create transaction rolls back |
| `openedAt` is in the future | `OPENED_AT_IN_FUTURE` |

## 5. Good / Base / Bad Cases

- Good: `50.85` shares at NAV `8.0647` creates amount `410.089995` and keeps exactly `50.85` shares.
- Base: no shares creates an empty observed fund.
- Bad: accepting a market value and dividing by NAV silently changes the user's factual shares.

## 6. Tests Required

- Integration: assert transaction shares and lot shares equal the request exactly.
- Integration: assert transaction amount equals shares multiplied by confirmed unit NAV.
- Integration: assert custom and default cost-per-share behavior.
- Integration: assert invalid shares, missing NAV, future date, and fetch failure behavior.
- Frontend: test/lint/build must confirm only `initialHoldingShares` is submitted.

## 7. Wrong vs Correct

Wrong:

```java
shares = initialMarketValue.divide(nav);
```

Correct:

```java
shares = initialHoldingShares;
amount = initialHoldingShares.multiply(nav);
```
