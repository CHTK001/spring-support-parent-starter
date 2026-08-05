-- ============================================================
-- 监控模块初始化 SQL
-- 模块: spring-support-monitor-starter
-- 命名规范: 表统一 ms_monitor_ 前缀
-- 数据库: MySQL 8.x / H2 (MySQL 模式) / 达梦 / Oracle 通过方言转换
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------
-- 1. 资产主表(主机视图)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_name`          VARCHAR(128)    NOT NULL                COMMENT '名称',
    `monitor_host`          VARCHAR(256)    NOT NULL                COMMENT '主机地址 / IP',
    `monitor_port`          INT             DEFAULT 22              COMMENT '连接端口(SSH 22 / Redis 6379 / MySQL 3306 等)',
    `service_type`          VARCHAR(32)     NOT NULL                COMMENT '服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ',
    `server_type`           VARCHAR(32)     DEFAULT NULL            COMMENT '服务器子类型(仅 SERVER): LINUX / WINDOWS / MACOS / JVM',
    `server_os_version`     VARCHAR(64)     DEFAULT NULL            COMMENT '操作系统版本',
    `monitor_group`         VARCHAR(64)     DEFAULT NULL            COMMENT '分组',
    `monitor_description`   VARCHAR(255)    DEFAULT NULL            COMMENT '描述',
    `monitor_enabled`       TINYINT(1)      DEFAULT 1               COMMENT '是否启用 1启用 0禁用',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_service` (`service_type`),
    KEY `idx_ms_monitor_enabled` (`monitor_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控资产';

-- ----------------------------------------------------------
-- 2. 全局配置
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_config` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_config_key`    VARCHAR(64)     NOT NULL                COMMENT '配置项 key',
    `monitor_config_value`  LONGTEXT        DEFAULT NULL            COMMENT '配置项 value',
    `monitor_config_desc`   VARCHAR(255)    DEFAULT NULL            COMMENT '配置项描述',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_monitor_config_key` (`monitor_config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控全局配置';

-- ----------------------------------------------------------
-- 3. 采集任务
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_task` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL                COMMENT '关联 ms_monitor.id',
    `monitor_task_name`     VARCHAR(128)    NOT NULL                COMMENT '任务名称',
    `service_type`          VARCHAR(32)     NOT NULL                COMMENT '服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ',
    `provider`              VARCHAR(32)     NOT NULL                COMMENT '采集方式: LOCAL / TERMINAL / HTTP / PROMETHEUS',
    `collect_config`        LONGTEXT        NOT NULL                COMMENT '采集参数 JSON',
    `monitor_task_cron`     VARCHAR(64)     NOT NULL                COMMENT 'cron 表达式',
    `monitor_task_timeout`  INT             DEFAULT 5000            COMMENT '超时(毫秒)',
    `monitor_task_enabled`  TINYINT(1)      DEFAULT 1               COMMENT '是否启用',
    `monitor_task_remark`   VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
    `last_run_time`         DATETIME        DEFAULT NULL            COMMENT '最近一次触发时间',
    `create_name`           VARCHAR(64)     DEFAULT NULL,
    `create_by`             VARCHAR(64)     DEFAULT NULL,
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_name`           VARCHAR(64)     DEFAULT NULL,
    `update_by`             VARCHAR(64)     DEFAULT NULL,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_task_monitor` (`monitor_id`),
    KEY `idx_ms_monitor_task_sp` (`service_type`, `provider`),
    KEY `idx_ms_monitor_task_enabled` (`monitor_task_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控采集任务';

-- ----------------------------------------------------------
-- 4. 解析规则 (任务 1:N)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_config` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`               BIGINT          NOT NULL                COMMENT '关联 ms_monitor_task.id',
    `metric_name`           VARCHAR(64)     NOT NULL                COMMENT '指标名(存入时序的指标名称)',
    `target_table`          VARCHAR(64)     NOT NULL                COMMENT '目标表: ms_monitor_metric / ms_monitor_metric_cpu / ...',
    `parse_rule`            LONGTEXT        NOT NULL                COMMENT '解析规则 JSON, 如 {type:regex, pattern, group, unit}',
    `metric_unit`           VARCHAR(16)     DEFAULT NULL            COMMENT '指标单位',
    `remark`                VARCHAR(255)    DEFAULT NULL,
    `create_name`           VARCHAR(64)     DEFAULT NULL,
    `create_by`             VARCHAR(64)     DEFAULT NULL,
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_name`           VARCHAR(64)     DEFAULT NULL,
    `update_by`             VARCHAR(64)     DEFAULT NULL,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_config_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控解析规则';

-- ----------------------------------------------------------
-- 5. Guacamole 网关
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_gateway` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL                COMMENT '关联 ms_monitor.id',
    `gateway_name`          VARCHAR(128)    NOT NULL                COMMENT '网关名称',
    `gateway_type`          VARCHAR(32)     DEFAULT 'GUACAMOLE'      COMMENT '网关类型: GUACAMOLE / SSH / WEB',
    `gateway_host`          VARCHAR(256)    DEFAULT NULL            COMMENT '网关主机',
    `gateway_port`          INT             DEFAULT NULL            COMMENT '网关端口',
    `gateway_config_url`    VARCHAR(512)    DEFAULT NULL            COMMENT '连接配置 URL',
    `gateway_username`      VARCHAR(64)     DEFAULT NULL,
    `gateway_password`      VARCHAR(255)    DEFAULT NULL            COMMENT '密码密文',
    `gateway_enabled`       TINYINT(1)      DEFAULT 1,
    `remark`                VARCHAR(255)    DEFAULT NULL,
    `create_name`           VARCHAR(64)     DEFAULT NULL,
    `create_by`             VARCHAR(64)     DEFAULT NULL,
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_name`           VARCHAR(64)     DEFAULT NULL,
    `update_by`             VARCHAR(64)     DEFAULT NULL,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_gateway_monitor` (`monitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控网关';

-- ----------------------------------------------------------
-- 6. 通用 server 时序指标
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL                COMMENT 'ms_monitor.id',
    `metric_name`           VARCHAR(64)     NOT NULL                COMMENT '指标名',
    `metric_value`          DOUBLE          NOT NULL                COMMENT '指标值',
    `metric_tags`           VARCHAR(1024)   DEFAULT NULL            COMMENT '标签 JSON',
    `collect_time`          DATETIME        NOT NULL                COMMENT '采集时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_sm` (`monitor_id`, `metric_name`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控通用时序';

-- ----------------------------------------------------------
-- 7. server 指标小时聚合
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_statistics` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL                COMMENT 'ms_monitor.id',
    `metric_name`           VARCHAR(64)     NOT NULL                COMMENT '指标名',
    `hour_bucket`           DATETIME        NOT NULL                COMMENT '小时桶',
    `avg_value`             DOUBLE          DEFAULT NULL,
    `min_value`             DOUBLE          DEFAULT NULL,
    `max_value`             DOUBLE          DEFAULT NULL,
    `count_value`           BIGINT          DEFAULT 0               COMMENT '样本数',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_monitor_metric_statistics_bucket` (`monitor_id`, `metric_name`, `hour_bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控小时聚合';

-- ----------------------------------------------------------
-- 8. CPU 明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_cpu` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL,
    `cpu_model`             VARCHAR(128)    DEFAULT NULL            COMMENT 'CPU 型号',
    `cpu_cores`             INT             DEFAULT NULL            COMMENT '核数',
    `cpu_usage`             DOUBLE          DEFAULT NULL            COMMENT '使用率 0-100',
    `collect_time`          DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_cpu_mt` (`monitor_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 CPU 明细';

-- ----------------------------------------------------------
-- 9. 内存明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_memory` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL,
    `total_bytes`           BIGINT          DEFAULT NULL            COMMENT '总内存(字节)',
    `used_bytes`            BIGINT          DEFAULT NULL            COMMENT '已用(字节)',
    `free_bytes`            BIGINT          DEFAULT NULL            COMMENT '空闲(字节)',
    `usage_percent`         DOUBLE          DEFAULT NULL            COMMENT '使用率 0-100',
    `collect_time`          DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_memory_mt` (`monitor_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控内存明细';

-- ----------------------------------------------------------
-- 10. 磁盘明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_disk` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL,
    `mount_point`           VARCHAR(128)    DEFAULT NULL            COMMENT '挂载点',
    `file_system`           VARCHAR(64)     DEFAULT NULL            COMMENT '文件系统',
    `total_bytes`           BIGINT          DEFAULT NULL,
    `used_bytes`            BIGINT          DEFAULT NULL,
    `usage_percent`         DOUBLE          DEFAULT NULL,
    `collect_time`          DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_disk_mt` (`monitor_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控磁盘明细';

-- ----------------------------------------------------------
-- 11. 网络明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_network` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL,
    `interface_name`        VARCHAR(64)     DEFAULT NULL            COMMENT '网卡名',
    `rx_bytes`              BIGINT          DEFAULT NULL            COMMENT '接收字节数',
    `tx_bytes`              BIGINT          DEFAULT NULL            COMMENT '发送字节数',
    `speed_bps`             BIGINT          DEFAULT NULL            COMMENT '网卡速率(bps)',
    `collect_time`          DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_network_mt` (`monitor_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控网络明细';

-- ----------------------------------------------------------
-- 12. JVM 明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_jvm` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL,
    `heap_used_bytes`       BIGINT          DEFAULT NULL,
    `heap_max_bytes`        BIGINT          DEFAULT NULL,
    `heap_usage_percent`    DOUBLE          DEFAULT NULL,
    `young_gc_count`        BIGINT          DEFAULT NULL,
    `full_gc_count`         BIGINT          DEFAULT NULL,
    `threads`               INT             DEFAULT NULL,
    `daemon_threads`        INT             DEFAULT NULL,
    `collect_time`          DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_jvm_mt` (`monitor_id`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 JVM 明细';

-- ----------------------------------------------------------
-- 13. Redis 时序指标
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_redis_metric` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL                COMMENT 'ms_monitor.id(REDIS 类型)',
    `metric_name`           VARCHAR(64)     NOT NULL,
    `metric_value`          DOUBLE          NOT NULL,
    `metric_tags`           VARCHAR(1024)   DEFAULT NULL,
    `collect_time`          DATETIME        NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_redis_metric_sm` (`monitor_id`, `metric_name`, `collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 Redis 时序';

-- ----------------------------------------------------------
-- 14. Redis 小时聚合
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_redis_metric_statistics` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `monitor_id`            BIGINT          NOT NULL,
    `metric_name`           VARCHAR(64)     NOT NULL,
    `hour_bucket`           DATETIME        NOT NULL,
    `avg_value`             DOUBLE          DEFAULT NULL,
    `min_value`             DOUBLE          DEFAULT NULL,
    `max_value`             DOUBLE          DEFAULT NULL,
    `count_value`           BIGINT          DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_monitor_redis_metric_statistics_bucket` (`monitor_id`, `metric_name`, `hour_bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 Redis 小时聚合';

-- ============================================================
-- 数据库指标表 / 网关 / 任务, 暂无锁, 后续模块再补
-- 预留: ms_monitor_database_metric / ms_monitor_database_metric_statistics
-- ============================================================

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 初始配置
-- ============================================================
INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.data.retention.days', '30', '历史数据保留天数'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.data.retention.days');

INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.aggregate.interval', '1h', '聚合粒度'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.aggregate.interval');

INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.collect.interval.ms', '10000', '采集周期(毫秒)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.collect.interval.ms');

INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.local.skip.macos', 'true', '本地采集在 macOS 下跳过(oshi 动态库不支持)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.local.skip.macos');

INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.prometheus.enabled', 'false', 'Prometheus 采集总开关'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.prometheus.enabled');

INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.prometheus.url', '', 'Prometheus 服务地址'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.prometheus.url');

INSERT INTO `ms_monitor_config` (`monitor_config_key`, `monitor_config_value`, `monitor_config_desc`)
SELECT 'monitor.cleanup.cron', '0 0 3 * * ?', '数据清理任务 cron'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `monitor_config_key` = 'monitor.cleanup.cron');
