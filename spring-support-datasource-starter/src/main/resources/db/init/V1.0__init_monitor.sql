-- ============================================================
-- 监控模块初始化 SQL（合并版）
-- 模块: spring-support-monitor-starter
-- 来源: 原 V1.0__init_monitor + V1.1__alarm_and_agent + V1.2__server_node + V1.3__monitor_host_link
-- 命名规范: 列名使用完整表名作为前缀，如 ms_monitor_name
-- 数据库: MySQL 8.x / H2 (MySQL 模式) / 达梦 / Oracle 通过方言转换
-- 幂等: 建表 IF NOT EXISTS，补列 IF NOT EXISTS，重复执行安全
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

-- ----------------------------------------------------------
-- 15. 告警策略
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_policy (
    id                               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_policy_name     VARCHAR(64)  NOT NULL                COMMENT '策略名称',
    ms_monitor_alarm_policy_enabled  TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    ms_monitor_alarm_policy_remark   VARCHAR(255) NULL                    COMMENT '备注',
    PRIMARY KEY (id)
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
    KEY idx_alarm_rule_policy (ms_monitor_alarm_rule_policy_id),
    KEY idx_alarm_rule_metric (ms_monitor_alarm_rule_metric_name)
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
    KEY idx_alarm_event_monitor (ms_monitor_alarm_event_monitor_id),
    KEY idx_alarm_event_status (ms_monitor_alarm_event_status)
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
    KEY idx_alarm_mw_enabled (ms_monitor_alarm_maintenance_window_enabled)
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
    UNIQUE KEY uk_ms_server_node_name (ms_server_node_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '服务管理-协议服务器节点';

-- ============================================================
-- 补列/索引（建表之后统一执行，幂等 IF NOT EXISTS）
-- ============================================================

-- MsMonitor 追加探针标识列(Agent 推送鉴权)
ALTER TABLE ms_monitor ADD COLUMN IF NOT EXISTS ms_monitor_agent_key VARCHAR(64) NULL COMMENT '探针标识(Agent推送鉴权)';

-- ms_monitor 追加关联服务器主机 ID 列(ms_server_host.id, 资产联动)
ALTER TABLE ms_monitor ADD COLUMN IF NOT EXISTS ms_server_host_id BIGINT NULL COMMENT '关联服务器主机 ID(ms_server_host.id, 资产联动)';

-- 便于按资产查询监控项
CREATE INDEX IF NOT EXISTS idx_ms_monitor_server_host ON ms_monitor (ms_server_host_id);

-- MsMonitorAlarmEvent 追加触发时规则配置快照列
ALTER TABLE ms_monitor_alarm_event ADD COLUMN IF NOT EXISTS ms_monitor_alarm_event_rule_snapshot VARCHAR(1024) NULL COMMENT '触发时规则配置快照(JSON)';

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

-- ============================================================
-- 项目守护/指标告警策略与规则种子（由 spring-support-project-starter 喂指标）
-- ============================================================
INSERT INTO ms_monitor_alarm_policy (`ms_monitor_alarm_policy_name`, `ms_monitor_alarm_policy_enabled`, `ms_monitor_alarm_policy_remark`)
SELECT '项目守护与指标', 1, '项目守护失败自动拉起与指标采集相关告警'
WHERE NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_policy WHERE `ms_monitor_alarm_policy_name` = '项目守护与指标');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.watchdog.fail', 'GT', 1, 1, 10, 'P2', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.watchdog.fail');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.watchdog.restart.fail', 'GT', 1, 1, 10, 'P1', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.watchdog.restart.fail');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.port.alive', 'LE', 0, 2, 10, 'P1', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.port.alive');

INSERT INTO ms_monitor_alarm_rule
(`ms_monitor_alarm_rule_policy_id`, `ms_monitor_alarm_rule_metric_name`, `ms_monitor_alarm_rule_condition`,
 `ms_monitor_alarm_rule_threshold`, `ms_monitor_alarm_rule_consecutive_count`, `ms_monitor_alarm_rule_silence_minutes`,
 `ms_monitor_alarm_rule_level`, `ms_monitor_alarm_rule_enabled`)
SELECT p.id, 'project.collect.fail', 'GT', 0, 3, 30, 'P3', 1
FROM ms_monitor_alarm_policy p
WHERE p.`ms_monitor_alarm_policy_name` = '项目守护与指标'
  AND NOT EXISTS (SELECT 1 FROM ms_monitor_alarm_rule r WHERE r.`ms_monitor_alarm_rule_metric_name` = 'project.collect.fail');