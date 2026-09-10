-- =====================================================================
-- 服务器资产模块增量 DDL：主机延迟检测配置字段
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明: 为历史库 ms_server_host 补充延迟检测字段（新库由 V1.0 全量包含）
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- 延迟检测开关(主机级)
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_delay_enabled INT NULL DEFAULT 0 COMMENT '延迟检测开关(主机级)';

-- 延迟检测间隔秒(主机级)
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_delay_interval INT NULL DEFAULT 60 COMMENT '延迟检测间隔秒(主机级)';

-- 最近延迟毫秒(检测结果)
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_delay_ms BIGINT NULL COMMENT '最近延迟毫秒(检测结果)';

-- 最近延迟检测时间(主机级)
ALTER TABLE ms_server_host ADD COLUMN IF NOT EXISTS ms_server_host_last_delay_time DATETIME NULL COMMENT '最近延迟检测时间(主机级)';