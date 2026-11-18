-- ---------------------------------------------------------------------------
-- 渠道管理整体归还账号模块：渠道 / 渠道模型 / 渠道连接 / 渠道健康
--
-- 背景：渠道管理属于账号模块（渠道 SPI 实现也都在 account-starter）。此前
-- 渠道的开关、模型行、连接凭据与健康记录被放进了 AI 模块的表：
--   sys_ai_provider / sys_ai_provider_model / sys_ai_provider_connection / sys_ai_channel_health
-- 导致「免费渠道模型清单」读的是 AI 模块的表。本脚本按账号模块命名口径重建：
--   sys_account_channel              （渠道：开关 + 上游信息）
--   sys_account_channel_model        （渠道模型：模型级开关）
--   sys_account_channel_connection   （渠道连接：绑定凭据）
--   sys_account_channel_health       （渠道健康：探针状态机）
-- 并把存量数据从旧表复制过来（旧表保留只读，不再被任何代码读写）。
--
-- 约定：脚本会被重复执行，一律 CREATE TABLE IF NOT EXISTS；
--       数据复制用 information_schema 判存 + 动态 SQL，旧表不存在时跳过。
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `sys_account_channel` (
    `sys_account_channel_id`              VARCHAR(64)  NOT NULL COMMENT '渠道ID（UUIDv7）',
    `sys_account_channel_platform`        VARCHAR(64)  NOT NULL COMMENT '平台类型 / 渠道键（openai/anthropic/google 等）',
    `sys_account_channel_name`            VARCHAR(255) NOT NULL COMMENT '渠道展示名',
    `sys_account_channel_base_url`        VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'API 基础地址（免费渠道为空串）',
    `sys_account_channel_auth_scheme`     VARCHAR(32)  NOT NULL DEFAULT 'api_key' COMMENT '认证方式（api_key/bearer/oauth/none）',
    `sys_account_channel_credentials`     TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `sys_account_channel_enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '渠道是否启用',
    `sys_account_channel_bedrock_config`  TEXT         NULL COMMENT 'AWS Bedrock配置JSON',
    `sys_account_channel_extra`           TEXT         NULL COMMENT '扩展配置JSON（如自定义请求头）',
    `sys_account_channel_proxy`           VARCHAR(512) NULL COMMENT 'HTTP代理地址',
    `sys_account_channel_sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_account_channel_create_time`     BIGINT       NOT NULL DEFAULT 0 COMMENT '创建时间（毫秒）',
    `sys_account_channel_update_time`     BIGINT       NOT NULL DEFAULT 0 COMMENT '更新时间（毫秒）',
    `sys_account_channel_type`            VARCHAR(64)  NULL DEFAULT 'custom' COMMENT '渠道类型（openai/anthropic/gemini/qwen/custom）',
    `sys_account_channel_api_key`         VARCHAR(512) NULL COMMENT '渠道 API Key（旧列，保留兼容）',
    `sys_account_channel_logo`            VARCHAR(1024) NULL COMMENT 'Logo 图片地址',
    `sys_account_channel_models`          TEXT         NULL COMMENT '可用模型列表（JSON 字符串，旧列）',
    `sys_account_channel_deleted`         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `create_name` VARCHAR(64) NULL COMMENT '创建人姓名',
    `create_by`   INT         NULL COMMENT '创建人',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_name` VARCHAR(64) NULL COMMENT '更新人姓名',
    `update_by`   INT         NULL COMMENT '更新人',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`sys_account_channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道';

CREATE TABLE IF NOT EXISTS `sys_account_channel_model` (
    `sys_account_channel_model_provider_id`        VARCHAR(64)  NOT NULL COMMENT '所属渠道ID',
    `sys_account_channel_model_model`              VARCHAR(255) NOT NULL COMMENT '模型名称',
    `sys_account_channel_model_alias`              VARCHAR(128) NULL COMMENT '模型别名',
    `sys_account_channel_model_enabled`            TINYINT      NOT NULL DEFAULT 0 COMMENT '模型是否启用（同步进来默认关）',
    `sys_account_channel_model_sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_account_channel_model_description`        TEXT         NULL COMMENT '模型描述',
    `sys_account_channel_model_capabilities`       TEXT         NULL COMMENT '任务能力JSON数组',
    `sys_account_channel_model_metrics`            TEXT         NULL COMMENT '模型指标JSON（延迟/智能/输出速度等）',
    `sys_account_channel_model_context_window`     INT          NULL COMMENT '上下文窗口',
    `sys_account_channel_model_max_tokens`         INT          NULL COMMENT '最大输出令牌',
    `sys_account_channel_model_temperature`        DOUBLE       NULL COMMENT '温度',
    `sys_account_channel_model_top_p`              DOUBLE       NULL COMMENT 'Top-P 采样',
    `sys_account_channel_model_frequency_penalty`  DOUBLE       NULL COMMENT '频率惩罚',
    `sys_account_channel_model_family`             VARCHAR(255) NULL COMMENT '模型系列（family）',
    `sys_account_channel_model_knowledge`          VARCHAR(255) NULL COMMENT '知识截止日期',
    `sys_account_channel_model_release_date`       VARCHAR(64)  NULL COMMENT '发布日期',
    `sys_account_channel_model_last_updated`       VARCHAR(64)  NULL COMMENT '最后更新时间',
    `sys_account_channel_model_modalities`         TEXT         NULL COMMENT '输入/输出模态JSON',
    `sys_account_channel_model_open_weights`       TINYINT      NULL COMMENT '是否开源权重',
    `sys_account_channel_model_attachment`         TINYINT      NULL COMMENT '是否支持附件',
    `sys_account_channel_model_reasoning`          TINYINT      NULL COMMENT '是否支持深度思考',
    `sys_account_channel_model_tool_call`          TINYINT      NULL COMMENT '是否支持工具调用',
    `sys_account_channel_model_temperature_supported` TINYINT   NULL COMMENT '是否支持温度调节',
    `sys_account_channel_model_create_time`        BIGINT       NOT NULL DEFAULT 0 COMMENT '创建时间（毫秒）',
    `sys_account_channel_model_update_time`        BIGINT       NOT NULL DEFAULT 0 COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_model_provider_id`, `sys_account_channel_model_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道模型';

CREATE TABLE IF NOT EXISTS `sys_account_channel_connection` (
    `sys_account_channel_connection_id`            VARCHAR(64)  NOT NULL COMMENT '连接ID（UUID）',
    `sys_account_channel_connection_provider_id`   VARCHAR(64)  NOT NULL COMMENT '所属渠道ID',
    `sys_account_channel_connection_role`          VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '连接角色（default/backup等）',
    `sys_account_channel_connection_account_type`  VARCHAR(16)  NOT NULL DEFAULT 'FIXED' COMMENT '账号类型（FIXED/TEMPORARY）',
    `sys_account_channel_connection_label`         VARCHAR(255) NULL COMMENT '连接标签',
    `sys_account_channel_connection_user_id`       VARCHAR(64)  NULL COMMENT '归属用户（账号隔离）',
    `sys_account_channel_connection_base_url`      VARCHAR(512) NULL COMMENT '覆盖的基础URL',
    `sys_account_channel_connection_auth_scheme`   VARCHAR(32)  NULL COMMENT '覆盖的认证方式',
    `sys_account_channel_connection_credentials`   TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `sys_account_channel_connection_extra`         TEXT         NULL COMMENT '扩展字段JSON',
    `sys_account_channel_connection_create_time`   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建时间（毫秒）',
    `sys_account_channel_connection_update_time`   BIGINT       NOT NULL DEFAULT 0 COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_connection_id`),
    KEY `idx_account_channel_conn_pid` (`sys_account_channel_connection_provider_id`),
    KEY `idx_account_channel_conn_user` (`sys_account_channel_connection_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道连接';

CREATE TABLE IF NOT EXISTS `sys_account_channel_health` (
    `sys_account_channel_health_id` VARCHAR(36) NOT NULL COMMENT '健康记录标识',
    `provider_id`   VARCHAR(64) NOT NULL COMMENT '渠道标识（UUID）',
    `connection_id` VARCHAR(36)  NOT NULL COMMENT '连接标识（UUID）',
    `channel`       VARCHAR(64)  NOT NULL COMMENT '渠道键（平台）',
    `label`         VARCHAR(128) DEFAULT NULL COMMENT '渠道展示名',
    `state`         VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态 (UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED)',
    `circuit_state` VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT '人工熔断状态（AUTO/OPEN/CLOSED）',
    `circuit_reason` VARCHAR(512) DEFAULT NULL COMMENT '人工熔断/放行的原因说明',
    `circuit_updated_at` BIGINT  DEFAULT NULL COMMENT '人工熔断状态变更时间（毫秒）',
    `failures`      BIGINT NOT NULL DEFAULT 0 COMMENT '累计失败次数',
    `successes`     BIGINT NOT NULL DEFAULT 0 COMMENT '累计成功次数',
    `consecutive_failures`  INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `consecutive_successes` INT NOT NULL DEFAULT 0 COMMENT '连续成功次数',
    `last_probe_at`      BIGINT DEFAULT NULL COMMENT '最近探针时间（毫秒）',
    `last_probe_status`  INT    DEFAULT NULL COMMENT '最近探针 HTTP 状态码',
    `last_probe_latency` INT    DEFAULT NULL COMMENT '最近探针延迟（毫秒）',
    `last_probe_error`   VARCHAR(512) DEFAULT NULL COMMENT '最近探针错误信息',
    `average_latency`    INT    DEFAULT NULL COMMENT '平均延迟（毫秒）',
    `total_failures`     BIGINT DEFAULT 0 COMMENT '计费/熔断口径累计失败次数',
    `interval_seconds`   INT NOT NULL DEFAULT 0 COMMENT '探针间隔秒（0=走全局设置）',
    `enabled`            TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用探针',
    `created_at`         BIGINT DEFAULT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_account_channel_health_id`),
    UNIQUE KEY `uk_provider_connection` (`provider_id`, `connection_id`),
    KEY `idx_state` (`state`),
    KEY `idx_channel` (`channel`),
    KEY `idx_updated` (`updated_at`),
    KEY `idx_circuit_state` (`circuit_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道管理-渠道健康探针记录';

-- ---------------------------------------------------------------------------
-- 存量数据复制（旧表不存在时整段跳过；已存在的行不覆盖）
-- ---------------------------------------------------------------------------

SET @copy_sql = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider'),
        CONCAT('INSERT IGNORE INTO `sys_account_channel` (',
               '`sys_account_channel_id`,`sys_account_channel_platform`,`sys_account_channel_name`,',
               '`sys_account_channel_base_url`,`sys_account_channel_auth_scheme`,`sys_account_channel_credentials`,',
               '`sys_account_channel_enabled`,`sys_account_channel_bedrock_config`,`sys_account_channel_extra`,',
               '`sys_account_channel_proxy`,`sys_account_channel_sort_order`,`sys_account_channel_create_time`,',
               '`sys_account_channel_update_time`,`sys_account_channel_type`,`sys_account_channel_api_key`,',
               '`sys_account_channel_logo`,`sys_account_channel_models`,`sys_account_channel_deleted`) ',
               'SELECT `sys_ai_provider_id`,`sys_ai_provider_platform`,`sys_ai_provider_name`,',
               '`sys_ai_provider_base_url`,`sys_ai_provider_auth_scheme`,`sys_ai_provider_credentials`,',
               '`sys_ai_provider_enabled`,`sys_ai_provider_bedrock_config`,`sys_ai_provider_extra`,',
               '`sys_ai_provider_proxy`,`sys_ai_provider_sort_order`,`sys_ai_provider_create_time`,',
               '`sys_ai_provider_update_time`,`sys_ai_provider_type`,`sys_ai_provider_api_key`,',
               '`sys_ai_provider_logo`,`sys_ai_provider_models`,`sys_ai_provider_deleted` ',
               'FROM `sys_ai_provider`'),
        'SELECT 1'));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;

SET @copy_sql = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider_model'),
        CONCAT('INSERT IGNORE INTO `sys_account_channel_model` (',
               '`sys_account_channel_model_provider_id`,`sys_account_channel_model_model`,`sys_account_channel_model_alias`,',
               '`sys_account_channel_model_enabled`,`sys_account_channel_model_sort_order`,`sys_account_channel_model_description`,',
               '`sys_account_channel_model_capabilities`,`sys_account_channel_model_metrics`,`sys_account_channel_model_context_window`,',
               '`sys_account_channel_model_max_tokens`,`sys_account_channel_model_temperature`,`sys_account_channel_model_top_p`,',
               '`sys_account_channel_model_frequency_penalty`,`sys_account_channel_model_family`,`sys_account_channel_model_knowledge`,',
               '`sys_account_channel_model_release_date`,`sys_account_channel_model_last_updated`,`sys_account_channel_model_modalities`,',
               '`sys_account_channel_model_open_weights`,`sys_account_channel_model_attachment`,`sys_account_channel_model_reasoning`,',
               '`sys_account_channel_model_tool_call`,`sys_account_channel_model_temperature_supported`,',
               '`sys_account_channel_model_create_time`,`sys_account_channel_model_update_time`) ',
               'SELECT `sys_ai_provider_model_provider_id`,`sys_ai_provider_model_model`,`sys_ai_provider_model_alias`,',
               '`sys_ai_provider_model_enabled`,`sys_ai_provider_model_sort_order`,`sys_ai_provider_model_description`,',
               '`sys_ai_provider_model_capabilities`,`sys_ai_provider_model_metrics`,`sys_ai_provider_model_context_window`,',
               '`sys_ai_provider_model_max_tokens`,`sys_ai_provider_model_temperature`,`sys_ai_provider_model_top_p`,',
               '`sys_ai_provider_model_frequency_penalty`,`sys_ai_provider_model_family`,`sys_ai_provider_model_knowledge`,',
               '`sys_ai_provider_model_release_date`,`sys_ai_provider_model_last_updated`,`sys_ai_provider_model_modalities`,',
               '`sys_ai_provider_model_open_weights`,`sys_ai_provider_model_attachment`,`sys_ai_provider_model_reasoning`,',
               '`sys_ai_provider_model_tool_call`,`sys_ai_provider_model_temperature_supported`,',
               '`sys_ai_provider_model_create_time`,`sys_ai_provider_model_update_time` ',
               'FROM `sys_ai_provider_model`'),
        'SELECT 1'));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;

SET @copy_sql = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider_connection'),
        CONCAT('INSERT IGNORE INTO `sys_account_channel_connection` (',
               '`sys_account_channel_connection_id`,`sys_account_channel_connection_provider_id`,',
               '`sys_account_channel_connection_role`,`sys_account_channel_connection_account_type`,',
               '`sys_account_channel_connection_label`,`sys_account_channel_connection_base_url`,',
               '`sys_account_channel_connection_auth_scheme`,`sys_account_channel_connection_credentials`,',
               '`sys_account_channel_connection_extra`,`sys_account_channel_connection_create_time`,',
               '`sys_account_channel_connection_update_time`) ',
               'SELECT `sys_ai_provider_connection_id`,`sys_ai_provider_connection_provider_id`,',
               '`sys_ai_provider_connection_role`,`sys_ai_provider_connection_account_type`,',
               '`sys_ai_provider_connection_label`,`sys_ai_provider_connection_base_url`,',
               '`sys_ai_provider_connection_auth_scheme`,`sys_ai_provider_connection_credentials`,',
               '`sys_ai_provider_connection_extra`,`sys_ai_provider_connection_create_time`,',
               '`sys_ai_provider_connection_update_time` ',
               'FROM `sys_ai_provider_connection`'),
        'SELECT 1'));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;

-- 归属用户列由运行期补丁创建，旧表可能没有该列：有则回填
SET @copy_sql = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_provider_connection'
                       AND COLUMN_NAME = 'sys_ai_provider_connection_user_id'),
        'UPDATE `sys_account_channel_connection` c JOIN `sys_ai_provider_connection` o '
        'ON c.`sys_account_channel_connection_id` = o.`sys_ai_provider_connection_id` '
        'SET c.`sys_account_channel_connection_user_id` = o.`sys_ai_provider_connection_user_id`',
        'SELECT 1'));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;

SET @copy_sql = (
    SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_channel_health'),
        CONCAT('INSERT IGNORE INTO `sys_account_channel_health` (',
               '`sys_account_channel_health_id`,`provider_id`,`connection_id`,`channel`,`label`,`state`,',
               '`circuit_state`,`circuit_reason`,`circuit_updated_at`,`failures`,`successes`,',
               '`consecutive_failures`,`consecutive_successes`,`last_probe_at`,`last_probe_status`,',
               '`last_probe_latency`,`last_probe_error`,`average_latency`,`total_failures`,',
               '`interval_seconds`,`enabled`,`created_at`,`updated_at`) ',
               'SELECT `sys_ai_channel_health_id`,`provider_id`,`connection_id`,`channel`,`label`,`state`,',
               '`circuit_state`,`circuit_reason`,`circuit_updated_at`,`failures`,`successes`,',
               '`consecutive_failures`,`consecutive_successes`,`last_probe_at`,`last_probe_status`,',
               '`last_probe_latency`,`last_probe_error`,`average_latency`,`total_failures`,',
               '`interval_seconds`,`enabled`,`created_at`,`updated_at` ',
               'FROM `sys_ai_channel_health`'),
        'SELECT 1'));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;
