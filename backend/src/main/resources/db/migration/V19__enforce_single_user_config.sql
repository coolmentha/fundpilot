-- 单用户部署只允许一条未软删配置。保留最近更新的一条，历史重复行软删后建立部分唯一索引。
WITH ranked AS (
    SELECT id,
           row_number() OVER (ORDER BY updated_date DESC NULLS LAST, id DESC) AS row_number
    FROM user_config
    WHERE deleted_date IS NULL
)
UPDATE user_config
SET deleted_date = now(), updated_date = now()
WHERE id IN (SELECT id FROM ranked WHERE row_number > 1);

CREATE UNIQUE INDEX uq_user_config_singleton
    ON user_config ((true))
    WHERE deleted_date IS NULL;
