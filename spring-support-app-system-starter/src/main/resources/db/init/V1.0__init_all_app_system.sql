-- ============================================================
-- 业务系统管理模块 DDL 初始化脚本 (spring-support-app-system-starter)
-- 适用数据库: MySQL 8.0+ / H2 (MODE=MySQL)
-- 说明: 建表语句幂等 (IF NOT EXISTS)，可直接重复执行；表名前缀 ms_app_system_*。
-- @author CH
-- @since 4.0.0.42
-- ============================================================

-- 业务系统台账（主机池按系统归属，系统内聚合部署项目）
CREATE TABLE IF NOT EXISTS ms_app_system (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_app_system_name     VARCHAR(128) NOT NULL COMMENT '业务系统名称',
    ms_app_system_code     VARCHAR(64)  NOT NULL COMMENT '业务系统编码',
    ms_app_system_type     VARCHAR(32)  NULL COMMENT '系统类型标签（可多个，逗号分隔）',
    ms_app_system_host_ids TEXT         NULL COMMENT '关联主机 ID 列表（JSON 数组）',
    ms_app_system_enabled  INT          NULL DEFAULT 1 COMMENT '是否启用（0否 1是）',
    ms_app_system_sort     INT          NULL DEFAULT 0 COMMENT '排序',
    ms_app_system_remark   VARCHAR(512) NULL COMMENT '备注',
    create_by              INT          NULL COMMENT '创建人',
    create_name            VARCHAR(64)  NULL COMMENT '创建人名称',
    create_time            DATETIME     NULL COMMENT '创建时间',
    update_name            VARCHAR(64)  NULL COMMENT '更新人名称',
    update_by              INT          NULL COMMENT '更新人',
    update_time            DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_system_code (ms_app_system_code),
    KEY idx_app_system_name (ms_app_system_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '业务系统台账';
