-- =====================================================================
-- 监控模块增量迁移: 资产(主机台账)↔监控项 联动列 (spring-support-monitor-starter)
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- 说明: MsMonitor.msServerHostId 实体字段持久化到 ms_server_host_id,
--       但此前所有 schema 脚本都未建该列, 导致资产→监控项关联读写报"列不存在"。
--       本迁移补齐该列, 使"资产 → 监控项 → 告警"链路可落库。
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. ms_monitor 追加关联服务器主机 ID 列(ms_server_host.id)
-- ---------------------------------------------------------------------
ALTER TABLE ms_monitor ADD COLUMN IF NOT EXISTS ms_server_host_id BIGINT NULL COMMENT '关联服务器主机 ID(ms_server_host.id, 资产联动)';

-- 便于按资产查询监控项
CREATE INDEX IF NOT EXISTS idx_ms_monitor_server_host ON ms_monitor (ms_server_host_id);
