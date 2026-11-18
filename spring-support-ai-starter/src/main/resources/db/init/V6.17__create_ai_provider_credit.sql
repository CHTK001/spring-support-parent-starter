-- =====================================================================
-- 供应商余额快照表（V6.17）
--
-- 为什么建表：余额读数原先只存进程内 ConcurrentHashMap（CREDIT_CACHE），
-- 重启即失；时间轮定时刷新（需求④）也要求读数可持久化、可回看。
-- 本表按「供应商 + 余额密钥哈希」存每个 Key 的最新一次读数，不存密钥明文。
--
-- 写入口径：每次同步（手动 refresh 或时间轮）对该供应商先删后增，
-- 表内始终只有每个 Key 的最新快照；synced_at 记录本次回源时间。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_provider_credit` (
    `sys_ai_provider_credit_id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `sys_ai_provider_credit_provider_id` VARCHAR(128)  NOT NULL COMMENT '供应商 ID',
    `sys_ai_provider_credit_key_hash`    VARCHAR(64)   NOT NULL COMMENT '余额密钥哈希（SHA-256 十六进制，不存明文）',
    `sys_ai_provider_credit_key_mask`    VARCHAR(128)  NULL COMMENT '余额密钥掩码（展示用）',
    `sys_ai_provider_credit_ok`          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '本次是否查到余额',
    `sys_ai_provider_credit_remain`      DOUBLE        NULL COMMENT '剩余余额',
    `sys_ai_provider_credit_used`        DOUBLE        NULL COMMENT '已用余额',
    `sys_ai_provider_credit_total`       DOUBLE        NULL COMMENT '总额（remain+used）',
    `sys_ai_provider_credit_currency`    VARCHAR(16)   NULL COMMENT '币种（USD/CNY/%）',
    `sys_ai_provider_credit_source`      VARCHAR(40)   NULL COMMENT '命中的余额实现（host/openai/moss/...）',
    `sys_ai_provider_credit_parts`       TEXT          NULL COMMENT '多段读数 JSON（label/amount/currency）',
    `sys_ai_provider_credit_error`       VARCHAR(1000) NULL COMMENT '失败原因（ok=1 时为空）',
    `sys_ai_provider_credit_synced_at`   BIGINT        NULL COMMENT '本次回源时间（毫秒）',
    `create_time`                        BIGINT        NULL COMMENT '创建时间（毫秒）',
    `update_time`                        BIGINT        NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_provider_credit_id`),
    UNIQUE KEY `uk_ai_provider_credit` (`sys_ai_provider_credit_provider_id`, `sys_ai_provider_credit_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商余额快照（按 Key 持久化，时间轮定期刷新）';
