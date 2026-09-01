-- =====================================================================
-- 服务器资产主机级系统配置字段 (spring-support-server-starter)
-- 在离线检测 / 采集 / Agent / 远控 每台主机独立配置
-- 兼容 MySQL 8.0+ / H2 MySQL 模式
-- 依赖 plugin.datasource.script.continue-on-error=true 容忍重复列
-- @since 4.0.0.42
-- =====================================================================

ALTER TABLE ms_server_host ADD COLUMN ms_server_host_online_enabled    INT NULL DEFAULT 1 COMMENT '在离线检测开关(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_online_interval   INT NULL DEFAULT 60 COMMENT '在离线检测间隔秒(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_collect_enabled   INT NULL DEFAULT 0 COMMENT '硬件采集开关(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_collect_mode      VARCHAR(16) NULL DEFAULT 'SCHEDULED' COMMENT '采集方式(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_collect_interval  INT NULL DEFAULT 3600 COMMENT '采集间隔秒(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_last_collect_time DATETIME NULL COMMENT '最近采集时间(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_agent_enabled     INT NULL DEFAULT 0 COMMENT 'Agent上报开关(主机级)';
ALTER TABLE ms_server_host ADD COLUMN ms_server_host_remote_enabled    INT NULL DEFAULT 1 COMMENT '远控开关(主机级)';
