-- ============================================================================
-- V1__init.sql - AI 
--
-- 
--   1.  sys_ai_* nomifun 
--   2. 
--   3.  VARCHAR(36) UUID  BIGINT  BIGINT JSON  TEXT
--
--  V1__ai_manage_schema + V3__bot_binding 
-- ============================================================================

-- moto MYSQL 5.7  CREATE INDEX IF NOT EXISTS 

-- ----------------------------------------------------------------------------
-- Part 1: Provider / Model / Connection / Conversation / Message
-- ----------------------------------------------------------------------------

-- V1__ai_manage_schema.sql - AI 
--
-- 
--   1.  sys_ai_* nomifun 
--   2. 
--   3.  VARCHAR(36) UUID BIGINT JSON  TEXT
--
--  sys_nomifun_*  V2 


CREATE TABLE IF NOT EXISTS `sys_ai_provider` (
    `sys_ai_provider_id`         VARCHAR(64)  NOT NULL COMMENT 'IDUUIDv7',
    `sys_ai_provider_platform`    VARCHAR(64)  NOT NULL COMMENT 'openai/anthropic/google',
    `sys_ai_provider_name`        VARCHAR(255) NOT NULL COMMENT '',
    `sys_ai_provider_base_url`    VARCHAR(512) NOT NULL COMMENT 'APIURL',
    `sys_ai_provider_auth_scheme` VARCHAR(32)  NOT NULL DEFAULT 'api_key' COMMENT 'api_key/bearer/oauth/none',
    `sys_ai_provider_credentials` TEXT         NULL COMMENT 'JSON',
    `sys_ai_provider_enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '',
    `sys_ai_provider_bedrock_config` TEXT      NULL COMMENT 'AWS BedrockJSON',


    `sys_ai_provider_extra`      TEXT NULL COMMENT 'JSON',
    `sys_ai_provider_sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '',
    `sys_ai_provider_create_time` BIGINT       NOT NULL COMMENT '',
    `sys_ai_provider_update_time` BIGINT       NOT NULL COMMENT '',
    PRIMARY KEY (`sys_ai_provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_provider_model` (
    `sys_ai_provider_model_provider_id`    VARCHAR(64)  NOT NULL COMMENT 'ID',
    `sys_ai_provider_model_model`          VARCHAR(255) NOT NULL COMMENT '',
    `sys_ai_provider_model_enabled`        TINYINT      NOT NULL DEFAULT 1 COMMENT '',
    `sys_ai_provider_model_sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '',
    `sys_ai_provider_model_description`    TEXT         NULL COMMENT '',
    `sys_ai_provider_model_capabilities`   TEXT         NULL COMMENT 'JSON',
    `sys_ai_provider_model_metrics`        TEXT         NULL COMMENT 'JSON//',
    `sys_ai_provider_model_create_time`    BIGINT       NOT NULL COMMENT '',
    `sys_ai_provider_model_update_time`    BIGINT       NOT NULL COMMENT '',
    PRIMARY KEY (`sys_ai_provider_model_provider_id`, `sys_ai_provider_model_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_provider_connection` (
    `sys_ai_provider_connection_id`         VARCHAR(64)  NOT NULL COMMENT 'IDUUID',
    `sys_ai_provider_connection_provider_id` VARCHAR(64) NOT NULL COMMENT 'ID',
    `sys_ai_provider_connection_role`       VARCHAR(64)  NOT NULL COMMENT 'default/backup',
    `sys_ai_provider_connection_label`      VARCHAR(255) NULL COMMENT '',
    `sys_ai_provider_connection_base_url`   VARCHAR(512) NULL COMMENT 'URL',
    `sys_ai_provider_connection_auth_scheme` VARCHAR(32) NULL COMMENT '',
    `sys_ai_provider_connection_credentials` TEXT        NULL COMMENT 'JSON',
    `sys_ai_provider_connection_extra`      TEXT        NULL COMMENT 'JSON',
    `sys_ai_provider_connection_create_time` BIGINT      NOT NULL COMMENT '',
    `sys_ai_provider_connection_update_time` BIGINT      NOT NULL COMMENT '',
    PRIMARY KEY (`sys_ai_provider_connection_id`),
    KEY `idx_ai_provider_conn_pid` (`sys_ai_provider_connection_provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_conversation` (
    `sys_ai_conversation_id`                 VARCHAR(36)  NOT NULL COMMENT 'IDUUIDv7',
    `sys_ai_conversation_type`               VARCHAR(32)  NOT NULL DEFAULT 'nomi' COMMENT '',
    `sys_ai_conversation_name`               VARCHAR(255) NULL COMMENT '',
    `sys_ai_conversation_model`              TEXT         NULL COMMENT 'JSONMap',
    `sys_ai_conversation_extra`              TEXT         NULL COMMENT 'JSONMap',
    `sys_ai_conversation_pinned`             TINYINT      NOT NULL DEFAULT 0 COMMENT '',
    `sys_ai_conversation_pinned_at`          BIGINT       NULL COMMENT '',
    `sys_ai_conversation_created_at`         BIGINT       NOT NULL COMMENT '',
    `sys_ai_conversation_modified_at`        BIGINT       NOT NULL COMMENT '',
    `sys_ai_conversation_execution_template_id` VARCHAR(64) NULL COMMENT 'ID',
    PRIMARY KEY (`sys_ai_conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_conversation_message` (
    `sys_ai_conversation_message_id`             VARCHAR(36)  NOT NULL COMMENT 'IDUUIDv7',
    `sys_ai_conversation_message_conversation_id` VARCHAR(36)  NOT NULL COMMENT 'ID',
    `sys_ai_conversation_message_msg_id`         VARCHAR(36)  NULL COMMENT 'ID',
    `sys_ai_conversation_message_type`           VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT '',
    `sys_ai_conversation_message_role`           VARCHAR(32)  NULL COMMENT 'user/assistant',
    `sys_ai_conversation_message_content`        TEXT         NULL COMMENT 'JSON',
    `sys_ai_conversation_message_position`       VARCHAR(16)  NULL COMMENT 'left/right',
    `sys_ai_conversation_message_status`         VARCHAR(32)  NOT NULL DEFAULT 'finish' COMMENT '',
    `sys_ai_conversation_message_hidden`         TINYINT      NOT NULL DEFAULT 0 COMMENT '',
    `sys_ai_conversation_message_created_at`     BIGINT       NOT NULL COMMENT '',
    PRIMARY KEY (`sys_ai_conversation_message_id`),
    KEY `idx_ai_conv_msg_conv` (`sys_ai_conversation_message_conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI ';

-- ----------------------------------------------------------------------------
-- Part 2: Bot  /  / SysBase  DATETIME
-- ----------------------------------------------------------------------------
-- ============================================================================
-- V3__bot_binding.sql -  iLink Bot 
--
-- com.chua.starter.ai.support.entity.SysAiBotBinding
--  BIGINT  SysBase 
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_bot_binding` (
    `sys_ai_bot_binding_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT ' ID',
    `sys_ai_bot_binding_user_id`          BIGINT       NULL COMMENT ' ID',
    `sys_ai_bot_binding_bot_id`          VARCHAR(128) NULL COMMENT 'Bot iLink  botId',
    `sys_ai_bot_binding_bot_token`       VARCHAR(512) NULL COMMENT ' Token',
    `sys_ai_bot_binding_platform`        VARCHAR(32)  NULL COMMENT 'wechat / wecom',
    `sys_ai_bot_binding_nickname`        VARCHAR(128) NULL COMMENT 'Bot ',
    `sys_ai_bot_binding_avatar_url`      VARCHAR(512) NULL COMMENT '',
    `sys_ai_bot_binding_gateway_port`    INT          NULL COMMENT '',
    `sys_ai_bot_binding_status`          VARCHAR(32)  NULL DEFAULT 'offline' COMMENT 'online / offline / expired',
    `sys_ai_bot_binding_bind_source`     VARCHAR(32)  NULL COMMENT 'qrcode_scan / manual_import',
    `sys_ai_bot_binding_last_active_time` DATETIME     NULL COMMENT '',
    `create_name`                         VARCHAR(64)  NULL COMMENT '',
    `create_by`                           INT          NULL COMMENT '',
    `create_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_name`                         VARCHAR(64)  NULL COMMENT '',
    `update_by`                           INT          NULL COMMENT '',
    `update_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
    PRIMARY KEY (`sys_ai_bot_binding_id`),
    KEY `idx_sys_ai_bot_binding_bot_id` (`sys_ai_bot_binding_bot_id`)
);

-- ============================================================================
-- sys_ai_pricing - AI  SysAiPricing 
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_pricing` (
    `sys_ai_pricing_id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT ' ID',
    `sys_ai_pricing_provider`            VARCHAR(64)  NULL COMMENT '',
    `sys_ai_pricing_model`              VARCHAR(128) NULL COMMENT '',
    `sys_ai_pricing_input_price_per_1k`  DOUBLE       NULL COMMENT ' 1k tokens',
    `sys_ai_pricing_output_price_per_1k` DOUBLE      NULL COMMENT ' 1k tokens',
    `sys_ai_pricing_currency`           VARCHAR(8)   NULL COMMENT 'CNY / USD',
    `sys_ai_pricing_effective_date`     DATETIME     NULL COMMENT '',
    `sys_ai_pricing_source`             VARCHAR(32)  NULL COMMENT 'manual / sync',
    `create_name`                        VARCHAR(64)  NULL COMMENT '',
    `create_by`                          INT          NULL COMMENT '',
    `create_time`                        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_name`                        VARCHAR(64)  NULL COMMENT '',
    `update_by`                          INT          NULL COMMENT '',
    `update_time`                        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
    PRIMARY KEY (`sys_ai_pricing_id`)
);

-- ============================================================================
-- sys_ai_usage - AI  SysAiUsage 
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_usage` (
    `sys_ai_usage_id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT ' ID',
    `sys_ai_usage_group_name`           VARCHAR(32)  NULL COMMENT 'chat / image / video / colorize / vision / rag',
    `sys_ai_usage_provider`            VARCHAR(64)  NULL COMMENT 'openai / zhipu / baidu ',
    `sys_ai_usage_model`               VARCHAR(128) NULL COMMENT '',
    `sys_ai_usage_call_type`           VARCHAR(16)  NULL COMMENT 'sync / async',
    `sys_ai_usage_prompt_tokens`        INT          NULL COMMENT ' Token ',
    `sys_ai_usage_completion_tokens`    INT          NULL COMMENT ' Token ',
    `sys_ai_usage_total_tokens`        INT          NULL COMMENT ' Token ',
    `sys_ai_usage_input_cost`          DOUBLE       NULL COMMENT '',
    `sys_ai_usage_output_cost`         DOUBLE       NULL COMMENT '',
    `sys_ai_usage_total_cost`          DOUBLE       NULL COMMENT '',
    `sys_ai_usage_request_id`          VARCHAR(128) NULL COMMENT ' ID',
    `sys_ai_usage_user_id`             VARCHAR(64)  NULL COMMENT '',
    `sys_ai_usage_call_time`           DATETIME     NULL COMMENT '',
    `create_name`                       VARCHAR(64)  NULL COMMENT '',
    `create_by`                         INT          NULL COMMENT '',
    `create_time`                       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_name`                       VARCHAR(64)  NULL COMMENT '',
    `update_by`                         INT          NULL COMMENT '',
    `update_time`                       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
    PRIMARY KEY (`sys_ai_usage_id`),
    KEY `idx_sys_ai_usage_request_id` (`sys_ai_usage_request_id`)
);

-- ============================================================================
-- sys_ai_usage_daily - AI  SysAiUsageDaily 
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_usage_daily` (
    `sys_ai_usage_daily_id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT ' ID',
    `sys_ai_usage_daily_stat_date`              DATE         NULL COMMENT '',
    `sys_ai_usage_daily_group_name`            VARCHAR(32)  NULL COMMENT '',
    `sys_ai_usage_daily_provider`              VARCHAR(64)  NULL COMMENT '',
    `sys_ai_usage_daily_model`                 VARCHAR(128) NULL COMMENT '',
    `sys_ai_usage_daily_total_calls`           INT          NULL COMMENT '',
    `sys_ai_usage_daily_total_prompt_tokens`   INT          NULL COMMENT ' Token',
    `sys_ai_usage_daily_total_completion_tokens` INT       NULL COMMENT ' Token',
    `sys_ai_usage_daily_total_tokens`         INT          NULL COMMENT ' Token',
    `sys_ai_usage_daily_total_cost`           DOUBLE       NULL COMMENT '',
    `create_name`                               VARCHAR(64)  NULL COMMENT '',
    `create_by`                                 INT          NULL COMMENT '',
    `create_time`                               DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_name`                               VARCHAR(64)  NULL COMMENT '',
    `update_by`                                 INT          NULL COMMENT '',
    `update_time`                               DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
    PRIMARY KEY (`sys_ai_usage_daily_id`)
);

-- ============================================================================
-- Agent Browser 核心表
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_agent_browser_lane` (
    `lane_id`                 VARCHAR(64)  NOT NULL COMMENT 'Lane ID',
    `lane_name`               VARCHAR(255) NULL COMMENT 'Lane 显示名称',
    `lifecycle_state`         VARCHAR(32)  NOT NULL DEFAULT 'running' COMMENT '生命周期状态',
    `browser_epoch`           BIGINT       DEFAULT 0 COMMENT '浏览器纪元',
    `user_id`                 VARCHAR(64)  NULL COMMENT '发起用户 ID',
    `conversation_id`         VARCHAR(64)  NULL COMMENT '关联对话 ID',
    `runtime_instance_id`     VARCHAR(64)  NULL COMMENT '运行时实例 ID',
    `execution_id`            VARCHAR(64)  NULL COMMENT '执行 ID',
    `attempt_id`              VARCHAR(64)  NULL COMMENT '尝试 ID',
    `agent_id`                VARCHAR(64)  NULL COMMENT 'Agent ID',
    `agent_name`              VARCHAR(128) NULL COMMENT 'Agent 名称',
    `surface`                 VARCHAR(64)  NULL COMMENT '使用场景',
    `identity_mode`           VARCHAR(32)  DEFAULT 'anonymous' COMMENT '身份模式',
    `identity_label`          VARCHAR(128) NULL COMMENT '身份标签',
    `queue_position`          INT          DEFAULT 0 COMMENT '排队位置',
    `queue_reason_code`       VARCHAR(64)  NULL COMMENT '排队原因',
    `active_tab_id`           VARCHAR(64)  NULL COMMENT '活跃 Tab ID',
    `title`                   VARCHAR(512) NULL COMMENT '标题',
    `url`                     VARCHAR(2048) NULL COMMENT 'URL',
    `resource_estimate_bytes` BIGINT       DEFAULT 0 COMMENT '资源估算字节数',
    `active_operation_count`  INT          DEFAULT 0 COMMENT '活跃操作数',
    `error_code`              VARCHAR(64)  NULL COMMENT '错误代码',
    `error_message`           TEXT         NULL COMMENT '错误信息',
    `recoverable`             BOOLEAN      DEFAULT TRUE COMMENT '是否可恢复',
    `last_active_at`          BIGINT       NULL COMMENT '最后活跃时间',
    `created_at`              BIGINT       NOT NULL COMMENT '创建时间',
    `updated_at`              BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`lane_id`),
    INDEX `idx_lane_conversation` (`conversation_id`),
    INDEX `idx_lane_lifecycle` (`lifecycle_state`),
    INDEX `idx_lane_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器 Lane 信息';

CREATE TABLE IF NOT EXISTS `sys_agent_browser_tab` (
    `tab_id`                  VARCHAR(64)  NOT NULL COMMENT 'Tab ID',
    `lane_id`                 VARCHAR(64)  NOT NULL COMMENT 'Lane ID',
    `title`                   VARCHAR(512) NULL COMMENT '标题',
    `url`                     VARCHAR(2048) NULL COMMENT 'URL',
    `active`                  BOOLEAN      DEFAULT FALSE COMMENT '是否前台',
    `crashed`                 BOOLEAN      DEFAULT FALSE COMMENT '是否崩溃',
    `created_at`              BIGINT       NOT NULL COMMENT '创建时间',
    `updated_at`              BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`tab_id`),
    INDEX `idx_tab_lane` (`lane_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器 Tab 信息';

CREATE TABLE IF NOT EXISTS `sys_agent_browser_host` (
    `host_id`                 VARCHAR(64)  NOT NULL COMMENT 'Host ID',
    `state`                   VARCHAR(32)  NOT NULL DEFAULT 'running' COMMENT '状态',
    `epoch`                   BIGINT       DEFAULT 1 COMMENT '纪元',
    `headful`                 BOOLEAN      DEFAULT FALSE COMMENT '是否有头',
    `identity_mode`           VARCHAR(32)  DEFAULT 'anonymous' COMMENT '身份模式',
    `lane_count`              INT          DEFAULT 0 COMMENT 'Lane 数',
    `rss_bytes`               BIGINT       DEFAULT 0 COMMENT '内存字节数',
    `created_at`              BIGINT       NOT NULL COMMENT '创建时间',
    `updated_at`              BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`host_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器宿主进程信息';

CREATE TABLE IF NOT EXISTS `sys_agent_browser_setting` (
    `setting_key`             VARCHAR(128) NOT NULL COMMENT '设置键',
    `setting_value`           TEXT         NULL COMMENT '设置值',
    `updated_at`              BIGINT       NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器全局设置';

-- ============================================================================



-- ============================================================================
