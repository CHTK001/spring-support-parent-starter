DROP TABLE IF EXISTS `sys_nomifun_provider_connection`;
DROP TABLE IF EXISTS `sys_nomifun_provider_model`;
DROP TABLE IF EXISTS `sys_nomifun_provider`;
DROP TABLE IF EXISTS `sys_nomifun_message`;
DROP TABLE IF EXISTS `sys_nomifun_conversation`;
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
    KEY `sys_ai_provider_connection_ai_provider_conn_pid` (`sys_ai_provider_connection_provider_id`)
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
    KEY `sys_ai_conversation_message_ai_conv_msg_conv` (`sys_ai_conversation_message_conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话消息';
CREATE TABLE IF NOT EXISTS `sys_ai_bot_binding` (
    `sys_ai_bot_binding_id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '绑定 ID',
    `sys_ai_bot_binding_user_id`          BIGINT       NULL COMMENT '关联用户 ID',
    `sys_ai_bot_binding_bot_id`          VARCHAR(128) NULL COMMENT 'Bot 唯一标识（iLink 分配的 botId）',
    `sys_ai_bot_binding_bot_token`       VARCHAR(512) NULL COMMENT '登录凭证 Token',
    `sys_ai_bot_binding_platform`        VARCHAR(32)  NULL COMMENT '平台类型（wechat / wecom）',
    `sys_ai_bot_binding_owner_key`       VARCHAR(128) NULL COMMENT '归属标识（u:<userId> / fp:<fingerprint>）',
    `sys_ai_bot_binding_nickname`        VARCHAR(128) NULL COMMENT 'Bot 昵称（来自微信资料）',
    `sys_ai_bot_binding_avatar_url`      VARCHAR(512) NULL COMMENT '头像地址',
    `sys_ai_bot_binding_gateway_port`    INT          NULL COMMENT '独立网关端口号',
    `sys_ai_bot_binding_status`          VARCHAR(32)  NULL DEFAULT 'offline' COMMENT '状态：online / offline / expired',
    `sys_ai_bot_binding_bind_source`     VARCHAR(32)  NULL COMMENT '绑定来源（qrcode_scan / manual_import）',
    `sys_ai_bot_binding_thinking`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用深度思考（默认关闭）',
    `sys_ai_bot_binding_thinking_effort` VARCHAR(16)  NULL COMMENT '深度思考强度（low / medium / high）',
    `sys_ai_bot_binding_system_prompt`   TEXT         NULL COMMENT '系统提示词（AI 回复时注入的 system prompt）',
    `sys_ai_bot_binding_last_active_time` DATETIME     NULL COMMENT '最后活跃时间',
    `create_name`                         VARCHAR(64)  NULL COMMENT '创建人姓名',
    `create_by`                           INT          NULL COMMENT '创建人',
    `create_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                         VARCHAR(64)  NULL COMMENT '更新人姓名',
    `update_by`                           INT          NULL COMMENT '更新人',
    `update_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_ai_bot_binding_id`),
    KEY `sys_ai_bot_binding_sys_ai_bot_binding_bot_id` (`sys_ai_bot_binding_bot_id`)
);

CREATE TABLE IF NOT EXISTS `sys_ai_bot_message` (
    `sys_ai_bot_message_id`       BIGINT    NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    `sys_ai_bot_message_bot_id`   VARCHAR(128) NULL COMMENT 'Bot 唯一标识（关联绑定表）',
    `sys_ai_bot_message_direction` VARCHAR(16) NULL COMMENT '方向：IN（用户→bot）/ OUT（bot→用户）',
    `sys_ai_bot_message_from_user` VARCHAR(128) NULL COMMENT '微信用户 ID（来源）',
    `sys_ai_bot_message_content`  TEXT      NULL COMMENT '消息内容',
    `sys_ai_bot_message_create_time` DATETIME NULL COMMENT '创建时间',
    PRIMARY KEY (`sys_ai_bot_message_id`),
    KEY `sys_ai_bot_message_sys_ai_bot_message_bot` (`sys_ai_bot_message_bot_id`),
    KEY `sys_ai_bot_message_sys_ai_bot_message_conv` (`sys_ai_bot_message_bot_id`, `sys_ai_bot_message_from_user`)
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
    KEY `sys_ai_usage_sys_ai_usage_request_id` (`sys_ai_usage_request_id`),
    KEY `sys_ai_usage_sys_ai_usage_call_time` (`sys_ai_usage_call_time`)
);
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
    INDEX `sys_agent_browser_lane_lane_conversation` (`conversation_id`),
    INDEX `sys_agent_browser_lane_lane_lifecycle` (`lifecycle_state`),
    INDEX `sys_agent_browser_lane_lane_user` (`user_id`)
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
    INDEX `sys_agent_browser_tab_tab_lane` (`lane_id`)
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
  UNIQUE KEY `sys_ai_module_sys_ai_module_code` (`sys_ai_module_code`)
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
  UNIQUE KEY `sys_ai_manufacturer_sys_ai_manufacturer_code` (`sys_ai_manufacturer_code`)
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
  UNIQUE KEY `sys_ai_session_compression_ai_session_compression_session` (`sys_ai_session_id`)
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
  KEY `sys_ai_session_ai_session_user_type` (`sys_user_id`, `sys_ai_session_type`)
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
  KEY `sys_ai_message_ai_message_session` (`sys_ai_session_id`)
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
  KEY `sys_ai_task_ai_task_user_type` (`sys_user_id`, `sys_ai_task_type`)
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
  KEY `sys_ai_style_ai_style_module` (`sys_ai_module_id`),
  UNIQUE KEY `sys_ai_style_ai_style_code` (`sys_ai_module_id`, `sys_ai_style_code`)
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
  KEY `sys_ai_template_ai_template_module` (`sys_ai_module_id`)
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
  KEY `sys_ai_setting_ai_setting_module` (`sys_ai_module_id`),
  UNIQUE KEY `sys_ai_setting_ai_setting_field` (`sys_ai_module_id`, `sys_ai_setting_field`)
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
  UNIQUE KEY `sys_ai_global_setting_ai_global_setting_field` (`sys_ai_global_setting_group`, `sys_ai_global_setting_field`)
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
  UNIQUE KEY `sys_ai_app_key_ai_app_key_value` (`sys_ai_app_key_app_key`)
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
  UNIQUE KEY `sys_ai_face_library_face_vector_id` (`sys_ai_face_library_vector_id`),
  KEY `sys_ai_face_library_face_group` (`sys_ai_face_library_group`),
  KEY `sys_ai_face_library_face_name` (`sys_ai_face_library_name`)
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
  KEY `sys_ai_face_recognize_record_record_create_time` (`create_time`)
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
  UNIQUE KEY `sys_ai_face_group_group_name` (`sys_ai_face_group_name`)
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
  UNIQUE KEY `sys_ai_image_library_image_vector_id` (`sys_ai_image_library_vector_id`),
  KEY `sys_ai_image_library_image_create_time` (`create_time`),
  KEY `sys_ai_image_library_image_tags` (`sys_ai_image_library_tags`)
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
  UNIQUE KEY `sys_ai_knowledge_graph_entity_kg_entity_uid` (`sys_ai_knowledge_graph_entity_uid`),
  KEY `sys_ai_knowledge_graph_entity_kg_entity_type` (`sys_ai_knowledge_graph_entity_type`),
  KEY `sys_ai_knowledge_graph_entity_kg_entity_name` (`sys_ai_knowledge_graph_entity_name`),
  KEY `sys_ai_knowledge_graph_entity_kg_entity_library` (`sys_ai_knowledge_graph_library_id`)
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
  UNIQUE KEY `sys_ai_knowledge_graph_relation_kg_relation_uid` (`sys_ai_knowledge_graph_relation_uid`),
  KEY `sys_ai_knowledge_graph_relation_kg_relation_source` (`sys_ai_knowledge_graph_relation_source_entity_uid`),
  KEY `sys_ai_knowledge_graph_relation_kg_relation_target` (`sys_ai_knowledge_graph_relation_target_entity_uid`),
  KEY `sys_ai_knowledge_graph_relation_kg_relation_type` (`sys_ai_knowledge_graph_relation_type`),
  KEY `sys_ai_knowledge_graph_relation_kg_relation_library` (`sys_ai_knowledge_graph_relation_library_id`)
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
  UNIQUE KEY `sys_ai_knowledge_library_lib_name` (`sys_ai_knowledge_library_name`)
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
  UNIQUE KEY `sys_ai_reid_library_reid_vector_id` (`sys_ai_reid_library_vector_id`),
  KEY `sys_ai_reid_library_reid_group` (`sys_ai_reid_library_group`),
  KEY `sys_ai_reid_library_reid_face_id` (`sys_ai_reid_library_face_id`),
  KEY `sys_ai_reid_library_reid_name` (`sys_ai_reid_library_name`)
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
  KEY `sys_ai_knowledge_graph_entity_attribute_attr_entity` (`entity_uid`),
  KEY `sys_ai_knowledge_graph_entity_attribute_attr_library` (`library_id`),
  KEY `sys_ai_knowledge_graph_entity_attribute_attr_name` (`attr_name`)
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
  KEY `sys_ai_manufacturer_key_mfr_key_code` (`sys_ai_manufacturer_code`)
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
  KEY `sys_ai_token_token_user` (`sys_user_id`),
  KEY `sys_ai_token_token_key_hash` (`sys_ai_token_key_hash`),
  KEY `sys_ai_token_token_status` (`sys_ai_token_status`)
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
  KEY `sys_ai_balance_balance_user` (`sys_user_id`),
  KEY `sys_ai_balance_balance_status` (`sys_ai_balance_status`)
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
    UNIQUE KEY `sys_ai_group_model_group_module` (`sys_ai_group_id`, `sys_ai_module_id`)
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
    UNIQUE KEY `sys_ai_model_sys_ai_model_code` (`sys_ai_model_code`)
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
    UNIQUE KEY `sys_ai_role_sys_ai_role_code` (`sys_ai_role_code`)
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
    KEY `sys_ai_agent_ai_agent_parent` (`sys_ai_agent_parent_id`)
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
    KEY `sys_ai_mcp_server_sys_ai_mcp_server_name` (`sys_ai_mcp_server_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务器档案';
CREATE TABLE IF NOT EXISTS `sys_ai_requirement` (
    `sys_ai_requirement_id` VARCHAR(36) NOT NULL COMMENT '需求 ID（UUIDv7）',
    `sys_ai_requirement_display_no` INT DEFAULT NULL COMMENT '展示序号（自增）',
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
CREATE TABLE IF NOT EXISTS `sys_ai_data_source` (`sys_ai_data_source_id` VARCHAR(36) NOT NULL COMMENT '数据源 ID（UUIDv7）', `sys_ai_data_source_name` VARCHAR(128) NOT NULL COMMENT '数据源名称', `sys_ai_data_source_type` VARCHAR(64) NOT NULL COMMENT '类型: lark / jira / notion / generic', `sys_ai_data_source_config` TEXT NULL COMMENT '配置 JSON', `sys_ai_data_source_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用', `sys_ai_data_source_sync_cron` VARCHAR(64) NULL COMMENT '同步 cron 表达式', `sys_ai_data_source_last_sync_at` BIGINT NULL COMMENT '上次同步时间戳', `sys_ai_data_source_last_sync_status` VARCHAR(32) NULL COMMENT '上次同步状态', `sys_ai_data_source_last_sync_error` VARCHAR(512) NULL COMMENT '上次同步错误', `create_name` VARCHAR(64) NULL, `create_by` INT NULL, `create_time` BIGINT NULL, `update_name` VARCHAR(64) NULL, `update_by` INT NULL, `update_time` BIGINT NULL, PRIMARY KEY (`sys_ai_data_source_id`), KEY `sys_ai_data_source_sys_ai_ds_type` (`sys_ai_data_source_type`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求数据源提供方配置';
ALTER TABLE `sys_ai_bot_binding`
    ADD COLUMN `sys_ai_bot_binding_model` VARCHAR(128) NULL COMMENT '绑定的 AI 模型（用于微信聊天时 AI 回复）' AFTER `sys_ai_bot_binding_bind_source`;
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_extra` TEXT NULL COMMENT '扩展配置JSON';
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_proxy` VARCHAR(512) NULL COMMENT 'HTTP代理地址（如 http://127.0.0.1:7890、socks5://127.0.0.1:1080）';
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_type` VARCHAR(64) NULL DEFAULT 'custom' COMMENT '供应商类型（openai/anthropic/gemini/qwen/custom）';
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_api_key` VARCHAR(512) NULL COMMENT '提供商 API Key';
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_logo` VARCHAR(1024) NULL COMMENT 'Logo 图片地址';
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_models` TEXT NULL COMMENT '可用模型列表（JSON 字符串）';
ALTER TABLE `sys_ai_provider` ADD COLUMN IF NOT EXISTS `sys_ai_provider_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记';
INSERT IGNORE INTO `sys_ai_face_group` (`sys_ai_face_group_name`, `sys_ai_face_group_sort`)
VALUES ('default', 0);
UPDATE `sys_ai_manufacturer`
SET `sys_ai_manufacturer_base_url` = 'https://newapis.xyz/v1',
    `sys_ai_manufacturer_claude_compatible` = 1
WHERE `sys_ai_manufacturer_code` = 'infer-ai'
  AND (`sys_ai_manufacturer_claude_compatible` = 0 OR `sys_ai_manufacturer_base_url` = 'https://newapis.xyz');
UPDATE `sys_ai_module`
SET `sys_ai_module_concurrency` = 1
WHERE `sys_ai_module_code` IN ('GLM-4.7-Flash', 'GLM-4.7', 'GLM-4.6V', 'GLM-4.6')
  AND (`sys_ai_module_concurrency` IS NULL OR `sys_ai_module_concurrency` = 0);
DELETE FROM `sys_ai_module`
WHERE `sys_ai_module_type` = 'LLM'
  AND `sys_ai_module_code` NOT IN (
    'KAT-Coder-Exp-72B-1010', 'DeepSeek-R1-0528-Qwen3-8B',
    'deepseek-v4-pro', 'Qwen/Qwen3-32B', 'deepseek-ai/DeepSeek-V4-Flash',
    'gpt-4o', 'gpt-4o-mini', 'gpt-5.2',
    'deepseek-ai/DeepSeek-V3', 'Qwen/Qwen2.5-72B-Instruct',
    'meta-llama/Meta-Llama-3.1-8B-Instruct',
    'deepseek-chat', 'deepseek-reasoner', 'deepseek-coder',
    'qwen-plus', 'qwen-max', 'qwen-turbo',
    'llama3.2', 'qwen2.5', 'deepseek-r1',
    'doubao-pro-32k', 'doubao-lite-32k',
    'hunyuan-pro', 'hunyuan-standard',
    'moonshot-v1-32k', 'kimi-k2-thinking',
    'abab6.5s-chat', 'abab6.5-pro',
    'glm-4-plus', 'glm-4-flash', 'glm-4-air',
    'gemini-2.0-flash', 'gemini-2.0-pro',
    'pangu-text-chat', 'pangu-text-turbo',
    'claude-opus-4-7', 'claude-sonnet-4-6', 'claude-haiku-4-5-20251001',
    'Spark Pro', 'Spark Max',
    'GLM-4.7', 'GLM-4.7-Flash', 'GLM-4.6V-Flash',
    'GLM-4.1V-Thinking-Flash', 'GLM-4V-Flash', 'GLM-5.1',
    'Cogview-3-Flash', 'CogVideoX-Flash',
    'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    'deepseek/deepseek-v4-flash:free', 'z-ai/glm-5.1',
    'google/gemma-4-26b-a4b-it:free',
    'deepseek-default', 'deepseek-v4-flash-l'
  );
UPDATE `sys_ai_module`
SET `sys_ai_module_type` = 'CHAT'
WHERE `sys_ai_module_type` = 'LLM';
INSERT IGNORE INTO `sys_ai_group` (`sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`, `sys_ai_group_sort`, `sys_ai_group_status`)
VALUES (1, '全部模型', '可访问所有已启用的模型', 0, 1);
INSERT IGNORE INTO `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_name`, `sys_ai_knowledge_library_description`, `sys_ai_knowledge_library_sort`
) VALUES ('默认知识库', '系统默认知识库', 0);
INSERT IGNORE INTO `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_name`, `sys_ai_knowledge_library_description`,
  `sys_ai_knowledge_library_summary`, `sys_ai_knowledge_library_sort`
) VALUES ('三国演义', '《三国演义》历史知识图谱 - 184年黄巾起义至280年西晋统一', '## 提取范围

三国时期（184年-280年）是中国历史上一个群雄割据的动荡时期。请提取以下各类实体和关系。

## 核心人物（按阵营）

### 蜀汉（刘备阵营）
- 刘备（字玄德，蜀汉开国皇帝）
- 关羽（字云长，五虎上将之首）
- 张飞（字翼德，五虎上将）
- 诸葛亮（字孔明，丞相）
- 赵云（字子龙，五虎上将）
- 马超（字孟起，五虎上将）
- 黄忠（字汉升，五虎上将）
- 魏延（字文长，汉中太守）
- 姜维（字伯约，蜀汉大将军）
- 庞统（字士元，军师中郎将）
- 法正（字孝直，尚书令）
- 马谡（字幼常，参军）

### 曹魏（曹操阵营）
- 曹操（字孟德，魏武帝）
- 曹丕（字子桓，魏文帝）
- 曹植（字子建，陈王）
- 司马懿（字仲达，太傅）
- 夏侯惇（字元让，大将军）
- 张辽（字文远，前将军）
- 许褚（字仲康，武卫将军）
- 郭嘉（字奉孝，军师祭酒）
- 荀彧（字文若，尚书令）
- 贾诩（字文和，太尉）
- 典韦（曹操近卫统领）
- 徐晃（字公明，右将军）

### 东吴（孙权阵营）
- 孙坚（字文台，破虏将军）
- 孙策（字伯符，讨逆将军）
- 孙权（字仲谋，吴大帝）
- 周瑜（字公瑾，大都督）
- 鲁肃（字子敬，都督）
- 吕蒙（字子明，都督）
- 陆逊（字伯言，丞相）
- 黄盖（字公覆，偏将军）
- 甘宁（字兴霸，折冲将军）
- 太史慈（字子义，建昌都尉）

### 群雄/其他
- 吕布（字奉先，温侯）
- 董卓（字仲颖，太师）
- 袁绍（字本初，大将军）
- 袁术（字公路，仲家皇帝）
- 貂蝉（王允义女）
- 汉献帝刘协（东汉末代皇帝）
- 王允（字子师，司徒）
- 刘表（字景升，荆州牧）
- 张角（太平道创始人）

## 需要提取的关系类型

- 君臣（served_as）：人物服务于某君主或政权
- 父子（father_of）：父子血缘关系
- 兄弟（brother_of）：兄弟关系（含结义）
- 夫妻（married_to）：配偶关系
- 盟友（allied_with）：军事/政治同盟
- 敌对（enemy_of）：战场敌对关系
- 师徒（teacher_of）：传授关系
- 举荐（recommended）：推荐人才
- 击败（defeated）：在战役中击败对手
- 继承（succeeded_by）：职位/政权继承
- 暗杀（assassinated）：刺杀行为
- 投降（surrendered_to）：投降某势力

## 重要事件时间线

- 184年 黄巾起义爆发
- 189年 董卓入京乱政
- 190年 关东诸侯讨董
- 192年 王允连环计杀董卓
- 196年 曹操迎汉献帝迁都许昌
- 200年 官渡之战（曹操大败袁绍）
- 207年 三顾茅庐请诸葛亮
- 208年 赤壁之战（孙刘联军败曹操）
- 214年 刘备入蜀夺取益州
- 219年 关羽水淹七军，吕蒙白衣渡江取荆州
- 220年 曹丕称帝建魏，汉朝灭亡
- 221年 刘备称帝建蜀汉，发动夷陵之战
- 222年 陆逊火烧连营，蜀汉大败
- 225年 诸葛亮七擒孟获平定南中
- 228年 诸葛亮第一次北伐（失街亭斩马谡）
- 228-234年 诸葛亮六出祁山
- 234年 五丈原诸葛亮病逝
- 249年 高平陵之变司马懿夺权
- 263年 邓艾钟会伐蜀，蜀汉灭亡
- 265年 司马炎建立西晋
- 280年 西晋灭吴统一天下

## 势力阵营

蜀汉、曹魏、东吴三方鼎立，每个角色应标注所属势力。', 1);
INSERT INTO `sys_ai_manufacturer` (
  `sys_ai_manufacturer_code`, `sys_ai_manufacturer_name`,
  `sys_ai_manufacturer_factory`, `sys_ai_manufacturer_base_url`,
  `sys_ai_manufacturer_app_key`,
  `sys_ai_manufacturer_openai_compatible`,
  `sys_ai_manufacturer_claude_compatible`,
  `sys_ai_manufacturer_status`, `sys_ai_manufacturer_sort`,
  `sys_ai_manufacturer_remark`
)
SELECT * FROM (
  -- ── 本地 / 代理厂家 ──────────────────────────────
  SELECT 'local-onnx' AS code, '本地ONNX模型' AS name,
    NULL AS factory, NULL AS base_url,
    NULL AS app_key,
    0 AS openai_compat, 0 AS claude_compat, 1 AS status, 5 AS sort,
    '本地 ONNX 推理模型（人脸检测/特征提取/OCR/ReID 等）' AS remark
  UNION ALL
  SELECT 'baishan', '白山智算',
    NULL, 'https://api.edgefn.net/v1',
    'sk-REPLACE_WITH_BAISHAN_KEY',
    1, 0, 1, 10,
    '白山智算 API，支持 KAT-Coder / DeepSeek-R1 等模型'
  UNION ALL
  SELECT 'infer-ai', 'Infer AI',
    NULL, 'https://newapis.xyz/v1',
    'sk-REPLACE_WITH_INFER_KEY',
    1, 1, 1, 20,
    'Claude Code 使用的 Infer AI 代理，支持 deepseek-v4-pro（Claude 协议）'
  UNION ALL
  SELECT 'claude-test', 'Claude 测试',
    NULL, 'https://newapis.xyz/v1',
    'sk-REPLACE_WITH_INFER_KEY',
    1, 1, 1, 25,
    'Claude 协议测试厂家，支持 deepseek-v4-pro'
  UNION ALL
  SELECT 'modelscope', 'ModelScope',
    NULL, 'https://api-inference.modelscope.cn/v1',
    'ms-REPLACE_WITH_MODELSCOPE_KEY',
    1, 0, 1, 30,
    '阿里 ModelScope 推理 API，支持 Qwen3 等模型'
  UNION ALL
  -- ── OpenAI ───────────────────────────────────────
  SELECT 'openai', 'OpenAI',
    NULL, 'https://api.openai.com/v1',
    '',
    1, 0, 1, 100,
    'OpenAI API，支持 GPT-4o / GPT-4o-mini / o3 等模型'
  UNION ALL
  -- ── SiliconFlow ──────────────────────────────────
  SELECT 'siliconflow', '硅基流动（SiliconFlow）',
    NULL, 'https://api.siliconflow.cn/v1',
    '',
    1, 0, 1, 110,
    'SiliconFlow 开源模型 API 服务，支持 DeepSeek-V3 / Qwen2.5 / Llama 等'
  UNION ALL
  -- ── DeepSeek ─────────────────────────────────────
  SELECT 'deepseek', 'DeepSeek',
    NULL, 'https://api.deepseek.com',
    '',
    1, 0, 1, 115,
    'DeepSeek 官方 API，支持 deepseek-chat / deepseek-reasoner'
  UNION ALL
  -- ── 阿里云通义千问 ──────────────────────────────
  SELECT 'aliyun', '阿里云通义千问',
    NULL, 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    '',
    1, 0, 1, 120,
    '阿里云 DashScope API，支持 Qwen-Plus / Qwen-Max 等通义千问模型'
  UNION ALL
  -- ── Ollama ───────────────────────────────────────
  SELECT 'ollama', 'Ollama（本地）',
    NULL, 'http://localhost:11434',
    '',
    1, 0, 1, 130,
    'Ollama 本地大模型服务，支持 Llama / Qwen / DeepSeek 等本地运行'
  UNION ALL
  -- ── 豆包 ─────────────────────────────────────────
  SELECT 'doubao', '豆包大模型',
    NULL, 'https://ark.cn-beijing.volces.com/api/v3',
    '',
    1, 0, 1, 140,
    '字节跳动豆包大模型 API，支持 doubao-pro / doubao-lite 系列'
  UNION ALL
  -- ── 腾讯混元 ─────────────────────────────────────
  SELECT 'tencent', '腾讯混元',
    NULL, 'https://api.hunyuan.cloud.tencent.com/v1',
    '',
    1, 0, 1, 145,
    '腾讯混元大模型 API，支持 hunyuan-pro / hunyuan-standard'
  UNION ALL
  -- ── Kimi ─────────────────────────────────────────
  SELECT 'kimi', '月之暗面（Kimi）',
    NULL, 'https://api.moonshot.cn/v1',
    '',
    1, 0, 1, 150,
    '月之暗面 Kimi API，支持 moonshot-v1 / kimi-k2 系列'
  UNION ALL
  -- ── MiniMax ──────────────────────────────────────
  SELECT 'minimax', 'MiniMax',
    NULL, 'https://api.minimax.chat/v1',
    '',
    1, 0, 1, 155,
    'MiniMax API，支持 abab6.5 系列模型'
  UNION ALL
  -- ── 智谱 ─────────────────────────────────────────
  SELECT 'zhipu', '智谱（Zhipu AI）',
    NULL, 'https://open.bigmodel.cn/api/paas/v4',
    '',
    1, 0, 1, 160,
    '智谱 AI 开放平台，支持 GLM-4 系列模型'
  UNION ALL
  -- ── Google Gemini ────────────────────────────────
  SELECT 'google', 'Google Gemini',
    NULL, 'https://generativelanguage.googleapis.com/v1beta',
    '',
    0, 0, 1, 165,
    'Google Gemini API，支持 gemini-2.0-flash / gemini-2.0-pro'
  UNION ALL
  -- ── 华为云盘古 ──────────────────────────────────
  SELECT 'huawei', '华为云盘古',
    NULL, 'https://pangu.cn-north-4.myhuaweicloud.com/v1',
    '',
    1, 0, 1, 170,
    '华为云盘古大模型 API，支持 pangu-text 系列'
  UNION ALL
  -- ── Claude ───────────────────────────────────────
  SELECT 'claude', 'Anthropic Claude',
    NULL, 'https://api.anthropic.com',
    '',
    0, 1, 1, 175,
    'Anthropic Claude API，支持 Claude Opus / Sonnet 系列'
  UNION ALL
  -- ── 讯飞星火 ─────────────────────────────────────
  SELECT 'xunfei', '讯飞星火',
    NULL, 'https://spark-api.xf-yun.com/v3.5',
    '',
    0, 0, 1, 180,
    '讯飞星火大模型 API，支持 Spark Pro / Max / V3.5 系列'
  UNION ALL
  -- ── Z.AI（智谱生态） ────────────────────────────
  SELECT 'zai', 'Z.AI（智谱生态）',
    NULL, 'https://api.z.ai/api/paas/v4',
    'REPLACE_WITH_ZHIPU_KEY',
    1, 0, 1, 185,
    'Z.AI API（智谱生态），提供 GLM-4.7 / GLM-4.6 / Cogview / CogVideo 等旗舰模型'
  UNION ALL
  -- ── OpenRouter ─────────────────────────────────────
  SELECT 'openrouter', 'OpenRouter',
    NULL, 'https://openrouter.ai/api/v1/chat/completions',
    'sk-or-REPLACE_WITH_OPENROUTER_KEY',
    1, 0, 1, 195,
    'OpenRouter API 聚合平台，提供多种开源/商业模型的统一访问入口'
  UNION ALL
  -- ── DeepSeek 本地 ─────────────────────────────────────
  SELECT 'deepseek-default', 'DeepSeek 本地',
    NULL, 'http://127.0.0.1:27777/v1',
    'sk-REPLACE_WITH_DEEPSEEK_KEY',
    1, 0, 1, 200,
    '本地部署的 DeepSeek 模型，兼容 OpenAI API'
  UNION ALL
  -- ── Sixthsense（Claude Code 兼容） ──────────────────
  SELECT 'sixthsense', 'Sixthsense LLM',
    NULL, 'https://sixthsense-llm.com',
    'sk-REPLACE_WITH_SIXTHSENSE_KEY',
    0, 1, 1, 205,
    'Sixthsense LLM API，Claude Code 兼容协议，支持 deepseek-v4-flash-l'
  UNION ALL
  -- ── SafeTensors（本地 Python 推理） ──────────────────
  SELECT 'safetensors', 'SafeTensors（本地Python）',
    NULL, NULL,
    NULL,
    0, 0, 1, 210,
    'SafeTensors 本地 Python 推理引擎，支持 Qwen2.5 / Llama3.2 / MiniMind2 等 safetensors 格式模型'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_manufacturer`
  WHERE `sys_ai_manufacturer_code` = seed.code
);

-- 更新已有 infer-ai 厂家：修正 base_url、开启 claude_compatible
INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_status`, `sys_ai_module_sort`,
  `sys_ai_module_vlm`, `sys_ai_module_support_think`,
  `sys_ai_module_concurrency`,
  `sys_ai_module_input_price`, `sys_ai_module_output_price`,
  `sys_ai_module_remark`
)
SELECT * FROM (
  -- ── 本地代理 / 私有模型 ──────────────────────────
  SELECT 'KAT-Coder-Exp-72B-1010' AS name, 'KAT-Coder-Exp-72B-1010' AS code,
    'CHAT' AS type, 'baishan' AS vendor, 1 AS status, 10 AS sort,
    0 AS vlm, 0 AS think,
    1 AS concurrency,
    0.0000 AS in_price, 0.0000 AS out_price,
    '白山智算 - KAT Coder 72B 实验版' AS remark
  UNION ALL
  SELECT 'DeepSeek-R1-0528-Qwen3-8B', 'DeepSeek-R1-0528-Qwen3-8B',
    'CHAT', 'baishan', 1, 20,
    0, 1,
    1,
    0.0000, 0.0000,
    '白山智算 - DeepSeek R1 推理模型(Qwen3-8B 蒸馏版)'
  UNION ALL
  SELECT 'DeepSeek-V4-Pro', 'deepseek-v4-pro',
    'CHAT', 'infer-ai', 1, 30,
    0, 0,
    1,
    0.0000, 0.0000,
    'Infer AI / Claude 测试 - DeepSeek V4 Pro（Claude 协议）'
  UNION ALL
  SELECT 'Qwen3-32B', 'Qwen/Qwen3-32B',
    'CHAT', 'modelscope', 1, 40,
    0, 0,
    1,
    0.0000, 0.0000,
    'ModelScope - 通义千问3 32B'
  UNION ALL
  SELECT 'DeepSeek-V4-Flash (ModelScope)', 'deepseek-ai/DeepSeek-V4-Flash',
    'CHAT', 'modelscope', 1, 45,
    0, 0,
    1,
    0.0000, 0.0000,
    'ModelScope - DeepSeek V4 Flash（免费，1M上下文）'

  UNION ALL
  -- ── OpenAI ─────────────────────────────────────────────
  SELECT 'GPT-4o', 'gpt-4o',
    'CHAT', 'openai', 1, 101,
    1, 0,
    1,
    2.5000, 10.0000,
    'OpenAI - GPT-4o 多模态通用模型，支持图像和文本'
  UNION ALL
  SELECT 'GPT-4o-mini', 'gpt-4o-mini',
    'CHAT', 'openai', 1, 102,
    1, 0,
    1,
    0.1500, 0.6000,
    'OpenAI - GPT-4o-mini 轻量多模态模型，速度快成本低'
  UNION ALL
  SELECT 'GPT-5.2', 'gpt-5.2',
    'CHAT', 'openai', 1, 100,
    0, 1,
    1,
    1.7500, 14.0000,
    'OpenAI - GPT-5.2 最新旗舰模型'

  UNION ALL
  -- ── SiliconFlow ────────────────────────────────────────
  SELECT 'DeepSeek-V3 (SiliconFlow)', 'deepseek-ai/DeepSeek-V3',
    'CHAT', 'siliconflow', 1, 111,
    0, 0,
    1,
    0.2800, 0.4200,
    'SiliconFlow - DeepSeek V3 高性能模型'
  UNION ALL
  SELECT 'Qwen2.5-72B-Instruct (SiliconFlow)', 'Qwen/Qwen2.5-72B-Instruct',
    'CHAT', 'siliconflow', 1, 112,
    0, 0,
    1,
    0.6000, 0.6000,
    'SiliconFlow - Qwen2.5 72B 高性能开源模型'
  UNION ALL
  SELECT 'Llama-3.1-8B-Instruct (SiliconFlow)', 'meta-llama/Meta-Llama-3.1-8B-Instruct',
    'CHAT', 'siliconflow', 1, 113,
    0, 0,
    1,
    0.1000, 0.1000,
    'SiliconFlow - Meta Llama 3.1 8B 轻量模型'

  UNION ALL
  -- ── DeepSeek ───────────────────────────────────────────
  SELECT 'DeepSeek Chat', 'deepseek-chat',
    'CHAT', 'deepseek', 1, 116,
    0, 0,
    1,
    0.1400, 0.2800,
    'DeepSeek - 通用对话模型，中文表现优秀'
  UNION ALL
  SELECT 'DeepSeek Reasoner', 'deepseek-reasoner',
    'CHAT', 'deepseek', 1, 117,
    0, 1,
    1,
    0.5500, 2.1900,
    'DeepSeek - 推理强化模型，深度思考能力'
  UNION ALL
  SELECT 'DeepSeek Coder', 'deepseek-coder',
    'CHAT', 'deepseek', 1, 118,
    0, 0,
    1,
    0.1400, 0.2800,
    'DeepSeek - 代码专用模型，编程能力强'

  UNION ALL
  -- ── 阿里云通义千问 ──────────────────────────────────────
  SELECT 'Qwen-Plus', 'qwen-plus',
    'CHAT', 'aliyun', 1, 121,
    0, 0,
    1,
    0.1150, 0.4600,
    '阿里云 - Qwen-Plus 高性能对话模型，平衡性能与成本'
  UNION ALL
  SELECT 'Qwen-Max', 'qwen-max',
    'CHAT', 'aliyun', 1, 122,
    0, 0,
    1,
    1.2000, 6.0000,
    '阿里云 - Qwen-Max 旗舰模型，最强性能'
  UNION ALL
  SELECT 'Qwen-Turbo', 'qwen-turbo',
    'CHAT', 'aliyun', 1, 123,
    0, 0,
    1,
    0.0350, 0.1400,
    '阿里云 - Qwen-Turbo 轻量快速模型'

  UNION ALL
  -- ── Ollama ─────────────────────────────────────────────
  SELECT 'Ollama Llama 3.2', 'llama3.2',
    'CHAT', 'ollama', 1, 131,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - Meta Llama 3.2 本地免费运行'
  UNION ALL
  SELECT 'Ollama Qwen 2.5', 'qwen2.5',
    'CHAT', 'ollama', 1, 132,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - Qwen 2.5 本地免费运行'
  UNION ALL
  SELECT 'Ollama DeepSeek R1', 'deepseek-r1',
    'CHAT', 'ollama', 1, 133,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - DeepSeek R1 本地免费运行'

  UNION ALL
  -- ── 豆包 ───────────────────────────────────────────────
  SELECT 'Doubao Pro 32K', 'doubao-pro-32k',
    'CHAT', 'doubao', 1, 141,
    0, 0,
    1,
    0.8000, 2.0000,
    '字节跳动 - doubao-pro-32k 专业版长上下文模型'
  UNION ALL
  SELECT 'Doubao Lite 32K', 'doubao-lite-32k',
    'CHAT', 'doubao', 1, 142,
    0, 0,
    1,
    0.4000, 1.0000,
    '字节跳动 - doubao-lite-32k 轻量版长上下文模型'

  UNION ALL
  -- ── 腾讯混元 ───────────────────────────────────────────
  SELECT 'Hunyuan Pro', 'hunyuan-pro',
    'CHAT', 'tencent', 1, 146,
    0, 0,
    1,
    0.5000, 2.0000,
    '腾讯混元 - hunyuan-pro 专业版模型'
  UNION ALL
  SELECT 'Hunyuan Standard', 'hunyuan-standard',
    'CHAT', 'tencent', 1, 147,
    0, 0,
    1,
    0.2000, 0.8000,
    '腾讯混元 - hunyuan-standard 标准版模型'

  UNION ALL
  -- ── Kimi ───────────────────────────────────────────────
  SELECT 'Moonshot v1 32K', 'moonshot-v1-32k',
    'CHAT', 'kimi', 1, 151,
    0, 0,
    1,
    12.0000, 12.0000,
    '月之暗面 - moonshot-v1-32k 长上下文模型（按 ¥/百万tokens 计费）'
  UNION ALL
  SELECT 'Kimi K2 Thinking', 'kimi-k2-thinking',
    'CHAT', 'kimi', 1, 152,
    0, 1,
    1,
    0.0000, 0.0000,
    '月之暗面 - kimi-k2-thinking 推理强化模型，MoE 架构'

  UNION ALL
  -- ── MiniMax ────────────────────────────────────────────
  SELECT 'MiniMax abab6.5s', 'abab6.5s-chat',
    'CHAT', 'minimax', 1, 156,
    0, 0,
    1,
    0.0000, 0.0000,
    'MiniMax - abab6.5s-chat 通用对话模型，快速响应'
  UNION ALL
  SELECT 'MiniMax abab6.5 Pro', 'abab6.5-pro',
    'CHAT', 'minimax', 1, 157,
    0, 0,
    1,
    0.0000, 0.0000,
    'MiniMax - abab6.5-pro 专业版模型，最强性能'

  UNION ALL
  -- ── 智谱 ───────────────────────────────────────────────
  SELECT 'GLM-4-Plus', 'glm-4-plus',
    'CHAT', 'zhipu', 1, 161,
    0, 0,
    1,
    0.5000, 2.0000,
    '智谱 - GLM-4-Plus 旗舰模型'
  UNION ALL
  SELECT 'GLM-4-Flash', 'glm-4-flash',
    'CHAT', 'zhipu', 1, 162,
    0, 0,
    1,
    0.0000, 0.0000,
    '智谱 - GLM-4-Flash 免费模型'
  UNION ALL
  SELECT 'GLM-4-Air', 'glm-4-air',
    'CHAT', 'zhipu', 1, 163,
    0, 0,
    1,
    0.0100, 0.0100,
    '智谱 - GLM-4-Air 轻量模型'

  UNION ALL
  -- ── Google Gemini ──────────────────────────────────────
  SELECT 'Gemini 2.0 Flash', 'gemini-2.0-flash',
    'CHAT', 'google', 1, 166,
    1, 0,
    1,
    0.1000, 0.4000,
    'Google - Gemini 2.0 Flash 快速多模态模型'
  UNION ALL
  SELECT 'Gemini 2.0 Pro', 'gemini-2.0-pro',
    'CHAT', 'google', 1, 167,
    1, 0,
    1,
    1.0000, 4.0000,
    'Google - Gemini 2.0 Pro 旗舰多模态模型'

  UNION ALL
  -- ── 华为盘古 ───────────────────────────────────────────
  SELECT '盘古 Text Chat', 'pangu-text-chat',
    'CHAT', 'huawei', 1, 171,
    0, 0,
    1,
    0.0080, 0.0080,
    '华为云 - pangu-text-chat 通用对话模型（按 ¥/千tokens 计费）'
  UNION ALL
  SELECT '盘古 Text Turbo', 'pangu-text-turbo',
    'CHAT', 'huawei', 1, 172,
    0, 0,
    1,
    0.0050, 0.0050,
    '华为云 - pangu-text-turbo 极速响应模型（按 ¥/千tokens 计费）'

  UNION ALL
  -- ── Claude ─────────────────────────────────────────────
  SELECT 'Claude Opus 4.7', 'claude-opus-4-7',
    'CHAT', 'claude', 1, 176,
    1, 0,
    1,
    15.0000, 75.0000,
    'Anthropic - Claude Opus 4.7 旗舰智能模型'
  UNION ALL
  SELECT 'Claude Sonnet 4.6', 'claude-sonnet-4-6',
    'CHAT', 'claude', 1, 177,
    1, 0,
    1,
    3.0000, 15.0000,
    'Anthropic - Claude Sonnet 4.6 最佳性价比模型'
  UNION ALL
  SELECT 'Claude Haiku 4.5', 'claude-haiku-4-5-20251001',
    'CHAT', 'claude', 1, 178,
    1, 0,
    1,
    1.0000, 5.0000,
    'Anthropic - Claude Haiku 4.5 轻量快速模型'

  UNION ALL
  -- ── 讯飞星火 ───────────────────────────────────────────
  SELECT 'Spark Pro', 'Spark Pro',
    'CHAT', 'xunfei', 1, 181,
    0, 0,
    1,
    0.0080, 0.0080,
    '讯飞 - Spark Pro 专业版模型（按 ¥/千tokens 计费）'
  UNION ALL
  SELECT 'Spark Max', 'Spark Max',
    'CHAT', 'xunfei', 1, 182,
    0, 0,
    1,
    0.0120, 0.0120,
    '讯飞 - Spark Max 旗舰模型（按 ¥/千tokens 计费）'

  UNION ALL
  -- ── Z.AI ───────────────────────────────────────────────
  SELECT 'GLM-4.7 (Z.AI)', 'GLM-4.7',
    'CHAT', 'zai', 1, 186,
    0, 0,
    1,
    0.6000, 2.2000,
    'Z.AI - GLM-4.7 最新旗舰文本模型（最佳性能）'
  UNION ALL
  SELECT 'GLM-4.7-Flash (Z.AI)', 'GLM-4.7-Flash',
    'CHAT', 'zai', 1, 187,
    0, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - GLM-4.7-Flash 免费文本模型'
  UNION ALL
  SELECT 'GLM-4.6V-Flash', 'GLM-4.6V-Flash',
    'CHAT', 'zai', 1, 188,
    1, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - GLM-4.6V-Flash 免费视觉模型（价格请以官方为准）'
  UNION ALL
  SELECT 'GLM-4.1V-Thinking-Flash', 'GLM-4.1V-Thinking-Flash',
    'CHAT', 'zai', 1, 189,
    1, 1,
    5,
    0.3000, 0.9000,
    'Z.AI - GLM-4.1V-Thinking-Flash 视觉推理模型（价格请以官方为准）'
  UNION ALL
  SELECT 'GLM-4V-Flash', 'GLM-4V-Flash',
    'CHAT', 'zai', 1, 190,
    1, 0,
    10,
    0.0000, 0.0000,
    'Z.AI - GLM-4V-Flash 视觉模型，高并发'
  UNION ALL
  SELECT 'GLM-5.1', 'GLM-5.1',
    'CHAT', 'zai', 1, 191,
    0, 1,
    10,
    2.0000, 8.0000,
    'Z.AI - GLM-5.1 最新旗舰文本模型（价格请以官方为准）'
  UNION ALL
  SELECT 'Cogview-3-Flash', 'Cogview-3-Flash',
    'IMAGE', 'zai', 1, 192,
    0, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - Cogview-3-Flash 图像生成模型，支持 1024x1024 / 768x1344 / 864x1152 / 1344x768 / 1152x864 / 1440x720 / 720x1440 等多种分辨率'
  UNION ALL
  SELECT 'CogVideoX-Flash', 'CogVideoX-Flash',
    'VIDEO', 'zai', 1, 193,
    0, 0,
    3,
    0.0000, 0.0000,
    'Z.AI - CogVideoX-Flash 视频生成模型'
  UNION ALL
  -- ── OpenRouter ────────────────────────────────────────────────
  SELECT 'Nemotron-3-Nano-Omni-30B-A3B-Reasoning (OpenRouter)', 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    'CHAT', 'openrouter', 1, 196,
    0, 1,
    5,
    0.0000, 0.0000,
    'OpenRouter - Nvidia Nemotron 3 Nano Omni 30B A3B Reasoning（免费，256K上下文）'
  UNION ALL
  SELECT 'DeepSeek-V4-Flash (OpenRouter)', 'deepseek/deepseek-v4-flash:free',
    'CHAT', 'openrouter', 1, 197,
    0, 0,
    10,
    0.0000, 0.0000,
    'OpenRouter - DeepSeek V4 Flash（免费，1M上下文）'
  UNION ALL
  SELECT 'GLM-5.1 (OpenRouter)', 'z-ai/glm-5.1',
    'CHAT', 'openrouter', 1, 198,
    0, 1,
    10,
    0.0000, 0.0000,
    'OpenRouter - Z.AI GLM-5.1 旗舰文本模型（203K上下文，免费）'
  UNION ALL
  SELECT 'Gemma-4-26B-A4B-IT (OpenRouter)', 'google/gemma-4-26b-a4b-it:free',
    'CHAT', 'openrouter', 1, 199,
    0, 0,
    5,
    0.0000, 0.0000,
    'OpenRouter - Google Gemma 4 26B A4B IT（免费，262K上下文）'
  UNION ALL
  -- ── DeepSeek 本地 ─────────────────────────────────────────────
  SELECT 'DeepSeek-Default', 'deepseek-default',
    'CHAT', 'deepseek-default', 1, 200,
    0, 0,
    10,
    0.0000, 0.0000,
    '本地 DeepSeek 默认模型（http://127.0.0.1:27777）'
  UNION ALL
  -- ── Sixthsense ─────────────────────────────────────────────────
  SELECT 'DeepSeek-V4-Flash-L', 'deepseek-v4-flash-l',
    'CHAT', 'sixthsense', 1, 201,
    0, 0,
    5,
    0.0000, 0.0000,
    'Sixthsense LLM - DeepSeek V4 Flash L（Claude Code 兼容协议）'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);
INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_model_id`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_version`, `sys_ai_module_local`, `sys_ai_module_status`,
  `sys_ai_module_sort`, `sys_ai_module_remark`
)
SELECT * FROM (
  -- ── 图像上色 ────────────────────────────────────────
  SELECT '本地 ONNX 图像上色 (DDColor)' AS name, 'local-onnx-colorization' AS code,
    'traced_ddcolor' AS model_id, 'COLORIZATION' AS type,
    'local-onnx' AS vendor, 'onnx' AS ver, 1 AS local, 1 AS status, 10 AS sort,
    '内置本地 ONNX DDColor 图像上色模块' AS remark
  UNION ALL
  -- ── 分辨率增强 ──────────────────────────────────────
  SELECT '本地 ONNX 图片增强 (Real-ESRGAN)', 'local-onnx-resolution',
    'real_esrgan', 'RESOLUTION',
    'local-onnx', 'onnx', 1, 1, 20,
    '内置本地 ONNX Real-ESRGAN 图片增强模块'
  UNION ALL
  -- ── 人脸增强 ────────────────────────────────────────
  SELECT '本地 ONNX 人脸增强 (GFPGAN)', 'local-onnx-face-enhance',
    'gfpgan', 'FACE',
    'local-onnx', 'onnx', 1, 1, 30,
    '内置本地 ONNX GFPGAN 人脸增强模块'
  UNION ALL
  SELECT '本地 ONNX 人脸增强 (RestoreFormer++)', 'local-onnx-face-enhance-restoreformer',
    'restoreformer_plusplus', 'FACE',
    'local-onnx', 'onnx', 1, 1, 31,
    '内置本地 ONNX RestoreFormer++ 人脸增强模块'
  UNION ALL
  -- ── 人脸检测 ────────────────────────────────────────
  SELECT '本地 ONNX 人脸检测 (Slim)', 'local-onnx-face-detect-slim',
    'slim', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 40,
    '内置本地 ONNX UltraFace Slim 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (MobileNet)', 'local-onnx-face-detect-mobilenet',
    'mobilenet', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 41,
    '内置本地 ONNX UltraFace MobileNet 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (RFB)', 'local-onnx-face-detect-rfb',
    'RFB', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 42,
    '内置本地 ONNX UltraFace RFB 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (SCRFD)', 'local-onnx-face-detect-scrfd',
    'scrfd_2_5g_bnkps', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 43,
    '内置本地 ONNX SCRFD 2.5G 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (RetinaFace 640)', 'local-onnx-face-retina-640',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 44,
    '基于 RetinaFace 的 ONNX 人脸检测模型（640px）'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (RetinaFace 1080x720)', 'local-onnx-face-retina-1080x720',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 45,
    '基于 RetinaFace 的 ONNX 人脸检测模型（1080x720）'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (UltraLight)', 'local-onnx-face-ultralight',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 46,
    'UltraLight 快速通用 ONNX 人脸检测模型'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (YOLOv5 320)', 'local-onnx-face-yolov5-320',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 47,
    '基于 YOLOv5 的 ONNX 人脸检测模型（320px）'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (YOLOv5 640)', 'local-onnx-face-yolov5-640',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 48,
    '基于 YOLOv5 的 ONNX 人脸检测模型（640px）'
  UNION ALL
  -- ── 人脸特征提取 ────────────────────────────────────
  SELECT '本地 ONNX 人脸特征 (ArcFace 512D)', 'local-onnx-face-feature-arcface',
    'face_feature', 'FACE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 50,
    '内置本地 ONNX ArcFace 512D 人脸特征提取模块'
  UNION ALL
  SELECT '本地 ONNX 人脸特征 (ResNet-50 512D)', 'local-onnx-face-feature-r50',
    'face_feature_r50', 'FACE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 51,
    '内置本地 ONNX ResNet-50 512D 人脸特征提取模块'
  UNION ALL
  -- ── 抠图 ────────────────────────────────────────────
  SELECT '本地 ONNX 抠图 (U2Net)', 'local-onnx-matting-u2net',
    'u2net', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 60,
    '内置本地 ONNX U2Net 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (U2Net-P 轻量)', 'local-onnx-matting-u2netp',
    'u2netp', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 61,
    '内置本地 ONNX U2Net-P 轻量级图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (U2Net_UNet)', 'local-onnx-matting-u2net-unet',
    'u2net_unet', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 62,
    '内置本地 ONNX U2Net_UNet 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Anime 动漫)', 'local-onnx-matting-anime',
    'anime', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 63,
    '内置本地 ONNX Anime 动漫风格图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Human 人像)', 'local-onnx-matting-human',
    'human', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 64,
    '内置本地 ONNX Human 人像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Silueta)', 'local-onnx-matting-silueta',
    'silueta', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 65,
    '内置本地 ONNX Silueta 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Cloth 衣物)', 'local-onnx-matting-cloth',
    'cloth', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 66,
    '内置本地 ONNX Cloth 衣物抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (ISNet 通用)', 'local-onnx-matting-isnet-general-use',
    'isnet-general-use', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 67,
    '内置本地 ONNX ISNet 通用图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (RMBG-2.0)', 'local-onnx-matting-rmbg',
    'RMBG-2.0', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 68,
    '内置本地 ONNX RMBG-2.0 高精度图像抠图模块'
  UNION ALL
  -- ── 图像特征提取（以图搜图） ────────────────────────
  SELECT '本地 ONNX 图像特征 (CLIP ViT-B-32)', 'local-onnx-image-feature-clip-b32',
    'CLIP-ViT-B-32-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 70,
    '内置本地 ONNX CLIP ViT-B-32 图像特征提取模块（512维，用于以图搜图）'
  UNION ALL
  SELECT '本地 ONNX 图像特征 (CLIP ViT-B-16)', 'local-onnx-image-feature-clip-b16',
    'CLIP-ViT-B-16-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 71,
    '内置本地 ONNX CLIP ViT-B-16 图像特征提取模块（512维，更高精度）'
  UNION ALL
  SELECT '本地 ONNX 图像特征 (DINOv2)', 'local-onnx-image-feature-dinov2',
    'DINOv2-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 72,
    '内置本地 ONNX DINOv2 图像特征提取模块（视觉基础模型）'
  UNION ALL
  SELECT '本地 ONNX 图像特征 (MobileCLIP)', 'local-onnx-image-feature-mobileclip',
    'MobileCLIP-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 73,
    '内置本地 ONNX MobileCLIP 轻量级图像特征提取模块'
  UNION ALL
  -- ── 文字识别 OCR ────────────────────────────────────
  SELECT '本地 ONNX 文字识别 (PP-OCRv4)', 'local-onnx-ocr-ppv4',
    'PP-OCRv4_mobile_det_infer', 'OCR',
    'local-onnx', 'onnx', 1, 1, 80,
    '内置本地 ONNX PP-OCRv4 文字检测识别模块（中英文）'
  UNION ALL
  SELECT '本地 ONNX 文字识别 (PP-OCRv5)', 'local-onnx-ocr-ppv5',
    'PP-OCRv5_mobile_det_infer', 'OCR',
    'local-onnx', 'onnx', 1, 1, 81,
    '内置本地 ONNX PP-OCRv5 文字检测识别模块（中英文）'
  UNION ALL
  SELECT '本地 ONNX 通用文字识别 (LightweightEdge)', 'local-onnx-ocr-lightweight',
    'cv_LightweightEdge_ocr-recognitoin-general_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 82,
    '内置本地 ONNX LightweightEdge 通用文字识别模块'
  UNION ALL
  SELECT '本地 ONNX 手写文字识别 (ConvNext)', 'local-onnx-ocr-handwritten',
    'cv_convnextTiny_ocr-recognition-handwritten_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 83,
    '内置本地 ONNX ConvNext 手写文字识别模块'
  UNION ALL
  SELECT '本地 ONNX 车牌识别 (ConvNext)', 'local-onnx-ocr-licenseplate',
    'cv_convnextTiny_ocr-recognition-licenseplate_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 84,
    '内置本地 ONNX ConvNext 车牌识别模块'
  UNION ALL
  -- ── 图像分类（动漫检测） ────────────────────────────
  SELECT '本地 ONNX 动漫/真人二分类', 'local-onnx-cls-anime-real',
    'anime_real_cls', 'CLASSIFICATION',
    'local-onnx', 'onnx', 1, 1, 90,
    '内置本地 ONNX 动漫/真人二分类模型'
  UNION ALL
  SELECT '本地 ONNX 动漫图片分类 (CL Tagger)', 'local-onnx-cls-anime-image',
    'anime_image_classifier', 'CLASSIFICATION',
    'local-onnx', 'onnx', 1, 1, 91,
    '内置本地 ONNX CLIP + CL-Tagger 轻量动漫图片二分类器'
  UNION ALL
  -- ── 行人重识别 ReID ─────────────────────────────────
  SELECT '本地 ONNX 行人重识别 (YouTu ReID 768D)', 'local-onnx-person-reid-youtu',
    NULL, 'REID',
    'local-onnx', 'onnx', 1, 1, 100,
    '基于 YouTu ReID 的本地 ONNX 行人重识别模型（768d）'
  UNION ALL
  SELECT '本地 ONNX 行人重识别 (OSNet-AIN 512D)', 'local-onnx-person-reid-osnet',
    NULL, 'REID',
    'local-onnx', 'onnx', 1, 1, 101,
    '基于 OSNet-AIN 的本地 ONNX 行人重识别模型（512d）'
  UNION ALL
  -- ── SafeTensors：Qwen2.5 系列 ────────────────────────
  SELECT '本地 SafeTensor Qwen2.5-0.5B', 'local-safetensor-qwen2.5-0.5b',
    'qwen2.5-0.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 201,
    'Qwen2.5 0.5B 超轻量文本生成模型，适合低资源环境'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-1.5B', 'local-safetensor-qwen2.5-1.5b',
    'qwen2.5-1.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 202,
    'Qwen2.5 1.5B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-3B', 'local-safetensor-qwen2.5-3b',
    'qwen2.5-3b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 203,
    'Qwen2.5 3B 标准文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-7B', 'local-safetensor-qwen2.5-7b',
    'qwen2.5-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 204,
    'Qwen2.5 7B 高性能文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-14B', 'local-safetensor-qwen2.5-14b',
    'qwen2.5-14b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 205,
    'Qwen2.5 14B 大容量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-Coder-7B', 'local-safetensor-qwen2.5-coder-7b',
    'qwen2.5-coder-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 206,
    'Qwen2.5 Coder 7B 代码生成专用模型'
  UNION ALL
  -- ── SafeTensors：Qwen2 系列 ──────────────────────────
  SELECT '本地 SafeTensor Qwen2-0.5B', 'local-safetensor-qwen2-0.5b',
    'qwen2-0.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 207,
    'Qwen2 0.5B 超轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2-1.5B', 'local-safetensor-qwen2-1.5b',
    'qwen2-1.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 208,
    'Qwen2 1.5B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2-7B', 'local-safetensor-qwen2-7b',
    'qwen2-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 209,
    'Qwen2 7B 标准文本生成模型'
  UNION ALL
  -- ── SafeTensors：Llama3.2 系列 ───────────────────────
  SELECT '本地 SafeTensor Llama3.2-1B', 'local-safetensor-llama3.2-1b',
    'llama3.2-1b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 211,
    'Meta Llama 3.2 1B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Llama3.2-3B', 'local-safetensor-llama3.2-3b',
    'llama3.2-3b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 212,
    'Meta Llama 3.2 3B 高效文本生成模型'
  UNION ALL
  -- ── SafeTensors：MiniMind2 系列 ──────────────────────
  SELECT '本地 SafeTensor MiniMind2-104M', 'local-safetensor-minimind2-104m',
    'minimind2-104m', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 213,
    'MiniMind2 104M 极致轻量文本生成模型，适合教学/实验场景'
  UNION ALL
  SELECT '本地 SafeTensor MiniMind2-Small', 'local-safetensor-minimind2-small',
    'minimind2-small', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 214,
    'MiniMind2 Small 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor MiniMind-3o (Omni)', 'local-safetensor-minimind-3o',
    'minimind-3o', 'ASR',
    'safetensors', 'safetensor', 1, 1, 215,
    'MiniMind-3o Omni 多模态模型，支持 ASR 语音转文字 / TTS / 图文理解'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);
INSERT INTO `sys_ai_global_setting` (
  `sys_ai_global_setting_group`,
  `sys_ai_global_setting_field`,
  `sys_ai_global_setting_value`,
  `sys_ai_global_setting_value_type`,
  `sys_ai_global_setting_description`,
  `sys_ai_global_setting_sort`,
  `sys_ai_global_setting_status`,
  `create_name`,
  `create_by`,
  `update_name`,
  `update_by`
)
SELECT * FROM (
  -- runtime 组
  SELECT 'runtime' AS grp, 'enabled' AS fld, 'true' AS val, 'BOOLEAN' AS vtype,
    '是否启用 AI ChatClient' AS descr, 5 AS sort, 1 AS st, 'system' AS cn, 1 AS cb, 'system' AS un, 1 AS ub
  UNION ALL
  SELECT 'runtime', 'factory', 'default', 'STRING', 'ChatClient SPI 工厂名称', 8, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider', 'zhipu', 'STRING', '默认 LLM 提供商', 9, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'default_model', 'GLM-4V-Flash', 'STRING', '默认聊天模型编码', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'system_prompt', '', 'STRING', '默认系统提示词', 15, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'temperature', '0.7', 'NUMBER', '默认采样温度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'max_tokens', '4096', 'NUMBER', '默认最大输出 token', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'timeout_millis', '30000', 'NUMBER', '默认超时时间(毫秒)', 35, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'app_key_enabled', 'true', 'BOOLEAN', '是否启用 AI AppKey 校验', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'input_optimization_enabled', 'false', 'BOOLEAN', '是否启用输入优化', 45, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_enabled', 'false', 'BOOLEAN', '是否启用上下文压缩', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_threshold', '12', 'NUMBER', '触发上下文压缩的历史阈值', 55, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_retain_messages', '6', 'NUMBER', '压缩后保留的消息数量', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider_base_url', 'https://open.bigmodel.cn/api/paas/v4/', 'STRING', '智谱 AI 基础地址（必须以 / 结尾）', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider_api_key', 'REPLACE_WITH_ZHIPU_KEY', 'STRING', '智谱 API Key', 71, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'charge_enabled', 'false', 'BOOLEAN', '每次请求扣减额度', 41, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'stop_when_balance_lt_0', 'true', 'BOOLEAN', '余额不足时停止服务', 42, 1, 'system', 1, 'system', 1
  -- face_recognition 组
  UNION ALL
  SELECT 'face_recognition', 'default_face_detect_model', 'local-onnx-face-detect-slim', 'STRING', '默认人脸检测模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'default_face_feature_model', 'local-onnx-face-feature-arcface', 'STRING', '默认特征提取模型', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'default_face_enhance_model', 'local-onnx-face-enhance', 'STRING', '默认人脸增强模型', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'feature_dimension', '512', 'INT', '特征向量维度', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'min_similarity', '0.5', 'FLOAT', '最低相似度阈值', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'vector_storage_engine', 'jvector', 'STRING', '向量存储引擎(memory/jvector/milvus/mysql)', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'animation_face_filter_enabled', 'true', 'BOOLEAN', '动漫人脸过滤(开启后可防止动漫/插画图片通过人脸识别)', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'anime_face_detect_model', '', 'STRING', '动漫人脸检测模型(留空则自动查找)', 80, 1, 'system', 1, 'system', 1
  -- image_search 组
  UNION ALL
  SELECT 'image_search', 'default_image_feature_model', 'CLIP-ViT-B-32-IMAGE', 'STRING', '默认图像特征提取模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'feature_dimension', '512', 'INT', '特征向量维度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'min_similarity', '0.3', 'FLOAT', '最低相似度阈值', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'vector_storage_engine', 'jvector', 'STRING', '向量存储引擎(memory/jvector/milvus/mysql)', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'max_image_size', '1024', 'INT', '图像最大边长(超此值压缩)', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'face_enhance_enabled', 'false', 'BOOLEAN', '是否启用图像增强', 60, 1, 'system', 1, 'system', 1
  -- ocr 组
  UNION ALL
  SELECT 'ocr', 'default_ocr_model', 'local-onnx-ocr-ppv5', 'STRING', '默认OCR模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_language', 'ch', 'STRING', '识别语言(ch/en/jp)', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_confidence_threshold', '0.5', 'FLOAT', '置信度阈值', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_max_image_size', '2048', 'INT', '图像最大边长', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_contrast', 'true', 'BOOLEAN', '对比度增强', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_brightness', '0', 'INT', '亮度调整(-100~100)', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_denoise', 'true', 'BOOLEAN', '降噪处理', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_binarize', 'false', 'BOOLEAN', '二值化处理', 80, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_deskew', 'true', 'BOOLEAN', '倾斜校正', 90, 1, 'system', 1, 'system', 1
  -- knowledge_graph 组
  UNION ALL
  SELECT 'knowledge_graph', 'enabled', 'true', 'BOOLEAN', '是否启用知识图谱功能', 5, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'extraction_prompt_template', '', 'STRING', '实体抽取提示词模板(留空使用默认)', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'relation_extraction_enabled', 'true', 'BOOLEAN', '是否启用关系抽取', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'max_entities_per_doc', '50', 'INT', '单文档最大抽取实体数', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'embedding_dimension', '512', 'INT', '实体嵌入向量维度', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'vector_storage_engine', 'jvector', 'STRING', '实体嵌入向量存储引擎', 70, 1, 'system', 1, 'system', 1
  -- person_reid 组
  UNION ALL
  SELECT 'person_reid', 'default_reid_model', 'local-onnx-person-reid-youtu', 'STRING',
    '默认 ReID 模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_feature_dimension', '768', 'INT',
    'ReID 特征向量维度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_min_similarity', '0.5', 'FLOAT',
    'ReID 最低相似度阈值', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_vector_storage_engine', 'jvector', 'STRING',
    'ReID 向量存储引擎(memory/jvector/milvus/mysql)', 40, 1, 'system', 1, 'system', 1
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_global_setting`
  WHERE `sys_ai_global_setting_group` = seed.grp
    AND `sys_ai_global_setting_field` = seed.fld
);
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('通用助手', 'default', '通用 AI 助手，无特殊人设', '你是一个智能 AI 助手，请用中文友好地回答用户的问题。', 0, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('Java 开发助理', 'java-dev', '专业的 Java 后端开发工程师', '你是一个资深的 Java 后端开发工程师，精通 Spring Boot、MyBatis、微服务架构。请用中文回答技术问题，提供代码示例时遵循最佳实践。', 1, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('UI 设计师', 'ui-designer', '前端 UI/UX 设计师', '你是一个优秀的前端 UI/UX 设计师，精通 Vue 3、Element Plus、Tailwind CSS。设计风格简洁现代，参考 Vercel/Linear 设计语言。', 2, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);
INSERT IGNORE INTO `sys_ai_group_model` (
    `sys_ai_group_model_id`, `sys_ai_group_id`, `sys_ai_module_id`, `create_time`
) VALUES (
    1, 1, 1, CURRENT_TIMESTAMP
);
INSERT IGNORE INTO `sys_ai_pricing` (
    `sys_ai_pricing_provider`, `sys_ai_pricing_model`, `sys_ai_pricing_input_price_per_1k`, `sys_ai_pricing_output_price_per_1k`,
    `sys_ai_pricing_currency`, `sys_ai_pricing_effective_date`, `sys_ai_pricing_source`
) VALUES
    ('mock', 'mock-gpt-4o', 0.000000, 0.000000, 'CNY', CURRENT_TIMESTAMP, 'manual'),
    ('openai', 'gpt-4o', 0.015000, 0.060000, 'USD', CURRENT_TIMESTAMP, 'manual'),
    ('openai', 'gpt-4o-mini', 0.001500, 0.006000, 'USD', CURRENT_TIMESTAMP, 'manual'),
    ('deepseek', 'deepseek-chat', 0.002000, 0.008000, 'CNY', CURRENT_TIMESTAMP, 'manual'),
    ('zhipu', 'glm-4-flash', 0.000000, 0.000000, 'CNY', CURRENT_TIMESTAMP, 'manual');
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('display_mode', 'auto', 0);
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('resource_policy', '{"preset":"automatic"}', 0);
INSERT INTO sys_agent_browser_setting (setting_key, setting_value, updated_at) VALUES 
    ('login_status', '{"open":false}', 0);
INSERT INTO sys_agent_browser_host (host_id, state, epoch, headful, identity_mode, lane_count, rss_bytes, created_at, updated_at)
VALUES ('default-host', 'running', 1, FALSE, 'anonymous', 0, 0, 0, 0);
CREATE TABLE IF NOT EXISTS `sys_ai_requirement` (
    `sys_ai_requirement_id` VARCHAR(36) NOT NULL COMMENT '需求 ID（UUIDv7）',
    `sys_ai_requirement_display_no` INT NOT NULL COMMENT '展示序号（自增）',
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
    PRIMARY KEY (`sys_ai_requirement_id`),
    UNIQUE KEY `sys_ai_requirement_display_no` (`sys_ai_requirement_display_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求条目';
CREATE TABLE IF NOT EXISTS `sys_ai_data_source` (
    `sys_ai_data_source_id` VARCHAR(36) NOT NULL COMMENT '数据源 ID（UUIDv7）',
    `sys_ai_data_source_name` VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `sys_ai_data_source_type` VARCHAR(64) NOT NULL COMMENT '类型: lark/jira/notion/generic',
    `sys_ai_data_source_config` TEXT NULL COMMENT '配置 JSON（app_id, app_secret, table_id, api_url 等）',
    `sys_ai_data_source_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_data_source_sync_cron` VARCHAR(64) NULL COMMENT '同步 cron 表达式',
    `sys_ai_data_source_last_sync_at` BIGINT NULL COMMENT '上次同步时间戳',
    `sys_ai_data_source_last_sync_status` VARCHAR(32) NULL COMMENT '上次同步状态: success/failed',
    `sys_ai_data_source_last_sync_error` VARCHAR(512) NULL COMMENT '上次同步错误信息',
    `create_name` VARCHAR(64) NULL,
    `create_by` INT NULL,
    `create_time` BIGINT NULL COMMENT '创建时间戳（epoch ms）',
    `update_name` VARCHAR(64) NULL,
    `update_by` INT NULL,
    `update_time` BIGINT NULL COMMENT '更新时间戳（epoch ms）',
    PRIMARY KEY (`sys_ai_data_source_id`),
    KEY `sys_ai_data_source_sys_ai_ds_type` (`sys_ai_data_source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求数据源提供方配置';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_inference_strength` VARCHAR(32) NULL COMMENT '推理强度';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_endpoint` VARCHAR(255) NULL COMMENT '端点URL';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_ip` VARCHAR(64) NULL COMMENT '请求IP';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_type` VARCHAR(32) NULL COMMENT '请求类型: chat/completion/embedding';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_billing_mode` VARCHAR(32) NULL COMMENT '计费模式: pay_as_you_go/subscription';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_cached_tokens` INT NULL COMMENT '缓存读取 Token';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_cached_cost` DOUBLE NULL COMMENT '缓存读取成本';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_input_price_per_1m` DOUBLE NULL COMMENT '输入单价(每1M Token)';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_output_price_per_1m` DOUBLE NULL COMMENT '输出单价(每1M Token)';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_cache_read_price_per_1m` DOUBLE NULL COMMENT '缓存读取单价(每1M Token)';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_service_tier` VARCHAR(32) NULL COMMENT '服务档位';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_ratio` DOUBLE NULL COMMENT '倍率';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_original_cost` DOUBLE NULL COMMENT '原始费用(未折扣)';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_first_token_latency` BIGINT NULL COMMENT '首字延迟(ms)';
ALTER TABLE `sys_ai_usage` ADD COLUMN IF NOT EXISTS `sys_ai_usage_total_latency` BIGINT NULL COMMENT '总耗时(ms)';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN IF NOT EXISTS `sys_ai_usage_daily_total_cached_tokens` INT DEFAULT 0 COMMENT '缓存读取 Token 合计';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN IF NOT EXISTS `sys_ai_usage_daily_total_latency` BIGINT DEFAULT 0 COMMENT '总耗时(ms)合计';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN IF NOT EXISTS `sys_ai_usage_daily_standard_cost` DOUBLE DEFAULT 0 COMMENT '标准费用(官方价格)';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN IF NOT EXISTS `sys_ai_usage_daily_total_first_token_latency` BIGINT DEFAULT 0 COMMENT '总首字延迟(ms)合计';
-- ---------------------------------------------------------------------
-- 增量并入（原 V1.2/V1.3/V1.4，SET @/PREPARE 动态段转通用幂等语法）
-- ---------------------------------------------------------------------
ALTER TABLE `sys_ai_model` ADD COLUMN IF NOT EXISTS `sys_ai_model_required_pipelines` VARCHAR(512) DEFAULT NULL COMMENT '必填管线(JSON)';
ALTER TABLE `sys_ai_model` ADD COLUMN IF NOT EXISTS `sys_ai_model_optional_pipelines` VARCHAR(512) DEFAULT NULL COMMENT '非必填管线(JSON)';
ALTER TABLE `sys_ai_model` ADD COLUMN IF NOT EXISTS `sys_ai_model_aliases` VARCHAR(512) DEFAULT NULL COMMENT '模型别名(JSON数组)';
ALTER TABLE `sys_ai_model` ADD COLUMN IF NOT EXISTS `sys_ai_model_last_latency` BIGINT DEFAULT NULL COMMENT '最近一次连通性测试延迟(毫秒)';
ALTER TABLE `sys_ai_model` ADD COLUMN IF NOT EXISTS `sys_ai_model_last_test_time` DATETIME DEFAULT NULL COMMENT '最近一次连通性测试时间';

CREATE TABLE IF NOT EXISTS `sys_ai_usage_sync_log` (
    `sys_ai_usage_sync_log_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sys_ai_usage_sync_log_level` VARCHAR(16)  NULL DEFAULT 'INFO' COMMENT '日志级别 INFO/WARN/ERROR',
    `sys_ai_usage_sync_log_source` VARCHAR(64) NULL DEFAULT 'schedule' COMMENT '来源（schedule/manual/parser）',
    `sys_ai_usage_sync_log_message` TEXT       NULL COMMENT '日志内容',
    `create_time`                  DATETIME    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sys_ai_usage_sync_log_id`),
    KEY `sys_ai_usage_sync_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量同步日志';
