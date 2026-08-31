-- ============================================================================
-- V1__ai_manage_schema.sql - AI 中台管理域表结构（规范化命名）
--
-- 命名规范：
--   1. 表名统一 sys_ai_*（去掉 nomifun 遗留前缀）
--   2. 所有列名以「表名」作为前缀
--   3. 主键 VARCHAR(36) UUID；时间戳 BIGINT 毫秒；JSON 用 TEXT
--
-- 说明：本脚本为全新规范建表，旧 sys_nomifun_* 表由 V2 清理脚本负责删表。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_provider` (
    `sys_ai_provider_id`         VARCHAR(64)  NOT NULL COMMENT '提供商ID（UUIDv7）',
    `sys_ai_provider_platform`    VARCHAR(64)  NOT NULL COMMENT '平台类型（openai/anthropic/google等）',
    `sys_ai_provider_name`        VARCHAR(255) NOT NULL COMMENT '提供商名称',
    `sys_ai_provider_base_url`    VARCHAR(512) NOT NULL COMMENT 'API基础URL',
    `sys_ai_provider_auth_scheme` VARCHAR(32)  NOT NULL DEFAULT 'api_key' COMMENT '认证方式（api_key/bearer/oauth/none）',
    `sys_ai_provider_credentials` TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `sys_ai_provider_enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_provider_bedrock_config` TEXT      NULL COMMENT 'AWS Bedrock配置JSON',
    `sys_ai_provider_extra`      TEXT         NULL COMMENT '扩展配置JSON（如自定义请求头）',
    `sys_ai_provider_proxy`      VARCHAR(512) NULL COMMENT 'HTTP代理地址（如 http://127.0.0.1:7890、socks5://127.0.0.1:1080）',
    `sys_ai_provider_sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_ai_provider_create_time` BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_provider_update_time` BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提供商';

CREATE TABLE IF NOT EXISTS `sys_ai_provider_model` (
    `sys_ai_provider_model_provider_id`    VARCHAR(64)  NOT NULL COMMENT '所属提供商ID',
    `sys_ai_provider_model_model`          VARCHAR(255) NOT NULL COMMENT '模型名称',
    `sys_ai_provider_model_enabled`        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_provider_model_sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_ai_provider_model_description`    TEXT         NULL COMMENT '模型描述',
    `sys_ai_provider_model_capabilities`   TEXT         NULL COMMENT '任务能力JSON数组',
    `sys_ai_provider_model_metrics`        TEXT         NULL COMMENT '模型指标JSON（延迟/智能/输出速度等）',
    `sys_ai_provider_model_create_time`    BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_provider_model_update_time`    BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_provider_model_provider_id`, `sys_ai_provider_model_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提供商模型';

CREATE TABLE IF NOT EXISTS `sys_ai_provider_connection` (
    `sys_ai_provider_connection_id`         VARCHAR(64)  NOT NULL COMMENT '连接ID（UUID）',
    `sys_ai_provider_connection_provider_id` VARCHAR(64) NOT NULL COMMENT '所属提供商ID',
    `sys_ai_provider_connection_role`       VARCHAR(64)  NOT NULL COMMENT '连接角色（default/backup等）',
    `sys_ai_provider_connection_label`      VARCHAR(255) NULL COMMENT '连接标签',
    `sys_ai_provider_connection_base_url`   VARCHAR(512) NULL COMMENT '覆盖的基础URL',
    `sys_ai_provider_connection_auth_scheme` VARCHAR(32) NULL COMMENT '覆盖的认证方式',
    `sys_ai_provider_connection_credentials` TEXT        NULL COMMENT '凭证JSON（写入后不回显）',
    `sys_ai_provider_connection_extra`      TEXT        NULL COMMENT '扩展字段JSON',
    `sys_ai_provider_connection_create_time` BIGINT      NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_provider_connection_update_time` BIGINT      NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_provider_connection_id`),
    KEY `idx_ai_provider_conn_pid` (`sys_ai_provider_connection_provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提供商连接';

CREATE TABLE IF NOT EXISTS `sys_ai_conversation` (
    `sys_ai_conversation_id`                 VARCHAR(36)  NOT NULL COMMENT '会话ID（UUIDv7）',
    `sys_ai_conversation_type`               VARCHAR(32)  NOT NULL DEFAULT 'nomi' COMMENT '会话类型',
    `sys_ai_conversation_name`               VARCHAR(255) NULL COMMENT '会话标题',
    `sys_ai_conversation_model`              TEXT         NULL COMMENT '模型配置JSON（Map）',
    `sys_ai_conversation_extra`              TEXT         NULL COMMENT '扩展字段JSON（Map）',
    `sys_ai_conversation_pinned`             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `sys_ai_conversation_pinned_at`          BIGINT       NULL COMMENT '置顶时间（毫秒）',
    `sys_ai_conversation_created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_conversation_modified_at`        BIGINT       NOT NULL COMMENT '最后修改时间（毫秒）',
    `sys_ai_conversation_execution_template_id` VARCHAR(64) NULL COMMENT '执行模板ID',
    PRIMARY KEY (`sys_ai_conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话';

CREATE TABLE IF NOT EXISTS `sys_ai_conversation_message` (
    `sys_ai_conversation_message_id`             VARCHAR(36)  NOT NULL COMMENT '消息ID（UUIDv7）',
    `sys_ai_conversation_message_conversation_id` VARCHAR(36)  NOT NULL COMMENT '所属会话ID',
    `sys_ai_conversation_message_msg_id`         VARCHAR(36)  NULL COMMENT '消息对外ID',
    `sys_ai_conversation_message_type`           VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT '消息类型',
    `sys_ai_conversation_message_role`           VARCHAR(32)  NULL COMMENT '角色（user/assistant）',
    `sys_ai_conversation_message_content`        TEXT         NULL COMMENT '消息内容（文本或JSON）',
    `sys_ai_conversation_message_position`       VARCHAR(16)  NULL COMMENT '气泡位置（left/right）',
    `sys_ai_conversation_message_status`         VARCHAR(32)  NOT NULL DEFAULT 'finish' COMMENT '消息状态',
    `sys_ai_conversation_message_hidden`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否隐藏',
    `sys_ai_conversation_message_created_at`     BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`sys_ai_conversation_message_id`),
    KEY `idx_ai_conv_msg_conv` (`sys_ai_conversation_message_conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话消息';