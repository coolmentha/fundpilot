# Database Guidelines

> Database patterns and conventions for this project.

---

## Overview

PostgreSQL schema changes are managed by ordered Flyway migrations. Production uses
Hibernate schema validation; runtime DDL generation must not own business tables.

---

## Query Patterns

- Fetch remote data outside database transactions, then persist it in a short transaction.
- For full-catalog synchronization, load existing rows in one query and use `saveAll`; do not issue
  one lookup query per source row.
- Tables with `deleted_date` use active-row unique indexes and Hibernate `@SQLRestriction`.
- User-owned business keys include `owner_id` in their active-row unique index. For example,
  active fund-group names are unique by `owner_id + lower(name)`, not globally.

---

## Migrations

- Use expand/migrate/contract. Add and backfill new ownership structures before deleting legacy data.
- Preserve conflicts in a reconciliation table when multiple legacy rows disagree. A deterministic
  selection rule does not replace conflict reporting.
- End a backfill migration with executable orphan/missing-reference checks that raise an exception.
- Before replacing a global unique index with an owner-scoped index, fail on active rows without an
  owner and on duplicate owner/business-key pairs; never silently merge them.
- Flyway migrations only move forward. Application rollback must remain compatible with expanded schema.

---

## Naming Conventions

- Tables and columns use lowercase snake case.
- Unique indexes use `uq_<table>_<business-key>`; ordinary indexes use `idx_<table>_<column>`.
- Foreign keys use `fk_<referenced-business-owner>` when the meaning is unambiguous.

---

## Common Mistakes

- Do not drop `fund` product columns or `fund_dict` during ProductCatalog expansion. They remain the
  rollback and reconciliation source until a separately approved contract migration.
- Do not hold a transaction while fetching the Eastmoney catalog or fee HTML.
- Do not silently choose one user's legacy product facts without writing the disagreement to
  `fund_product_migration_conflict`.
