-- V11__ensure_missing_schema.sql
-- 补充 V1 重构后缺失的表与列（既有库因 ONCE 模式跳过了 V1，需增量补齐）

-- 1. sys_ai_requirement 表（V1 新增表，既有库未创建）
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

-- 2. sys_ai_usage 扩展列（原 V10 已删除，V1 未包含，需增量补充）
DROP PROCEDURE IF EXISTS `migrate_v11_usage_columns`;
CREATE PROCEDURE `migrate_v11_usage_columns`()
BEGIN
    DECLARE col_exists INT DEFAULT 0;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_inference_strength');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_inference_strength` VARCHAR(32) NULL COMMENT '推理强度' AFTER `sys_ai_usage_call_type`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_endpoint');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_endpoint` VARCHAR(255) NULL COMMENT '端点URL' AFTER `sys_ai_usage_inference_strength`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_ip');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_ip` VARCHAR(64) NULL COMMENT '请求IP' AFTER `sys_ai_usage_endpoint`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_type');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_type` VARCHAR(32) NULL COMMENT '请求类型: chat/completion/embedding' AFTER `sys_ai_usage_ip`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_billing_mode');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_billing_mode` VARCHAR(32) NULL COMMENT '计费模式: pay_as_you_go/subscription' AFTER `sys_ai_usage_type`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_cached_tokens');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cached_tokens` INT NULL COMMENT '缓存读取 Token' AFTER `sys_ai_usage_total_cost`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_cached_cost');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cached_cost` DOUBLE NULL COMMENT '缓存读取成本' AFTER `sys_ai_usage_cached_tokens`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_input_price_per_1m');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_input_price_per_1m` DOUBLE NULL COMMENT '输入单价(每1M Token)' AFTER `sys_ai_usage_cached_cost`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_output_price_per_1m');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_output_price_per_1m` DOUBLE NULL COMMENT '输出单价(每1M Token)' AFTER `sys_ai_usage_input_price_per_1m`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_cache_read_price_per_1m');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cache_read_price_per_1m` DOUBLE NULL COMMENT '缓存读取单价(每1M Token)' AFTER `sys_ai_usage_output_price_per_1m`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_service_tier');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_service_tier` VARCHAR(32) NULL COMMENT '服务档位' AFTER `sys_ai_usage_cache_read_price_per_1m`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_ratio');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_ratio` DOUBLE NULL COMMENT '倍率' AFTER `sys_ai_usage_service_tier`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_original_cost');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_original_cost` DOUBLE NULL COMMENT '原始费用(未折扣)' AFTER `sys_ai_usage_ratio`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_first_token_latency');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_first_token_latency` BIGINT NULL COMMENT '首字延迟(ms)' AFTER `sys_ai_usage_original_cost`;
    END IF;
    SET col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_total_latency');
    IF col_exists = 0 THEN
        ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_total_latency` BIGINT NULL COMMENT '总耗时(ms)' AFTER `sys_ai_usage_first_token_latency`;
    END IF;
END;
CALL `migrate_v11_usage_columns`();
DROP PROCEDURE IF EXISTS `migrate_v11_usage_columns`;

SELECT 1;
