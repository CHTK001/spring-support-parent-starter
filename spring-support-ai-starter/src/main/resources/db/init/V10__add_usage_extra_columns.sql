-- V10__add_usage_extra_columns.sql
-- 补充 sys_ai_usage 表的额外字段，支持前端用量库详情展示

ALTER TABLE `sys_ai_usage`
    ADD COLUMN `sys_ai_usage_inference_strength` VARCHAR(32) NULL COMMENT '推理强度' AFTER `sys_ai_usage_call_type`,
    ADD COLUMN `sys_ai_usage_endpoint` VARCHAR(255) NULL COMMENT '端点URL' AFTER `sys_ai_usage_inference_strength`,
    ADD COLUMN `sys_ai_usage_ip` VARCHAR(64) NULL COMMENT '请求IP' AFTER `sys_ai_usage_endpoint`,
    ADD COLUMN `sys_ai_usage_type` VARCHAR(32) NULL COMMENT '请求类型: chat/completion/embedding' AFTER `sys_ai_usage_ip`,
    ADD COLUMN `sys_ai_usage_billing_mode` VARCHAR(32) NULL COMMENT '计费模式: pay_as_you_go/subscription' AFTER `sys_ai_usage_type`,
    ADD COLUMN `sys_ai_usage_cached_tokens` INT NULL COMMENT '缓存读取 Token' AFTER `sys_ai_usage_total_cost`,
    ADD COLUMN `sys_ai_usage_cached_cost` DOUBLE NULL COMMENT '缓存读取成本' AFTER `sys_ai_usage_cached_tokens`,
    ADD COLUMN `sys_ai_usage_input_price_per_1m` DOUBLE NULL COMMENT '输入单价(每1M Token)' AFTER `sys_ai_usage_cached_cost`,
    ADD COLUMN `sys_ai_usage_output_price_per_1m` DOUBLE NULL COMMENT '输出单价(每1M Token)' AFTER `sys_ai_usage_input_price_per_1m`,
    ADD COLUMN `sys_ai_usage_cache_read_price_per_1m` DOUBLE NULL COMMENT '缓存读取单价(每1M Token)' AFTER `sys_ai_usage_output_price_per_1m`,
    ADD COLUMN `sys_ai_usage_service_tier` VARCHAR(32) NULL COMMENT '服务档位' AFTER `sys_ai_usage_cache_read_price_per_1m`,
    ADD COLUMN `sys_ai_usage_ratio` DOUBLE NULL COMMENT '倍率' AFTER `sys_ai_usage_service_tier`,
    ADD COLUMN `sys_ai_usage_original_cost` DOUBLE NULL COMMENT '原始费用(未折扣)' AFTER `sys_ai_usage_ratio`,
    ADD COLUMN `sys_ai_usage_first_token_latency` BIGINT NULL COMMENT '首字延迟(ms)' AFTER `sys_ai_usage_original_cost`,
    ADD COLUMN `sys_ai_usage_total_latency` BIGINT NULL COMMENT '总耗时(ms)' AFTER `sys_ai_usage_first_token_latency`;

CREATE INDEX `idx_sys_ai_usage_call_time` ON `sys_ai_usage` (`sys_ai_usage_call_time`);
CREATE INDEX `idx_sys_ai_usage_provider` ON `sys_ai_usage` (`sys_ai_usage_provider`);
CREATE INDEX `idx_sys_ai_usage_model` ON `sys_ai_usage` (`sys_ai_usage_model`);
CREATE INDEX `idx_sys_ai_usage_group` ON `sys_ai_usage` (`sys_ai_usage_group_name`);