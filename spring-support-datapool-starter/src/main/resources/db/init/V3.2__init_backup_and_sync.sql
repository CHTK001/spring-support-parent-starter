-- =====================================================================
-- V3.2__init_backup_and_sync.sql - 数据库备份与数据同步表结构
-- 通道: INIT 同步建表
-- 归位说明: ms_backup_history / ms_sync_task / ms_sync_task_run_log 原建在
--   spring-support-monitor-starter 的 V5.2__init_all_monitor.sql 中，
--   现随数据库备份中心与数据同步模块整体迁入 spring-support-datapool-starter。
-- 幂等: 全文件只含 CREATE TABLE IF NOT EXISTS，兼容存量库与新装库。
-- =====================================================================

-- ----------------------------------------------------------
-- 1. 数据库备份历史（备份中心）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_backup_history` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `record_id`        VARCHAR(64)  DEFAULT NULL            COMMENT '备份记录ID(批次标识)',
    `db_name`          VARCHAR(128) DEFAULT NULL            COMMENT '数据库名',
    `success`          TINYINT      DEFAULT NULL            COMMENT '结果: 1 成功 / 0 失败',
    `backup_path`      VARCHAR(512) DEFAULT NULL            COMMENT '备份产物路径',
    `manifest_path`    VARCHAR(512) DEFAULT NULL            COMMENT '备份清单路径',
    `file_count`       INT          DEFAULT 0               COMMENT '产物文件数',
    `total_size_bytes` BIGINT       DEFAULT 0               COMMENT '产物总大小(字节)',
    `duration_millis`  BIGINT       DEFAULT 0               COMMENT '耗时(毫秒)',
    `start_time`       VARCHAR(48)  DEFAULT NULL            COMMENT '开始时间(ISO-8601)',
    `end_time`         VARCHAR(48)  DEFAULT NULL            COMMENT '结束时间(ISO-8601)',
    `error_message`    TEXT         DEFAULT NULL            COMMENT '错误信息',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '落库时间',
    PRIMARY KEY (`id`),
    KEY `ms_backup_history_idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据库备份历史';

-- ----------------------------------------------------------
-- 2. 数据同步任务
-- ----------------------------------------------------------
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据同步任务';

-- ----------------------------------------------------------
-- 3. 数据同步任务执行过程日志
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据同步任务执行过程日志';
