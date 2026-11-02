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
    `sys_ai_provider_type` VARCHAR(64) NULL DEFAULT 'custom' COMMENT '供应商类型（openai/anthropic/gemini/qwen/custom）',
    `sys_ai_provider_api_key` VARCHAR(512) NULL COMMENT '提供商 API Key',
    `sys_ai_provider_logo` VARCHAR(1024) NULL COMMENT 'Logo 图片地址',
    `sys_ai_provider_models` TEXT NULL COMMENT '可用模型列表（JSON 字符串）',
    `sys_ai_provider_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    `create_name` VARCHAR(64) NULL COMMENT '创建人姓名',
    `create_by` INT NULL COMMENT '创建人',
    `create_time` DATETIME NULL COMMENT '创建时间',
    `update_name` VARCHAR(64) NULL COMMENT '更新人姓名',
    `update_by` INT NULL COMMENT '更新人',
    `update_time` DATETIME NULL COMMENT '更新时间',
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
    `sys_ai_provider_model_context_window` INT          NULL COMMENT '上下文窗口',
    `sys_ai_provider_model_max_tokens`     INT          NULL COMMENT '最大输出令牌',
    `sys_ai_provider_model_temperature`    DOUBLE       NULL COMMENT '温度',
    `sys_ai_provider_model_top_p`          DOUBLE       NULL COMMENT 'Top-P 采样',
    `sys_ai_provider_model_frequency_penalty` DOUBLE    NULL COMMENT '频率惩罚',
    `sys_ai_provider_model_create_time`    BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_provider_model_update_time`    BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
  `sys_ai_provider_model_alias` VARCHAR(128) NULL COMMENT '模型别名',

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
    `sys_ai_conversation_user_id`            VARCHAR(64)  NULL COMMENT '归属用户 ID（登录用户名）',
    PRIMARY KEY (`sys_ai_conversation_id`),
    KEY `sys_ai_conversation_ai_conv_user` (`sys_ai_conversation_user_id`, `sys_ai_conversation_modified_at`)
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
    `sys_ai_conversation_message_user_id`        VARCHAR(64)  NULL COMMENT '归属用户 ID（登录用户名，随所属会话）',
    PRIMARY KEY (`sys_ai_conversation_message_id`),
    KEY `sys_ai_conversation_message_ai_conv_msg_conv` (`sys_ai_conversation_message_conversation_id`),
    KEY `sys_ai_conversation_message_ai_conv_msg_user` (`sys_ai_conversation_message_user_id`, `sys_ai_conversation_message_conversation_id`)
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
    `sys_ai_bot_binding_model`           VARCHAR(128) NULL COMMENT '绑定的 AI 模型（用于微信聊天时 AI 回复）',
    `sys_ai_bot_binding_thinking`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用深度思考（默认关闭）',
    `sys_ai_bot_binding_thinking_effort` VARCHAR(16)  NULL COMMENT '深度思考强度（low / medium / high）',
    `sys_ai_bot_binding_system_prompt`   TEXT         NULL COMMENT '系统提示词（AI 回复时注入的 system prompt）',
    `sys_ai_bot_binding_mention_only`    TINYINT      NOT NULL DEFAULT 0 COMMENT '群聊仅回复 @ 了 Bot 的消息（默认关闭，私聊不受影响）',
    `sys_ai_bot_binding_random_reply_enabled` TINYINT  NOT NULL DEFAULT 0 COMMENT '是否启用随机回复（默认关闭）',
    `sys_ai_bot_binding_random_context_count` INT      NULL DEFAULT 10 COMMENT '随机回复携带的上下文消息条数',
    `sys_ai_bot_binding_random_probability`   DOUBLE   NULL DEFAULT 0 COMMENT '随机回复命中概率百分比（0-100，0 表示从不）',
    `sys_ai_bot_binding_mcp_server_ids`   VARCHAR(500) NULL DEFAULT NULL COMMENT '勾选的 MCP 服务器ID（逗号分隔，仅管理员可配）',
    `sys_ai_bot_binding_last_active_time` DATETIME     NULL COMMENT '最后活跃时间',
    `create_name`                         VARCHAR(64)  NULL COMMENT '创建人姓名',
    `create_by`                           INT          NULL COMMENT '创建人',
    `create_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_name`                         VARCHAR(64)  NULL COMMENT '更新人姓名',
    `update_by`                           INT          NULL COMMENT '更新人',
    `update_time`                         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `sys_ai_bot_binding_record_usage` TINYINT NOT NULL DEFAULT 1 COMMENT '是否记录AI用量（令牌/费用）',
    PRIMARY KEY (`sys_ai_bot_binding_id`),
    KEY `sys_ai_bot_binding_sys_ai_bot_binding_bot_id` (`sys_ai_bot_binding_bot_id`),
    KEY `sys_ai_bot_binding_sys_ai_bot_binding_user` (`sys_ai_bot_binding_user_id`)
);

