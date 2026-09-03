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

-- ---------------------------------------------------------------------
-- 2. 告警维护窗口表（此前所有 schema 脚本均未建此表, 实体已引用导致告警触发时
--    "Table ms_monitor_alarm_maintenance_window not found", 此处补齐）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_maintenance_window (
    id                                           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_maintenance_window_policy_id BIGINT        NULL                  COMMENT '策略ID, 空为全局',
    ms_monitor_alarm_maintenance_window_name     VARCHAR(64)   NULL                  COMMENT '窗口名称',
    ms_monitor_alarm_maintenance_window_level    CHAR(2)       NULL                  COMMENT '级别: P0~P4/空为全局',
    ms_monitor_alarm_maintenance_window_start_time VARCHAR(8)  NULL                  COMMENT '开始时间 HH:mm',
    ms_monitor_alarm_maintenance_window_end_time   VARCHAR(8)  NULL                  COMMENT '结束时间 HH:mm',
    ms_monitor_alarm_maintenance_window_weekdays VARCHAR(32)   NULL                  COMMENT '生效星期, 逗号分隔, 空为每天',
    ms_monitor_alarm_maintenance_window_enabled  TINYINT       NOT NULL DEFAULT 1    COMMENT '启用 1启用 0停用',
    ms_monitor_alarm_maintenance_window_remark   VARCHAR(255)  NULL                  COMMENT '备注',
    ms_monitor_alarm_maintenance_window_create_time DATETIME    NULL                  COMMENT '创建时间',
    ms_monitor_alarm_maintenance_window_update_time DATETIME    NULL                  COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_alarm_mw_enabled (ms_monitor_alarm_maintenance_window_enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警维护窗口';
