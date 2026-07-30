# Slice 2 Result: IdentityAccess

## Outcome

IdentityAccess now owns `site_user`, password hashing, signed sessions, authentication,
roles, user administration, and the current actor context. The database table and stored
password/session semantics remain compatible; no schema migration was added.

## Boundaries

- Web authentication resolves a real enabled user and opens a thread-bound actor scope.
- Web user administration calls Application command/query handlers directly.
- Other contexts use the named `adapter.api` contracts only.
- `CurrentActorApi` replaces the legacy HTTP-aware `CurrentUserService`.
- Background portfolio snapshots enumerate enabled users and run once per user through
  `runAsSystem(userId, ...)`.
- There is no implicit global identity. Actor and session user IDs must be positive, and
  absence of an actor fails closed.
- The legacy admin key is accepted only when it maps to a real enabled ADMIN user. It never
  creates a synthetic user `0`.

## Removed Legacy Code

The old authentication filter, session service, security properties/configuration,
authentication/user controllers, site-user JPA model/repository, password service,
admin-user service, and current-user service were removed after consumers and tests were
migrated.

## Compatibility

- Database: compatible; `site_user` is still mapped without schema changes.
- REST: current paths were retained for this slice, but REST/JSON compatibility is not a
  migration guarantee.
- Authorization: an ADMIN using an ordinary endpoint is owner-scoped exactly like a USER.
  Cross-user user management remains behind `/api/admin/**` and Handler role checks.

## Verification

Passed:

```powershell
cd backend
.\mvnw.cmd -DskipTests test-compile
.\mvnw.cmd '-Dtest=SpringModulithStructureTest,DddLayerArchitectureTest,AuthenticationFilterTest,UserAdministrationCommandHandlerTest,ThreadLocalActorContextTest,DcaBudgetSummaryServiceUnitTest,FundPnlServiceDateTest,PortfolioReturnTrendServiceTest,YangjibaoImportServiceTest' test
```

The combined targeted run passed 29 tests with zero failures. `git diff --check` passed.
Searches found no remaining legacy identity references, `userId == 0` authorization
branches, or new-module dependencies on legacy identity internals.

Full-suite attempt:

```powershell
.\mvnw.cmd test
```

The run executed 580 tests and reported 222 errors after
`TestDatabaseSchema.resetOnce` could not connect to PostgreSQL at `localhost:5432`.
The first cause was `Connection refused`; subsequent integration tests inherited the same
failed static initialization. Database-backed authentication and multi-user isolation tests
are compiled but cannot run until the test database is available.

## Data And Rollback

No data migration or reconciliation was required because the existing table, columns, role
names, PBKDF2 format, and session signing shape are retained. Before a later schema contract
migration, run the database-backed authentication and isolation tests against a fresh schema.

Rollback at this stop point is code-only: restore the deleted legacy authentication/user
classes and switch consumers back from `CurrentActorApi`. No database rollback is needed.

## Remaining Risk

- Database-backed startup, login, cookie, and multi-user isolation require PostgreSQL to
  complete runtime verification.
- `InitialAdminInitializer` remains a legacy cross-module data-claim orchestrator. It now
  creates/loads the user only through `UserAdministrationApi`; ownership claims move with
  their respective modules in later slices.
