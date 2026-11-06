-- =====================================================================
-- V1.0__init_all_api_collect.sql - spring-support-collect-starter 全量 DDL
-- 通道: INIT 同步建表（MySQL 8 语法，H2 MODE=MySQL 回放），索引按 INDEX `表名_idx_/uk_列` 内联声明。
-- 种子数据见 V1.0__initdata_all_api_collect.sql（INITDATA 通道，幂等）。
-- 列名统一带表名前缀，公共审计列沿用 SysBase（create_*/update_*）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 采集分类（树形）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_category` (
    `collect_category_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_category_name` varchar(64) NULL DEFAULT NULL COMMENT '分类名称',
    `collect_category_pid` INT NULL DEFAULT 0 COMMENT '父分类ID，顶级为0',
    `collect_category_tree_id` varchar(255) NULL DEFAULT NULL COMMENT '分类路径链，根到当前节点的ID路径',
    `collect_category_sort` INT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    `collect_category_remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-分类';

-- ---------------------------------------------------------------------
-- 采集流程（可视化编排定义，DSL 为 JSON：nodes + edges + 全局参数）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_flow` (
    `collect_flow_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_flow_name` varchar(128) NULL DEFAULT NULL COMMENT '流程名称',
    `collect_flow_category_id` INT NULL DEFAULT 0 COMMENT '所属分类ID',
    `collect_flow_code` varchar(64) NULL DEFAULT NULL COMMENT '流程编码，全局唯一，供外部调用',
    `collect_flow_dsl` longtext NULL DEFAULT NULL COMMENT '流程图 DSL（JSON）',
    `collect_flow_cron` varchar(64) NULL DEFAULT NULL COMMENT '定时表达式，空表示不定时',
    `collect_flow_status` INT NULL DEFAULT 0 COMMENT '调度状态 - 0:停用, 1:启用',
    `collect_flow_concurrency` INT NULL DEFAULT 1 COMMENT '同一流程允许并发运行的任务数',
    `collect_flow_running` INT NOT NULL DEFAULT 0 COMMENT '当前运行中的任务数（跨节点并发计数）',
    `collect_flow_thread_count` INT NULL DEFAULT 4 COMMENT '流程内并行线程数',
    `collect_flow_remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
    `collect_flow_last_exec_time` datetime(6) NULL DEFAULT NULL COMMENT '最近一次开始执行时间',
    `collect_flow_exec_count` INT NULL DEFAULT 0 COMMENT '累计执行次数',
    `collect_flow_record_count` BIGINT NULL DEFAULT 0 COMMENT '累计采集条数',
    `collect_flow_is_deleted` INT NULL DEFAULT 0 COMMENT '逻辑删除 - 0:正常, 1:已删除',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_flow_id`),
    UNIQUE INDEX `collect_flow_uk_code` (`collect_flow_code`),
    INDEX `collect_flow_idx_category` (`collect_flow_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-流程定义';

-- ---------------------------------------------------------------------
-- 采集数据源（SQL 节点与结果导出目标，凭据以 Base64 存储）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_datasource` (
    `collect_datasource_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_datasource_name` varchar(64) NULL DEFAULT NULL COMMENT '数据源名称',
    `collect_datasource_type` varchar(32) NULL DEFAULT 'MYSQL' COMMENT '类型 - MYSQL/H2/POSTGRESQL/SQLSERVER/ORACLE',
    `collect_datasource_driver_class_name` varchar(255) NULL DEFAULT NULL COMMENT '驱动类名',
    `collect_datasource_jdbc_url` varchar(500) NULL DEFAULT NULL COMMENT '连接串',
    `collect_datasource_username` varchar(64) NULL DEFAULT NULL COMMENT '用户名',
    `collect_datasource_password` varchar(255) NULL DEFAULT NULL COMMENT '密码（b64: 前缀的 Base64）',
    `collect_datasource_status` INT NULL DEFAULT 1 COMMENT '状态 - 0:禁用, 1:启用',
    `collect_datasource_remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-数据源';

