-- V12__data_source.sql
-- 需求数据源配置表（支持飞书多维表格、Jira、Notion、通用 REST API 等）

CREATE TABLE IF NOT EXISTS `sys_ai_data_source` (
    `sys_ai_data_source_id` VARCHAR(36) NOT NULL COMMENT '数据源 ID（UUIDv7）',
    `sys_ai_data_source_name` VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `sys_ai_data_source_type` VARCHAR(64) NOT NULL COMMENT '类型: lark/jira/notion/generic',
    `sys_ai_data_source_config` TEXT NULL COMMENT '配置 JSON（app_id, app_secret, table_id, api_url 等）',
    `sys_ai_data_source_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_data_source_sync_cron` VARCHAR(64) NULL COMMENT '同步 cron 表达式',
    `sys_ai_data_source_last_sync_at` BIGINT NULL COMMENT '上次同步时间戳',
    `sys_ai_data_source_last_sync_status` VARCHAR(32) NULL COMMENT '上次同步状态: success/failed',
    `sys_ai_data_source_last_sync_error` VARCHAR(512) NULL COMMENT '上次同步错误信息',
    `create_name` VARCHAR(64) NULL,
    `create_by` INT NULL,
    `create_time` BIGINT NULL COMMENT '创建时间戳（epoch ms）',
    `update_name` VARCHAR(64) NULL,
    `update_by` INT NULL,
    `update_time` BIGINT NULL COMMENT '更新时间戳（epoch ms）',
    PRIMARY KEY (`sys_ai_data_source_id`),
    KEY `idx_sys_ai_ds_type` (`sys_ai_data_source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求数据源提供方配置';
