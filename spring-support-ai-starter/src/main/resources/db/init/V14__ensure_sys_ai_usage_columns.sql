-- V14__ensure_sys_ai_usage_columns.sql
-- 补充 sys_ai_usage 扩展列（原 V10 被重构删除、V1 未包含，既有库缺失导致列表/统计查询报 Unknown column）
-- 注意：不使用存储过程（Spring ScriptUtils 无法解析 BEGIN...END），改为 PREPARE/EXECUTE 动态 SQL 实现 IF NOT EXISTS 幂等语义

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_inference_strength';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_inference_strength` VARCHAR(32) NULL COMMENT ''推理强度'' AFTER `sys_ai_usage_call_type`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_endpoint';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_endpoint` VARCHAR(255) NULL COMMENT ''端点URL'' AFTER `sys_ai_usage_inference_strength`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_ip';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_ip` VARCHAR(64) NULL COMMENT ''请求IP'' AFTER `sys_ai_usage_endpoint`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_type';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_type` VARCHAR(32) NULL COMMENT ''请求类型: chat/completion/embedding'' AFTER `sys_ai_usage_ip`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_billing_mode';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_billing_mode` VARCHAR(32) NULL COMMENT ''计费模式: pay_as_you_go/subscription'' AFTER `sys_ai_usage_type`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_cached_tokens';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cached_tokens` INT NULL COMMENT ''缓存读取 Token'' AFTER `sys_ai_usage_total_cost`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_cached_cost';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cached_cost` DOUBLE NULL COMMENT ''缓存读取成本'' AFTER `sys_ai_usage_cached_tokens`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_input_price_per_1m';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_input_price_per_1m` DOUBLE NULL COMMENT ''输入单价(每1M Token)'' AFTER `sys_ai_usage_cached_cost`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_output_price_per_1m';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_output_price_per_1m` DOUBLE NULL COMMENT ''输出单价(每1M Token)'' AFTER `sys_ai_usage_input_price_per_1m`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_cache_read_price_per_1m';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_cache_read_price_per_1m` DOUBLE NULL COMMENT ''缓存读取单价(每1M Token)'' AFTER `sys_ai_usage_output_price_per_1m`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_service_tier';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_service_tier` VARCHAR(32) NULL COMMENT ''服务档位'' AFTER `sys_ai_usage_cache_read_price_per_1m`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_ratio';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_ratio` DOUBLE NULL COMMENT ''倍率'' AFTER `sys_ai_usage_service_tier`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_original_cost';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_original_cost` DOUBLE NULL COMMENT ''原始费用(未折扣)'' AFTER `sys_ai_usage_ratio`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_first_token_latency';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_first_token_latency` BIGINT NULL COMMENT ''首字延迟(ms)'' AFTER `sys_ai_usage_original_cost`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT COUNT(*) INTO @v14_exist FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_ai_usage' AND column_name = 'sys_ai_usage_total_latency';
SET @v14_sql = IF(@v14_exist = 0, 'ALTER TABLE `sys_ai_usage` ADD COLUMN `sys_ai_usage_total_latency` BIGINT NULL COMMENT ''总耗时(ms)'' AFTER `sys_ai_usage_first_token_latency`', 'SELECT 1');
PREPARE v14_stmt FROM @v14_sql; EXECUTE v14_stmt; DEALLOCATE PREPARE v14_stmt;

SELECT 1;