-- ---------------------------------------------------------------------
-- 全局变量（流程运行时以 ${name} 引用）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_variable` (
    `collect_variable_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_variable_name` varchar(64) NULL DEFAULT NULL COMMENT '变量名',
    `collect_variable_value` varchar(1000) NULL DEFAULT NULL COMMENT '变量值',
    `collect_variable_encrypted` INT NULL DEFAULT 0 COMMENT '是否加密 - 0:明文, 1:密文（返回时脱敏）',
    `collect_variable_description` varchar(255) NULL DEFAULT NULL COMMENT '说明',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_variable_id`),
    UNIQUE INDEX `collect_variable_uk_name` (`collect_variable_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-全局变量';

-- ---------------------------------------------------------------------
-- 采集任务（一次流程执行实例）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_task` (
    `collect_task_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_task_no` varchar(64) NULL DEFAULT NULL COMMENT '任务号，全局唯一',
    `collect_task_flow_id` INT NULL DEFAULT NULL COMMENT '所属流程ID',
    `collect_task_flow_name` varchar(128) NULL DEFAULT NULL COMMENT '流程名称快照',
    `collect_task_status` varchar(16) NULL DEFAULT NULL COMMENT '状态 - RUNNING/SUCCESS/FAILURE/STOPPED',
    `collect_task_trigger_type` varchar(16) NULL DEFAULT NULL COMMENT '触发方式 - CRON/MANUAL/TEST',
    `collect_task_params` varchar(2000) NULL DEFAULT NULL COMMENT '本次执行的入参覆盖（JSON）',
    `collect_task_begin_time` datetime(6) NULL DEFAULT NULL COMMENT '开始时间',
    `collect_task_end_time` datetime(6) NULL DEFAULT NULL COMMENT '结束时间',
    `collect_task_cost` BIGINT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `collect_task_node_count` INT NULL DEFAULT 0 COMMENT '执行节点次数',
    `collect_task_record_count` INT NULL DEFAULT 0 COMMENT '产出记录数',
    `collect_task_request_count` INT NULL DEFAULT 0 COMMENT '发起抓取次数',
    `collect_task_error` varchar(2000) NULL DEFAULT NULL COMMENT '失败原因',
    `collect_task_executor_id` varchar(64) NULL DEFAULT NULL COMMENT '执行节点标识（实例名+PID）',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_task_id`),
    UNIQUE INDEX `collect_task_uk_no` (`collect_task_no`),
    INDEX `collect_task_idx_flow_begin` (`collect_task_flow_id`, `collect_task_begin_time`),
    INDEX `collect_task_idx_flow_status_id` (`collect_task_flow_id`, `collect_task_status`, `collect_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-执行任务';

-- ---------------------------------------------------------------------
-- 节点执行日志（一次任务内每个节点每次执行一条）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_task_log` (
    `collect_task_log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_task_log_task_id` BIGINT NULL DEFAULT NULL COMMENT '任务ID',
    `collect_task_log_flow_id` INT NULL DEFAULT NULL COMMENT '流程ID',
    `collect_task_log_node_id` varchar(64) NULL DEFAULT NULL COMMENT '节点ID',
    `collect_task_log_node_name` varchar(128) NULL DEFAULT NULL COMMENT '节点名称',
    `collect_task_log_node_shape` varchar(64) NULL DEFAULT NULL COMMENT '节点类型',
    `collect_task_log_iteration` INT NULL DEFAULT 0 COMMENT '循环轮次，从0开始',
    `collect_task_log_status` varchar(16) NULL DEFAULT NULL COMMENT '结果 - SUCCESS/FAILURE/SKIPPED',
    `collect_task_log_cost` BIGINT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `collect_task_log_message` varchar(2000) NULL DEFAULT NULL COMMENT '摘要或异常信息',
    `collect_task_log_output` varchar(2000) NULL DEFAULT NULL COMMENT '出参预览（截断）',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_task_log_id`),
    INDEX `collect_task_log_idx_task` (`collect_task_log_task_id`),
    INDEX `collect_task_log_idx_task_id` (`collect_task_log_task_id`, `collect_task_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-节点执行日志';

-- ---------------------------------------------------------------------
-- 结果字段定义（决定结果表格列、去重键与导出列）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_field` (
    `collect_field_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_field_flow_id` INT NULL DEFAULT NULL COMMENT '所属流程ID',
    `collect_field_node_id` varchar(64) NULL DEFAULT NULL COMMENT '产出该字段的节点ID，空表示流程级',
    `collect_field_name` varchar(64) NULL DEFAULT NULL COMMENT '字段名（记录 payload 的 key）',
    `collect_field_label` varchar(64) NULL DEFAULT NULL COMMENT '列标题',
    `collect_field_type` varchar(16) NULL DEFAULT 'STRING' COMMENT '类型 - STRING/INT/LONG/DECIMAL/DATE/TEXT',
    `collect_field_primary` INT NULL DEFAULT 0 COMMENT '是否业务主键 - 0:否, 1:是（同键覆盖写入）',
    `collect_field_length` INT NULL DEFAULT 0 COMMENT '导出建表时的字符串长度，0 取默认',
    `collect_field_sort` INT NULL DEFAULT 0 COMMENT '列顺序',
    `collect_field_enabled` INT NULL DEFAULT 1 COMMENT '是否启用 - 0:隐藏, 1:显示',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_field_id`),
    INDEX `collect_field_idx_flow_node` (`collect_field_flow_id`, `collect_field_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-结果字段定义';

-- ---------------------------------------------------------------------
-- 采集结果记录（统一存储，payload 为字段 JSON）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_record` (
    `collect_record_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_record_flow_id` INT NULL DEFAULT NULL COMMENT '流程ID',
    `collect_record_task_id` BIGINT NULL DEFAULT NULL COMMENT '产出该记录的任务ID',
    `collect_record_node_id` varchar(64) NULL DEFAULT NULL COMMENT '产出节点ID',
    `collect_record_key` varchar(255) NULL DEFAULT NULL COMMENT '业务主键值，用于同键覆盖',
    `collect_record_url` varchar(1000) NULL DEFAULT NULL COMMENT '来源 URL',
    `collect_record_payload` longtext NULL DEFAULT NULL COMMENT '记录内容（JSON）',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_record_id`),
    INDEX `collect_record_idx_flow_node_key` (`collect_record_flow_id`, `collect_record_node_id`, `collect_record_key`),
    INDEX `collect_record_idx_task` (`collect_record_task_id`),
    INDEX `collect_record_idx_flow_id` (`collect_record_flow_id`, `collect_record_id`),
    INDEX `collect_record_idx_task_id` (`collect_record_task_id`, `collect_record_id`),
    INDEX `collect_record_idx_flow_time` (`collect_record_flow_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-结果记录';

-- ---------------------------------------------------------------------
-- 执行通知（任务终态后按渠道推送；平台级配置，没有流程维度）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_notice` (
    `collect_notice_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_notice_name` varchar(64) NULL DEFAULT NULL COMMENT '通知名称',
    `collect_notice_channel` varchar(32) NULL DEFAULT 'webhook' COMMENT '渠道 - email/webhook，可由 CollectNotifyChannel SPI 扩展',
    `collect_notice_target` varchar(500) NULL DEFAULT NULL COMMENT '收件邮箱（逗号分隔）或回调地址',
    `collect_notice_timing` varchar(16) NULL DEFAULT 'FAILURE' COMMENT '触发时机 - SUCCESS/FAILURE/ALWAYS',
    `collect_notice_subject` varchar(255) NULL DEFAULT NULL COMMENT '标题模板，支持 ${变量}',
    `collect_notice_content` text NULL DEFAULT NULL COMMENT '正文模板，支持 ${变量}',
    `collect_notice_enabled` INT NULL DEFAULT 1 COMMENT '是否启用 - 0:停用, 1:启用',
    `collect_notice_last_result` varchar(500) NULL DEFAULT NULL COMMENT '最近一次发送结果',
    `collect_notice_last_time` datetime(6) NULL DEFAULT NULL COMMENT '最近一次发送时间',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-执行通知';

CREATE TABLE IF NOT EXISTS `collect_function` (
    `collect_function_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_function_name` varchar(64) NOT NULL COMMENT '函数名，小写字母开头的标识符',
    `collect_function_script` text NOT NULL COMMENT '闭包字面量 { a, b -> ... }',
    `collect_function_parameters` INT NULL DEFAULT 0 COMMENT '形参个数，保存时从闭包推导',
    `collect_function_description` varchar(500) NULL DEFAULT NULL COMMENT '用途说明',
    `collect_function_enabled` INT NULL DEFAULT 1 COMMENT '是否启用 - 0:停用, 1:启用',
    `collect_function_last_result` varchar(500) NULL DEFAULT NULL COMMENT '最近一次试跑结果',
    `collect_function_last_time` datetime(6) NULL DEFAULT NULL COMMENT '最近一次试跑时间',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_function_id`),
    UNIQUE INDEX `collect_function_uk_name` (`collect_function_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-自定义函数';

-- ---------------------------------------------------------------------
-- 采集告警规则（失败次数/零产出/数据量骤降/节点超时/错误率）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_alert_rule` (
    `collect_alert_rule_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_flow_id` INT NULL DEFAULT NULL COMMENT '所属流程ID，空表示全局规则',
    `collect_alert_rule_name` varchar(128) NULL DEFAULT NULL COMMENT '规则名称',
    `alert_type` varchar(32) NULL DEFAULT 'FAILURE_COUNT' COMMENT '条件类型 - FAILURE_COUNT/ZERO_OUTPUT/RECORDS_DROP/NODE_TIMEOUT/ERROR_RATE',
    `threshold` INT NULL DEFAULT 0 COMMENT '阈值，含义随 alert_type 而定',
    `alert_timing` varchar(16) NULL DEFAULT 'ALWAYS' COMMENT '告警时机 - ALWAYS/ONCE',
    `alert_enabled` INT NULL DEFAULT 1 COMMENT '是否启用 - 0:停用, 1:启用',
    `last_alert_time` datetime(6) NULL DEFAULT NULL COMMENT '最近触发时间',
    `last_alert_result` varchar(500) NULL DEFAULT NULL COMMENT '最近触发结果',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_alert_rule_id`),
    INDEX `collect_alert_rule_idx_flow` (`collect_flow_id`),
    INDEX `collect_alert_rule_idx_enabled` (`alert_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-告警规则';

-- ---------------------------------------------------------------------
-- 流程版本快照（保存/回滚 DSL）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `collect_flow_version` (
    `collect_flow_version_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_flow_id` INT NULL DEFAULT NULL COMMENT '流程ID',
    `version_no` INT NULL DEFAULT 0 COMMENT '版本号，从1开始递增',
    `flow_dsl` longtext NULL DEFAULT NULL COMMENT '流程 DSL 快照（JSON）',
    `version_remark` varchar(255) NULL DEFAULT NULL COMMENT '版本说明',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_flow_version_id`),
    INDEX `collect_flow_version_idx_flow_no` (`collect_flow_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-流程版本';