CREATE TABLE IF NOT EXISTS `sys_ai_bot_message` (
    `sys_ai_bot_message_id`       BIGINT    NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    `sys_ai_bot_message_bot_id`   VARCHAR(128) NULL COMMENT 'Bot 唯一标识（关联绑定表）',
    `sys_ai_bot_message_direction` VARCHAR(16) NULL COMMENT '方向：IN（用户→bot）/ OUT（bot→用户）',
    `sys_ai_bot_message_from_user` VARCHAR(128) NULL COMMENT '微信用户 ID（来源）',
    `sys_ai_bot_message_content`  TEXT      NULL COMMENT '消息内容',
    `sys_ai_bot_message_context_token` VARCHAR(512) NULL COMMENT '会话上下文令牌（入站消息携带，回复/主动发送时原样回传）',
    `sys_ai_bot_message_user_id`  VARCHAR(64) NULL COMMENT '归属登录用户标识（与绑定表 owner_key 的 u:<userId> 同源）',
    `sys_ai_bot_message_create_time` DATETIME NULL COMMENT '创建时间',
  `sys_ai_bot_message_chat_id` VARCHAR(128) NULL COMMENT '会话标识',
  `sys_ai_bot_message_from_group` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否群聊',
  `sys_ai_bot_message_dedup_key` VARCHAR(190) NULL COMMENT '消息幂等去重键',
  UNIQUE KEY `uk_bot_message_dedup` (`sys_ai_bot_message_dedup_key`),

    PRIMARY KEY (`sys_ai_bot_message_id`),
    KEY `sys_ai_bot_message_sys_ai_bot_message_bot` (`sys_ai_bot_message_bot_id`),
    KEY `sys_ai_bot_message_sys_ai_bot_message_conv` (`sys_ai_bot_message_bot_id`, `sys_ai_bot_message_from_user`),
    KEY `sys_ai_bot_message_sys_ai_bot_message_user` (`sys_ai_bot_message_user_id`, `sys_ai_bot_message_bot_id`)
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
    PRIMARY KEY (`sys_ai_pricing_id`),
    -- 多数据源对同一 (厂商, 模型) 会各给一条报价，厂商已统一小写；唯一键兜底防止重复入库
    UNIQUE KEY `uk_sys_ai_pricing_provider_model` (`sys_ai_pricing_provider`, `sys_ai_pricing_model`)
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
    `sys_ai_usage_inference_strength` VARCHAR(32) NULL COMMENT '推理强度',
    `sys_ai_usage_endpoint` VARCHAR(255) NULL COMMENT '端点URL',
    `sys_ai_usage_ip` VARCHAR(64) NULL COMMENT '请求IP',
    `sys_ai_usage_location` VARCHAR(128) NULL COMMENT 'IP物理地址（省/市，来自IP定位）',
    `sys_ai_usage_type` VARCHAR(32) NULL COMMENT '请求类型: chat/completion/embedding',
    `sys_ai_usage_billing_mode` VARCHAR(32) NULL COMMENT '计费模式: pay_as_you_go/subscription',
    `sys_ai_usage_cached_tokens` INT NULL COMMENT '缓存读取 Token',
    `sys_ai_usage_cached_cost` DOUBLE NULL COMMENT '缓存读取成本',
    `sys_ai_usage_input_price_per_1m` DOUBLE NULL COMMENT '输入单价(每1M Token)',
    `sys_ai_usage_output_price_per_1m` DOUBLE NULL COMMENT '输出单价(每1M Token)',
    `sys_ai_usage_cache_read_price_per_1m` DOUBLE NULL COMMENT '缓存读取单价(每1M Token)',
    `sys_ai_usage_service_tier` VARCHAR(32) NULL COMMENT '服务档位',
    `sys_ai_usage_ratio` DOUBLE NULL COMMENT '倍率',
    `sys_ai_usage_original_cost` DOUBLE NULL COMMENT '原始费用(未折扣)',
    `sys_ai_usage_first_token_latency` BIGINT NULL COMMENT '首字延迟(ms)',
    `sys_ai_usage_total_latency` BIGINT NULL COMMENT '总耗时(ms)',
  `sys_ai_usage_source` VARCHAR(32) NULL DEFAULT 'third_party' COMMENT '用量来源',
  `sys_ai_usage_estimated_tokens` TINYINT NULL DEFAULT 0 COMMENT 'token是否估算',

    PRIMARY KEY (`sys_ai_usage_id`),
    KEY `sys_ai_usage_sys_ai_usage_request_id` (`sys_ai_usage_request_id`),
    KEY `sys_ai_usage_sys_ai_usage_call_time` (`sys_ai_usage_call_time`),
    KEY `sys_ai_usage_sys_ai_usage_user` (`sys_ai_usage_user_id`, `sys_ai_usage_call_time`)
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
    `sys_ai_usage_daily_total_cached_tokens` INT DEFAULT 0 COMMENT '缓存读取 Token 合计',
    `sys_ai_usage_daily_total_latency` BIGINT DEFAULT 0 COMMENT '总耗时(ms)合计',
    `sys_ai_usage_daily_standard_cost` DOUBLE DEFAULT 0 COMMENT '标准费用(官方价格)',
    `sys_ai_usage_daily_total_first_token_latency` BIGINT DEFAULT 0 COMMENT '总首字延迟(ms)合计',
    `sys_ai_usage_daily_user_id`            VARCHAR(64)  NULL COMMENT '归属用户 ID（登录用户名，NULL 表示全局汇总）',
    PRIMARY KEY (`sys_ai_usage_daily_id`),
    KEY `sys_ai_usage_daily_sys_ai_usage_daily_user` (`sys_ai_usage_daily_user_id`, `sys_ai_usage_daily_stat_date`)
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
    `user_id`                 VARCHAR(64)  NULL COMMENT '归属用户 ID（与所属 lane 同源）',
    PRIMARY KEY (`tab_id`),
    INDEX `sys_agent_browser_tab_tab_lane` (`lane_id`),
    INDEX `sys_agent_browser_tab_tab_user` (`user_id`, `lane_id`)
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
  `sys_ai_manufacturer_logo` VARCHAR(1024) DEFAULT NULL COMMENT 'Logo',
  `sys_ai_manufacturer_openai_compatible` INT NOT NULL DEFAULT 0 COMMENT 'OpenAI',
  `sys_ai_manufacturer_claude_compatible` INT NOT NULL DEFAULT 0 COMMENT 'Claude',
  `sys_ai_manufacturer_api_secret` VARCHAR(255) DEFAULT NULL COMMENT 'API',
  `sys_ai_manufacturer_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `sys_ai_manufacturer_sort` INT NOT NULL DEFAULT 0 COMMENT '',
  `sys_ai_manufacturer_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
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
  `sys_ai_session_compression_user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户 ID（随所属会话）',
  PRIMARY KEY (`sys_ai_session_compression_id`),
  UNIQUE KEY `sys_ai_session_compression_ai_session_compression_session` (`sys_ai_session_id`),
  KEY `sys_ai_session_compression_ai_session_compression_user` (`sys_ai_session_compression_user_id`, `sys_ai_session_id`)
) COMMENT='AI ';
CREATE TABLE IF NOT EXISTS `sys_ai_session` (
  `sys_ai_session_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_session_title` VARCHAR(255) NOT NULL COMMENT '',
  `sys_ai_session_type` VARCHAR(64) NOT NULL DEFAULT 'CHAT' COMMENT '',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '',
  `model_id` VARCHAR(255) DEFAULT NULL COMMENT '会话默认模型ID',
  `character_id` BIGINT DEFAULT NULL COMMENT '绑定角色（助手）ID，对应 sys_ai_role 主键',
  `mcp_server_ids` VARCHAR(500) DEFAULT NULL COMMENT '会话级 MCP 服务器ID列表（逗号分隔，仅管理员）',
  `sys_user_id` VARCHAR(255) NOT NULL COMMENT 'ID',
  `sys_ai_session_status` INT NOT NULL DEFAULT 1 COMMENT '',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
    `is_archived` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已归档',
  `settings` TEXT NULL COMMENT '会话设置JSON',

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
  `parent_id` BIGINT DEFAULT NULL COMMENT '父消息ID（AI回复指向用户消息）',
  `model_name` VARCHAR(255) DEFAULT NULL COMMENT 'AI回复时使用的模型名称',
  `prompt_tokens` INT DEFAULT NULL COMMENT '本次回复的输入 Token 数',
  `completion_tokens` INT DEFAULT NULL COMMENT '本次回复的输出 Token 数',
  `total_tokens` INT DEFAULT NULL COMMENT '本次回复的总 Token 数',
  `sys_ai_message_confirmation` LONGTEXT DEFAULT NULL COMMENT '人工确认请求 JSON（plan_exit：replyId/status/items）',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_message_user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户 ID（随所属会话）',
  PRIMARY KEY (`sys_ai_message_id`),
  KEY `sys_ai_message_ai_message_session` (`sys_ai_session_id`),
  KEY `sys_ai_message_ai_message_user` (`sys_ai_message_user_id`, `sys_ai_session_id`)
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
  `sys_ai_template_remark` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_template_category` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_template_recommend_prompt` LONGTEXT COMMENT '',
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
-- 用户覆盖层：与 sys_ai_global_setting 同构的「分组 + 字段」键值行，多一列归属用户。
-- 读取顺序 = 用户覆盖 -> 全局默认；全局默认表不加用户列，避免与既有唯一键冲突。
CREATE TABLE IF NOT EXISTS `sys_ai_user_setting` (
  `sys_ai_user_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_user_setting_user_id` VARCHAR(64) NOT NULL COMMENT '归属用户 ID（登录用户名）',
  `sys_ai_user_setting_group` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '设置分组',
  `sys_ai_user_setting_field` VARCHAR(128) NOT NULL COMMENT '字段名',
  `sys_ai_user_setting_value` LONGTEXT COMMENT '字段值',
  `sys_ai_user_setting_value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '值类型',
  `sys_ai_user_setting_description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
  `sys_ai_user_setting_sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `sys_ai_user_setting_status` INT NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `create_by` INT DEFAULT NULL COMMENT 'ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `update_by` INT DEFAULT NULL COMMENT 'ID',
  PRIMARY KEY (`sys_ai_user_setting_id`),
  UNIQUE KEY `sys_ai_user_setting_ai_user_setting_field` (`sys_ai_user_setting_user_id`, `sys_ai_user_setting_group`, `sys_ai_user_setting_field`)
) COMMENT='AI 用户设置覆盖';
CREATE TABLE IF NOT EXISTS `sys_ai_app_key` (
  `sys_ai_app_key_id` INT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `sys_ai_app_key_app_id` VARCHAR(128) DEFAULT NULL COMMENT 'ID',
  `sys_ai_app_key_app_key` VARCHAR(128) NOT NULL COMMENT 'Key',
  `sys_ai_app_key_secret` VARCHAR(256) NOT NULL COMMENT 'Secret',
  `sys_ai_app_key_name` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_value` VARCHAR(512) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_provider_id` VARCHAR(64) DEFAULT NULL COMMENT '供应商编码（openai/agnes/onnx 等）',
  `sys_ai_app_key_model` VARCHAR(255) DEFAULT NULL COMMENT '',
  `sys_ai_app_key_max_use` BIGINT DEFAULT NULL COMMENT 'null ',
  `sys_ai_app_key_enabled` INT DEFAULT 1 COMMENT '',
  `sys_ai_app_key_expire_datetime` DATETIME DEFAULT NULL COMMENT 'null ',
  `sys_user_id` INT DEFAULT NULL COMMENT 'ID',
  `sys_ai_app_key_user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户 ID（登录用户名）',
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
  UNIQUE KEY `sys_ai_app_key_ai_app_key_value` (`sys_ai_app_key_app_key`),
  KEY `sys_ai_app_key_sys_ai_app_key_user` (`sys_ai_app_key_user_id`, `sys_ai_app_key_delete`)
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
-- ---------------------------------------------------------------------
-- 知识图谱：实体/关系 与「知识点」的关联表。
--
-- 为什么单独建表而不是给 entity/relation 加列：
--   同一个实体（如「关羽」）会出现在多个知识点里，加列会让它按知识点重复成
--   多行，图被拆成一个个孤立小团体；关联表能保证实体在库内唯一、同时保留
--   「哪些知识点提到过它」这一反向查询能力。
--
-- 为什么不在 sys_ai_knowledge_library 上加 knowledge_base_id 列：
--   本文件刻意不输出兼容 ALTER（MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，
--   留着只会在每次启动报语法错误并被 continue-on-error 静默吞掉）。
--   因此图谱库与 RAG 知识库的映射走 name 约定：'kb:<knowledge_base_id>'。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_ref` (
  `sys_ai_knowledge_graph_ref_id`                 INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sys_ai_knowledge_graph_ref_uid`                VARCHAR(64)  NOT NULL COMMENT '关联记录UID(UUID)',
  `sys_ai_knowledge_graph_library_id`             INT          NOT NULL DEFAULT 0 COMMENT '图谱库ID(对应sys_ai_knowledge_library)',
  `sys_ai_knowledge_graph_ref_target_type`        VARCHAR(32)  NOT NULL COMMENT '关联目标类型(entity 实体/relation 关系)',
  `sys_ai_knowledge_graph_ref_target_uid`         VARCHAR(64)  NOT NULL COMMENT '实体UID或关系UID',
  `sys_ai_knowledge_graph_ref_knowledge_base_id`  VARCHAR(64)  NOT NULL COMMENT 'RAG知识库ID(knowledge_base_id)',
  `sys_ai_knowledge_graph_ref_doc_path`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '知识点在知识库内的相对路径',
  `sys_ai_knowledge_graph_ref_status`             INT          NOT NULL DEFAULT 1 COMMENT '状态:1有效0失效',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人',
  `create_by`    INT          DEFAULT NULL COMMENT '创建人ID',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人',
  `update_by`    INT          DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_knowledge_graph_ref_id`),
  UNIQUE KEY `sys_ai_knowledge_graph_ref_uid` (`sys_ai_knowledge_graph_ref_uid`),
  UNIQUE KEY `sys_ai_knowledge_graph_ref_binding` (
    `sys_ai_knowledge_graph_ref_target_type`,
    `sys_ai_knowledge_graph_ref_target_uid`,
    `sys_ai_knowledge_graph_ref_knowledge_base_id`,
    `sys_ai_knowledge_graph_ref_doc_path`
  ),
  KEY `sys_ai_knowledge_graph_ref_kb` (`sys_ai_knowledge_graph_ref_knowledge_base_id`, `sys_ai_knowledge_graph_ref_doc_path`),
  KEY `sys_ai_knowledge_graph_ref_target` (`sys_ai_knowledge_graph_ref_target_type`, `sys_ai_knowledge_graph_ref_target_uid`),
  KEY `sys_ai_knowledge_graph_ref_library` (`sys_ai_knowledge_graph_library_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识图谱-实体/关系与知识点的关联';
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
    `sys_ai_model_user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属账号（按账号隔离；NULL=无主历史行）',
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
    `sys_ai_model_required_pipelines` VARCHAR(512) DEFAULT NULL COMMENT '必填管线(JSON)',
    `sys_ai_model_optional_pipelines` VARCHAR(512) DEFAULT NULL COMMENT '非必填管线(JSON)',
    `sys_ai_model_aliases` VARCHAR(512) DEFAULT NULL COMMENT '模型别名(JSON数组)',
    `sys_ai_model_last_latency` BIGINT DEFAULT NULL COMMENT '最近一次连通性测试延迟(毫秒)',
    `sys_ai_model_last_test_time` DATETIME DEFAULT NULL COMMENT '最近一次连通性测试时间',
    KEY `sys_ai_model_user_id` (`sys_ai_model_user_id`),
    UNIQUE KEY `sys_ai_model_owner_code` (`sys_ai_model_user_id`, `sys_ai_model_code`)
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
    `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户（多用户隔离）',
    `sys_ai_role_name` VARCHAR(128) NOT NULL COMMENT '',
    `sys_ai_role_code` VARCHAR(64) NOT NULL COMMENT '',
    `sys_ai_role_description` VARCHAR(512) DEFAULT NULL COMMENT '',
    `sys_ai_role_system_prompt` TEXT NOT NULL COMMENT '',
    `sys_ai_role_model` VARCHAR(128) DEFAULT NULL COMMENT '',
    `sys_ai_role_avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像访问路径',
    `sys_ai_role_temperature` DOUBLE DEFAULT NULL COMMENT '',
    `sys_ai_role_settings` LONGTEXT DEFAULT NULL COMMENT '角色完整工具与能力配置JSON',
    `sys_ai_role_sort` INT DEFAULT 0 COMMENT '',
    `sys_ai_role_status` INT DEFAULT 1 COMMENT '10',
    `sys_ai_role_delete` INT DEFAULT 0 COMMENT '01',
    `create_name` VARCHAR(255) DEFAULT NULL COMMENT '',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '',
    UNIQUE KEY `uk_role_user_code` (`user_id`, `sys_ai_role_code`),
    KEY `idx_role_user` (`user_id`)
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
    `sys_ai_ssh_host_user_id`          VARCHAR(64)  NULL COMMENT '归属用户 ID（登录用户名，凭证仅归属者可见）',
    PRIMARY KEY (`sys_ai_ssh_host_id`),
    KEY `sys_ai_ssh_host_sys_ai_ssh_host_user` (`sys_ai_ssh_host_user_id`, `sys_ai_ssh_host_created_at`)
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
  `sys_ai_mcp_server_icon` VARCHAR(512) NULL COMMENT '图标地址',

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
    `sys_ai_requirement_user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户 ID（登录用户名）',
    `create_name` VARCHAR(100) DEFAULT NULL,
    `create_by` INT DEFAULT NULL,
    `create_time` BIGINT DEFAULT NULL,
    `update_name` VARCHAR(100) DEFAULT NULL,
    `update_by` INT DEFAULT NULL,
    `update_time` BIGINT DEFAULT NULL,
    PRIMARY KEY (`sys_ai_requirement_id`),
    KEY `sys_ai_requirement_sys_ai_requirement_user` (`sys_ai_requirement_user_id`, `sys_ai_requirement_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求条目';
CREATE TABLE IF NOT EXISTS `sys_ai_data_source` (
    `sys_ai_data_source_id`               VARCHAR(36)  NOT NULL COMMENT '数据源 ID（UUIDv7）',
    `sys_ai_data_source_name`             VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `sys_ai_data_source_type`             VARCHAR(64)  NOT NULL COMMENT '类型: lark / jira / notion / generic',
    `sys_ai_data_source_config`           TEXT         NULL COMMENT '配置 JSON',
    `sys_ai_data_source_enabled`          TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_data_source_sync_cron`        VARCHAR(64)  NULL COMMENT '同步 cron 表达式',
    `sys_ai_data_source_last_sync_at`     BIGINT       NULL COMMENT '上次同步时间戳',
    `sys_ai_data_source_last_sync_status` VARCHAR(32)  NULL COMMENT '上次同步状态',
    `sys_ai_data_source_last_sync_error`  VARCHAR(512) NULL COMMENT '上次同步错误',
    `create_name`                         VARCHAR(64)  NULL,
    `create_by`                           INT          NULL,
    `create_time`                         BIGINT       NULL,
    `update_name`                         VARCHAR(64)  NULL,
    `update_by`                           INT          NULL,
    `update_time`                         BIGINT       NULL,
    `sys_ai_data_source_user_id`          VARCHAR(64)  NULL COMMENT '归属用户 ID（登录用户名，凭证仅归属者可见）',
    PRIMARY KEY (`sys_ai_data_source_id`),
    KEY `sys_ai_data_source_sys_ai_ds_type` (`sys_ai_data_source_type`),
    KEY `sys_ai_data_source_sys_ai_ds_user` (`sys_ai_data_source_user_id`, `sys_ai_data_source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求数据源提供方配置';


CREATE TABLE IF NOT EXISTS `sys_ai_usage_sync_log` (
    `sys_ai_usage_sync_log_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sys_ai_usage_sync_log_level` VARCHAR(16)  NULL DEFAULT 'INFO' COMMENT '日志级别 INFO/WARN/ERROR',
    `sys_ai_usage_sync_log_source` VARCHAR(64) NULL DEFAULT 'schedule' COMMENT '来源（schedule/manual/parser）',
    `sys_ai_usage_sync_log_message` TEXT       NULL COMMENT '日志内容',
    `create_time`                  DATETIME    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sys_ai_usage_sync_log_id`),
    KEY `sys_ai_usage_sync_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量同步日志';

-- ---------------------------------------------------------------------
-- 增量并入：实体已存在但库中缺表的四张表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_role_group` (
    `sys_ai_role_group_id`     INT          NOT NULL AUTO_INCREMENT COMMENT '分组ID',
    `sys_ai_role_group_name`   VARCHAR(128) NOT NULL                COMMENT '分组名称',
    `sys_ai_role_group_sort`   INT          NOT NULL DEFAULT 0      COMMENT '排序序号',
    `sys_ai_role_group_status` INT          NOT NULL DEFAULT 1      COMMENT '状态 0禁用 1启用',
    `sys_ai_role_group_delete` INT          NOT NULL DEFAULT 0      COMMENT '逻辑删除 0未删除 1已删除',
    `create_name`              VARCHAR(64)  NULL                    COMMENT '创建人姓名',
    `create_time`              DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`              DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_ai_role_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 角色分组';

CREATE TABLE IF NOT EXISTS `sys_ai_session_group` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分组ID',
    `name`        VARCHAR(128) NOT NULL                COMMENT '分组名称',
    `sort_order`  INT          NOT NULL DEFAULT 0      COMMENT '排序序号',
    `user_id`     VARCHAR(64)  NULL                    COMMENT '归属用户 ID（登录用户名）',
    `create_time` DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `sys_ai_session_group_user` (`user_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话分组';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_shared_config` (
    `sys_ai_companion_shared_config_key`      VARCHAR(64) NOT NULL COMMENT '配置键',
    `sys_ai_companion_shared_config_value`    TEXT        NULL     COMMENT '配置值（JSON）',
    `sys_ai_companion_shared_config_updated_at` BIGINT    NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_companion_shared_config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 伴生共享配置';

CREATE TABLE IF NOT EXISTS `workshop_asset` (
    `asset_id`           VARCHAR(64)   NOT NULL COMMENT '资产ID（UUIDv7）',
    `asset_kind`         VARCHAR(32)   NULL     COMMENT '资产类型',
    `asset_title`        VARCHAR(512)  NULL     COMMENT '标题',
    `asset_collection`   VARCHAR(255)  NULL     COMMENT '所属集合',
    `asset_tags`         TEXT          NULL     COMMENT '标签JSON数组',
    `asset_mime`         VARCHAR(128)  NULL     COMMENT 'MIME类型',
    `asset_width`        INT           NULL     COMMENT '宽度',
    `asset_height`       INT           NULL     COMMENT '高度',
    `asset_bytes`        BIGINT        NULL     COMMENT '字节数',
    `asset_in_library`   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否入库',
    `asset_text_content` LONGTEXT      NULL     COMMENT '文本内容',
    `asset_origin`       VARCHAR(512)  NULL     COMMENT '来源',
    `asset_file_path`    VARCHAR(1024) NULL     COMMENT '文件路径',
    `asset_thumb_path`   VARCHAR(1024) NULL     COMMENT '缩略图路径',
    `asset_created_at`   BIGINT        NOT NULL COMMENT '创建时间（毫秒）',
    `asset_updated_at`   BIGINT        NOT NULL COMMENT '更新时间（毫秒）',
    `asset_user_id`      VARCHAR(64)   NULL     COMMENT '归属用户 ID（登录用户名）',
    PRIMARY KEY (`asset_id`),
    KEY `workshop_asset_collection` (`asset_collection`),
    KEY `workshop_asset_kind` (`asset_kind`),
    KEY `workshop_asset_in_library` (`asset_in_library`),
    KEY `workshop_asset_user` (`asset_user_id`, `asset_created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工坊资产';


-- =====================================================================
-- 数字伙伴（companion）相关表：实体 SysAiCompanion* 存在但合并 init 时遗漏，
-- 此处按实体列（前缀命名）补齐。CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS
-- 均为幂等，且不改动既有表，对已部署库安全。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_companion` (
    `sys_ai_companion_id`                   VARCHAR(64)  NOT NULL COMMENT '伙伴标识',
    `sys_ai_companion_seq`                  INT          NULL     COMMENT '排序序号',
    `sys_ai_companion_name`                 VARCHAR(128) NULL     COMMENT '名称',
    `sys_ai_companion_character`            VARCHAR(64)  NULL     COMMENT '角色',
    `sys_ai_companion_persona_text`         TEXT         NULL     COMMENT '人格(JSON)',
    `sys_ai_companion_model_text`           TEXT         NULL     COMMENT '模型(JSON)',
    `sys_ai_companion_fallback_model_text`  TEXT         NULL     COMMENT '降级模型(JSON)',
    `sys_ai_companion_vision_model_text`    TEXT         NULL     COMMENT '视觉模型(JSON)',
    `sys_ai_companion_voice_text`           TEXT         NULL     COMMENT '语音(JSON)',
    `sys_ai_companion_learn_text`           TEXT         NULL     COMMENT '学习配置(JSON)',
    `sys_ai_companion_evolve_text`          TEXT         NULL     COMMENT '进化配置(JSON)',
    `sys_ai_companion_skills_text`          TEXT         NULL     COMMENT '技能(JSON)',
    `sys_ai_companion_appearance_text`      TEXT         NULL     COMMENT '外观(JSON)',
    `sys_ai_companion_applied_preset_text`  TEXT         NULL     COMMENT '已应用预设(JSON)',
    `sys_ai_companion_order_index`          INT          NULL     COMMENT '展示顺序',
    `sys_ai_companion_conversation_id`      VARCHAR(64)  NULL     COMMENT '会话标识',
    `sys_ai_companion_created_at`           BIGINT       NULL     COMMENT '创建时间(毫秒)',
    `sys_ai_companion_updated_at`           BIGINT       NULL     COMMENT '更新时间(毫秒)',
    `sys_ai_companion_user_id`              VARCHAR(64)  NULL     COMMENT '归属用户 ID（登录用户名）',
    PRIMARY KEY (`sys_ai_companion_id`),
    KEY `sys_ai_companion_user` (`sys_ai_companion_user_id`, `sys_ai_companion_order_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字伙伴';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_figure` (
    `sys_ai_companion_figure_id`          VARCHAR(64)  NOT NULL COMMENT '形象标识',
    `sys_ai_companion_figure_name`        VARCHAR(128) NULL     COMMENT '名称',
    `sys_ai_companion_figure_aspect`      DOUBLE       NULL     COMMENT '宽高比',
    `sys_ai_companion_figure_head_box`    TEXT         NULL     COMMENT '头部框(JSON)',
    `sys_ai_companion_figure_size_tier`   VARCHAR(32)  NULL     COMMENT '尺寸档位',
    `sys_ai_companion_figure_source_path` VARCHAR(512) NULL     COMMENT '源路径',
    `sys_ai_companion_figure_created_at`  BIGINT       NULL     COMMENT '创建时间(毫秒)',
    `sys_ai_companion_figure_user_id`     VARCHAR(64)  NULL     COMMENT '归属用户 ID（NULL 表示内置共享形象）',
    PRIMARY KEY (`sys_ai_companion_figure_id`),
    KEY `sys_ai_companion_figure_user` (`sys_ai_companion_figure_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字伙伴形象';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_memory` (
    `sys_ai_companion_memory_id`               VARCHAR(64) NOT NULL COMMENT '记忆标识',
    `sys_ai_companion_memory_companion_id`     VARCHAR(64) NULL     COMMENT '伙伴标识',
    `sys_ai_companion_memory_kind`             VARCHAR(32) NULL     COMMENT '种类',
    `sys_ai_companion_memory_content`          TEXT        NULL     COMMENT '内容',
    `sys_ai_companion_memory_tags`             TEXT        NULL     COMMENT '标签(JSON数组)',
    `sys_ai_companion_memory_importance`       DOUBLE      NULL     COMMENT '重要度',
    `sys_ai_companion_memory_strength`         DOUBLE      NULL     COMMENT '强度',
    `sys_ai_companion_memory_pinned`           BOOLEAN     NULL     COMMENT '是否置顶',
    `sys_ai_companion_memory_source`           VARCHAR(64) NULL     COMMENT '来源',
    `sys_ai_companion_memory_status`           VARCHAR(32) NULL     COMMENT '状态',
    `sys_ai_companion_memory_created_at`       BIGINT      NULL     COMMENT '创建时间(毫秒)',
    `sys_ai_companion_memory_updated_at`       BIGINT      NULL     COMMENT '更新时间(毫秒)',
    `sys_ai_companion_memory_last_reinforced_at` BIGINT    NULL     COMMENT '最近强化时间(毫秒)',
    `sys_ai_companion_memory_user_id`          VARCHAR(64) NULL     COMMENT '归属用户 ID（登录用户名）',
    PRIMARY KEY (`sys_ai_companion_memory_id`),
    KEY `idx_ai_companion_memory_companion` (`sys_ai_companion_memory_companion_id`),
    KEY `idx_ai_companion_memory_user` (`sys_ai_companion_memory_user_id`, `sys_ai_companion_memory_companion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字伙伴记忆';

CREATE TABLE IF NOT EXISTS `sys_ai_companion_skill` (
    `sys_ai_companion_skill_id`                   VARCHAR(64)  NOT NULL COMMENT '技能标识',
    `sys_ai_companion_skill_companion_id`         VARCHAR(64)  NULL     COMMENT '伙伴标识',
    `sys_ai_companion_skill_name`                 VARCHAR(128) NULL     COMMENT '技能名称',
    `sys_ai_companion_skill_status`               VARCHAR(32)  NULL     COMMENT '状态',
    `sys_ai_companion_skill_source`               VARCHAR(64)  NULL     COMMENT '来源',
    `sys_ai_companion_skill_confidence`           DOUBLE       NULL     COMMENT '置信度',
    `sys_ai_companion_skill_provenance_event_ids` TEXT         NULL     COMMENT '溯源事件ID(JSON数组)',
    `sys_ai_companion_skill_strength`             DOUBLE       NULL     COMMENT '强度',
    `sys_ai_companion_skill_version`              INT          NULL     COMMENT '版本',
    `sys_ai_companion_skill_skill_pattern_id`     VARCHAR(64)  NULL     COMMENT '技能模式标识',
    `sys_ai_companion_skill_usage_count`          INT          NULL     COMMENT '使用次数',
    `sys_ai_companion_skill_last_used_at`         BIGINT       NULL     COMMENT '最近使用时间(毫秒)',
    `sys_ai_companion_skill_created_at`           BIGINT       NULL     COMMENT '创建时间(毫秒)',
    `sys_ai_companion_skill_updated_at`           BIGINT       NULL     COMMENT '更新时间(毫秒)',
    `sys_ai_companion_skill_description`          TEXT         NULL     COMMENT '描述',
    `sys_ai_companion_skill_content`              TEXT         NULL     COMMENT '内容',
    `sys_ai_companion_skill_user_id`              VARCHAR(64)  NULL     COMMENT '归属用户 ID（登录用户名）',
    PRIMARY KEY (`sys_ai_companion_skill_id`),
    KEY `idx_ai_companion_skill_companion` (`sys_ai_companion_skill_companion_id`),
    KEY `idx_ai_companion_skill_user` (`sys_ai_companion_skill_user_id`, `sys_ai_companion_skill_companion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字伙伴技能';

-- ---------------------------------------------------------------------
-- 模型指标库 sys_ai_model_metrics（原 V1.1 独立脚本，归口全量脚本）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_model_metrics` (
    `sys_ai_model_metrics_id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `sys_ai_model_metrics_model_key`             VARCHAR(160)  NOT NULL COMMENT '归一化模型键（唯一）',
    `sys_ai_model_metrics_model`                 VARCHAR(220)  NULL COMMENT '原始模型标识/slug',
    `sys_ai_model_metrics_provider`              VARCHAR(128)  NULL COMMENT '厂商',
    `sys_ai_model_metrics_price_input`           DOUBLE        NULL COMMENT '输入价 USD/百万 token',
    `sys_ai_model_metrics_price_output`          DOUBLE        NULL COMMENT '输出价 USD/百万 token',
    `sys_ai_model_metrics_price_cache_hit`       DOUBLE        NULL COMMENT '缓存命中价 USD/百万',
    `sys_ai_model_metrics_price_cache_write`     DOUBLE        NULL COMMENT '缓存写入价 USD/百万',
    `sys_ai_model_metrics_intelligence_index`    DOUBLE        NULL COMMENT '智能指数',
    `sys_ai_model_metrics_hle_score`             DOUBLE        NULL COMMENT 'HLE 人类最后考试得分（0~1）',
    `sys_ai_model_metrics_omniscience_index`     DOUBLE        NULL COMMENT '全知指数（知识准确-幻觉，可为负）',
    `sys_ai_model_metrics_gpqa_score` DOUBLE NULL COMMENT 'GPQA 得分（0~1）',
    `sys_ai_model_metrics_aime25_score` DOUBLE NULL COMMENT 'AIME 2025 数学基准得分（0~1）',
    `sys_ai_model_metrics_livecodebench_score` DOUBLE NULL COMMENT 'LiveCodeBench 编程基准得分（0~1）',
    `sys_ai_model_metrics_scicode_score` DOUBLE NULL COMMENT 'SciCode 科学编程基准得分（0~1）',
    `sys_ai_model_metrics_ifbench_score` DOUBLE NULL COMMENT 'IFBench 指令遵循基准得分（0~1）',
    `sys_ai_model_metrics_critpt_score` DOUBLE NULL COMMENT 'CritPt 物理基准得分（0~1）',
    `sys_ai_model_metrics_mmmu_pro_score` DOUBLE NULL COMMENT 'MMMU-Pro 多模态基准得分（0~1）',
    `sys_ai_model_metrics_tau2_score` DOUBLE NULL COMMENT 'Tau2-Bench 工具调用基准得分（0~1）',
    `sys_ai_model_metrics_tau_banking_score` DOUBLE NULL COMMENT 'Tau-Banking 银行场景基准得分（0~1）',
    `sys_ai_model_metrics_lcr_score` DOUBLE NULL COMMENT 'LCR 长上下文推理得分（0~1）',
    `sys_ai_model_metrics_terminalbench_hard_score` DOUBLE NULL COMMENT 'Terminal-Bench Hard 得分（0~1）',
    `sys_ai_model_metrics_terminal_bench21_score` DOUBLE NULL COMMENT 'Terminal-Bench 2.1 得分（0~1）',
    `sys_ai_model_metrics_terminal_bench40_score` DOUBLE NULL COMMENT 'Terminal-Bench 4.0 得分（0~1）',
    `sys_ai_model_metrics_automation_bench_score` DOUBLE NULL COMMENT 'AutomationBench 自动化基准得分（0~1）',
    `sys_ai_model_metrics_gdpval_normalized_score` DOUBLE NULL COMMENT 'GDPval 归一化得分（0~1）',
    `sys_ai_model_metrics_briefcase_score` DOUBLE NULL COMMENT 'Briefcase 专业任务基准得分（0~1）',
    `sys_ai_model_metrics_harvey_lab_score` DOUBLE NULL COMMENT 'Harvey Lab 法律任务基准得分（0~1）',
    `sys_ai_model_metrics_apex_agents_score` DOUBLE NULL COMMENT 'Apex Agents 基准得分（0~1）',
    `sys_ai_model_metrics_itbench_sre_score` DOUBLE NULL COMMENT 'ITBench SRE 运维基准得分（0~1）',
    `sys_ai_model_metrics_omniscience_accuracy` DOUBLE NULL COMMENT '全知指数-准确率（0~1）',
    `sys_ai_model_metrics_omniscience_non_hallucination` DOUBLE NULL COMMENT '全知指数-非幻觉率（0~1）',
    `sys_ai_model_metrics_elo` DOUBLE NULL COMMENT 'Artificial Analysis Elo 评分',
    `sys_ai_model_metrics_cost_per_task` DOUBLE NULL COMMENT '单任务平均成本（USD）',
    `sys_ai_model_metrics_json_mode` TINYINT(1) NULL COMMENT '是否支持 JSON 输出模式',
    `sys_ai_model_metrics_openai_compatible` TINYINT(1) NULL COMMENT '是否兼容 OpenAI 协议',
    `sys_ai_model_metrics_intelligence_index_estimated` TINYINT(1) NULL COMMENT '智能指数是否为估算值',
    `sys_ai_model_metrics_output_speed_tps`      DOUBLE        NULL COMMENT '输出速度 token/秒',
    `sys_ai_model_metrics_latency_first_token`   DOUBLE        NULL COMMENT '首 token 延迟（秒）',
    `sys_ai_model_metrics_context_window`        BIGINT        NULL COMMENT '上下文窗口 token',
    `sys_ai_model_metrics_icon_url`              VARCHAR(512)  NULL COMMENT '模型图标',
    `sys_ai_model_metrics_support_reasoning`     TINYINT(1)    NULL COMMENT '是否支持深度思考',
    `sys_ai_model_metrics_support_web_search`    TINYINT(1)    NULL COMMENT '是否支持联网检索',
    `sys_ai_model_metrics_support_image_input`   TINYINT(1)    NULL COMMENT '是否支持视觉输入',
    `sys_ai_model_metrics_support_function_call` TINYINT(1)    NULL COMMENT '是否支持函数调用',
    `sys_ai_model_metrics_manual_locked`         TEXT          NULL COMMENT '手动锁定字段名 JSON 数组',
    `sys_ai_model_metrics_manual_updated_at`     BIGINT        NULL COMMENT '手动更新时间（毫秒）',
    `sys_ai_model_metrics_manual_updated_by`     VARCHAR(64)   NULL COMMENT '手动更新人',
    `sys_ai_model_metrics_source`                VARCHAR(40)   NULL COMMENT '数据源 artificialanalysis',
    `sys_ai_model_metrics_status`                TINYINT       NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time`                                BIGINT        NULL COMMENT '创建时间（毫秒）',
    `update_time`                                BIGINT        NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_model_metrics_id`),
    UNIQUE KEY `uk_sys_ai_model_metrics_key` (`sys_ai_model_metrics_model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型指标库（全量，模型按 key 关联取价）';

-- sys_ai_bot_binding 的 mention_only / random_* 列已并入上面的全量 CREATE TABLE，
-- 此处不再输出兼容 ALTER：MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，保留只会在每次启动
-- 产生语法错误并被 continue-on-error 静默吞掉。存量库补列请走独立变更单。


-- 由 API system 根目录 ddl-h2.sql 归位：sys_ai_connector（AI 表归属 AI 模块）。
CREATE TABLE IF NOT EXISTS sys_ai_connector (
  sys_ai_connector_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_connector_key VARCHAR(128),
  sys_ai_connector_name VARCHAR(128),
  sys_ai_connector_description VARCHAR(512),
  sys_ai_connector_type VARCHAR(64),
  sys_ai_connector_category VARCHAR(64),
  sys_ai_connector_icon VARCHAR(256),
  sys_ai_connector_endpoint LONGTEXT,
  sys_ai_connector_params LONGTEXT,
  sys_ai_connector_builtin TINYINT DEFAULT 0,
  sys_ai_connector_enabled TINYINT DEFAULT 1,
  sys_ai_connector_last_test_status VARCHAR(32),
  sys_ai_connector_last_tested_at TIMESTAMP,
  sys_ai_connector_created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  sys_ai_connector_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 由 API system 根目录 ddl-h2.sql 归位：sys_ai_requirement_check（AI 表归属 AI 模块）。
CREATE TABLE IF NOT EXISTS sys_ai_requirement_check (
  sys_ai_requirement_check_id VARCHAR(36) NOT NULL,
  sys_ai_requirement_check_requirement_id VARCHAR(36),
  sys_ai_requirement_check_status VARCHAR(32) DEFAULT 'pending',
  create_time BIGINT,
  update_time BIGINT,
  PRIMARY KEY (sys_ai_requirement_check_id)
);

-- 生成任务（文生图 / 文生视频）。与历史遗留且无 Java 侧使用者的 sys_ai_task 并存：
-- 那张表列语义面向旧版「模块 + 项目」模型，本表面向「供应商 + 模型 + SPI 任务」的新链路，
-- 字段更贴合 GenerateClientResolver 的解析维度，故独立建表而不复用。
CREATE TABLE IF NOT EXISTS `sys_ai_generate_task` (
    `sys_ai_generate_task_id`          VARCHAR(64)  NOT NULL COMMENT '任务ID（对外暴露，同 provider 任务ID）',
    `sys_ai_generate_task_type`        VARCHAR(32)  NOT NULL COMMENT '任务类型（image / video）',
    `sys_ai_generate_task_status`      VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELED）',
    `sys_ai_generate_task_progress`    INT          NULL COMMENT '进度百分比 0-100',
    `sys_ai_generate_task_prompt`      TEXT         NULL COMMENT '正向提示词',
    `sys_ai_generate_task_negative`    TEXT         NULL COMMENT '负面提示词',
    `sys_ai_generate_task_provider_id` VARCHAR(64)  NULL COMMENT '供应商标识（sys_ai_provider_id）',
    `sys_ai_generate_task_model`       VARCHAR(255) NULL COMMENT '模型名',
    `sys_ai_generate_task_size`        VARCHAR(32)  NULL COMMENT '分辨率（如 1024x1024）',
    `sys_ai_generate_task_duration`    INT          NULL COMMENT '视频时长（秒）',
    `sys_ai_generate_task_count`       INT          NOT NULL DEFAULT 1 COMMENT '请求张数',
    `sys_ai_generate_task_seed`        BIGINT       NULL COMMENT '随机种子',
    `sys_ai_generate_task_style`       VARCHAR(64)  NULL COMMENT '风格',
    `sys_ai_generate_task_quality`     VARCHAR(64)  NULL COMMENT '质量档',
    `sys_ai_generate_task_steps`       INT          NULL COMMENT '推理步数',
    `sys_ai_generate_task_reference`   LONGTEXT     NULL COMMENT '参考图（data URL / base64）',
    `sys_ai_generate_task_result`      LONGTEXT     NULL COMMENT '生成结果JSON（媒体URL列表等）',
    `sys_ai_generate_task_error`       TEXT         NULL COMMENT '失败原因',
    `sys_user_id`                      VARCHAR(255) NULL COMMENT '创建人ID',
    `sys_ai_generate_task_create_time` BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_generate_task_update_time` BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    `sys_ai_generate_task_elapsed`     BIGINT       NULL COMMENT '生成耗时（毫秒）',
    PRIMARY KEY (`sys_ai_generate_task_id`),
    KEY `sys_ai_generate_task_user_type` (`sys_user_id`, `sys_ai_generate_task_type`, `sys_ai_generate_task_create_time`),
    KEY `sys_ai_generate_task_status` (`sys_ai_generate_task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成任务（文生图/文生视频）';

-- ---------------------------------------------------------------------
-- RAG 知识库元数据
--
-- 背景：ApiKnowledgeController 的 bases 之前是纯内存 ConcurrentHashMap，
--       后端每次重启知识库列表连同文件树一起消失（生产上是数据丢失级问题）。
--       本表把「库本身」持久化，目录内容仍由 rag-uploads/kb-<id> 承载。
--
-- 为什么新建表而不是给 sys_ai_knowledge_library 加列
--   1) 本仓 init SQL 的 ONCE 语义是「仅当库为空时执行」，而 MySQL 的
--      ADD COLUMN 不支持 IF NOT EXISTS，两者叠加会导致线上永远加不上列；
--      sys_ai_knowledge_library 的「不写兼容 ALTER」说明正是这个原因。
--   2) 语义也不同：sys_ai_knowledge_library 是「图谱/模型库」这一层的资源目录，
--      而这里的记录是「RAG 知识库」——带 root_path、托管标记、采集来源配置。
--      两者不是同一层实体，混在一张表里会让 kind/source 这类列对图谱库恒为空。
--
-- 与内存 Map 的关系
--   启动时全量载入内存 Map（保持既有 20 余处读取点行为不变），
--   之后所有写操作同时落这张表（写穿缓存）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_base` (
    `sys_ai_knowledge_base_id`           VARCHAR(64)   NOT NULL COMMENT '主键（UUID，对应内存记录的 knowledge_base_id）',
    `sys_ai_knowledge_base_name`         VARCHAR(255)  NOT NULL COMMENT '知识库名称',
    `sys_ai_knowledge_base_description`  TEXT          DEFAULT NULL COMMENT '知识库描述',
    `sys_ai_knowledge_base_root_path`    VARCHAR(1024) DEFAULT NULL COMMENT '根目录；托管库为 <uploadDir>/kb-<id>，外部库为用户指定目录',
    `sys_ai_knowledge_base_kind`         VARCHAR(32)   NOT NULL DEFAULT 'local' COMMENT '类型：local 外部本地目录 / web 网址采集',
    `sys_ai_knowledge_base_managed`      TINYINT       NOT NULL DEFAULT 0 COMMENT '目录是否由本服务托管（托管库删除时可一并清理磁盘）',
    `sys_ai_knowledge_base_is_public`    TINYINT       NOT NULL DEFAULT 0 COMMENT '是否公开可读（1 公开，0 仅归属人与管理员）',
    `sys_ai_knowledge_base_tags`         VARCHAR(1024) DEFAULT NULL COMMENT '标签，JSON 数组文本（如 ["三国","验收"]）',
    `sys_ai_knowledge_base_source`       TEXT          DEFAULT NULL COMMENT '采集来源配置（JSON，{mode,entries[]}）；kind=web 时非空',
    `sys_ai_knowledge_base_file_count`   BIGINT        NOT NULL DEFAULT 0 COMMENT '文件数（缓存值，读取时按需重算）',
    `sys_ai_knowledge_base_total_size`   BIGINT        NOT NULL DEFAULT 0 COMMENT '目录总字节数（缓存值，读取时按需重算）',
    `sys_ai_knowledge_base_chunk_max_size`     INT NOT NULL DEFAULT 1000 COMMENT '分片最大长度（字符）',
    `sys_ai_knowledge_base_chunk_overlap_size` INT NOT NULL DEFAULT 100  COMMENT '分片重叠长度（字符）',
    `sys_ai_knowledge_base_chunk_min_size`     INT NOT NULL DEFAULT 50   COMMENT '分片最小长度（字符）',
    `sys_ai_knowledge_base_user_id`      VARCHAR(64)   DEFAULT NULL COMMENT '归属用户ID（写入者，非空表示仅本人与管理员可见可写）',
    `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人',
    `create_by`    INT          DEFAULT NULL COMMENT '创建人ID',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人',
    `update_by`    INT          DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_ai_knowledge_base_id`),
    KEY `sys_ai_knowledge_base_user` (`sys_ai_knowledge_base_user_id`),
    KEY `sys_ai_knowledge_base_kind` (`sys_ai_knowledge_base_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG知识库元数据（目录内容由 rag-uploads/kb-<id> 承载）';

-- AI 服务实例：端口 / 协议开关 / 用量记录配置。
-- 此前只有实体 SysAiServiceInstance（@TableName）而没有 DDL，导致
-- ServiceInstanceManager.recoverInstances 在全新库上抛
-- `Table 'ai_test.sys_ai_service_instance' doesn't exist`（实测新环境必现）。
CREATE TABLE IF NOT EXISTS `sys_ai_service_instance` (
    `instance_id`     VARCHAR(64)  NOT NULL COMMENT '实例 ID',
    `instance_name`   VARCHAR(128) DEFAULT NULL COMMENT '实例名称',
    `port`            INT          DEFAULT NULL COMMENT '端口',
    `enabled`         TINYINT      DEFAULT 1 COMMENT '是否启用',
    `openai_enabled`  TINYINT      DEFAULT 0 COMMENT 'OpenAI 协议开关',
    `cc_enabled`      TINYINT      DEFAULT 0 COMMENT 'Claude Code 协议开关',
    `gemini_enabled`  TINYINT      DEFAULT 0 COMMENT 'Gemini 协议开关',
    `record_usage`    TINYINT      DEFAULT 0 COMMENT '是否记录用量',
    `user_id`         VARCHAR(64)  DEFAULT NULL COMMENT '所属用户ID',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`instance_id`),
    KEY `sys_ai_service_instance_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 服务实例（端口/协议开关/用量记录）';

-- ------------------------------------------------------------
-- 存量库兼容 ALTER 已迁至 V1.1__add_ai_model_owner_and_task_elapsed.sql
--
-- 原先 sys_ai_generate_task_elapsed 与 sys_ai_model 归属列/唯一键的两组 ALTER
-- 被追加在本文件末尾，对两类库都无效，是纯粹的负债：
--   * 存量库：本文件自 2026-09-30 起已记入 sys_database_version（success=true），
--     DataSourceFlyway 按文件名去重、整份跳过，追加在末尾的语句永远不会执行；
--   * 全新库：两列都已写进各自的 CREATE TABLE，裸 ADD COLUMN 必然报 1060 重复列，
--     整份脚本被标记 success=false 并在每次启动重试。
-- 请把新的存量库兼容 DDL 一律写成独立的版本脚本，不要追加到已发布的 V1.0 末尾。
-- ------------------------------------------------------------

