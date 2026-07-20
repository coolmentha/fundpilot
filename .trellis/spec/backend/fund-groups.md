# Fund Groups

## 1. Scope / Trigger

Applies when creating, updating, listing, ordering, renaming, or deleting user-defined fund groups.
Groups are presentation metadata only and must never affect holdings, P&L, transactions, signals, or strategies.

## 2. Signatures

- `GET /api/fund-groups -> ApiResponse<List<FundGroupView>>`
- `PUT /api/fund-groups` with `{ "groups": [{ "id": number|null, "name": string }] }`
- `FundCreateRequest.groupNames: List<String>|null`
- `FundView.groups: List<{ id: number, name: string }>`
- Tables: `fund_group`, `fund_group_member`

## 3. Contracts

- A fund may belong to zero or many groups.
- `PUT /api/fund-groups` is a complete, ordered snapshot saved in one transaction.
- Existing IDs update groups; null IDs create groups; omitted existing IDs delete groups.
- Deleting a group removes memberships only. It never deletes or archives funds.
- `groupNames == null` preserves memberships on update; an explicit empty list clears memberships.
- Names are trimmed, 1-20 characters, free of control characters, and unique case-insensitively.

## 4. Validation & Error Matrix

- Missing batch `groups` field -> `FUND_GROUP_NAME_INVALID`
- Empty, overlong, or control-character name -> `FUND_GROUP_NAME_INVALID`
- Duplicate normalized name -> `FUND_GROUP_NAME_DUPLICATE`
- Unknown or duplicate submitted ID -> `FUND_GROUP_NOT_FOUND`

## 5. Good / Base / Bad Cases

- Good: one fund uses `["核心", "QDII"]`; both groups are returned in saved order.
- Base: `groupNames: []` leaves the fund valid and visible under the UI's “全部” tab.
- Bad: `["QDII", "qdii"]` is rejected without partial writes.

## 6. Tests Required

- Flyway fresh-schema migration and Hibernate validation.
- Integration tests for multi-group assignment, reorder persistence, normalized duplicate rejection, and delete-with-fund-preserved.
- Frontend tests for “全部”, single-group filtering, and multi-group membership.

## 7. Wrong vs Correct

### Wrong

Delete funds when a group is deleted, or treat a missing batch field as an empty list.

### Correct

Delete only `fund_group_member` associations and reject malformed batch requests before any write.
