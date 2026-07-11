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

## Forbidden Patterns

```javascript
String(instant).slice(0, 19);  // displays UTC as if it were local time
globalHandle = value;          // render-time module mutation
const helper = () => useQueryClient(); // Hook hidden behind a non-use name
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

## Review Checklist

- ESLint passes with no errors or warnings.
- Tests and production build pass.
- Hooks have stable names and dependencies.
- Derived state is computed directly unless local editing genuinely requires a draft override.
- Shared constants and formatters remain the single source of truth.
- No unused import, console debug statement, stale comment, or duplicated API contract remains.
