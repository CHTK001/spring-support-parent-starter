-- =====================================================================
-- V5.7__add_channel_health.sql
-- 通道: INIT 建表
-- 说明: 渠道健康探针（对标 Sub2API Channel Monitor）：以「提供者 + 连接」为粒度，
--   对每个渠道发轻量探针（OAuth 渠道走 `AccountChannelAuthManager.status()` / `checkin()`，
--   API Key 渠道走 `GET {baseUrl}/v1/models`），记录成功/失败/延迟，透析状态机
--   给路由（连续失败 → DOWN，连续成功 → 恢复），供网关鉴权/熔断/计费层消费。
-- =====================================================================
CREATE TABLE IF NOT EXISTS `sys_ai_channel_health` (
    `sys_ai_channel_health_id` VARCHAR(36) NOT NULL COMMENT '健康记录标识',
    `provider_id` VARCHAR(64) NOT NULL COMMENT '提供商标识（UUID）',
    `connection_id` VARCHAR(36) NOT NULL COMMENT '连接标识（UUID）',
    `channel` VARCHAR(64) NOT NULL COMMENT '渠道标识（平台，如 openai/anthropic/google）',
    `label` VARCHAR(128) DEFAULT NULL COMMENT '渠道展示名',
    `state` VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态 (UNKNOWN/ONLINE/WARNING/DOWN/RATE_LIMITED)',
    `failures` BIGINT NOT NULL DEFAULT 0 COMMENT '累计失败次数',
    `successes` BIGINT NOT NULL DEFAULT 0 COMMENT '累计成功次数',
    `consecutive_failures` INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
    `consecutive_successes` INT NOT NULL DEFAULT 0 COMMENT '连续成功次数',
    `last_probe_at` BIGINT DEFAULT NULL COMMENT '最近探针时间（毫秒）',
    `last_probe_status` INT DEFAULT NULL COMMENT '最近探针 HTTP 状态码',
    `last_probe_latency` INT DEFAULT NULL COMMENT '最近探针延迟（毫秒）',
    `last_probe_error` VARCHAR(512) DEFAULT NULL COMMENT '最近探针错误信息',
    `average_latency` INT DEFAULT NULL COMMENT '平均延迟（毫秒）',
    `total_failures` BIGINT DEFAULT 0 COMMENT '累计失败次数（计费/熔断口径）',
    `interval_seconds` INT NOT NULL DEFAULT 0 COMMENT '探针间隔（秒，0=走全局设置）',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用探针',
    `created_at` BIGINT DEFAULT NULL COMMENT '创建时间（毫秒）',
    `updated_at` BIGINT DEFAULT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_channel_health_id`),
    UNIQUE KEY `uk_provider_connection` (`provider_id`, `connection_id`),
    KEY `idx_state` (`state`),
    KEY `idx_channel` (`channel`),
    KEY `idx_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道健康探针记录';
