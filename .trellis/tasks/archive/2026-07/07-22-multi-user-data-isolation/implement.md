# Implementation Checklist

1. Add user entity, role enum, repository, password hashing and session identity.
2. Add Flyway migration for users, ownership columns, shared fund product normalization, and legacy backfill.
3. Add authenticated-user scope helpers and update repositories/services for private data isolation.
4. Protect admin APIs and add user management endpoints.
5. Return current-user role in auth verification; conditionally render admin navigation and route guard.
6. Add backend isolation/auth/admin tests and frontend role-navigation tests.
7. Run backend tests, frontend tests, lint and build; inspect migration validation.
