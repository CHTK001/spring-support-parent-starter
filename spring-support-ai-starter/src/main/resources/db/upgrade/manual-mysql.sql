-- =====================================================================
-- manual-mysql.sql - 存量老库手工升级脚本
-- 本目录不在 DEFAULT_SCRIPT_PATH（db/init、db/migration、db/sync）扫描范围内，不会被自动执行。
-- 新库请直接执行 db/init/V1.0__init_all.sql，下列列/索引已并入其中，勿重复执行。
-- 老库（表已存在但缺下列列/索引）按顺序手工执行本文件。
-- 由 .tmp/fold_generic_ddl.py 自动归并生成。
-- =====================================================================

ALTER TABLE `sys_ai_provider` ADD COLUMN `sys_ai_provider_type` VARCHAR(64) NULL DEFAULT 'custom' COMMENT '供应商类型（openai/anthropic/gemini/qwen/custom）';
ALTER TABLE `sys_ai_provider` ADD COLUMN `sys_ai_provider_api_key` VARCHAR(512) NULL COMMENT '提供商 API Key';
ALTER TABLE `sys_ai_provider` ADD COLUMN `sys_ai_provider_logo` VARCHAR(1024) NULL COMMENT 'Logo 图片地址';
ALTER TABLE `sys_ai_provider` ADD COLUMN `sys_ai_provider_models` TEXT NULL COMMENT '可用模型列表（JSON 字符串）';
ALTER TABLE `sys_ai_provider` ADD COLUMN `sys_ai_provider_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记';
ALTER TABLE `sys_ai_provider` ADD COLUMN `create_name` VARCHAR(64) NULL COMMENT '创建人姓名';
ALTER TABLE `sys_ai_provider` ADD COLUMN `create_by` INT NULL COMMENT '创建人';
ALTER TABLE `sys_ai_provider` ADD COLUMN `create_time` DATETIME NULL COMMENT '创建时间';
ALTER TABLE `sys_ai_provider` ADD COLUMN `update_name` VARCHAR(64) NULL COMMENT '更新人姓名';
ALTER TABLE `sys_ai_provider` ADD COLUMN `update_by` INT NULL COMMENT '更新人';
ALTER TABLE `sys_ai_provider` ADD COLUMN `update_time` DATETIME NULL COMMENT '更新时间';
ALTER TABLE `sys_ai_bot_binding` ADD COLUMN `sys_ai_bot_binding_record_usage` TINYINT NOT NULL DEFAULT 1 COMMENT '是否记录AI用量（令牌/费用）';
ALTER TABLE `sys_ai_session` ADD COLUMN `is_archived` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已归档';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_inference_strength` VARCHAR(32) NULL COMMENT '推理强度';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_endpoint` VARCHAR(255) NULL COMMENT '端点URL';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_ip` VARCHAR(64) NULL COMMENT '请求IP';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_location` VARCHAR(128) NULL COMMENT 'IP物理地址（省/市，来自IP定位）';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_type` VARCHAR(32) NULL COMMENT '请求类型: chat/completion/embedding';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_billing_mode` VARCHAR(32) NULL COMMENT '计费模式: pay_as_you_go/subscription';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cached_tokens` INT NULL COMMENT '缓存读取 Token';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cached_cost` DOUBLE NULL COMMENT '缓存读取成本';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_input_price_per_1m` DOUBLE NULL COMMENT '输入单价(每1M Token)';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_output_price_per_1m` DOUBLE NULL COMMENT '输出单价(每1M Token)';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cache_read_price_per_1m` DOUBLE NULL COMMENT '缓存读取单价(每1M Token)';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_service_tier` VARCHAR(32) NULL COMMENT '服务档位';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_ratio` DOUBLE NULL COMMENT '倍率';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_original_cost` DOUBLE NULL COMMENT '原始费用(未折扣)';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_first_token_latency` BIGINT NULL COMMENT '首字延迟(ms)';
ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_total_latency` BIGINT NULL COMMENT '总耗时(ms)';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN `sys_ai_usage_daily_total_cached_tokens` INT DEFAULT 0 COMMENT '缓存读取 Token 合计';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN `sys_ai_usage_daily_total_latency` BIGINT DEFAULT 0 COMMENT '总耗时(ms)合计';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN `sys_ai_usage_daily_standard_cost` DOUBLE DEFAULT 0 COMMENT '标准费用(官方价格)';
ALTER TABLE `sys_ai_usage_daily` ADD COLUMN `sys_ai_usage_daily_total_first_token_latency` BIGINT DEFAULT 0 COMMENT '总首字延迟(ms)合计';
ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_required_pipelines` VARCHAR(512) DEFAULT NULL COMMENT '必填管线(JSON)';
ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_optional_pipelines` VARCHAR(512) DEFAULT NULL COMMENT '非必填管线(JSON)';
ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_aliases` VARCHAR(512) DEFAULT NULL COMMENT '模型别名(JSON数组)';
ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_last_latency` BIGINT DEFAULT NULL COMMENT '最近一次连通性测试延迟(毫秒)';
ALTER TABLE `sys_ai_model` ADD COLUMN `sys_ai_model_last_test_time` DATETIME DEFAULT NULL COMMENT '最近一次连通性测试时间';
