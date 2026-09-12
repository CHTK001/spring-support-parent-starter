-- ============================================================================
-- schema.sql - AI 中台全部表结构（DDL）
-- 由 db/init/*.sql 合并生成。
-- ============================================================================

-- 1. 清理遗留表
DROP TABLE IF EXISTS `sys_nomifun_provider_connection`;

DROP TABLE IF EXISTS `sys_nomifun_provider_model`;

DROP TABLE IF EXISTS `sys_nomifun_provider`;

DROP TABLE IF EXISTS `sys_nomifun_message`;

DROP TABLE IF EXISTS `sys_nomifun_conversation`;

-- 2. 表结构
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

CREATE TABLE IF NOT EXISTS `sys_ai_bot_binding` (
    `sys_ai_bot_binding_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '绑定 ID',
    `sys_ai_bot_binding_user_id`          BIGINT       NULL COMMENT '关联用户 ID',
    `sys_ai_bot_binding_bot_id`          VARCHAR(128) NULL COMMENT 'Bot 唯一标识（iLink 分配的 botId）',
    `sys_ai_bot_binding_bot_token`       VARCHAR(512) NULL COMMENT '登录凭证 Token',
    `sys_ai_bot_binding_platform`        VARCHAR(32)  NULL COMMENT '平台类型（wechat / wecom）',
    `sys_ai_bot_binding_nickname`        VARCHAR(128) NULL COMMENT 'Bot 昵称（来自微信资料）',
    `sys_ai_bot_binding_avatar_url`      VARCHAR(512) NULL COMMENT '头像地址',
    `sys_ai_bot_binding_gateway_port`    INT          NULL COMMENT '独立网关端口号',
    `sys_ai_bot_binding_status`          VARCHAR(32)  NULL DEFAULT 'offline' COMMENT '状态：online / offline / expired',
    `sys_ai_bot_binding_bind_source`     VARCHAR(32)  NULL COMMENT '绑定来源（qrcode_scan / manual_import）',
    `sys_ai_bot_binding_thinking`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用深度思考（默认关闭）',
    `sys_ai_bot_binding_thinking_effort` VARCHAR(16)  NULL COMMENT '深度思考强度（low / medium / high）',
    `sys_ai_bot_binding_system_prompt`   TEXT         NULL COMMENT '系统提示词（AI 回复时注入的 system prompt）',
    sys_ai_bot_binding_record_usage   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否记录 AI 用量（bot 专属开关，默认开启）',
    `sys_ai_bot_binding_last_active_time` DATETIME     NULL COMMENT '最后活跃时间',
    `create_name`                         VARCHAR(64)  NULL COMMENT '创建人姓名',
    `create_by`                           INT          NULL COMMENT '创建人',
    `create_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                         VARCHAR(64)  NULL COMMENT '更新人姓名',
    `update_by`                           INT          NULL COMMENT '更新人',
    `update_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_ai_bot_binding_id`),
    KEY `idx_sys_ai_bot_binding_bot_id` (`sys_ai_bot_binding_bot_id`)
);

CREATE TABLE IF NOT EXISTS `sys_ai_bot_message` (
    `sys_ai_bot_message_id`       BIGINT    NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    `sys_ai_bot_message_bot_id`   VARCHAR(128) NULL COMMENT 'Bot 唯一标识（关联绑定表）',
    `sys_ai_bot_message_direction` VARCHAR(16) NULL COMMENT '方向：IN（用户→bot）/ OUT（bot→用户）',
    `sys_ai_bot_message_from_user` VARCHAR(128) NULL COMMENT '微信用户 ID（来源）',
    `sys_ai_bot_message_content`  TEXT      NULL COMMENT '消息内容',
    `sys_ai_bot_message_create_time` DATETIME NULL COMMENT '创建时间',
    PRIMARY KEY (`sys_ai_bot_message_id`),
    KEY `idx_sys_ai_bot_message_bot` (`sys_ai_bot_message_bot_id`),
    KEY `idx_sys_ai_bot_message_conv` (`sys_ai_bot_message_bot_id`, `sys_ai_bot_message_from_user`)
);

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
    KEY `idx_sys_ai_usage_request_id` (`sys_ai_usage_request_id`),
    KEY `idx_sys_ai_usage_call_time` (`sys_ai_usage_call_time`)
);

CREATE TABLE IF NOT EXISTS `sys_ai_usage_daily` (
    `sys_ai_usage_daily_id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT ' ID',
    `sys_ai_usage_daily_stat_date`              DATE         NULL COMMENT '',
    `sys_ai_usage_daily_group_name`            VARCHAR(32)  NULL COMMENT '',
    `sys_ai_usage_daily_provider`              VARCHAR(64)  NULL COMMENT '',
    `sys_ai_usage_daily_model`                 VARCHAR(128) NULL COMMENT '',
    `sys_ai_usage_daily_total_calls`           BIGINT       NULL COMMENT '',
    `sys_ai_usage_daily_total_prompt_tokens`   BIGINT       NULL COMMENT ' Token',
    `sys_ai_usage_daily_total_completion_tokens` BIGINT    NULL COMMENT ' Token',
    `sys_ai_usage_daily_total_tokens`         BIGINT       NULL COMMENT ' Token',
    `sys_ai_usage_daily_total_cost`           DOUBLE       NULL COMMENT '',
    `create_name`                               VARCHAR(64)  NULL COMMENT '',
    `create_by`                                 INT          NULL COMMENT '',
    `create_time`                               DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_name`                               VARCHAR(64)  NULL COMMENT '',
    `update_by`                                 INT          NULL COMMENT '',
    `update_time`                               DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
    PRIMARY KEY (`sys_ai_usage_daily_id`)
);

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

CREATE TABLE IF NOT EXISTS `sys_ai_module` (
  `sys_ai_module_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_module_name` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_module_code` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_module_model_id` VARCHAR(255) DEFAULT NULL COMMENT 'ID',
  `sys_ai_module_image` VARCHAR(1024) DEFAULT NULL COMMENT '',
  `sys_ai_module_type` VARCHAR(64) NOT NULL COMMENT '',
  `sys_ai_module_vendor` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_module_manufacturers` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_module_url` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_module_version` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_module_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `sys_ai_module_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_vlm` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_support_think` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_support_optimize` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_concurrency` INT DEFAULT 1 COMMENT '',
  `sys_ai_module_local` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_input_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '',
  `sys_ai_module_output_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '',
  `sys_ai_module_cache_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '',
  `sys_ai_module_generate_limit` INT DEFAULT 0 COMMENT '',
  `sys_ai_module_support_template` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_support_style` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_module_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
	  `sys_ai_module_api_expose` INT NOT NULL DEFAULT 1 COMMENT '10',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_module_id`),
  UNIQUE KEY `uk_sys_ai_module_code` (`sys_ai_module_code`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_manufacturer` (
  `sys_ai_manufacturer_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_manufacturer_code` VARCHAR(128) NOT NULL COMMENT '',
  `sys_ai_manufacturer_name` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_manufacturer_factory` VARCHAR(128) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_base_url` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_app_key` VARCHAR(255) DEFAULT NULL COMMENT 'Key',
  `sys_ai_manufacturer_app_secret` VARCHAR(255) DEFAULT NULL COMMENT 'Secret',
  `sys_ai_manufacturer_icon` VARCHAR(1024) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_logo` VARCHAR(1024) DEFAULT NULL COMMENT 'Logo',
  `sys_ai_manufacturer_website` VARCHAR(1024) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_openai_compatible` INT NOT NULL DEFAULT 0 COMMENT 'OpenAI',
  `sys_ai_manufacturer_claude_compatible` INT NOT NULL DEFAULT 0 COMMENT 'Claude',
  `sys_ai_manufacturer_app_id` VARCHAR(255) DEFAULT NULL COMMENT 'ID',
  `sys_ai_manufacturer_api_secret` VARCHAR(255) DEFAULT NULL COMMENT 'API',
  `sys_ai_manufacturer_api_version` VARCHAR(128) DEFAULT NULL COMMENT 'API',
  `sys_ai_manufacturer_api_domain` VARCHAR(255) DEFAULT NULL COMMENT 'API',
  `sys_ai_manufacturer_memory_enabled` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_manufacturer_compression_enabled` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_manufacturer_compression_threshold` INT NOT NULL DEFAULT 12 COMMENT '',
  `sys_ai_manufacturer_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `sys_ai_manufacturer_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_manufacturer_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_api_expose` INT NOT NULL DEFAULT 1 COMMENT '10',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_manufacturer_id`),
  UNIQUE KEY `uk_sys_ai_manufacturer_code` (`sys_ai_manufacturer_code`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_session_compression` (
  `sys_ai_session_compression_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_session_id` INT NOT NULL COMMENT 'ID',
  `sys_ai_manufacturer_code` VARCHAR(128) DEFAULT NULL COMMENT '',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_compression_message_count` BIGINT DEFAULT 0 COMMENT '',
  `sys_ai_compression_from_message_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_compression_context` LONGTEXT COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_session_compression_id`),
  UNIQUE KEY `uk_ai_session_compression_session` (`sys_ai_session_id`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_session` (
  `sys_ai_session_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_session_title` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_session_type` VARCHAR(64) NOT NULL DEFAULT 'CHAT' COMMENT '',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_user_id` VARCHAR(255) NOT NULL COMMENT 'ID',
  `sys_ai_session_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_session_id`),
  KEY `idx_ai_session_user_type` (`sys_user_id`, `sys_ai_session_type`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_message` (
  `sys_ai_message_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_session_id` INT NOT NULL COMMENT 'ID',
  `sys_ai_message_role` VARCHAR(32) NOT NULL COMMENT '',
  `sys_ai_message_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '',
  `sys_ai_message_content` LONGTEXT COMMENT '',
  `sys_ai_message_attachment` VARCHAR(1024) DEFAULT NULL COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_message_id`),
  KEY `idx_ai_message_session` (`sys_ai_session_id`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_task` (
  `sys_ai_task_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_task_uid` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_task_type` VARCHAR(64) NOT NULL COMMENT '',
  `sys_project_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_module_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_task_prompt` TEXT COMMENT '',
  `sys_ai_task_condition` LONGTEXT COMMENT 'JSON',
  `sys_ai_task_request` LONGTEXT COMMENT 'JSON',
  `sys_ai_task_result` LONGTEXT COMMENT 'JSON',
  `sys_ai_task_url` LONGTEXT COMMENT '',
  `sys_ai_task_local_url` LONGTEXT COMMENT '',
  `sys_ai_task_request_id` VARCHAR(128) DEFAULT NULL COMMENT 'ID',
  `sys_ai_task_status` VARCHAR(64) NOT NULL DEFAULT 'SUCCESS' COMMENT '',
  `sys_ai_task_progress` INT DEFAULT 100 COMMENT '',
  `sys_user_id` VARCHAR(255) NOT NULL COMMENT 'ID',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_task_id`),
  KEY `idx_ai_task_user_type` (`sys_user_id`, `sys_ai_task_type`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_style` (
  `sys_ai_style_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_module_id` INT NOT NULL COMMENT 'ID',
  `sys_ai_style_name` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_style_code` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_style_image` VARCHAR(1024) DEFAULT NULL COMMENT '',
  `sys_ai_style_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_style_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_style_id`),
  KEY `idx_ai_style_module` (`sys_ai_module_id`),
  UNIQUE KEY `uk_ai_style_code` (`sys_ai_module_id`, `sys_ai_style_code`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_template` (
  `sys_ai_template_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_module_id` INT NOT NULL COMMENT 'ID',
  `sys_ai_template_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_template_name` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_template_type` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_template_address` VARCHAR(1024) DEFAULT NULL COMMENT '',
  `sys_ai_template_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_template_model_id` VARCHAR(255) DEFAULT NULL COMMENT 'ID',
  `sys_ai_template_model_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_template_category` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_template_train_model` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_template_recommend_prompt` LONGTEXT COMMENT '',
  `sys_ai_template_resolutions` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_template_word` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_template_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_template_id`),
  KEY `idx_ai_template_module` (`sys_ai_module_id`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_setting` (
  `sys_ai_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_module_id` INT NOT NULL COMMENT 'ID',
  `sys_ai_setting_field` VARCHAR(128) NOT NULL COMMENT '',
  `sys_ai_setting_value` LONGTEXT COMMENT '',
  `sys_ai_setting_value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '',
  `sys_ai_setting_description` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_setting_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_setting_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_setting_id`),
  KEY `idx_ai_setting_module` (`sys_ai_module_id`),
  UNIQUE KEY `uk_ai_setting_field` (`sys_ai_module_id`, `sys_ai_setting_field`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_global_setting` (
  `sys_ai_global_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_global_setting_group` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '',
  `sys_ai_global_setting_field` VARCHAR(128) NOT NULL COMMENT '',
  `sys_ai_global_setting_value` LONGTEXT COMMENT '',
  `sys_ai_global_setting_value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '',
  `sys_ai_global_setting_description` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_global_setting_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_global_setting_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_global_setting_id`),
  UNIQUE KEY `uk_ai_global_setting_field` (`sys_ai_global_setting_group`, `sys_ai_global_setting_field`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_app_key` (
  `sys_ai_app_key_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_app_key_app_id` VARCHAR(128) DEFAULT NULL COMMENT 'ID',
  `sys_ai_app_key_app_key` VARCHAR(128) NOT NULL COMMENT 'Key',
  `sys_ai_app_key_secret` VARCHAR(256) NOT NULL COMMENT 'Secret',
  `sys_ai_app_key_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_value` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_provider_id` BIGINT DEFAULT NULL COMMENT ' ID',
  `sys_ai_app_key_model` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_max_use` BIGINT DEFAULT NULL COMMENT 'null ',
  `sys_ai_app_key_enabled` INT DEFAULT 1 COMMENT '',
  `sys_ai_app_key_expire_datetime` DATETIME DEFAULT NULL COMMENT 'null ',
  `sys_user_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_app_key_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `sys_ai_app_key_delete` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_app_key_group_id` INT DEFAULT NULL COMMENT ' ID',
  `sys_ai_app_key_expire_time` BIGINT DEFAULT NULL COMMENT '',
  `sys_ai_app_key_valid_days` INT DEFAULT NULL COMMENT '',
  `sys_ai_app_key_ip_whitelist` VARCHAR(512) DEFAULT NULL COMMENT 'IP',
  `sys_ai_app_key_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_last_used_time` BIGINT DEFAULT NULL COMMENT '',
  `sys_ai_app_key_use_count` INT DEFAULT 0 COMMENT '',
  `sys_ai_app_key_max_tokens` BIGINT DEFAULT NULL COMMENT 'Token null ',
  `sys_ai_app_key_total_tokens` BIGINT DEFAULT 0 COMMENT ' Token ',
  `sys_ai_app_key_scope` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_app_key_id`),
  UNIQUE KEY `uk_ai_app_key_value` (`sys_ai_app_key_app_key`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_face_library` (
  `sys_ai_face_library_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_face_library_name` VARCHAR(128) NOT NULL COMMENT '/',
  `sys_ai_face_library_full_name` VARCHAR(128) DEFAULT NULL COMMENT '',
  `sys_ai_face_library_id_card` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_face_library_vector_id` VARCHAR(128) NOT NULL COMMENT 'ID',
  `sys_ai_face_library_thumbnail` LONGTEXT COMMENT '(base64)',
  `sys_ai_face_library_group` VARCHAR(64) DEFAULT 'default' COMMENT '',
  `sys_ai_face_library_meta` VARCHAR(2048) DEFAULT NULL COMMENT '(JSON)',
  `sys_ai_face_library_status` INT NOT NULL DEFAULT 1 COMMENT '10',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_face_library_id`),
  UNIQUE KEY `uk_face_vector_id` (`sys_ai_face_library_vector_id`),
  KEY `idx_face_group` (`sys_ai_face_library_group`),
  KEY `idx_face_name` (`sys_ai_face_library_name`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_face_recognize_record` (
  `sys_ai_face_recognize_record_id` INT NOT NULL AUTO_INCREMENT,
  `sys_ai_face_recognize_record_thumbnail` LONGTEXT COMMENT '(base64)',
  `sys_ai_face_recognize_record_result` JSON COMMENT 'JSON',
  `sys_ai_face_recognize_record_face_count` INT DEFAULT 0 COMMENT '',
  `sys_ai_face_recognize_record_match_count` INT DEFAULT 0 COMMENT '',
  `sys_ai_face_recognize_record_group` VARCHAR(64) DEFAULT NULL COMMENT '',
  `sys_ai_face_recognize_record_cost` BIGINT DEFAULT 0 COMMENT '(ms)',
  `sys_ai_face_recognize_record_status` INT DEFAULT 1,
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_face_recognize_record_id`),
  KEY `idx_record_create_time` (`create_time`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_face_group` (
  `sys_ai_face_group_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_face_group_name` VARCHAR(64) NOT NULL COMMENT '',
  `sys_ai_face_group_sort` INT DEFAULT 0 COMMENT '',
  `sys_ai_face_group_remark` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_face_group_status` INT DEFAULT 1 COMMENT '10',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_face_group_id`),
  UNIQUE KEY `uk_group_name` (`sys_ai_face_group_name`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_image_library` (
  `sys_ai_image_library_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_image_library_name` VARCHAR(255) DEFAULT NULL COMMENT '/',
  `sys_ai_image_library_vector_id` VARCHAR(128) NOT NULL COMMENT 'ID',
  `sys_ai_image_library_thumbnail` LONGTEXT COMMENT '(base64)',
  `sys_ai_image_library_url` VARCHAR(2048) DEFAULT NULL COMMENT 'URL',
  `sys_ai_image_library_tags` VARCHAR(512) DEFAULT NULL COMMENT '()',
  `sys_ai_image_library_meta` JSON DEFAULT NULL COMMENT '',
  `sys_ai_image_library_width` INT DEFAULT 0 COMMENT '',
  `sys_ai_image_library_height` INT DEFAULT 0 COMMENT '',
  `sys_ai_image_library_status` INT NOT NULL DEFAULT 1 COMMENT '10',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_image_library_id`),
  UNIQUE KEY `uk_image_vector_id` (`sys_ai_image_library_vector_id`),
  KEY `idx_image_create_time` (`create_time`),
  KEY `idx_image_tags` (`sys_ai_image_library_tags`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_entity` (
  `sys_ai_knowledge_graph_entity_id`       INT           NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_knowledge_graph_entity_uid`      VARCHAR(64)   NOT NULL COMMENT '(UUID)',
  `sys_ai_knowledge_graph_library_id`      INT           NOT NULL DEFAULT 1 COMMENT 'ID',
  `sys_ai_knowledge_graph_entity_name`     VARCHAR(255)  NOT NULL COMMENT '',
  `sys_ai_knowledge_graph_entity_type`     VARCHAR(128)  NOT NULL DEFAULT 'default' COMMENT '(person/organization/location/product)',
  `sys_ai_knowledge_graph_entity_properties` JSON        DEFAULT NULL COMMENT '(JSON)',
  `sys_ai_knowledge_graph_entity_source`   VARCHAR(32)   NOT NULL DEFAULT 'manual' COMMENT '(manual/llm_extracted/imported)',
  `sys_ai_knowledge_graph_entity_weight`   DOUBLE        DEFAULT 1.0 COMMENT '',
  `sys_ai_knowledge_graph_entity_logo`     VARCHAR(1024) DEFAULT NULL COMMENT 'LogoURL',
  `sys_ai_knowledge_graph_entity_status`   INT           NOT NULL DEFAULT 1 COMMENT '10',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by`    INT          DEFAULT NULL COMMENT '',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by`    INT          DEFAULT NULL COMMENT '',
  PRIMARY KEY (`sys_ai_knowledge_graph_entity_id`),
  UNIQUE KEY `uk_kg_entity_uid` (`sys_ai_knowledge_graph_entity_uid`),
  KEY `idx_kg_entity_type` (`sys_ai_knowledge_graph_entity_type`),
  KEY `idx_kg_entity_name` (`sys_ai_knowledge_graph_entity_name`),
  KEY `idx_kg_entity_library` (`sys_ai_knowledge_graph_library_id`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_relation` (
  `sys_ai_knowledge_graph_relation_id`                 INT           NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_knowledge_graph_relation_uid`                VARCHAR(64)   NOT NULL COMMENT '(UUID)',
  `sys_ai_knowledge_graph_relation_library_id`         INT           NOT NULL DEFAULT 1 COMMENT 'ID',
  `sys_ai_knowledge_graph_relation_source_entity_uid`  VARCHAR(64)   NOT NULL COMMENT 'UID',
  `sys_ai_knowledge_graph_relation_target_entity_uid`  VARCHAR(64)   NOT NULL COMMENT 'UID',
  `sys_ai_knowledge_graph_relation_type`               VARCHAR(128)  NOT NULL COMMENT '',
  `sys_ai_knowledge_graph_relation_properties`         JSON          DEFAULT NULL COMMENT '(JSON)',
  `sys_ai_knowledge_graph_relation_weight`             DOUBLE        DEFAULT 1.0 COMMENT '',
  `sys_ai_knowledge_graph_relation_source`             VARCHAR(32)   NOT NULL DEFAULT 'manual' COMMENT '(manual/llm_extracted/imported)',
  `sys_ai_knowledge_graph_relation_status`             INT           NOT NULL DEFAULT 1 COMMENT '10',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by`    INT          DEFAULT NULL COMMENT '',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by`    INT          DEFAULT NULL COMMENT '',
  PRIMARY KEY (`sys_ai_knowledge_graph_relation_id`),
  UNIQUE KEY `uk_kg_relation_uid` (`sys_ai_knowledge_graph_relation_uid`),
  KEY `idx_kg_relation_source` (`sys_ai_knowledge_graph_relation_source_entity_uid`),
  KEY `idx_kg_relation_target` (`sys_ai_knowledge_graph_relation_target_entity_uid`),
  KEY `idx_kg_relation_type` (`sys_ai_knowledge_graph_relation_type`),
  KEY `idx_kg_relation_library` (`sys_ai_knowledge_graph_relation_library_id`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_id`                      INT           NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_knowledge_library_name`                    VARCHAR(128)  NOT NULL COMMENT '',
  `sys_ai_knowledge_library_description`             VARCHAR(512)  DEFAULT NULL COMMENT '',
  `sys_ai_knowledge_library_summary`                 TEXT          DEFAULT NULL COMMENT 'AI',
  `sys_ai_knowledge_library_llm_provider`            VARCHAR(64)   DEFAULT NULL COMMENT 'LLM()',
  `sys_ai_knowledge_library_llm_model`               VARCHAR(128)  DEFAULT NULL COMMENT 'LLM()',
  `sys_ai_knowledge_library_temperature`             DOUBLE        DEFAULT NULL COMMENT '()',
  `sys_ai_knowledge_library_max_tokens`              INT           DEFAULT NULL COMMENT 'token()',
  `sys_ai_knowledge_library_prompt_template`         TEXT          DEFAULT NULL COMMENT '()',
  `sys_ai_knowledge_library_relation_extraction_enabled` INT       DEFAULT 1 COMMENT '',
  `sys_ai_knowledge_library_max_entities_per_doc`    INT           DEFAULT 50 COMMENT '',
  `sys_ai_knowledge_library_web_search_enabled`     INT           DEFAULT 0 COMMENT '10',
  `sys_ai_knowledge_library_thinking_rounds`       INT           DEFAULT 4 COMMENT 'AI(1-5)4',
  `sys_ai_knowledge_library_sort`                    INT           DEFAULT 0 COMMENT '',
  `sys_ai_knowledge_library_status`                  INT           NOT NULL DEFAULT 1 COMMENT '10',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by`    INT          DEFAULT NULL COMMENT '',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by`    INT          DEFAULT NULL COMMENT '',
  PRIMARY KEY (`sys_ai_knowledge_library_id`),
  UNIQUE KEY `uk_lib_name` (`sys_ai_knowledge_library_name`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_reid_library` (
  `sys_ai_reid_library_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_reid_library_face_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_reid_library_name` VARCHAR(128) NOT NULL COMMENT '/',
  `sys_ai_reid_library_face_name` VARCHAR(128) DEFAULT NULL COMMENT '',
  `sys_ai_reid_library_vector_id` VARCHAR(128) NOT NULL COMMENT 'ID',
  `sys_ai_reid_library_thumbnail` LONGTEXT COMMENT '(base64)',
  `sys_ai_reid_library_group` VARCHAR(64) DEFAULT 'default' COMMENT ' face_group',
  `sys_ai_reid_library_meta` VARCHAR(2048) DEFAULT NULL COMMENT '(JSON)',
  `sys_ai_reid_library_status` INT NOT NULL DEFAULT 1 COMMENT '10',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_reid_library_id`),
  UNIQUE KEY `uk_reid_vector_id` (`sys_ai_reid_library_vector_id`),
  KEY `idx_reid_group` (`sys_ai_reid_library_group`),
  KEY `idx_reid_face_id` (`sys_ai_reid_library_face_id`),
  KEY `idx_reid_name` (`sys_ai_reid_library_name`)
) COMMENT='AI ReID';

CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_entity_attribute` (
  `attr_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `entity_uid`       VARCHAR(64)  NOT NULL COMMENT 'UUID',
  `library_id`       INT          NOT NULL DEFAULT 1 COMMENT 'ID',
  `attr_name`        VARCHAR(200) NOT NULL COMMENT 'startYearendYear',
  `attr_value`       TEXT         DEFAULT NULL COMMENT '',
  `attr_type`        VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT 'text/number/date/event',
  `attr_sort`        INT          NOT NULL DEFAULT 0 COMMENT '',
  `status`           INT          NOT NULL DEFAULT 1 COMMENT '10',
  `create_name`      VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by`        INT          DEFAULT NULL COMMENT 'ID',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name`      VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by`        INT          DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`attr_id`),
  KEY `idx_attr_entity` (`entity_uid`),
  KEY `idx_attr_library` (`library_id`),
  KEY `idx_attr_name` (`attr_name`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_manufacturer_key` (
  `sys_ai_manufacturer_key_id` INT NOT NULL AUTO_INCREMENT COMMENT '',
  `sys_ai_manufacturer_code` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_key_value` VARCHAR(512) DEFAULT NULL COMMENT 'API Key',
  `sys_ai_manufacturer_key_secret` VARCHAR(512) DEFAULT NULL COMMENT 'API Secret',
  `sys_ai_manufacturer_key_status` INT DEFAULT 1 COMMENT ': 1=, 0=, -1=',
  `sys_ai_manufacturer_key_weight` INT DEFAULT 0 COMMENT '',
  `sys_ai_manufacturer_key_rate_limited_at` BIGINT DEFAULT NULL COMMENT '',
  `sys_ai_manufacturer_key_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_manufacturer_key_id`),
  KEY `idx_mfr_key_code` (`sys_ai_manufacturer_code`)
) COMMENT='AI  API Key ';

CREATE TABLE IF NOT EXISTS `sys_ai_token` (
  `sys_ai_token_id` INT NOT NULL AUTO_INCREMENT COMMENT '',
  `sys_ai_token_name` VARCHAR(128) DEFAULT NULL COMMENT '',
  `sys_ai_token_key` VARCHAR(128) NOT NULL COMMENT '',
  `sys_ai_token_key_prefix` VARCHAR(32) DEFAULT NULL COMMENT '',
  `sys_ai_token_key_hash` VARCHAR(64) NOT NULL COMMENT '',
  `sys_user_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_app_key_id` INT DEFAULT NULL COMMENT 'AppKey ID',
  `sys_ai_token_group_id` INT DEFAULT NULL COMMENT ' ID',
  `sys_ai_token_status` INT DEFAULT 1 COMMENT '10',
  `sys_ai_token_expire_time` DATETIME DEFAULT NULL COMMENT '',
  `sys_ai_token_input_fee` DECIMAL(18,4) DEFAULT NULL COMMENT '',
  `sys_ai_token_output_fee` DECIMAL(18,4) DEFAULT NULL COMMENT '',
  `sys_ai_token_total_input_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '',
  `sys_ai_token_total_output_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '',
  `sys_ai_token_total_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '',
  `sys_ai_token_use_count` BIGINT DEFAULT 0 COMMENT '',
  `sys_ai_token_total_tokens` BIGINT DEFAULT 0 COMMENT 'token',
  `sys_ai_token_today_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '',
  `sys_ai_token_today_date` VARCHAR(10) DEFAULT NULL COMMENT '',
  `sys_ai_token_last_used_time` DATETIME DEFAULT NULL COMMENT '',
  `sys_ai_token_delete` INT DEFAULT 0 COMMENT '01',
  `sys_ai_token_allowed_models` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_token_rate_limit` INT DEFAULT NULL COMMENT '',
  `sys_ai_token_rate_count` INT DEFAULT 0 COMMENT '',
  `sys_ai_token_rate_window_start` DATETIME DEFAULT NULL COMMENT '',
  `sys_ai_token_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_token_id`),
  KEY `idx_token_user` (`sys_user_id`),
  KEY `idx_token_key_hash` (`sys_ai_token_key_hash`),
  KEY `idx_token_status` (`sys_ai_token_status`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_balance` (
  `sys_ai_balance_id` INT NOT NULL AUTO_INCREMENT COMMENT '',
  `sys_user_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_balance_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '',
  `sys_ai_balance_total_recharge` DECIMAL(18,4) DEFAULT 0 COMMENT '',
  `sys_ai_balance_total_consume` DECIMAL(18,4) DEFAULT 0 COMMENT '',
  `sys_ai_balance_gift` DECIMAL(18,4) DEFAULT 0 COMMENT '',
  `sys_ai_balance_warning_threshold` DECIMAL(18,4) DEFAULT NULL COMMENT '',
  `sys_ai_balance_expire_time` DATETIME DEFAULT NULL COMMENT '',
  `sys_ai_balance_status` INT DEFAULT 1 COMMENT '10',
  `sys_ai_balance_currency` VARCHAR(10) DEFAULT 'CNY' COMMENT '',
  `sys_ai_balance_last_consume_time` DATETIME DEFAULT NULL COMMENT '',
  `sys_ai_balance_delete` INT DEFAULT 0 COMMENT '',
  `sys_ai_balance_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_balance_id`),
  KEY `idx_balance_user` (`sys_user_id`),
  KEY `idx_balance_status` (`sys_ai_balance_status`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_group` (
    `sys_ai_group_id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `sys_ai_group_name` VARCHAR(128) NOT NULL COMMENT '',
    `sys_ai_group_desc` VARCHAR(256) DEFAULT '' COMMENT '',
    `sys_ai_group_models` TEXT DEFAULT NULL COMMENT '(JSON)',
    `sys_ai_group_app_key_ids` TEXT DEFAULT NULL COMMENT ' AppKey ID (JSON)',
    `sys_ai_group_enabled` INT DEFAULT 1 COMMENT '',
    `sys_ai_group_sort` INT DEFAULT 0 COMMENT '',
    `sys_ai_group_status` INT DEFAULT 1 COMMENT '1 0 ',
    `sys_ai_group_delete` INT DEFAULT 0 COMMENT '0 1 ',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `create_by` VARCHAR(64) COMMENT '',
    `update_time` TIMESTAMP COMMENT '',
    `update_by` VARCHAR(64) COMMENT ''
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_group_model` (
    `sys_ai_group_model_id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `sys_ai_group_id` INT NOT NULL COMMENT ' ID',
    `sys_ai_module_id` INT NOT NULL COMMENT ' ID',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '',
    UNIQUE KEY `uk_group_module` (`sys_ai_group_id`, `sys_ai_module_id`)
) COMMENT='-';

CREATE TABLE IF NOT EXISTS `sys_ai_model` (
    `sys_ai_model_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `sys_ai_model_name` VARCHAR(255) NOT NULL COMMENT '',
    `sys_ai_model_code` VARCHAR(255) NOT NULL COMMENT '(gpt-4o, deepseek-chat)',
    `sys_ai_model_provider_id` BIGINT DEFAULT NULL COMMENT ' ID',
    `sys_ai_model_capabilities` VARCHAR(512) DEFAULT NULL COMMENT '(: chat,vision,think,search)',
    `sys_ai_model_description` VARCHAR(512) DEFAULT NULL COMMENT '',
    `sys_ai_model_enabled` INT DEFAULT 1 COMMENT '',
    `sys_ai_model_sort` INT DEFAULT 0 COMMENT '',
    `sys_ai_model_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `sys_ai_model_update_time` DATETIME DEFAULT NULL COMMENT '',
    `sys_ai_model_deleted` INT DEFAULT 0 COMMENT '',
    `sys_ai_model_input_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '(1k tokens)',
    `sys_ai_model_output_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '(1k tokens)',
    `sys_ai_model_cache_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '(1k tokens)',
    UNIQUE KEY `uk_sys_ai_model_code` (`sys_ai_model_code`)
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_service_status` (
    `sys_ai_service_status_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `sys_ai_service_status_service_name` VARCHAR(255) NOT NULL COMMENT '',
    `sys_ai_service_status_running` INT DEFAULT 0 COMMENT '',
    `sys_ai_service_status_pid` BIGINT DEFAULT NULL COMMENT ' PID',
    `sys_ai_service_status_start_time` DATETIME DEFAULT NULL COMMENT '',
    `sys_ai_service_status_uptime_sec` BIGINT DEFAULT 0 COMMENT '()',
    `sys_ai_service_status_update_time` DATETIME DEFAULT NULL COMMENT ''
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_alert` (
    `sys_ai_alert_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `sys_ai_alert_name` VARCHAR(255) NOT NULL COMMENT '',
    `sys_ai_alert_app_key_id` BIGINT DEFAULT NULL COMMENT ' AppKey ID',
    `sys_ai_alert_condition` VARCHAR(128) NOT NULL COMMENT '',
    `sys_ai_alert_threshold` BIGINT NOT NULL COMMENT '',
    `sys_ai_alert_channels` VARCHAR(512) DEFAULT NULL COMMENT '',
    `sys_ai_alert_enabled` INT DEFAULT 1 COMMENT '',
    `sys_ai_alert_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `sys_ai_alert_update_time` DATETIME DEFAULT NULL COMMENT '',
    `sys_ai_alert_deleted` INT DEFAULT 0 COMMENT ''
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_model_setting` (
    `sys_ai_model_setting_id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `module_id` INT DEFAULT NULL COMMENT ' ID',
    `model_code` VARCHAR(255) DEFAULT NULL COMMENT '',
    `model_name` VARCHAR(255) DEFAULT NULL COMMENT '',
    `model_provider` VARCHAR(128) DEFAULT NULL COMMENT '',
    `model_value` VARCHAR(512) DEFAULT NULL COMMENT ' baseUrl / model ',
    `status` INT DEFAULT 1 COMMENT '1 0 ',
    `sort` INT DEFAULT 0 COMMENT '',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''
) COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_role` (
    `sys_ai_role_id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '',
    `sys_ai_role_name` VARCHAR(128) NOT NULL COMMENT '',
    `sys_ai_role_code` VARCHAR(64) NOT NULL COMMENT '',
    `sys_ai_role_description` VARCHAR(512) DEFAULT NULL COMMENT '',
    `sys_ai_role_system_prompt` TEXT NOT NULL COMMENT '',
    `sys_ai_role_model` VARCHAR(128) DEFAULT NULL COMMENT '',
    `sys_ai_role_temperature` DOUBLE DEFAULT NULL COMMENT '',
    `sys_ai_role_sort` INT DEFAULT 0 COMMENT '',
    `sys_ai_role_status` INT DEFAULT 1 COMMENT '10',
    `sys_ai_role_delete` INT DEFAULT 0 COMMENT '01',
    `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    UNIQUE KEY `uk_sys_ai_role_code` (`sys_ai_role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI ';

CREATE TABLE IF NOT EXISTS `sys_ai_ssh_host` (
    `sys_ai_ssh_host_id`               VARCHAR(36)  NOT NULL COMMENT '主机主键（UUIDv7）',
    `sys_ai_ssh_host_name`             VARCHAR(255) NULL COMMENT '主机显示名称',
    `sys_ai_ssh_host_host`             VARCHAR(255) NULL COMMENT '主机地址（IP 或域名）',
    `sys_ai_ssh_host_port`             INT          NULL DEFAULT 22 COMMENT 'SSH 端口号',
    `sys_ai_ssh_host_username`         VARCHAR(128) NULL COMMENT '登录用户名',
    `sys_ai_ssh_host_auth_type`        VARCHAR(32)  NULL DEFAULT 'password' COMMENT '认证方式（password/key/certificate/agent）',
    `sys_ai_ssh_host_password`         VARCHAR(1024) NULL COMMENT '登录密码（写入后不回显）',
    `sys_ai_ssh_host_private_key`      TEXT         NULL COMMENT '私钥内容（写入后不回显）',
    `sys_ai_ssh_host_passphrase`       VARCHAR(1024) NULL COMMENT '私钥口令（写入后不回显）',
    `sys_ai_ssh_host_certificate`      TEXT         NULL COMMENT '证书内容（写入后不回显）',
    `sys_ai_ssh_host_sudo_password`    VARCHAR(1024) NULL COMMENT 'sudo 密码（写入后不回显）',
    `sys_ai_ssh_host_host_fingerprint` VARCHAR(512) NULL COMMENT '主机密钥指纹',
    `sys_ai_ssh_host_status`           VARCHAR(32)  NULL DEFAULT 'unknown' COMMENT '主机状态（unknown/error/ok）',
    `sys_ai_ssh_host_last_connected_at` BIGINT      NULL COMMENT '最后连接成功时间（毫秒）',
    `sys_ai_ssh_host_created_at`       BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_ssh_host_updated_at`       BIGINT       NOT NULL COMMENT '最后更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_ssh_host_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='远程主机（SSH 主机簿）';

CREATE TABLE IF NOT EXISTS `sys_ai_agent` (
    `sys_ai_agent_id`            VARCHAR(64)  NOT NULL COMMENT 'Agent ID（UUIDv7）',
    `sys_ai_agent_parent_id`     VARCHAR(64)  NULL COMMENT '所属主 Agent ID（空表示主 Agent）',
    `sys_ai_agent_name`          VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    `sys_ai_agent_description`   VARCHAR(512) NULL COMMENT 'Agent 描述',
    `sys_ai_agent_role`          VARCHAR(255) NULL COMMENT 'Agent 角色',
    `sys_ai_agent_instruction`   TEXT         NULL COMMENT '系统指令（system prompt）',
    `sys_ai_agent_system_prompt` TEXT         NULL COMMENT '完整系统提示词（为空则自动生成）',
    `sys_ai_agent_provider_id`   VARCHAR(64)  NULL COMMENT '关联提供商 ID（model 使用）',
    `sys_ai_agent_model`         VARCHAR(255) NULL COMMENT '关联模型名称',
    `sys_ai_agent_mcp_ids`       TEXT         NULL COMMENT '关联 MCP 列表 JSON（数组）',
    `sys_ai_agent_skill_ids`     TEXT         NULL COMMENT '关联 Skill 列表 JSON（数组）',
    `sys_ai_agent_strategy`      TEXT         NULL COMMENT '策略 JSON（mode/planning/memory/max_tool_iterations/temperature/max_tokens 等）',
    `sys_ai_agent_enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_agent_sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_ai_agent_create_time`   BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_agent_update_time`   BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_agent_id`),
    KEY `idx_ai_agent_parent` (`sys_ai_agent_parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 定义';

CREATE TABLE IF NOT EXISTS `sys_ai_mcp_server` (
    `sys_ai_mcp_server_id`          VARCHAR(36)  NOT NULL COMMENT 'MCP 服务器主键（UUIDv7）',
    `sys_ai_mcp_server_name`        VARCHAR(255) NOT NULL COMMENT '服务器显示名称',
    `sys_ai_mcp_server_description` VARCHAR(512) NULL COMMENT '描述',
    `sys_ai_mcp_server_transport`   TEXT         NOT NULL COMMENT '传输配置 JSON（type/url/command/args/env/headers）',
    `sys_ai_mcp_server_original_json` TEXT       NULL COMMENT '原始 JSON 配置（用于编辑时准确回显）',
    `sys_ai_mcp_server_builtin`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置（1=内置，隐藏编辑/删除）',
    `sys_ai_mcp_server_enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否默认启用（新会话默认勾选）',
    `sys_ai_mcp_server_last_test_status` VARCHAR(32) NULL DEFAULT 'disconnected' COMMENT '最近测试状态：connected/disconnected/error/testing',
    `sys_ai_mcp_server_last_connected_at` BIGINT  NULL COMMENT '最近连接成功时间（毫秒）',
    `sys_ai_mcp_server_created_at`  BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_mcp_server_updated_at`  BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_mcp_server_id`),
    KEY `idx_sys_ai_mcp_server_name` (`sys_ai_mcp_server_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务器档案';

CREATE TABLE IF NOT EXISTS `sys_ai_requirement` (
    `sys_ai_requirement_id` VARCHAR(36) NOT NULL COMMENT '需求 ID（UUIDv7）',
    `sys_ai_requirement_display_no` INT AUTO_INCREMENT COMMENT '展示序号（自增）',
    `sys_ai_requirement_title` VARCHAR(255) NOT NULL COMMENT '需求标题',
    `sys_ai_requirement_content` TEXT COMMENT '需求内容（Markdown）',
    `sys_ai_requirement_tag` VARCHAR(64) DEFAULT NULL COMMENT '标签',
    `sys_ai_requirement_order_key` VARCHAR(64) DEFAULT NULL COMMENT '执行次序（点分十进制）',
    `sys_ai_requirement_status` VARCHAR(32) DEFAULT 'pending' COMMENT '状态',
    `sys_ai_requirement_completion_note` TEXT COMMENT '完成记录',
    `sys_ai_requirement_owner_conversation_id` VARCHAR(36) DEFAULT NULL COMMENT '来源会话 ID',
    `sys_ai_requirement_owner_terminal_id` VARCHAR(36) DEFAULT NULL COMMENT '来源终端 ID',
    `sys_ai_requirement_origin` VARCHAR(64) DEFAULT NULL COMMENT '创建来源',
    `sys_ai_requirement_started_at` BIGINT DEFAULT NULL COMMENT '开始执行时间戳',
    `sys_ai_requirement_completed_at` BIGINT DEFAULT NULL COMMENT '完成时间戳',
    `sys_ai_requirement_attempt_count` INT DEFAULT 0 COMMENT '重试次数',
    `sys_ai_requirement_extra` JSON COMMENT '扩展字段 JSON',
    `create_name` VARCHAR(100) DEFAULT NULL,
    `create_by` INT DEFAULT NULL,
    `create_time` BIGINT DEFAULT NULL,
    `update_name` VARCHAR(100) DEFAULT NULL,
    `update_by` INT DEFAULT NULL,
    `update_time` BIGINT DEFAULT NULL,
    PRIMARY KEY (`sys_ai_requirement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求条目';

CREATE TABLE IF NOT EXISTS `sys_ai_data_source` (`sys_ai_data_source_id` VARCHAR(36) NOT NULL COMMENT '数据源 ID（UUIDv7）', `sys_ai_data_source_name` VARCHAR(128) NOT NULL COMMENT '数据源名称', `sys_ai_data_source_type` VARCHAR(64) NOT NULL COMMENT '类型: lark / jira / notion / generic', `sys_ai_data_source_config` TEXT NULL COMMENT '配置 JSON', `sys_ai_data_source_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用', `sys_ai_data_source_sync_cron` VARCHAR(64) NULL COMMENT '同步 cron 表达式', `sys_ai_data_source_last_sync_at` BIGINT NULL COMMENT '上次同步时间戳', `sys_ai_data_source_last_sync_status` VARCHAR(32) NULL COMMENT '上次同步状态', `sys_ai_data_source_last_sync_error` VARCHAR(512) NULL COMMENT '上次同步错误', `create_name` VARCHAR(64) NULL, `create_by` INT NULL, `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP, `update_name` VARCHAR(64) NULL, `update_by` INT NULL, `update_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`sys_ai_data_source_id`), KEY `idx_sys_ai_ds_type` (`sys_ai_data_source_type`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求数据源提供方配置';

-- 3. 列变更（ALTER TABLE）
ALTER TABLE `sys_ai_bot_binding`
    ADD COLUMN `sys_ai_bot_binding_model` VARCHAR(128) NULL COMMENT '绑定的 AI 模型（用于微信聊天时 AI 回复）' AFTER `sys_ai_bot_binding_bind_source`;

-- 4. 动态列变更（幂等 PREPARE/EXECUTE）
SET @dbname = DATABASE();

SET @tablename = 'sys_ai_provider';

SET @columnname = 'sys_ai_provider_extra';

SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` TEXT NULL COMMENT ''扩展配置JSON'' AFTER `sys_ai_provider_bedrock_config`')
));

PREPARE alterIfNotExists FROM @preparedStatement;

EXECUTE alterIfNotExists;

DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'sys_ai_provider_proxy';

SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` VARCHAR(512) NULL COMMENT ''HTTP代理地址（如 http://127.0.0.1:7890、socks5://127.0.0.1:1080）'' AFTER `sys_ai_provider_extra`')
));



-- ============================================================================
-- 桌面伙伴（Companion）模块表结构
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_companion` (
    `sys_ai_companion_id`           VARCHAR(36)  NOT NULL COMMENT '伙伴ID（UUIDv7）',
    `sys_ai_companion_seq`          INT          NOT NULL DEFAULT 0 COMMENT '展示序号',
    `sys_ai_companion_name`         VARCHAR(128) NULL COMMENT '伙伴名称',
    `sys_ai_companion_character`    VARCHAR(64)  NULL COMMENT '角色ID（mochi/ink/roux/pixel/bolt/boo）',
    `sys_ai_companion_persona_text` TEXT         NULL COMMENT 'persona JSON（preset/custom）',
    `sys_ai_companion_model_text`   TEXT         NULL COMMENT 'model JSON（provider_id/model/use_model）',
    `sys_ai_companion_voice_text`   TEXT         NULL COMMENT 'voice JSON（asr/tts/vad）',
    `sys_ai_companion_learn_text`   TEXT         NULL COMMENT 'learn JSON（enabled/interval_minutes/model）',
    `sys_ai_companion_evolve_text`  TEXT         NULL COMMENT 'evolve JSON',
    `sys_ai_companion_skills_text`  TEXT         NULL COMMENT 'skills JSON（enabled/disabled_auto）',
    `sys_ai_companion_appearance_text` TEXT     NULL COMMENT 'appearance JSON（window config）',
    `sys_ai_companion_applied_preset_text` TEXT NULL COMMENT 'applied preset JSON',
    `sys_ai_companion_fallback_model_text` TEXT NULL COMMENT 'fallback model JSON',
    `sys_ai_companion_vision_model_text` TEXT NULL COMMENT 'vision model JSON',
    `sys_ai_companion_order_index`  INT          NULL COMMENT '用户选择的位置序号',
    `sys_ai_companion_conversation_id` VARCHAR(36) NULL COMMENT '专属会话ID',
    `sys_ai_companion_created_at`   BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_companion_updated_at`   BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_companion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌面伙伴';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_memory` (
    `sys_ai_companion_memory_id`             VARCHAR(36)  NOT NULL COMMENT '记忆ID（UUIDv7）',
    `sys_ai_companion_memory_companion_id`   VARCHAR(36)  NULL COMMENT '所属伙伴ID（null=legacy）',
    `sys_ai_companion_memory_kind`           VARCHAR(32)  NOT NULL DEFAULT 'knowledge' COMMENT '记忆类型',
    `sys_ai_companion_memory_content`        TEXT         NULL COMMENT '记忆内容',
    `sys_ai_companion_memory_tags`           TEXT         NULL COMMENT '标签 JSON 数组',
    `sys_ai_companion_memory_importance`     DOUBLE       NOT NULL DEFAULT 0 COMMENT '重要性',
    `sys_ai_companion_memory_strength`       DOUBLE       NOT NULL DEFAULT 0 COMMENT '强度',
    `sys_ai_companion_memory_pinned`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `sys_ai_companion_memory_source`         VARCHAR(64)  NULL COMMENT '来源',
    `sys_ai_companion_memory_status`         VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active/archived',
    `sys_ai_companion_memory_created_at`     BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_companion_memory_updated_at`     BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    `sys_ai_companion_memory_last_reinforced_at` BIGINT  NULL COMMENT '最后强化时间（毫秒）',
    PRIMARY KEY (`sys_ai_companion_memory_id`),
    KEY `idx_ai_companion_memory_owner` (`sys_ai_companion_memory_companion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌面伙伴记忆';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_skill` (
    `sys_ai_companion_skill_id`               VARCHAR(36)  NOT NULL COMMENT '技能ID（UUIDv7）',
    `sys_ai_companion_skill_companion_id`     VARCHAR(36)  NULL COMMENT '所属伙伴ID（null=legacy）',
    `sys_ai_companion_skill_name`             VARCHAR(128) NULL COMMENT '技能名',
    `sys_ai_companion_skill_status`           VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/archived',
    `sys_ai_companion_skill_source`           VARCHAR(64)  NULL COMMENT '来源',
    `sys_ai_companion_skill_confidence`       DOUBLE       NOT NULL DEFAULT 0 COMMENT '置信度',
    `sys_ai_companion_skill_provenance_event_ids` TEXT    NULL COMMENT '事件ID JSON 数组',
    `sys_ai_companion_skill_strength`         DOUBLE       NOT NULL DEFAULT 0 COMMENT '强度',
    `sys_ai_companion_skill_version`          INT          NOT NULL DEFAULT 1 COMMENT '版本',
    `sys_ai_companion_skill_skill_pattern_id` VARCHAR(36)  NULL COMMENT '技能模式ID',
    `sys_ai_companion_skill_usage_count`      INT          NOT NULL DEFAULT 0 COMMENT '使用次数',
    `sys_ai_companion_skill_last_used_at`     BIGINT       NULL COMMENT '最后使用时间（毫秒）',
    `sys_ai_companion_skill_created_at`       BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_companion_skill_updated_at`       BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    `sys_ai_companion_skill_description`      TEXT         NULL COMMENT 'SKILL.md 描述',
    `sys_ai_companion_skill_content`          TEXT         NULL COMMENT 'SKILL.md 内容',
    PRIMARY KEY (`sys_ai_companion_skill_id`),
    KEY `idx_ai_companion_skill_owner` (`sys_ai_companion_skill_companion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌面伙伴技能';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_figure` (
    `sys_ai_companion_figure_id`        VARCHAR(36)  NOT NULL COMMENT '形象ID（UUIDv7）',
    `sys_ai_companion_figure_name`      VARCHAR(128) NULL COMMENT '形象名',
    `sys_ai_companion_figure_aspect`    DOUBLE       NOT NULL DEFAULT 1 COMMENT '宽高比',
    `sys_ai_companion_figure_head_box`  TEXT         NULL COMMENT 'head_box JSON（x/y/w/h）',
    `sys_ai_companion_figure_size_tier` VARCHAR(4)   NOT NULL DEFAULT 'm' COMMENT 's/m/l',
    `sys_ai_companion_figure_source_path` VARCHAR(512) NULL COMMENT '源图片路径',
    `sys_ai_companion_figure_created_at` BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`sys_ai_companion_figure_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌面伙伴形象';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_shared_config` (
    `sys_ai_companion_shared_config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键',
    `sys_ai_companion_shared_config_value` TEXT         NULL COMMENT '配置值（JSON）',
    `sys_ai_companion_shared_config_updated_at` BIGINT  NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_companion_shared_config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌面伙伴共享配置';
