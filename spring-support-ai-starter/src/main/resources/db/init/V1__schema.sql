-- ============================================================================
-- V1__schema.sql - AI 中台全部表结构（DDL）
--
-- 包含：sys_ai_*（AI 核心模块）、sys_nomifun_*（nomifun 会话/消息/供应商）、
--       sys_agent_browser_*（Agent 浏览器）
-- 说明：主键 VARCHAR(36) UUID；时间戳 BIGINT 毫秒；JSON 用 TEXT 存储。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_nomifun_conversation` (
    `conversation_id`        VARCHAR(36)  NOT NULL COMMENT '会话ID（UUID）',
    `type`                   VARCHAR(32)  NOT NULL DEFAULT 'nomi' COMMENT '会话类型',
    `name`                   VARCHAR(255) NULL COMMENT '会话标题',
    `model`                  TEXT         NULL COMMENT '模型配置JSON（Map）',
    `extra`                  TEXT         NULL COMMENT '扩展字段JSON（Map）',
    `pinned`                 TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `pinned_at`              BIGINT       NULL COMMENT '置顶时间（毫秒）',
    `created_at`             BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `modified_at`            BIGINT       NOT NULL COMMENT '最后修改时间（毫秒）',
    `execution_template_id`  VARCHAR(64)  NULL COMMENT '执行模板ID',
    PRIMARY KEY (`conversation_id`)
);

CREATE TABLE IF NOT EXISTS `sys_nomifun_message` (
    `message_id`       VARCHAR(36)  NOT NULL COMMENT '消息ID（UUID）',
    `conversation_id`  VARCHAR(36)  NOT NULL COMMENT '所属会话ID（UUID）',
    `msg_id`           VARCHAR(36)  NULL COMMENT '消息对外ID' ,
    `type`             VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT '消息类型',
    `role`             VARCHAR(32)  NULL COMMENT '角色（user/assistant）',
    `content`          TEXT         NULL COMMENT '消息内容（文本或JSON）',
    `position`         VARCHAR(16)  NULL COMMENT '气泡位置（left/right）',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'finish' COMMENT '消息状态',
    `hidden`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否隐藏',
    `created_at`       BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`message_id`),
    KEY `idx_sys_nomifun_msg_conv` (`conversation_id`)
);

CREATE TABLE IF NOT EXISTS `sys_nomifun_provider` (
    `provider_id`        VARCHAR(64)  NOT NULL COMMENT '提供商ID（UUID）',
    `platform`           VARCHAR(64)  NOT NULL COMMENT '平台类型（openai/anthropic/google等）',
    `name`               VARCHAR(255) NOT NULL COMMENT '提供商名称',
    `base_url`           VARCHAR(512) NOT NULL COMMENT 'API基础URL',
    `auth_scheme`        VARCHAR(32)  NOT NULL DEFAULT 'api_key' COMMENT '认证方式（api_key/bearer/oauth/none）',
    `credentials`        TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `enabled`            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `bedrock_config`     TEXT         NULL COMMENT 'AWS Bedrock配置JSON',
    `sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`provider_id`)
);

CREATE TABLE IF NOT EXISTS `sys_nomifun_provider_model` (
    `provider_id`        VARCHAR(64)  NOT NULL COMMENT '所属供应商ID',
    `model`              VARCHAR(255) NOT NULL COMMENT '模型名称',
    `enabled`            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `description`        TEXT         NULL COMMENT '模型描述',
    `capabilities`       TEXT         NULL COMMENT '任务能力JSON数组',
    `metrics`            JSON         NULL COMMENT '模型指标JSON（延迟/智能/输出速度）',
    `created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`provider_id`, `model`)
);

CREATE TABLE IF NOT EXISTS `sys_nomifun_provider_connection` (
    `connection_id`      VARCHAR(64)  NOT NULL COMMENT '连接ID（UUID）',
    `provider_id`        VARCHAR(64)  NOT NULL COMMENT '所属提供商ID',
    `role`               VARCHAR(64)  NOT NULL COMMENT '连接角色（default/backup等）',
    `label`              VARCHAR(255) NULL COMMENT '连接标签',
    `base_url`           VARCHAR(512) NULL COMMENT '覆盖的基础URL',
    `auth_scheme`        VARCHAR(32)  NULL COMMENT '覆盖的认证方式',
    `credentials`        TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `extra`              TEXT         NULL COMMENT '扩展字段JSON',
    `created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`connection_id`),
    KEY `idx_nomifun_prov_conn_pid` (`provider_id`)
);

CREATE TABLE IF NOT EXISTS sys_agent_browser_lane (

    lane_id             VARCHAR(64)     PRIMARY KEY,

    lane_name           VARCHAR(255),

    lifecycle_state     VARCHAR(32)     NOT NULL DEFAULT 'running',


    browser_epoch       BIGINT          DEFAULT 0,


    user_id             VARCHAR(64),

    conversation_id     VARCHAR(64),

    runtime_instance_id VARCHAR(64),

    execution_id        VARCHAR(64),

    attempt_id          VARCHAR(64),

    agent_id            VARCHAR(64),

    agent_name          VARCHAR(128),

    surface             VARCHAR(64),


    identity_mode       VARCHAR(32)     DEFAULT 'anonymous',

    identity_label      VARCHAR(128),


    queue_position      INT             DEFAULT 0,

    queue_reason_code   VARCHAR(64),


    active_tab_id       VARCHAR(64),

    title               VARCHAR(512),

    url                 VARCHAR(2048),


    resource_estimate_bytes BIGINT      DEFAULT 0,

    active_operation_count  INT         DEFAULT 0,


    error_code          VARCHAR(64),

    error_message       TEXT,

    recoverable         BOOLEAN         DEFAULT TRUE,


    last_active_at      BIGINT,

    created_at          BIGINT          NOT NULL,

    updated_at          BIGINT          NOT NULL,

    INDEX idx_lane_conversation (conversation_id),
    INDEX idx_lane_lifecycle (lifecycle_state),
    INDEX idx_lane_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器 Lane 信息';

CREATE TABLE IF NOT EXISTS sys_agent_browser_tab (

    tab_id              VARCHAR(64)     PRIMARY KEY,

    lane_id             VARCHAR(64)     NOT NULL,

    title               VARCHAR(512),

    url                 VARCHAR(2048),

    active              BOOLEAN         DEFAULT FALSE,

    crashed             BOOLEAN         DEFAULT FALSE,

    created_at          BIGINT          NOT NULL,

    updated_at          BIGINT          NOT NULL,

    INDEX idx_tab_lane (lane_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器 Tab 信息';

CREATE TABLE IF NOT EXISTS sys_agent_browser_host (

    host_id             VARCHAR(64)     PRIMARY KEY,

    state               VARCHAR(32)     NOT NULL DEFAULT 'running',


    epoch               BIGINT          DEFAULT 1,


    headful             BOOLEAN         DEFAULT FALSE,

    identity_mode       VARCHAR(32)     DEFAULT 'anonymous',

    lane_count          INT             DEFAULT 0,

    rss_bytes           BIGINT          DEFAULT 0,

    created_at          BIGINT          NOT NULL,

    updated_at          BIGINT          NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器宿主进程信息';

CREATE TABLE IF NOT EXISTS sys_agent_browser_setting (

    setting_key         VARCHAR(128)    PRIMARY KEY,




    setting_value       TEXT,

    updated_at          BIGINT          NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览器全局设置';
