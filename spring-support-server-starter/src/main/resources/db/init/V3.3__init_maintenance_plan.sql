-- ============================================================
-- 维护计划：服务器运维窗口台账
-- 由 MaintenancePlanService 读写，只保存计划，不负责调度执行。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_maintenance_plan` (
    `id`                                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_maintenance_plan_name`              VARCHAR(128) NOT NULL COMMENT '计划名称',
    `ms_maintenance_plan_type`              VARCHAR(32)  NOT NULL DEFAULT 'MAINTENANCE' COMMENT '计划类型 MAINTENANCE/UPGRADE/BACKUP/REBOOT',
    `ms_maintenance_plan_start_time`        DATETIME     NOT NULL COMMENT '开始时间',
    `ms_maintenance_plan_end_time`          DATETIME     NOT NULL COMMENT '结束时间',
    `ms_maintenance_plan_target_host_ids`   VARCHAR(1024) DEFAULT NULL COMMENT '目标主机 ID JSON 数组',
    `ms_maintenance_plan_status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '计划状态 PENDING/RUNNING/COMPLETED/CANCELLED',
    `ms_maintenance_plan_description`       VARCHAR(512) DEFAULT NULL COMMENT '计划说明',
    `create_name`                           VARCHAR(64)  DEFAULT NULL COMMENT '创建人姓名',
    `create_by`                             INT          DEFAULT NULL COMMENT '创建人',
    `create_time`                           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                           VARCHAR(64)  DEFAULT NULL COMMENT '更新人姓名',
    `update_by`                             INT          DEFAULT NULL COMMENT '更新人',
    `update_time`                           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_maintenance_plan_status` (`ms_maintenance_plan_status`),
    KEY `idx_ms_maintenance_plan_window` (`ms_maintenance_plan_start_time`, `ms_maintenance_plan_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维护计划';
