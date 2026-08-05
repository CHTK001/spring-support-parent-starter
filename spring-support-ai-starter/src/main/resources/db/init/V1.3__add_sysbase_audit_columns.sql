-- ============================================================
-- 给 SysBase 子类表补充标准审计字段（create_name/create_by/create_time/update_time/update_name/update_by）
-- 说明：由 spring-support-datasource-starter 的 Flyway-like 迁移器扫描执行。
-- AiProvider / AiAppKey 继承 SysBase，MyBatis-Plus 生成的 SQL 会带上审计列，
-- 而 V1.2 建表未包含这些列，导致 Unknown column 查询失败。
-- ============================================================

-- 1. ai_provider 补充审计字段
ALTER TABLE `ai_provider`
    ADD COLUMN `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名' AFTER `provider_deleted`,
    ADD COLUMN `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER `create_name`,
    ADD COLUMN `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `create_by`,
    ADD COLUMN `update_time` DATETIME DEFAULT NULL COMMENT '更新时间' AFTER `create_time`,
    ADD COLUMN `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名' AFTER `update_time`,
    ADD COLUMN `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID' AFTER `update_name`;

-- 2. ai_app_key 补充审计字段
ALTER TABLE `ai_app_key`
    ADD COLUMN `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人姓名' AFTER `app_key_deleted`,
    ADD COLUMN `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID' AFTER `create_name`,
    ADD COLUMN `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `create_by`,
    ADD COLUMN `update_time` DATETIME DEFAULT NULL COMMENT '更新时间' AFTER `create_time`,
    ADD COLUMN `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人姓名' AFTER `update_time`,
    ADD COLUMN `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID' AFTER `update_name`;
