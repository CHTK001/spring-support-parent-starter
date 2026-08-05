-- ============================================================
-- AI 新设置体系表结构（供应商 / 模型 / 分组 / AppKey / 全局 / 告警 / 服务 / 用量）
-- 说明：由 spring-support-datasource-starter 的 Flyway-like 迁移器扫描执行。
-- 表名采用 ai_ 前缀（新设置体系），与 sys_ai_* 旧表区分。
-- ============================================================

-- 1. 模型供应商表
CREATE TABLE IF NOT EXISTS `ai_provider` (
    `provider_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '供应商主键',
    `provider_name` VARCHAR(255) NOT NULL COMMENT '供应商名称',
    `provider_type` VARCHAR(64) NOT NULL COMMENT '供应商类型(openai/anthropic/gemini/qwen/custom)',
    `provider_base_url` VARCHAR(512) DEFAULT NULL COMMENT '接入 Base URL',
    `provider_api_key` VARCHAR(512) DEFAULT NULL COMMENT 'API Key',
    `provider_logo` VARCHAR(1024) DEFAULT NULL COMMENT 'Logo 图片地址',
    `provider_models` TEXT DEFAULT NULL COMMENT '可用模型列表(JSON)',
    `provider_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `provider_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `provider_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `provider_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY `uk_ai_provider_name` (`provider_name`)
) COMMENT='模型供应商表';

-- 2. 模型分组表
CREATE TABLE IF NOT EXISTS `ai_group` (
    `group_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分组主键',
    `group_name` VARCHAR(128) NOT NULL COMMENT '分组名称',
    `group_description` VARCHAR(256) DEFAULT NULL COMMENT '分组描述',
    `group_models` TEXT DEFAULT NULL COMMENT '分组关联模型列表(JSON)',
    `group_app_key_ids` TEXT DEFAULT NULL COMMENT '绑定 AppKey ID 列表(JSON)',
    `group_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `group_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `group_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `group_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY `uk_ai_group_name` (`group_name`)
) COMMENT='模型分组表';

-- 3. 模型定义表
CREATE TABLE IF NOT EXISTS `ai_model` (
    `model_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模型主键',
    `model_name` VARCHAR(255) NOT NULL COMMENT '模型显示名称',
    `model_code` VARCHAR(255) NOT NULL COMMENT '模型标识(gpt-4o, deepseek-chat)',
    `model_provider_id` BIGINT DEFAULT NULL COMMENT '关联供应商 ID',
    `model_capabilities` VARCHAR(512) DEFAULT NULL COMMENT '能力标签(逗号分隔: chat,vision,think,search)',
    `model_description` VARCHAR(512) DEFAULT NULL COMMENT '模型描述',
    `model_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `model_sort` INT DEFAULT 0 COMMENT '排序值',
    `model_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `model_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `model_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY `uk_ai_model_code` (`model_code`)
) COMMENT='AI 模型定义表';

-- 4. AppKey 表
CREATE TABLE IF NOT EXISTS `ai_app_key` (
    `app_key_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'AppKey 主键',
    `app_key_name` VARCHAR(255) NOT NULL COMMENT 'AppKey 名称',
    `app_key_value` VARCHAR(512) NOT NULL COMMENT 'AppKey 密钥值',
    `app_key_provider_id` BIGINT DEFAULT NULL COMMENT '关联供应商 ID',
    `app_key_model` VARCHAR(255) DEFAULT NULL COMMENT '限定使用的模型',
    `app_key_group_id` BIGINT DEFAULT NULL COMMENT '绑定分组 ID（分组的 key 只能用分组下的模型）',
    `app_key_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `app_key_use_count` BIGINT DEFAULT 0 COMMENT '已使用次数',
    `app_key_max_use` BIGINT DEFAULT NULL COMMENT '次数上限（null 表示不限）',
    `app_key_expire_time` DATETIME DEFAULT NULL COMMENT '过期时间（null 表示不限）',
    `app_key_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `app_key_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `app_key_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记'
) COMMENT='AI AppKey 表';

-- 5. 全局设置表
CREATE TABLE IF NOT EXISTS `ai_global_setting` (
    `setting_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设置主键',
    `setting_chat_client_enabled` INT DEFAULT 1 COMMENT '是否启用 ChatClient 聚合',
    `setting_usage_interval_sec` INT DEFAULT 60 COMMENT '用量自动刷新间隔(秒)',
    `setting_enable_usage_analysis` INT DEFAULT 1 COMMENT '是否启用用量分析',
    `setting_default_model` VARCHAR(255) DEFAULT NULL COMMENT '默认模型',
    `setting_temperature` DECIMAL(4,2) DEFAULT 0.70 COMMENT '默认温度',
    `setting_max_tokens` INT DEFAULT 2048 COMMENT '默认最大 tokens',
    `setting_timeout` INT DEFAULT 30 COMMENT '默认请求超时(秒)',
    `setting_enable_cache` INT DEFAULT 1 COMMENT '是否启用缓存',
    `setting_stream` INT DEFAULT 1 COMMENT '是否流式输出',
    `setting_update_time` DATETIME DEFAULT NULL COMMENT '更新时间'
) COMMENT='AI 全局设置表';

-- 6. 服务运行状态表
CREATE TABLE IF NOT EXISTS `ai_service_status` (
    `status_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '状态主键',
    `status_service_name` VARCHAR(255) NOT NULL COMMENT '服务名称',
    `status_running` INT DEFAULT 0 COMMENT '是否运行中',
    `status_pid` BIGINT DEFAULT NULL COMMENT '进程 PID',
    `status_start_time` DATETIME DEFAULT NULL COMMENT '启动时间',
    `status_uptime_sec` BIGINT DEFAULT 0 COMMENT '运行时长(秒)',
    `status_update_time` DATETIME DEFAULT NULL COMMENT '更新时间'
) COMMENT='AI 服务运行状态表';

-- 7. 用量统计表
CREATE TABLE IF NOT EXISTS `ai_usage` (
    `usage_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用量主键',
    `usage_model` VARCHAR(255) NOT NULL COMMENT '模型名称',
    `usage_app_key_id` BIGINT DEFAULT NULL COMMENT '关联的 AppKey ID',
    `usage_request_count` BIGINT DEFAULT 0 COMMENT '请求次数',
    `usage_prompt_tokens` BIGINT DEFAULT 0 COMMENT '提示 tokens',
    `usage_completion_tokens` BIGINT DEFAULT 0 COMMENT '生成 tokens',
    `usage_total_tokens` BIGINT DEFAULT 0 COMMENT '总 tokens',
    `usage_timestamp` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '统计时间戳'
) COMMENT='AI 用量统计表';

-- 8. 告警规则表
CREATE TABLE IF NOT EXISTS `ai_alert` (
    `alert_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '告警主键',
    `alert_name` VARCHAR(255) NOT NULL COMMENT '告警名称',
    `alert_app_key_id` BIGINT DEFAULT NULL COMMENT '关联 AppKey ID',
    `alert_condition` VARCHAR(128) NOT NULL COMMENT '告警条件',
    `alert_threshold` BIGINT NOT NULL COMMENT '告警阈值',
    `alert_channels` VARCHAR(512) DEFAULT NULL COMMENT '通知渠道',
    `alert_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `alert_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `alert_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `alert_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记'
) COMMENT='AI 告警规则表';
