-- ============================================================
-- 监控模块初始化 SQL
-- 模块: spring-support-monitor-starter
-- 命名规范: 列名使用完整表名作为前缀，如 ms_monitor_name
-- 数据库: MySQL 8.x / H2 (MySQL 模式) / 达梦 / Oracle 通过方言转换
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------
-- 1. 资产主表(主机视图)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_name`          VARCHAR(128)    NOT NULL                COMMENT '名称',
    `ms_monitor_host`          VARCHAR(256)    NOT NULL                COMMENT '主机地址 / IP',
    `ms_monitor_port`          INT             DEFAULT 22              COMMENT '连接端口(SSH 22 / Redis 6379 / MySQL 3306 等)',
    `ms_monitor_service_type`  VARCHAR(32)     NOT NULL                COMMENT '服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ',
    `ms_monitor_server_type`   VARCHAR(32)     DEFAULT NULL            COMMENT '服务器子类型(仅 SERVER): LINUX / WINDOWS / MACOS / JVM',
    `ms_monitor_server_os_version` VARCHAR(64) DEFAULT NULL            COMMENT '操作系统版本',
    `ms_monitor_group`         VARCHAR(64)     DEFAULT NULL            COMMENT '分组',
    `ms_monitor_description`   VARCHAR(255)    DEFAULT NULL            COMMENT '描述',
    `ms_monitor_enabled`       TINYINT(1)      DEFAULT 1               COMMENT '是否启用 1启用 0禁用',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_service` (`ms_monitor_service_type`),
    KEY `idx_ms_monitor_enabled` (`ms_monitor_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控资产';

-- ----------------------------------------------------------
-- 2. 全局配置
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_config` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_config_key`    VARCHAR(64)     NOT NULL                COMMENT '配置项 key',
    `ms_monitor_config_value`  LONGTEXT        DEFAULT NULL            COMMENT '配置项 value',
    `ms_monitor_config_desc`   VARCHAR(255)    DEFAULT NULL            COMMENT '配置项描述',
    `create_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '创建人姓名',
    `create_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '创建人',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`           VARCHAR(64)     DEFAULT NULL            COMMENT '更新人姓名',
    `update_by`             VARCHAR(64)     DEFAULT NULL            COMMENT '更新人',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_monitor_config_key` (`ms_monitor_config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控全局配置';

-- ----------------------------------------------------------
-- 3. 采集任务
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_task` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_task_monitor_id` BIGINT      NOT NULL                COMMENT '关联 ms_monitor.id',
    `ms_monitor_task_name`     VARCHAR(128)    NOT NULL                COMMENT '任务名称',
    `ms_monitor_task_service_type` VARCHAR(32) NOT NULL               COMMENT '服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ',
    `ms_monitor_task_provider`  VARCHAR(32)     NOT NULL                COMMENT '采集方式: LOCAL / TERMINAL / HTTP / PROMETHEUS',
    `ms_monitor_task_collect_config` LONGTEXT NOT NULL                COMMENT '采集参数 JSON',
    `ms_monitor_task_cron`     VARCHAR(64)     NOT NULL                COMMENT 'cron 表达式',
    `ms_monitor_task_timeout`  INT             DEFAULT 5000            COMMENT '超时(毫秒)',
    `ms_monitor_task_enabled`  TINYINT(1)      DEFAULT 1               COMMENT '是否启用',
    `ms_monitor_task_remark`   VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
    `ms_monitor_task_last_run_time` DATETIME   DEFAULT NULL            COMMENT '最近一次触发时间',
    `create_name`           VARCHAR(64)     DEFAULT NULL,
    `create_by`             VARCHAR(64)     DEFAULT NULL,
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_name`           VARCHAR(64)     DEFAULT NULL,
    `update_by`             VARCHAR(64)     DEFAULT NULL,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_task_monitor` (`ms_monitor_task_monitor_id`),
    KEY `idx_ms_monitor_task_sp` (`ms_monitor_task_service_type`, `ms_monitor_task_provider`),
    KEY `idx_ms_monitor_task_enabled` (`ms_monitor_task_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控采集任务';

-- ----------------------------------------------------------
-- 4. 解析规则 (任务 1:N)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_config` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_config_task_id` BIGINT NOT NULL                COMMENT '关联 ms_monitor_task.id',
    `ms_monitor_metric_config_metric_name` VARCHAR(64) NOT NULL       COMMENT '指标名(存入时序的指标名称)',
    `ms_monitor_metric_config_target_table` VARCHAR(64) NOT NULL      COMMENT '目标表: ms_monitor_metric / ms_monitor_metric_cpu / ...',
    `ms_monitor_metric_config_parse_rule` LONGTEXT NOT NULL           COMMENT '解析规则 JSON, 如 {type:regex, pattern, group, unit}',
    `ms_monitor_metric_config_metric_unit` VARCHAR(16) DEFAULT NULL   COMMENT '指标单位',
    `ms_monitor_metric_config_remark` VARCHAR(255) DEFAULT NULL,
    `create_name`           VARCHAR(64)     DEFAULT NULL,
    `create_by`             VARCHAR(64)     DEFAULT NULL,
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_name`           VARCHAR(64)     DEFAULT NULL,
    `update_by`             VARCHAR(64)     DEFAULT NULL,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_config_task` (`ms_monitor_metric_config_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控解析规则';

-- ----------------------------------------------------------
-- 5. Guacamole 网关
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_gateway` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_gateway_monitor_id` BIGINT  NOT NULL                COMMENT '关联 ms_monitor.id',
    `ms_monitor_gateway_name` VARCHAR(128)  NOT NULL                COMMENT '网关名称',
    `ms_monitor_gateway_type` VARCHAR(32)   DEFAULT 'GUACAMOLE'      COMMENT '网关类型: GUACAMOLE / SSH / WEB',
    `ms_monitor_gateway_host` VARCHAR(256)  DEFAULT NULL            COMMENT '网关主机',
    `ms_monitor_gateway_port` INT           DEFAULT NULL            COMMENT '网关端口',
    `ms_monitor_gateway_config_url` VARCHAR(512) DEFAULT NULL       COMMENT '连接配置 URL',
    `ms_monitor_gateway_username` VARCHAR(64) DEFAULT NULL,
    `ms_monitor_gateway_password` VARCHAR(255) DEFAULT NULL         COMMENT '密码密文',
    `ms_monitor_gateway_enabled` TINYINT(1)  DEFAULT 1,
    `ms_monitor_gateway_remark` VARCHAR(255) DEFAULT NULL,
    `create_name`           VARCHAR(64)     DEFAULT NULL,
    `create_by`             VARCHAR(64)     DEFAULT NULL,
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP,
    `update_name`           VARCHAR(64)     DEFAULT NULL,
    `update_by`             VARCHAR(64)     DEFAULT NULL,
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_gateway_monitor` (`ms_monitor_gateway_monitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控网关';

-- ----------------------------------------------------------
-- 6. 通用 server 时序指标
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_monitor_id` BIGINT   NOT NULL                COMMENT 'ms_monitor.id',
    `ms_monitor_metric_metric_name` VARCHAR(64) NOT NULL            COMMENT '指标名',
    `ms_monitor_metric_metric_value` DOUBLE  NOT NULL                COMMENT '指标值',
    `ms_monitor_metric_metric_tags` VARCHAR(1024) DEFAULT NULL      COMMENT '标签 JSON',
    `ms_monitor_metric_collect_time` DATETIME NOT NULL               COMMENT '采集时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_sm` (`ms_monitor_metric_monitor_id`, `ms_monitor_metric_metric_name`, `ms_monitor_metric_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控通用时序';

-- ----------------------------------------------------------
-- 7. server 指标小时聚合
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_statistics` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_statistics_monitor_id` BIGINT NOT NULL        COMMENT 'ms_monitor.id',
    `ms_monitor_metric_statistics_metric_name` VARCHAR(64) NOT NULL  COMMENT '指标名',
    `ms_monitor_metric_statistics_hour_bucket` DATETIME NOT NULL     COMMENT '小时桶',
    `ms_monitor_metric_statistics_avg_value` DOUBLE DEFAULT NULL,
    `ms_monitor_metric_statistics_min_value` DOUBLE DEFAULT NULL,
    `ms_monitor_metric_statistics_max_value` DOUBLE DEFAULT NULL,
    `ms_monitor_metric_statistics_count_value` BIGINT DEFAULT 0     COMMENT '样本数',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_monitor_metric_statistics_bucket` (`ms_monitor_metric_statistics_monitor_id`, `ms_monitor_metric_statistics_metric_name`, `ms_monitor_metric_statistics_hour_bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控小时聚合';

-- ----------------------------------------------------------
-- 8. CPU 明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_cpu` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_cpu_monitor_id` BIGINT NOT NULL,
    `ms_monitor_metric_cpu_cpu_model` VARCHAR(128) DEFAULT NULL     COMMENT 'CPU 型号',
    `ms_monitor_metric_cpu_cpu_cores` INT     DEFAULT NULL           COMMENT '核数',
    `ms_monitor_metric_cpu_cpu_usage` DOUBLE  DEFAULT NULL           COMMENT '使用率 0-100',
    `ms_monitor_metric_cpu_load1` DOUBLE     DEFAULT NULL           COMMENT '1 分钟平均负载',
    `ms_monitor_metric_cpu_load5` DOUBLE     DEFAULT NULL           COMMENT '5 分钟平均负载',
    `ms_monitor_metric_cpu_load15` DOUBLE    DEFAULT NULL            COMMENT '15 分钟平均负载',
    `ms_monitor_metric_cpu_collect_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_cpu_mt` (`ms_monitor_metric_cpu_monitor_id`, `ms_monitor_metric_cpu_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 CPU 明细';

-- ----------------------------------------------------------
-- 9. 内存明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_memory` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_memory_monitor_id` BIGINT NOT NULL,
    `ms_monitor_metric_memory_total_bytes` BIGINT DEFAULT NULL       COMMENT '总内存(字节)',
    `ms_monitor_metric_memory_used_bytes` BIGINT DEFAULT NULL        COMMENT '已用(字节)',
    `ms_monitor_metric_memory_free_bytes` BIGINT DEFAULT NULL        COMMENT '空闲(字节)',
    `ms_monitor_metric_memory_usage_percent` DOUBLE DEFAULT NULL     COMMENT '使用率 0-100',
    `ms_monitor_metric_memory_collect_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_memory_mt` (`ms_monitor_metric_memory_monitor_id`, `ms_monitor_metric_memory_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控内存明细';

-- ----------------------------------------------------------
-- 10. 磁盘明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_disk` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_disk_monitor_id` BIGINT NOT NULL,
    `ms_monitor_metric_disk_mount_point` VARCHAR(128) DEFAULT NULL   COMMENT '挂载点',
    `ms_monitor_metric_disk_file_system` VARCHAR(64) DEFAULT NULL   COMMENT '文件系统',
    `ms_monitor_metric_disk_total_bytes` BIGINT DEFAULT NULL,
    `ms_monitor_metric_disk_used_bytes` BIGINT DEFAULT NULL,
    `ms_monitor_metric_disk_usage_percent` DOUBLE DEFAULT NULL,
    `ms_monitor_metric_disk_collect_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_disk_mt` (`ms_monitor_metric_disk_monitor_id`, `ms_monitor_metric_disk_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控磁盘明细';

-- ----------------------------------------------------------
-- 11. 网络明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_network` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_network_monitor_id` BIGINT NOT NULL,
    `ms_monitor_metric_network_interface_name` VARCHAR(64) DEFAULT NULL COMMENT '网卡名',
    `ms_monitor_metric_network_rx_bytes` BIGINT DEFAULT NULL        COMMENT '接收字节数',
    `ms_monitor_metric_network_tx_bytes` BIGINT DEFAULT NULL        COMMENT '发送字节数',
    `ms_monitor_metric_network_speed_bps` BIGINT DEFAULT NULL       COMMENT '网卡速率(bps)',
    `ms_monitor_metric_network_collect_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_network_mt` (`ms_monitor_metric_network_monitor_id`, `ms_monitor_metric_network_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控网络明细';

-- ----------------------------------------------------------
-- 12. JVM 明细
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_metric_jvm` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_metric_jvm_monitor_id` BIGINT NOT NULL,
    `ms_monitor_metric_jvm_heap_used_bytes` BIGINT DEFAULT NULL,
    `ms_monitor_metric_jvm_heap_max_bytes` BIGINT DEFAULT NULL,
    `ms_monitor_metric_jvm_heap_usage_percent` DOUBLE DEFAULT NULL,
    `ms_monitor_metric_jvm_young_gc_count` BIGINT DEFAULT NULL,
    `ms_monitor_metric_jvm_full_gc_count` BIGINT DEFAULT NULL,
    `ms_monitor_metric_jvm_threads` INT     DEFAULT NULL,
    `ms_monitor_metric_jvm_daemon_threads` INT DEFAULT NULL,
    `ms_monitor_metric_jvm_collect_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_metric_jvm_mt` (`ms_monitor_metric_jvm_monitor_id`, `ms_monitor_metric_jvm_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 JVM 明细';

-- ----------------------------------------------------------
-- 13. Redis 时序指标
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_redis_metric` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_redis_metric_monitor_id` BIGINT NOT NULL             COMMENT 'ms_monitor.id(REDIS 类型)',
    `ms_monitor_redis_metric_metric_name` VARCHAR(64) NOT NULL,
    `ms_monitor_redis_metric_metric_value` DOUBLE NOT NULL,
    `ms_monitor_redis_metric_metric_tags` VARCHAR(1024) DEFAULT NULL,
    `ms_monitor_redis_metric_collect_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ms_monitor_redis_metric_sm` (`ms_monitor_redis_metric_monitor_id`, `ms_monitor_redis_metric_metric_name`, `ms_monitor_redis_metric_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 Redis 时序';

-- ----------------------------------------------------------
-- 14. Redis 小时聚合
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_monitor_redis_metric_statistics` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_redis_metric_statistics_monitor_id` BIGINT NOT NULL,
    `ms_monitor_redis_metric_statistics_metric_name` VARCHAR(64) NOT NULL,
    `ms_monitor_redis_metric_statistics_hour_bucket` DATETIME NOT NULL,
    `ms_monitor_redis_metric_statistics_avg_value` DOUBLE DEFAULT NULL,
    `ms_monitor_redis_metric_statistics_min_value` DOUBLE DEFAULT NULL,
    `ms_monitor_redis_metric_statistics_max_value` DOUBLE DEFAULT NULL,
    `ms_monitor_redis_metric_statistics_count_value` BIGINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_monitor_redis_metric_statistics_bucket` (`ms_monitor_redis_metric_statistics_monitor_id`, `ms_monitor_redis_metric_statistics_metric_name`, `ms_monitor_redis_metric_statistics_hour_bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 Redis 小时聚合';

-- ============================================================
-- 数据库指标表 / 网关 / 任务, 暂无锁, 后续模块再补
-- 预留: ms_monitor_database_metric / ms_monitor_database_metric_statistics
-- ============================================================

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 初始配置
-- ============================================================
INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.data.retention.days', '30', '历史数据保留天数'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.data.retention.days');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.aggregate.interval', '1h', '聚合粒度'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.aggregate.interval');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.collect.interval.ms', '10000', '采集周期(毫秒)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.collect.interval.ms');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.local.skip.macos', 'true', '本地采集在 macOS 下跳过(oshi 动态库不支持)'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.local.skip.macos');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.prometheus.enabled', 'false', 'Prometheus 采集总开关'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.prometheus.enabled');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.prometheus.url', '', 'Prometheus 服务地址'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.prometheus.url');

INSERT INTO `ms_monitor_config` (`ms_monitor_config_key`, `ms_monitor_config_value`, `ms_monitor_config_desc`)
SELECT 'monitor.cleanup.cron', '0 0 3 * * ?', '数据清理任务 cron'
WHERE NOT EXISTS (SELECT 1 FROM `ms_monitor_config` WHERE `ms_monitor_config_key` = 'monitor.cleanup.cron');