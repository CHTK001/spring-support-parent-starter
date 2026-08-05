-- =====================================================
-- 爬虫中心建表脚本（V1.0.0）
-- 由 spring-support-datasource-starter 自动扫描执行
-- 命名规范：V{版本}__init_{描述}.sql
-- @author CH
-- =====================================================

-- -----------------------------------------------------
-- 1. 爬虫定义表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_definition` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `code`            VARCHAR(64)  NOT NULL                COMMENT '爬虫唯一编码',
    `name`            VARCHAR(128) NOT NULL                COMMENT '爬虫名称',
    `description`     VARCHAR(512)          DEFAULT NULL   COMMENT '描述',
    `flow_id`         BIGINT                DEFAULT NULL   COMMENT '关联流程 ID（spider_flow.id）',
    `enabled`         TINYINT      NOT NULL DEFAULT 0      COMMENT '是否启用（1 启用 / 0 停用）',
    `cron_expression` VARCHAR(64)           DEFAULT NULL   COMMENT '定时 cron 表达式，为空则不自动调度',
    `last_run_at`     DATETIME              DEFAULT NULL   COMMENT '上次执行时间',
    `last_status`     VARCHAR(16)           DEFAULT NULL   COMMENT '上次执行状态（SUCCESS/FAIL/RUNNING）',
    `last_message`    VARCHAR(512)          DEFAULT NULL   COMMENT '上次执行消息',
    `create_name`     VARCHAR(64)           DEFAULT NULL   COMMENT '创建人姓名',
    `create_by`       BIGINT                DEFAULT NULL   COMMENT '创建人 ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`     VARCHAR(64)           DEFAULT NULL   COMMENT '更新人姓名',
    `update_by`       BIGINT                DEFAULT NULL   COMMENT '更新人 ID',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sys_deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_flow_id` (`flow_id`)
) ENGINE = InnoDB COMMENT '爬虫定义表';

-- -----------------------------------------------------
-- 2. 流程编排表（前端 ReFlow 导出的 JSON）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_flow` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `definition_id`   BIGINT       NOT NULL                COMMENT '关联爬虫定义 ID',
    `name`            VARCHAR(128)          DEFAULT NULL   COMMENT '流程名称',
    `nodes_json`      TEXT                 COMMENT '节点 JSON（ReFlow 导出）',
    `edges_json`      TEXT                 COMMENT '连线 JSON（ReFlow 导出）',
    `create_name`     VARCHAR(64)           DEFAULT NULL   COMMENT '创建人姓名',
    `create_by`       BIGINT                DEFAULT NULL   COMMENT '创建人 ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`     VARCHAR(64)           DEFAULT NULL   COMMENT '更新人姓名',
    `update_by`       BIGINT                DEFAULT NULL   COMMENT '更新人 ID',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sys_deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_definition_id` (`definition_id`),
    KEY `idx_name` (`name`)
) ENGINE = InnoDB COMMENT '流程编排表';

-- -----------------------------------------------------
-- 3. 采集数据记录表（output 节点写入）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_data_record` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `execution_no`    VARCHAR(64)   NOT NULL                COMMENT '执行号（traceId）',
    `definition_code` VARCHAR(64)   NOT NULL                COMMENT '爬虫编码',
    `title`           VARCHAR(256)           DEFAULT NULL   COMMENT '标题',
    `content`         MEDIUMTEXT             DEFAULT NULL   COMMENT '内容',
    `source_url`      VARCHAR(512)           DEFAULT NULL   COMMENT '来源 URL',
    `raw_data`        JSON          NOT NULL                COMMENT '原始采集数据（JSON）',
    `status`          VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT '状态（SUCCESS/FAIL）',
    `error_message`   VARCHAR(1024)          DEFAULT NULL   COMMENT '失败信息',
    `record_time`     DATETIME               DEFAULT NULL   COMMENT '记录时间',
    `create_name`     VARCHAR(64)            DEFAULT NULL   COMMENT '创建人姓名',
    `create_by`       BIGINT                 DEFAULT NULL   COMMENT '创建人 ID',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`     VARCHAR(64)            DEFAULT NULL   COMMENT '更新人姓名',
    `update_by`       BIGINT                 DEFAULT NULL   COMMENT '更新人 ID',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sys_deleted`     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_execution_no` (`execution_no`),
    KEY `idx_definition_code` (`definition_code`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB COMMENT '采集数据记录表';

-- -----------------------------------------------------
-- 4. 执行日志表（节点级，串联 traceId）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_execution_log` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `execution_no`    VARCHAR(64)    NOT NULL                COMMENT '执行号（traceId）',
    `definition_code` VARCHAR(64)    NOT NULL                COMMENT '爬虫编码',
    `flow_id`         BIGINT                  DEFAULT NULL   COMMENT '流程 ID',
    `node_id`         VARCHAR(64)             DEFAULT NULL   COMMENT '节点 ID',
    `node_name`       VARCHAR(128)            DEFAULT NULL   COMMENT '节点名称',
    `node_type`       VARCHAR(64)             DEFAULT NULL   COMMENT '节点类型',
    `status`          VARCHAR(16)    NOT NULL                COMMENT '状态（RUNNING/SUCCESS/FAIL）',
    `input_data`      TEXT                     DEFAULT NULL  COMMENT '节点输入数据（JSON）',
    `output_data`     TEXT                     DEFAULT NULL  COMMENT '节点输出数据（JSON）',
    `error_message`   VARCHAR(1024)            DEFAULT NULL  COMMENT '失败信息',
    `start_at`        DATETIME                 DEFAULT NULL  COMMENT '节点开始时间',
    `end_at`          DATETIME                 DEFAULT NULL  COMMENT '节点结束时间',
    `create_name`     VARCHAR(64)              DEFAULT NULL  COMMENT '创建人姓名',
    `create_by`       BIGINT                   DEFAULT NULL  COMMENT '创建人 ID',
    `create_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`     VARCHAR(64)              DEFAULT NULL  COMMENT '更新人姓名',
    `update_by`       BIGINT                   DEFAULT NULL  COMMENT '更新人 ID',
    `update_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sys_deleted`     TINYINT        NOT NULL DEFAULT 0      COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_execution_no` (`execution_no`),
    KEY `idx_definition_code` (`definition_code`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT '执行日志表';

-- -----------------------------------------------------
-- 5. 环境变量表（{{env.KEY}} 插值）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `spider_env` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `env_key`     VARCHAR(128) NOT NULL                COMMENT '变量名（如 COOKIE）',
    `env_value`   VARCHAR(2048)         DEFAULT NULL   COMMENT '变量值',
    `description` VARCHAR(256)          DEFAULT NULL   COMMENT '描述',
    `enabled`     TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用（1 启用 / 0 停用）',
    `create_name` VARCHAR(64)           DEFAULT NULL   COMMENT '创建人姓名',
    `create_by`   BIGINT                DEFAULT NULL   COMMENT '创建人 ID',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name` VARCHAR(64)           DEFAULT NULL   COMMENT '更新人姓名',
    `update_by`   BIGINT                DEFAULT NULL   COMMENT '更新人 ID',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sys_deleted` TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除标记（0 未删除 / 1 已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_env_key` (`env_key`)
) ENGINE = InnoDB COMMENT '环境变量表';