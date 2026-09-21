-- 价格提醒邮件收件人邮箱:用户登录后在 /settings 自助维护,留空表示不接收提醒邮件。
ALTER TABLE site_user ADD COLUMN email VARCHAR(255);

-- 同一邮箱只允许绑定一个未删除账号;邮箱不区分大小写。
CREATE UNIQUE INDEX uq_site_user_email ON site_user (LOWER(email))
    WHERE deleted_date IS NULL AND email IS NOT NULL;
