-- ============================================================
-- 新增 AppKey Token 总量配额字段
-- 包含：sys_ai_app_key_max_tokens（Token 上限）
--       sys_ai_app_key_total_tokens（已用 Token 累计）
-- ============================================================

-- 新增 Token 总量上限字段（null 表示不限）
ALTER TABLE `sys_ai_app_key`
    ADD COLUMN `sys_ai_app_key_max_tokens` BIGINT DEFAULT NULL
    COMMENT 'Token 总量上限（null 表示不限）'
    AFTER `sys_ai_app_key_max_use`;

-- 新增已使用 Token 累计值字段
ALTER TABLE `sys_ai_app_key`
    ADD COLUMN `sys_ai_app_key_total_tokens` BIGINT DEFAULT 0
    COMMENT '已使用 Token 累计值'
    AFTER `sys_ai_app_key_max_tokens`;