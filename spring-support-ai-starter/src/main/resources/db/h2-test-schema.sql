-- Minimal H2-compatible schema for local AI-starter test (H2 MySQL mode)
-- Stripped of MySQL-only procedural syntax; plain CREATE TABLE only.

CREATE TABLE IF NOT EXISTS sys_ai_bot_binding (
  sys_ai_bot_binding_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_bot_binding_user_id BIGINT,
  sys_ai_bot_binding_owner_key VARCHAR(128),
  sys_ai_bot_binding_bot_id VARCHAR(128),
  sys_ai_bot_binding_bot_token VARCHAR(512),
  sys_ai_bot_binding_platform VARCHAR(32),
  sys_ai_bot_binding_nickname VARCHAR(128),
  sys_ai_bot_binding_avatar_url VARCHAR(512),
  sys_ai_bot_binding_gateway_port INT,
  sys_ai_bot_binding_status VARCHAR(32) DEFAULT 'offline',
  sys_ai_bot_binding_bind_source VARCHAR(32),
  sys_ai_bot_binding_model VARCHAR(128),
  sys_ai_bot_binding_thinking TINYINT DEFAULT 0,
  sys_ai_bot_binding_thinking_effort VARCHAR(16),
  sys_ai_bot_binding_system_prompt CLOB,
  sys_ai_bot_binding_record_usage TINYINT DEFAULT 1,
  sys_ai_bot_binding_context_thinking TINYINT,
  sys_ai_bot_binding_context_size INT,
  sys_ai_bot_binding_history_size INT,
  sys_ai_bot_binding_command_enabled TINYINT,
  sys_ai_bot_binding_last_active_time TIMESTAMP,
  create_name VARCHAR(64),
  create_by INT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_name VARCHAR(64),
  update_by INT,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_ai_global_setting (
  sys_ai_global_setting_id INT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_global_setting_group VARCHAR(64) DEFAULT 'default',
  sys_ai_global_setting_field VARCHAR(128),
  sys_ai_global_setting_value CLOB,
  sys_ai_global_setting_value_type VARCHAR(32) DEFAULT 'STRING',
  sys_ai_global_setting_description VARCHAR(512),
  sys_ai_global_setting_sort INT DEFAULT 0,
  sys_ai_global_setting_status INT DEFAULT 1,
  create_name VARCHAR(255),
  create_by INT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_name VARCHAR(255),
  update_by INT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_global_setting_field ON sys_ai_global_setting(sys_ai_global_setting_group, sys_ai_global_setting_field);

CREATE TABLE IF NOT EXISTS sys_ai_usage (
  sys_ai_usage_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_usage_group_name VARCHAR(32),
  sys_ai_usage_provider VARCHAR(64),
  sys_ai_usage_model VARCHAR(128),
  sys_ai_usage_call_type VARCHAR(16),
  sys_ai_usage_prompt_tokens INT,
  sys_ai_usage_completion_tokens INT,
  sys_ai_usage_total_tokens INT,
  sys_ai_usage_input_cost DOUBLE,
  sys_ai_usage_output_cost DOUBLE,
  sys_ai_usage_total_cost DOUBLE,
  sys_ai_usage_request_id VARCHAR(128),
  sys_ai_usage_user_id VARCHAR(64),
  sys_ai_usage_call_time TIMESTAMP,
  sys_ai_usage_inference_strength VARCHAR(32),
  sys_ai_usage_endpoint VARCHAR(128),
  sys_ai_usage_ip VARCHAR(64),
  sys_ai_usage_type VARCHAR(16),
  sys_ai_usage_billing_mode VARCHAR(16),
  sys_ai_usage_cached_tokens INT,
  sys_ai_usage_cached_cost DOUBLE,
  sys_ai_usage_input_price_per_1m DOUBLE,
  sys_ai_usage_output_price_per_1m DOUBLE,
  sys_ai_usage_cache_read_price_per_1m DOUBLE,
  sys_ai_usage_service_tier VARCHAR(32),
  sys_ai_usage_ratio DOUBLE,
  sys_ai_usage_original_cost DOUBLE,
  sys_ai_usage_first_token_latency BIGINT,
  sys_ai_usage_total_latency BIGINT,
  create_name VARCHAR(64),
  create_by INT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_name VARCHAR(64),
  update_by INT
);

CREATE TABLE IF NOT EXISTS sys_ai_usage_daily (
  sys_ai_usage_daily_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_usage_daily_date DATE,
  sys_ai_usage_daily_group VARCHAR(64),
  sys_ai_usage_daily_provider VARCHAR(64),
  sys_ai_usage_daily_model VARCHAR(128),
  sys_ai_usage_daily_tokens INT,
  sys_ai_usage_daily_cost DOUBLE,
  sys_ai_usage_daily_count INT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_ai_pricing (
  sys_ai_pricing_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_pricing_provider VARCHAR(64),
  sys_ai_pricing_model VARCHAR(128),
  sys_ai_pricing_input_price_per_1k DOUBLE,
  sys_ai_pricing_output_price_per_1k DOUBLE,
  sys_ai_pricing_currency VARCHAR(8),
  sys_ai_pricing_effective_date TIMESTAMP,
  sys_ai_pricing_source VARCHAR(32),
  create_name VARCHAR(64),
  create_by INT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_name VARCHAR(64),
  update_by INT,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_ai_manufacturer (
  sys_ai_manufacturer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_manufacturer_name VARCHAR(64),
  sys_ai_manufacturer_code VARCHAR(64),
  sys_ai_manufacturer_status INT DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_ai_manufacturer_key (
  sys_ai_manufacturer_key_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_manufacturer_key_manufacturer_id BIGINT,
  sys_ai_manufacturer_key_key VARCHAR(256),
  sys_ai_manufacturer_key_status INT DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_ai_model_setting (
  sys_ai_model_setting_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_model_setting_bot_id VARCHAR(128),
  sys_ai_model_setting_field VARCHAR(128),
  sys_ai_model_setting_value CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_ai_app_key (
  sys_ai_app_key_id INT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_app_key_app_id VARCHAR(128),
  sys_ai_app_key_app_key VARCHAR(128),
  sys_ai_app_key_secret VARCHAR(256),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------------------------------------------------
-- 远程主机（SSH 主机簿）— /api/ssh-hosts 读写依赖此表
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_ai_ssh_host (
  sys_ai_ssh_host_id             VARCHAR(36) NOT NULL PRIMARY KEY,
  sys_ai_ssh_host_name           VARCHAR(255),
  sys_ai_ssh_host_host           VARCHAR(255),
  sys_ai_ssh_host_port           INT DEFAULT 22,
  sys_ai_ssh_host_username       VARCHAR(128),
  sys_ai_ssh_host_auth_type      VARCHAR(32) DEFAULT 'password',
  sys_ai_ssh_host_password       VARCHAR(1024),
  sys_ai_ssh_host_private_key    CLOB,
  sys_ai_ssh_host_passphrase     VARCHAR(1024),
  sys_ai_ssh_host_certificate    CLOB,
  sys_ai_ssh_host_sudo_password  VARCHAR(1024),
  sys_ai_ssh_host_host_fingerprint VARCHAR(512),
  sys_ai_ssh_host_status         VARCHAR(32) DEFAULT 'unknown',
  sys_ai_ssh_host_last_connected_at BIGINT,
  sys_ai_ssh_host_created_at     BIGINT NOT NULL,
  sys_ai_ssh_host_updated_at     BIGINT NOT NULL
);

-- -------------------------------------------------------------------
-- AI Agent 定义 — /v2/ai/agents 依赖此表
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_ai_agent (
  sys_ai_agent_id              VARCHAR(64) NOT NULL PRIMARY KEY,
  sys_ai_agent_parent_id       VARCHAR(64),
  sys_ai_agent_name            VARCHAR(128) NOT NULL,
  sys_ai_agent_description     VARCHAR(512),
  sys_ai_agent_role            VARCHAR(255),
  sys_ai_agent_instruction     CLOB,
  sys_ai_agent_system_prompt   CLOB,
  sys_ai_agent_provider_id     VARCHAR(64),
  sys_ai_agent_model           VARCHAR(255),
  sys_ai_agent_mcp_ids         CLOB,
  sys_ai_agent_skill_ids       CLOB,
  sys_ai_agent_strategy        CLOB,
  sys_ai_agent_enabled         TINYINT DEFAULT 1,
  sys_ai_agent_sort_order      INT DEFAULT 0,
  sys_ai_agent_create_time     BIGINT NOT NULL,
  sys_ai_agent_update_time     BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_agent_parent ON sys_ai_agent(sys_ai_agent_parent_id);

-- -------------------------------------------------------------------
-- MCP 服务器定义 — /v2/mcp/* 依赖此表
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_ai_mcp_server (
  sys_ai_mcp_server_id          VARCHAR(36) NOT NULL PRIMARY KEY,
  sys_ai_mcp_server_name        VARCHAR(255) NOT NULL,
  sys_ai_mcp_server_description VARCHAR(512),
  sys_ai_mcp_server_transport   CLOB NOT NULL,
  sys_ai_mcp_server_original_json CLOB,
  sys_ai_mcp_server_enabled     TINYINT DEFAULT 1,
  sys_ai_mcp_server_status      VARCHAR(32) DEFAULT 'unknown',
  sys_ai_mcp_server_create_time BIGINT NOT NULL,
  sys_ai_mcp_server_update_time BIGINT NOT NULL
);

-- -------------------------------------------------------------------
-- 用量日志/浏览历史 — Usage/Session 相关接口依赖此表
-- -------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_ai_bot_message (
  sys_ai_bot_message_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  sys_ai_bot_message_bot_id   VARCHAR(128),
  sys_ai_bot_message_direction VARCHAR(16),
  sys_ai_bot_message_from_user VARCHAR(128),
  sys_ai_bot_message_content  CLOB,
  sys_ai_bot_message_create_time TIMESTAMP
);

-- -------------------------------------------------------------------
-- OCR 分组默认配置项（与 OcrGlobalSettingProvider 对齐，供演示/联调）
-- -------------------------------------------------------------------
INSERT INTO sys_ai_global_setting (sys_ai_global_setting_group, sys_ai_global_setting_field, sys_ai_global_setting_value, sys_ai_global_setting_value_type, sys_ai_global_setting_description, sys_ai_global_setting_sort, sys_ai_global_setting_status) VALUES
('ocr','detector_model','paddleocrv6-det','STRING','文字检测模型（paddleocrv6-det / paddleocrv6-medium-det）',1,1),
('ocr','recognizer_model','paddleocrv6-rec','STRING','文字识别模型（paddleocrv6-rec / paddleocrv6-medium-rec / svtr-extractor / paddle-ocr-recognition / pp-word-extractor）',2,1),
('ocr','detector_threshold','0.5','NUMBER','OCR 检测置信度阈值（过滤低置信度文本框，0~1）',3,1),
('ocr','sort_reading_order','true','BOOLEAN','是否按阅读顺序排序（先 y 后 x）',4,1),
('ocr','direction_model','','STRING','方向矫正模型（pp-word-rotate，留空关闭）',5,1),
('ocr','restorer_model','','STRING','文字超分修复模型（text-bsr，留空关闭）',6,1),
('ocr','device','cpu','STRING','推理设备（cpu / cuda）',7,1);