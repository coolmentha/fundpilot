# Frontend Quality Guidelines

## Scope

Applies to JavaScript and JSX under `frontend/src`.

## Required Patterns

- Server state is accessed through the hooks in `src/api/hooks.js`; components do not call `fetch` directly.
- Functions that call React Hooks start with `use` and obey the Hooks rules.
- Backend Instant values are formatted through shared helpers in `src/constants.js`, with `Asia/Shanghai` specified explicitly.
- Null or invalid display values render as `-` rather than fake zeroes or invalid dates.
- API error codes are added to the shared `errorTitles` mapping when the backend introduces a user-facing `ErrorCode`.
- JSX imports, callbacks, and derived state must pass ESLint without disabled core or Hooks rules.
- Top-level route pages are loaded with `React.lazy` under one `React.Suspense` boundary. Keep the shared shell eager so navigation and authentication layout stay stable.
- Theme-aware custom styles use the `--color-*` variables in `src/styles.css`; Ant Design theme tokens stay centralized in `src/main.jsx`. Do not add hard-coded dark backgrounds to page components.

## Chart Rendering Contract

- Fund market charts use the shared ECharts lifecycle helpers in the chart components; every initialized instance must register resize handling and be disposed on unmount or chart-type replacement.
- `FundIntradayChart` consumes the existing `baseNav`, `points`, and `tradingSessions` payload. Future category slots remain `null`, lunch breaks do not become categories, and percentage mode sets explicit symmetric bounds around zero instead of relying on a library default percentage axis.
- K-line data is passed to a candlestick series as `[open, close, low, high]`. MA, VOL, and MACD/DIF/DEA are derived in one frontend calculation module so toolbar and tooltip values use the same result.
- K-line data remains fully available, but the initial viewport shows a period-specific recent window (daily 120 bars, weekly 104 bars, monthly 60 bars); users can still zoom to older data.
- Chart options must use theme-aware colors and keep the existing loading, error, empty-data, and NAV fallback states. Do not add direct external market-data requests from chart components.

### Chart Tests

- Component tests inspect `setOption` output rather than chart-library internals. Cover symmetric positive/negative bounds, all-positive values, future `null` slots, skipped lunch categories, candlestick ordering, indicator switching, NAV fallback, and instance disposal.
- Pure indicator helpers cover normal, warm-up, zero, and invalid-value boundaries.

## Forbidden Patterns

```javascript
String(instant).slice(0, 19);  // displays UTC as if it were local time
globalHandle = value;          // render-time module mutation
const helper = () => useQueryClient(); // Hook hidden behind a non-use name
.panel { background: #0f172a; } // breaks light mode; use var(--color-surface)
```

Use shared formatting, component-local closures, and `use*` custom Hook names instead.

## Comments

- Comments describe API polling, cache invalidation, financial units, and unusual interaction constraints.
- Do not keep references to removed fields, old routes, old timezones, or outdated backend behavior.
- Avoid comments that merely narrate visible JSX.

## Validation

```powershell
cd frontend
npm run lint
npm test
npm run build
```

- Pure utilities require Vitest coverage for normal, boundary, null, and invalid inputs.
- User-visible date/time changes require an assertion with a fixed UTC timestamp and expected Shanghai output.
- Build warnings must be reported; do not silence them without addressing or documenting the reason.
- Route changes require a Vitest navigation check that renders the lazy page, follows at least one parameterized route when applicable, and verifies the unknown-route redirect.

```javascript
const FundsPage = React.lazy(() => import('./pages/FundsPage.jsx'));

<React.Suspense fallback={<div role="status">加载中...</div>}>
    <Routes>{/* route pages */}</Routes>
</React.Suspense>
```

Do not raise `chunkSizeWarningLimit` only to hide a bundle warning. Report the remaining shared chunk and split its ownership module when the initial-load cost justifies that larger refactor.

## Review Checklist

- ESLint passes with no errors or warnings.
- Tests and production build pass.
- Hooks have stable names and dependencies.
- Derived state is computed directly unless local editing genuinely requires a draft override.
- Shared constants and formatters remain the single source of truth.
- No unused import, console debug statement, stale comment, or duplicated API contract remains.
