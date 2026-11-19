-- =====================================================================
-- V5.1__init_all_monitor.sql - spring-support-monitor-starter 全量表结构
-- 模块: spring-support-monitor-starter
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口；本目录 INIT 通道只保留本文件，
--   INITDATA 通道只保留 V5.0__initdata_all_monitor.sql。此前分散的
--   V1.0__init_all_monitor.sql、V1.5__init_tshark.sql 已在历史收敛中并入，
--   V4.0.0.44 / 45 / 52 / 53 / 54 / 55 / 56 / 57 / 58 九个增量也已并入
--   （V4.0.0.59 为 device 模块表补列，已由 device 模块 init 全量承载）。
-- 版本递进原因: V5.0 -> V5.1。FlywayLike 按「脚本名 + 版本」记账，命中成功
--   记录即跳过，故本次「动态 DDL 全部移除、列定义内联」的清理必须递进版本号，
--   存量库与新库才会以新版本重跑本文件。
-- 迁移口径: 动态 DDL 全部移除、列/索引定义全部内联进 CREATE TABLE——
--   原文件尾部「查系统表判存 + 会话变量拼 SQL + 预编译执行」的动态补列段
--   （涉及 ms_tshark_session / ms_tshark_packet /
--   ms_sync_task_run_log / ms_monitor_alarm_policy / ms_host_metric_history /
--   ms_server_host）已删除；受守卫保护的列与索引此前均已内联进对应建表语句，
--   ms_server_host 归 server 模块所有，其网络收发列由 server 模块 init
--   （server_host_net_rx_kbs / server_host_net_tx_kbs）承载，不再跨模块补列。
--   本文件只用通用 SQL（CREATE TABLE IF NOT EXISTS + 内联列/索引定义），
--   不再包含任何动态 DDL、判存补列与改表/删表/改名语句。
-- 约束: MySQL 专属语法仅保留 ENGINE=InnoDB / CHARSET / COMMENT 内联写法
--   （MySQL 8.4 / MariaDB 11.8 均不支持「IF NOT EXISTS」式加列，
--   故一律以内联建表替代判存补列）。
-- =====================================================================
--
-- 表归位: nginx 时序指标表 datasource_nginx_metric 的实体
--   MsMonitorNginxMetric 属于 spring-support-datasource-hub-starter，
--   建表语句在该模块的 V2.0__init_all_datasource_hub.sql（新表名/新列名），
--   存量库表名/列名迁移见同模块 V3.0__rename_datasource_hub_tables.sql。
--
-- 命名规范: 列名使用完整表名作为前缀，如 ms_monitor_name
-- 数据库: MySQL 8.x / H2 (MySQL 模式) / 达梦 / Oracle 通过方言转换
-- 幂等: 建表 IF NOT EXISTS，重复执行安全
-- =====================================================================
-- (H2-incompatible) SET NAMES utf8mb4;
-- (H2-incompatible) SET FOREIGN_KEY_CHECKS = 0;

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
    `ms_monitor_agent_key` VARCHAR(64) NULL COMMENT '探针标识(Agent推送鉴权)',
    `ms_server_host_id` BIGINT NULL COMMENT '关联服务器主机 ID(ms_server_host.id, 资产联动)',
    PRIMARY KEY (`id`),
    KEY `ms_monitor_ms_monitor_service` (`ms_monitor_service_type`),
    KEY `ms_monitor_ms_monitor_enabled` (`ms_monitor_enabled`),
    KEY `idx_ms_monitor_server_host` (`ms_server_host_id`)
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
    UNIQUE KEY `ms_monitor_config_ms_monitor_config_key` (`ms_monitor_config_key`)
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
    KEY `ms_monitor_task_ms_monitor_task_monitor` (`ms_monitor_task_monitor_id`),
    KEY `ms_monitor_task_ms_monitor_task_sp` (`ms_monitor_task_service_type`, `ms_monitor_task_provider`),
    KEY `ms_monitor_task_ms_monitor_task_enabled` (`ms_monitor_task_enabled`)
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
    KEY `ms_monitor_metric_config_ms_monitor_metric_config_task` (`ms_monitor_metric_config_task_id`)
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
    KEY `ms_monitor_gateway_ms_monitor_gateway_monitor` (`ms_monitor_gateway_monitor_id`)
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
    KEY `ms_monitor_metric_ms_monitor_metric_sm` (`ms_monitor_metric_monitor_id`, `ms_monitor_metric_metric_name`, `ms_monitor_metric_collect_time`)
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
    UNIQUE KEY `ms_monitor_metric_statistics_uk_bucket` (`ms_monitor_metric_statistics_monitor_id`, `ms_monitor_metric_statistics_metric_name`, `ms_monitor_metric_statistics_hour_bucket`)
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
    KEY `ms_monitor_metric_cpu_ms_monitor_metric_cpu_mt` (`ms_monitor_metric_cpu_monitor_id`, `ms_monitor_metric_cpu_collect_time`)
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
    KEY `ms_monitor_metric_memory_ms_monitor_metric_memory_mt` (`ms_monitor_metric_memory_monitor_id`, `ms_monitor_metric_memory_collect_time`)
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
    KEY `ms_monitor_metric_disk_ms_monitor_metric_disk_mt` (`ms_monitor_metric_disk_monitor_id`, `ms_monitor_metric_disk_collect_time`)
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
    KEY `ms_monitor_metric_network_ms_monitor_metric_network_mt` (`ms_monitor_metric_network_monitor_id`, `ms_monitor_metric_network_collect_time`)
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
    KEY `ms_monitor_metric_jvm_ms_monitor_metric_jvm_mt` (`ms_monitor_metric_jvm_monitor_id`, `ms_monitor_metric_jvm_collect_time`)
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
    KEY `ms_monitor_redis_metric_ms_monitor_redis_metric_sm` (`ms_monitor_redis_metric_monitor_id`, `ms_monitor_redis_metric_metric_name`, `ms_monitor_redis_metric_collect_time`)
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
    UNIQUE KEY `ms_monitor_redis_metric_statistics_uk_bucket` (`ms_monitor_redis_metric_statistics_monitor_id`, `ms_monitor_redis_metric_statistics_metric_name`, `ms_monitor_redis_metric_statistics_hour_bucket`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控 Redis 小时聚合';

-- ----------------------------------------------------------
-- 15. 告警策略
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_policy (
    id                               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_policy_name     VARCHAR(64)  NOT NULL                COMMENT '策略名称',
    ms_monitor_alarm_policy_enabled  TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    ms_monitor_alarm_policy_remark   VARCHAR(255) NULL                    COMMENT '备注',
    ms_server_host_id                BIGINT       NULL                    COMMENT '归属主机ID(ms_server_host.id),空为全局',
    PRIMARY KEY (id),
    KEY `idx_ms_monitor_alarm_policy_host` (ms_server_host_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警策略';

-- ----------------------------------------------------------
-- 16. 告警规则
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_rule (
    id                                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_rule_policy_id         BIGINT        NOT NULL              COMMENT '所属策略ID',
    ms_monitor_alarm_rule_monitor_id        BIGINT        NULL                  COMMENT '限定目标ID,空为全部目标',
    ms_monitor_alarm_rule_metric_name       VARCHAR(64)   NOT NULL              COMMENT '指标名',
    ms_monitor_alarm_rule_condition         VARCHAR(4)    NOT NULL DEFAULT 'GT' COMMENT '条件: GT/GE/EQ/LE/LT',
    ms_monitor_alarm_rule_threshold         DECIMAL(19,4) NOT NULL DEFAULT 0    COMMENT '阈值',
    ms_monitor_alarm_rule_consecutive_count INT           NOT NULL DEFAULT 1    COMMENT '连续触发次数',
    ms_monitor_alarm_rule_silence_minutes   INT           NOT NULL DEFAULT 10   COMMENT '静默期(分钟)',
    ms_monitor_alarm_rule_level             CHAR(2)       NULL DEFAULT 'P2'     COMMENT '级别: P0~P4',
    ms_monitor_alarm_rule_enabled           TINYINT       NOT NULL DEFAULT 1    COMMENT '启用状态 1启用 0停用',
    PRIMARY KEY (id),
    KEY `ms_monitor_alarm_rule_alarm_rule_policy` (ms_monitor_alarm_rule_policy_id),
    KEY `ms_monitor_alarm_rule_alarm_rule_metric` (ms_monitor_alarm_rule_metric_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警规则';

-- ----------------------------------------------------------
-- 17. 告警事件
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_event (
    id                                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_event_monitor_id    BIGINT        NULL                  COMMENT '监控目标ID',
    ms_monitor_alarm_event_monitor_name  VARCHAR(64)   NULL                  COMMENT '目标名称(冗余)',
    ms_monitor_alarm_event_rule_id       BIGINT        NULL                  COMMENT '命中规则ID',
    ms_monitor_alarm_event_metric_name   VARCHAR(64)   NOT NULL              COMMENT '指标名',
    ms_monitor_alarm_event_alarm_value   DECIMAL(19,4) NULL                  COMMENT '实际值',
    ms_monitor_alarm_event_threshold     DECIMAL(19,4) NULL                  COMMENT '阈值',
    ms_monitor_alarm_event_level         CHAR(2)       NULL DEFAULT 'P2'     COMMENT '级别: P0~P4',
    ms_monitor_alarm_event_status        VARCHAR(12)   NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/PROCESSED/FALSE_ALARM',
    ms_monitor_alarm_event_handle_remark VARCHAR(512)  NULL                  COMMENT '处理备注',
    ms_monitor_alarm_event_trigger_time  DATETIME      NULL                  COMMENT '触发时间',
    ms_monitor_alarm_event_handle_time   DATETIME      NULL                  COMMENT '处理时间',
    ms_monitor_alarm_event_rule_snapshot VARCHAR(1024) NULL                  COMMENT '触发时规则配置快照(JSON)',
    PRIMARY KEY (id),
    KEY `ms_monitor_alarm_event_alarm_event_monitor` (ms_monitor_alarm_event_monitor_id),
    KEY `ms_monitor_alarm_event_alarm_event_status` (ms_monitor_alarm_event_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警事件';

-- ----------------------------------------------------------
-- 18. 告警维护窗口
-- ----------------------------------------------------------
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
    KEY `ms_monitor_alarm_maintenance_window_alarm_mw_enabled` (ms_monitor_alarm_maintenance_window_enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警维护窗口';

-- ----------------------------------------------------------
-- 19. 协议服务器节点(服务管理)
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_server_node (
    id                               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_server_node_name              VARCHAR(64)   NOT NULL              COMMENT '节点名称(唯一)',
    ms_server_node_protocol          VARCHAR(32)   NOT NULL              COMMENT '协议类型: HTTP / HTTP_PROXY / TCP_PROXY / SIP',
    ms_server_node_engine            VARCHAR(32)   DEFAULT 'jdk'         COMMENT '传输引擎: http= jdk/nio/aio, 代理= jdk, sip= sip',
    ms_server_node_host              VARCHAR(128)  DEFAULT '0.0.0.0'     COMMENT '监听地址',
    ms_server_node_port              INT           NOT NULL              COMMENT '监听端口',
    ms_server_node_enabled           TINYINT       NOT NULL DEFAULT 1    COMMENT '是否启用: 1 启用(随应用自动拉起), 0 停用',
    ms_server_node_remark            VARCHAR(255)  NULL                  COMMENT '备注',
    ms_server_node_config            TEXT          NULL                  COMMENT '协议专属配置(JSON): sip.token, proxy.backendHost/backendPort 等',
    ms_server_node_filters           TEXT          NULL                  COMMENT 'ServerFilter 插件配置(JSON 数组)',
    create_name                      VARCHAR(64)   DEFAULT NULL          COMMENT '创建人姓名',
    create_by                        INT           DEFAULT NULL          COMMENT '创建人',
    create_time                      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_name                      VARCHAR(64)   DEFAULT NULL          COMMENT '更新人姓名',
    update_by                        INT           DEFAULT NULL          COMMENT '更新人',
    update_time                      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY `ms_server_node_ms_server_node_name` (ms_server_node_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务管理-协议服务器节点';

-- ============================================================
-- 存量兼容说明: 历史上此处以「查系统表判存 + 动态 SQL」为已存在的表补列；
--   V5.1 起动态补列段全部移除，相关列
--   （ms_monitor_agent_key / ms_server_host_id /
--   ms_monitor_alarm_event_rule_snapshot 等）均已内联进上方 CREATE TABLE。
-- (H2-incompatible) SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- TShark 抓包建表脚本
-- 模块: spring-support-monitor-starter
-- 背景: MsTsharkSession / MsTsharkPacket 两个实体长期无对应 DDL，
--       抓包结果落库必然报"表不存在"，故在本脚本内补上建表语句。
-- 加载: 由 datasource-starter 的 FlywayLike 机制统一扫描
--       classpath*:db/init/*.sql 执行（plugin.datasource.script），
--       不再依赖 spring.sql.init.schema-locations 手工列举。
-- 数据库: MySQL 8.x / H2 (MySQL 模式)
-- 幂等: 建表 IF NOT EXISTS，重复执行安全
-- ============================================================

-- ----------------------------------------------------------
-- 1. 抓包会话
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_tshark_session` (
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_tshark_session_name`        VARCHAR(128) DEFAULT NULL            COMMENT '会话名称',
    `ms_tshark_session_interface`   VARCHAR(128) DEFAULT NULL            COMMENT '抓包网卡',
    -- 来源：live=网卡实时抓包 / import=导入的 pcap。两类会话同表，必须能分开查，
    -- 否则「PCAP 分析」页会把实时抓包的历史也列进来。
    -- 注：该列已内联进本建表语句（新库一次建全）；存量库由 TsharkSchemaGuard
    --     在启动时按列缺失补 DDL 兜底。
    `ms_tshark_session_source`      VARCHAR(16) DEFAULT 'live'            COMMENT '来源: live 实时抓包 / import 导入 pcap',
    `ms_tshark_session_filter`      VARCHAR(512) DEFAULT NULL            COMMENT 'BPF 过滤器',
    `ms_tshark_session_status`      TINYINT      DEFAULT 0               COMMENT '状态: 0停止 1运行中 2异常',
    -- 异常原因 / 停止说明、文件大小与大小上限：抓包异常状态与磁盘保护的落点。
    -- 存量库由 TsharkSchemaGuard 在启动时按列缺失补 DDL，这里只保证全新库一次建全。
    `ms_tshark_session_last_error`  VARCHAR(2000) DEFAULT NULL           COMMENT '异常原因/停止说明',
    `ms_tshark_session_file_size`   BIGINT       DEFAULT 0               COMMENT 'pcap 文件大小(字节)',
    `ms_tshark_session_max_size`    INT          DEFAULT 0               COMMENT '会话大小上限(MB)，0=不限',
    `ms_tshark_session_packet_count` INT         DEFAULT 0               COMMENT '已入库数据包数',
    `ms_tshark_session_file_path`   VARCHAR(512) DEFAULT NULL            COMMENT 'pcap 文件路径',
    `ms_tshark_session_duration`    INT          DEFAULT NULL            COMMENT '抓包时长(秒)',
    `ms_tshark_session_start_time`  DATETIME     DEFAULT NULL            COMMENT '开始时间',
    `ms_tshark_session_end_time`    DATETIME     DEFAULT NULL            COMMENT '结束时间',
    `create_name`                   VARCHAR(64)  NULL                    COMMENT '创建人姓名',
    `create_by`                     INT          NULL                    COMMENT '创建人',
    `create_time`                   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                   VARCHAR(64)  NULL                    COMMENT '更新人姓名',
    `update_by`                     INT          NULL                    COMMENT '更新人',
    `update_time`                   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_tshark_session_status` (`ms_tshark_session_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TShark 抓包会话';

-- ----------------------------------------------------------
-- 2. 抓包数据包
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_tshark_packet` (
    `id`                         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_tshark_packet_session_id` BIGINT      NOT NULL                COMMENT '所属会话 ID',
    `ms_tshark_packet_frame_no`  BIGINT       DEFAULT NULL            COMMENT 'tshark 帧号(frame.number)',
    `ms_tshark_packet_time`      DATETIME(3)  DEFAULT NULL            COMMENT '报文时间(毫秒精度)',
    `ms_tshark_packet_source_ip` VARCHAR(64)  DEFAULT NULL            COMMENT '源 IP',
    `ms_tshark_packet_dest_ip`   VARCHAR(64)  DEFAULT NULL            COMMENT '目的 IP',
    `ms_tshark_packet_source_port` INT        DEFAULT NULL            COMMENT '源端口',
    `ms_tshark_packet_dest_port` INT          DEFAULT NULL            COMMENT '目的端口',
    `ms_tshark_packet_protocol`  VARCHAR(64)  DEFAULT NULL            COMMENT '协议分层',
    `ms_tshark_packet_length`    INT          DEFAULT NULL            COMMENT '报文长度',
    `ms_tshark_packet_info`      VARCHAR(1024) DEFAULT NULL           COMMENT '摘要信息',
    `ms_tshark_packet_raw`       LONGTEXT     DEFAULT NULL            COMMENT '解码后的 JSON 原文',
    `ms_tshark_packet_restored`  LONGTEXT     DEFAULT NULL            COMMENT '还原出的载荷文本',
    `create_name`                VARCHAR(64)  NULL                    COMMENT '创建人姓名',
    `create_by`                  INT          NULL                    COMMENT '创建人',
    `create_time`                DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                VARCHAR(64)  NULL                    COMMENT '更新人姓名',
    `update_by`                  INT          NULL                    COMMENT '更新人',
    `update_time`                DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_tshark_packet_session` (`ms_tshark_packet_session_id`, `id`),
    KEY `idx_ms_tshark_packet_protocol` (`ms_tshark_packet_protocol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TShark 抓包数据包';

-- ============================================================
-- Nginx 时序指标
-- ------------------------------------------------------------
-- 数据来源：SSH 读目标机 stub_status 的瞬时/累计值。
-- 粒度：按采集周期（默认 30s）一行，保留窗口由
--      monitor.data.retention.days 控制（默认 1，即滚动 24 小时）。
-- 说明：stub_status 的 accepts/handled/requests 是「worker 启动以来」的累计计数，
--      画趋势必须换算成速率（差值 / 间隔），直接画累计值只会得到单调上升的直线。
--      本表存原始计数，速率在查询侧按相邻两点差值计算。
-- 幂等建表：这里刻意不写「删表重建」，改为 CREATE TABLE IF NOT EXISTS。
-- 原先写成「DROP + CREATE」后，一旦 plugin.datasource.script.enable 打开
-- （或本脚本在 sys_database_version 里被记为 FAILED 而每次启动重跑），
-- nginx 指标历史会被整体清空——属于静默数据丢失，因此必须改成幂等形态。
-- ============================================================
-- 20. 主机内置指标历史（宽表）
-- 写入方：HostMetricHistoryServiceImpl → SeriesEngine.writeBatch(target, hostId, metrics, ts)
-- 读取方：DataSourceSeriesEngine 的列规约 ——
--   监控列 = {target}_monitor_id、时间列 = {target}_collect_time、
--   数值列 = {target}_{指标名}，因此下面 5 个指标列名必须与
--   HostMetricHistoryServiceImpl.CURVE_TARGETS 逐字一致：
--   cpu_usage / mem_usage / disk_usage / disk_read_kbs / disk_write_kbs
--   （改任一侧都要同时改另一侧，否则会静默变成 Unknown column）。
-- 时间列必须是 DATETIME：引擎写入用 java.sql.Timestamp，查询也按 Timestamp 比较。
--
-- ⚠ 本表此前全仓没有任何 DDL：只在 Java 里被引用，于是每次主机采集都报
--   `Table 'monitordb.ms_host_metric_history' doesn't exist`，指标历史永久写不进去。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_host_metric_history` (
    `id`                                        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_host_metric_history_monitor_id`         BIGINT   NOT NULL COMMENT '主机 ID(ms_server_node.id)',
    `ms_host_metric_history_cpu_usage`          DOUBLE   DEFAULT NULL COMMENT 'CPU 使用率(0-100)',
    `ms_host_metric_history_mem_usage`          DOUBLE   DEFAULT NULL COMMENT '内存使用率(0-100)',
    `ms_host_metric_history_disk_usage`         DOUBLE   DEFAULT NULL COMMENT '磁盘使用率(0-100)',
    `ms_host_metric_history_disk_read_kbs`      DOUBLE   DEFAULT NULL COMMENT '磁盘读(KB/s)',
    `ms_host_metric_history_disk_write_kbs`     DOUBLE   DEFAULT NULL COMMENT '磁盘写(KB/s)',
    `ms_host_metric_history_net_rx_kbs`         DOUBLE   DEFAULT NULL COMMENT '网络接收(KB/s)',
    `ms_host_metric_history_net_tx_kbs`         DOUBLE   DEFAULT NULL COMMENT '网络发送(KB/s)',
    `ms_host_metric_history_collect_time`       DATETIME NOT NULL COMMENT '采集时刻',
    PRIMARY KEY (`id`),
    KEY `idx_ms_host_metric_history_monitor_time` (`ms_host_metric_history_monitor_id`, `ms_host_metric_history_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主机内置指标历史(宽表)';

-- ============================================================
-- 21. 数据同步任务
-- 由 SyncTaskServiceImpl 读写；源/目标均为 datasource_info 里的 jdbc 类型数据源。
-- 数据面复用本仓 datasync 引擎：JdbcDataSyncAgentSource 读、DefaultFieldMappingConverter 转、
-- MonitorJdbcSyncSink 写；调度由 Spring ThreadPoolTaskScheduler 驱动（引擎自带调度器有
-- 「同 topic 只订阅一次」语义，改任务后仍会写旧 Sink，故不采用）。
-- 列名沿用「完整表名做前缀」的模块规范。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_sync_task` (
    `id`                                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_sync_task_name`                     VARCHAR(128) NOT NULL COMMENT '任务名称',
    `ms_sync_task_source_type`              VARCHAR(16)  NOT NULL DEFAULT 'JDBC' COMMENT '输入源类型 JDBC/FILE/DIR/HTTP_JSON/HTTP_HTML',
    `ms_sync_task_source_datasource_id`     BIGINT       DEFAULT NULL COMMENT '源数据源 ID(datasource_info.id)，仅 JDBC 源使用',
    `ms_sync_task_source_sql`               TEXT         DEFAULT NULL COMMENT '源读取 SQL，必须是 SELECT，仅 JDBC 源使用',
    `ms_sync_task_source_path`              VARCHAR(512) DEFAULT NULL COMMENT '源定位：FILE=本地文件绝对路径 / DIR=本地目录绝对路径 / HTTP_*=http(s) 请求地址',
    `ms_sync_task_source_options`           TEXT         DEFAULT NULL COMMENT '输入源扩展选项 JSON：文件族 {format,delimiter,sheetIndex,pattern,recursive,afterProcess}；HTTP 族 {method,headers,body,encoding,jsonPath,rowSelector,cellSelector,headerRow,timeoutSeconds}',
    `ms_sync_task_target_datasource_id`     BIGINT       NOT NULL COMMENT '目标数据源 ID(datasource_info.id)',
    `ms_sync_task_target_table`             VARCHAR(128) NOT NULL COMMENT '目标表名',
    `ms_sync_task_column_mapping`           TEXT         DEFAULT NULL COMMENT '字段映射 JSON 数组 [{"source":"a","target":"b"}]，空表示按同名匹配',
    `ms_sync_task_write_mode`               VARCHAR(16)  NOT NULL DEFAULT 'INSERT' COMMENT '写入模式 INSERT/REPLACE/UPSERT/OVERWRITE',
    `ms_sync_task_batch_size`               INT          NOT NULL DEFAULT 500 COMMENT '批量提交条数',
    `ms_sync_task_cron`                     VARCHAR(64)  DEFAULT NULL COMMENT '定时表达式(Spring 6 段 cron)，空表示仅手动执行',
    `ms_sync_task_enabled`                  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用定时 1启用 0停用',
    `ms_sync_task_last_run_time`            DATETIME     DEFAULT NULL COMMENT '最近一次执行时间',
    `ms_sync_task_last_status`              VARCHAR(16)  DEFAULT NULL COMMENT '最近一次状态 SUCCESS/FAILED/RUNNING',
    `ms_sync_task_last_read_count`          BIGINT       DEFAULT NULL COMMENT '最近一次读取行数',
    `ms_sync_task_last_write_count`         BIGINT       DEFAULT NULL COMMENT '最近一次写入行数',
    `ms_sync_task_last_cost_ms`             BIGINT       DEFAULT NULL COMMENT '最近一次耗时(毫秒)',
    `ms_sync_task_last_message`             VARCHAR(512) DEFAULT NULL COMMENT '最近一次结果说明/错误信息',
    `ms_sync_task_sync_mode`                VARCHAR(16)  NOT NULL DEFAULT 'FULL' COMMENT '同步模式 FULL 全量/INCREMENTAL 增量',
    `ms_sync_task_inc_field`                VARCHAR(255) DEFAULT NULL COMMENT '增量字段（仅增量模式），只允许字母数字下划线；多字段按顺序逗号分隔',
    `ms_sync_task_inc_batch_size`           INT          DEFAULT NULL COMMENT '增量每批数量：单次执行最多同步条数，空表示不限',
    `ms_sync_task_last_seq`                 VARCHAR(255) DEFAULT NULL COMMENT '增量水位：上次成功同步到的最大增量字段值（服务端回写）',
    `ms_sync_task_hook_url`                 VARCHAR(512) DEFAULT NULL COMMENT 'Hook 通知地址（http/https），空表示不通知',
    `ms_sync_task_hook_notify`              VARCHAR(16)  DEFAULT NULL COMMENT 'Hook 通知时机 DONE 完成通知/ROW 每条数据+完成，全量固定 DONE',
    `ms_sync_task_targets`                  MEDIUMTEXT   DEFAULT NULL COMMENT '多目标 JSON 数组：一次采集分别写入多张表，空表示按单目标字段解析',
    `ms_sync_task_target_seqs`              TEXT         DEFAULT NULL COMMENT '多目标各自的增量水位 JSON：{"数据源ID#表名":"水位"}，服务端回写',
    `create_name`                           VARCHAR(64)  DEFAULT NULL COMMENT '创建人姓名',
    `create_by`                             INT          DEFAULT NULL COMMENT '创建人',
    `create_time`                           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                           VARCHAR(64)  DEFAULT NULL COMMENT '更新人姓名',
    `update_by`                             INT          DEFAULT NULL COMMENT '更新人',
    `update_time`                           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_sync_task_enabled` (`ms_sync_task_enabled`),
    KEY `idx_ms_sync_task_source_ds` (`ms_sync_task_source_datasource_id`),
    KEY `idx_ms_sync_task_target_ds` (`ms_sync_task_target_datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步任务';

-- ============================================================
-- 22. 维护计划
-- 由 MaintenancePlanService 读写；维护窗口供值班人员规划升级、备份、重启等窗口期任务。
-- 本表只保存计划台账，不负责调度执行，状态由运维流程或后续编排任务推进。
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


-- ----------------------------------------------------------
-- 存量库补列：ms_tshark_* 的新增列
--
-- 原先这里用「查系统表判存 + 会话变量拼 SQL + 预编译执行」的动态 DDL 为
-- ms_tshark_session / ms_tshark_packet 补列（source / last_error /
-- file_size / max_size / frame_no）。V5.1 起动态 DDL 移除：上述列均已
-- 内联进上方 CREATE TABLE，新库一次建全；存量库的列缺失仍由
-- TsharkSchemaGuard（Java，启动时按列缺失补 DDL）兜底。
-- ----------------------------------------------------------

-- ============================================================
-- [二] 增量并入的新表（源自原 V4.0.0.44 / 45 / 52 / 55 / 56）
-- ============================================================

-- ----------------------------------------------------------
-- 23. 数据同步执行日志
-- 由 SyncTaskRunLogBuffer 在一次执行结束时写入、SyncTaskServiceImpl 读回。
-- 一次执行一行，正文用 LONGTEXT 存「时间<TAB>级别<TAB>内容」逐行拼接；
-- 不建外键：任务删除后日志仍需保留以便事后追溯；由保留策略按时间裁剪。
-- read_count / write_count 原名 V4.0.0.45 的补列，已内联进建表：
--   列表页只需行数标题，正文在点开详情时才整体读回；存量行读/写为 NULL，
--   详情页仍会从正文动态解析作为兜底。
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_sync_task_run_log` (
    `id`                                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_sync_task_run_log_task_id`          BIGINT       NOT NULL COMMENT '任务 ID(ms_sync_task.id)，任务删除后本行保留',
    `ms_sync_task_run_log_trigger`          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式 MANUAL手动/SCHEDULED定时/REALTIME实时录入',
    `ms_sync_task_run_log_status`           VARCHAR(16)  NOT NULL COMMENT '执行状态 SUCCESS/FAILED',
    `ms_sync_task_run_log_run_time`         DATETIME     NOT NULL COMMENT '执行开始时刻',
    `ms_sync_task_run_log_cost_ms`          BIGINT       DEFAULT NULL COMMENT '本次执行总耗时(毫秒)',
    `ms_sync_task_run_log_lines`            LONGTEXT     DEFAULT NULL COMMENT '过程日志正文：逐行「时间\t级别\t内容」，换行分隔',
    `ms_sync_task_run_log_line_count`       INT          NOT NULL DEFAULT 0 COMMENT '日志行数（列表展示用，免解析正文）',
    `ms_sync_task_run_log_read_count`       BIGINT       DEFAULT NULL COMMENT '本轮读取行数（执行结束时从过程日志解析）',
    `ms_sync_task_run_log_write_count`      BIGINT       DEFAULT NULL COMMENT '本轮写入行数（执行结束时从过程日志解析）',
    `ms_sync_task_run_log_create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '入库时刻',
    PRIMARY KEY (`id`),
    KEY `idx_ms_sync_task_run_log_task_time` (`ms_sync_task_run_log_task_id`, `ms_sync_task_run_log_run_time`),
    KEY `idx_ms_sync_task_run_log_time` (`ms_sync_task_run_log_run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步执行日志';

-- ----------------------------------------------------------
-- 24. 服务管理节点操作日志
-- 一行 = 一次操作，由 @SysLog 切面发事件、ServerOperLogListener 落库。
-- 保留窗口由 ServerOperLogScheduler 滚动清理。
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_server_oper_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `node_id`     BIGINT       NOT NULL COMMENT '节点 id（ms_server_node.id）',
    `node_name`   VARCHAR(128) NULL DEFAULT '' COMMENT '节点名称（冗余，节点删除后仍可读）',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作码：CREATE/UPDATE/HOT_UPDATE/FILTER_*/START/STOP/DELETE',
    `action_name` VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`      VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（协议与监听地址摘要），可空',
    `detail`      TEXT         NULL COMMENT '补充说明',
    `success`     TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`     BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`    VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`   VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url` VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time` DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_server_oper_log_node_time` (`node_id`, `create_time`),
    KEY `idx_ms_server_oper_log_action` (`node_id`, `action`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务管理节点操作日志';

-- ----------------------------------------------------------
-- 25. APM 操作日志
-- 一行 = 一次 APM 写操作，由 ApmDispatcher 回调 ApmOperLogSink 落库。
-- 只记写操作（POST）；保留窗口由 ApmOperLogScheduler 滚动清理。
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_apm_oper_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作码：CONFIG_UPDATE/DETECT',
    `action_name` VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`      VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（本次改动的设置项）',
    `detail`      TEXT         NULL COMMENT '补充说明',
    `success`     TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`     BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`    VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`   VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url` VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time` DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_apm_oper_log_time` (`create_time`),
    KEY `idx_ms_apm_oper_log_action` (`action`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='APM 操作日志';

-- ----------------------------------------------------------
-- 26. ACME 账户 / 证书 / 订单（证书管理）
-- 语句全部 IF NOT EXISTS，存量库重复执行安全。
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `monitor_acme_account` (
  `acme_account_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `acme_account_email` varchar(255) NULL DEFAULT NULL COMMENT '账号邮箱',
  `acme_account_server` varchar(255) NULL DEFAULT NULL COMMENT 'ACME 服务端点',
  `acme_account_provider_type` varchar(32) NOT NULL DEFAULT 'acme' COMMENT '证书服务商类型(acme=ACME协议/acme4j、cas=阿里云CAS)',
  `acme_account_status` varchar(255) NULL DEFAULT NULL COMMENT '账户状态',
  `acme_account_private_key_pem` text NULL DEFAULT NULL COMMENT '账户私钥（PEM）',
  `acme_account_eab_kid` varchar(255) NULL DEFAULT NULL COMMENT 'EAB Key ID (ZeroSSL 等需要)',
  `acme_account_eab_hmac_key` varchar(512) NULL DEFAULT NULL COMMENT 'EAB HMAC Key (Base64 编码)',
  `acme_account_timeout_seconds` int NULL DEFAULT 60 COMMENT 'ACME 网络请求超时（秒）',
  `acme_account_remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`acme_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACME 账户表';

CREATE TABLE IF NOT EXISTS `monitor_acme_certificate` (
  `acme_cert_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `acme_account_id` int NULL DEFAULT NULL COMMENT '账户ID',
  `acme_cert_primary_domain` varchar(255) NULL DEFAULT NULL COMMENT '主域名',
  `acme_cert_san` varchar(255) NULL DEFAULT NULL COMMENT '备用域名(SAN)，逗号分隔',
  `acme_cert_challenge_type` varchar(255) NULL DEFAULT NULL COMMENT '挑战类型（HTTP-01/DNS-01）',
  `acme_cert_status` varchar(255) NULL DEFAULT NULL COMMENT '证书状态',
  `acme_cert_not_before` varchar(255) NULL DEFAULT NULL COMMENT '有效期起',
  `acme_cert_not_after` varchar(255) NULL DEFAULT NULL COMMENT '有效期止',
  `acme_cert_renew_at` varchar(255) NULL DEFAULT NULL COMMENT '计划续签日期',
  `acme_cert_chain_pem` text NULL COMMENT '证书PEM（含链）',
  `acme_cert_key_pem` text NULL COMMENT '私钥PEM',
  `acme_cert_last_error` varchar(255) NULL DEFAULT NULL COMMENT '最近错误',
  `acme_cert_deploy_mode` varchar(20) NULL DEFAULT 'manual' COMMENT '部署模式：local(本机自动)/manual(手动)',
  `acme_cert_webroot_path` varchar(255) NULL DEFAULT NULL COMMENT '本机模式webroot路径',
  `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`acme_cert_id`),
  KEY `monitor_acme_certificate_idx_account_id` (`acme_account_id`),
  KEY `monitor_acme_certificate_idx_domain` (`acme_cert_primary_domain`),
  KEY `monitor_acme_certificate_idx_status` (`acme_cert_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACME 证书表';

CREATE TABLE IF NOT EXISTS `monitor_acme_order` (
  `acme_order_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `acme_account_id` int NULL DEFAULT NULL COMMENT '账户ID',
  `acme_cert_id` int NULL DEFAULT NULL COMMENT '证书ID',
  `acme_order_url` varchar(255) NULL DEFAULT NULL COMMENT '订单URL',
  `acme_authz_urls` text NULL COMMENT '授权URL列表(JSON)',
  `acme_order_status` varchar(255) NULL DEFAULT NULL COMMENT '状态',
  `acme_certificate_url` varchar(255) NULL DEFAULT NULL COMMENT '最终证书URL',
  `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`acme_order_id`),
  KEY `monitor_acme_order_idx_account_id` (`acme_account_id`),
  KEY `monitor_acme_order_idx_cert_id` (`acme_cert_id`),
  KEY `monitor_acme_order_idx_status` (`acme_order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACME 订单表';

-- ============================================================
-- [三] 存量库兼容 ALTER 段已随 V5.1 清理移除（原 V4.0.0.45 / 53 / 57 / 58）
--
-- 原先此处用「查系统表判存 + 会话变量拼 SQL + 预编译执行」的动态 DDL 为
-- 存量库补列/补索引；V5.1 起动态 DDL 全部移除，处置如下：
--   ms_sync_task_run_log.read_count / write_count、
--   ms_monitor_alarm_policy.ms_server_host_id 及 idx_ms_monitor_alarm_policy_host、
--   ms_host_metric_history.net_rx_kbs / net_tx_kbs
--   —— 均已内联进上方对应 CREATE TABLE；
--   ms_server_host.net_rx_kbs / net_tx_kbs
--   —— 该表归 server 模块所有，列由 server 模块 init 承载
--   （server_host_net_rx_kbs / server_host_net_tx_kbs），不再跨模块补列。
-- ============================================================
