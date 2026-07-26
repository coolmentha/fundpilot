-- Fund group names are unique within an owner, not globally across all users.
DO $$
DECLARE
    missing_owner_count BIGINT;
    duplicate_name_count BIGINT;
BEGIN
    SELECT count(*) INTO missing_owner_count
    FROM fund_group
    WHERE deleted_date IS NULL AND owner_id IS NULL;

    SELECT count(*) INTO duplicate_name_count
    FROM (
        SELECT owner_id, lower(name)
        FROM fund_group
        WHERE deleted_date IS NULL AND owner_id IS NOT NULL
        GROUP BY owner_id, lower(name)
        HAVING count(*) > 1
    ) duplicates;

    IF missing_owner_count > 0 OR duplicate_name_count > 0 THEN
        RAISE EXCEPTION
            'fund_group owner-scoped uniqueness blocked: active missing owner_id=%, duplicate owner/name=%',
            missing_owner_count, duplicate_name_count;
    END IF;
END $$;

DROP INDEX uq_fund_group_name;

CREATE UNIQUE INDEX uq_fund_group_owner_name
    ON fund_group (owner_id, lower(name))
    WHERE deleted_date IS NULL;
