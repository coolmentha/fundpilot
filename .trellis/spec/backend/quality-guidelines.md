# Backend Quality Guidelines

## Scope

Applies to Java code under `backend/src/main/java` and its tests. The hard rules in
`docs/agents/coding-standards.md` remain authoritative.

## Required Patterns

- Spring `@Service`, `@Component`, and `@RestController` classes use `@RequiredArgsConstructor` with final collaborator fields.
- User-correctable validation failures throw `BusinessException` with an `ErrorCode`; raw runtime exceptions are reserved for internal invariant failures and parser boundaries.
- Business and persistence time values use `Instant`. Convert a China business date through `ChinaTradingDate` and store date labels as UTC 00:00 Instants.
- Every `@Scheduled` method declares `zone = "Asia/Shanghai"` unless the schedule is explicitly UTC and documented as such.
- Controllers only route requests and return View DTOs. Entity creation, repository access, and branching belong in services.
- External-source failures log enough context for diagnosis and either follow the documented degradation contract or throw the source-chain business error.
- Integration tests prepare their own required singleton business configuration. They must not depend on another test class's committed rows or replace explicit negative coverage with a global default fixture.

## Forbidden Patterns

```java
throw new IllegalArgumentException("bad request"); // becomes INTERNAL_ERROR 500
LocalDate today = LocalDate.now();                  // splits the business-date convention
@Scheduled(cron = "0 0 3 * * *")                 // depends on host timezone
```

Use:

```java
throw new BusinessException(ErrorCode.STRATEGY_PARAM_INVALID, "...");
Instant today = ChinaTradingDate.toUtcDate(clock.instant());
@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
```

## Comments And Logging

- Comments explain business intent, date/percentage units, degradation behavior, or non-obvious transaction boundaries.
- When behavior changes, update Javadocs in controllers, services, jobs, DTOs, specs, and tests in the same change.
- Do not leave comments that restate an old cron timezone or an old data-selection rule.
- Do not log credentials, cookies, tokens, or complete external payloads.

## Validation

```powershell
cd backend
mvn test
```

- New business validation requires a regression test asserting the `ErrorCode`.
- Date logic requires boundary tests around UTC/Asia-Shanghai day changes.
- Database changes require a fresh-schema Flyway migration and Hibernate validation run.
- Integration-test fixes for shared database state require a clean-schema run of the affected classes and a full-suite run to detect order dependencies.

## Review Checklist

- Controller/service/repository boundaries remain intact.
- All Spring collaborators use constructor injection.
- Error paths preserve 400 vs 500 semantics.
- Time, ratio, money, shares, and NAV units are explicit.
- Scheduled work is timezone-stable and retry/degradation behavior is documented.
- No debug output, swallowed exception, stale comment, or unrelated formatting change remains.
