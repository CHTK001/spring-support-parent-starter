-- ============================================================================
-- V1.2__add_model_pipeline_and_usage_sync_log.sql
-- 1) sys_ai_model 增加必填/非必填管线列（JSON 数组）
-- 2) 新增 sys_ai_usage_sync_log 用量同步日志表
-- ============================================================================

-- 1. 模型管线列
SET @dbname = DATABASE();

SET @tablename = 'sys_ai_model';

SET @columnname = 'sys_ai_model_required_pipelines';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` VARCHAR(512) DEFAULT NULL COMMENT ''必填管线(JSON)'' AFTER `sys_ai_model_capabilities`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'sys_ai_model_optional_pipelines';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` VARCHAR(512) DEFAULT NULL COMMENT ''非必填管线(JSON)'' AFTER `sys_ai_model_required_pipelines`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 2. 用量同步日志表
CREATE TABLE IF NOT EXISTS `sys_ai_usage_sync_log` (
    `sys_ai_usage_sync_log_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sys_ai_usage_sync_log_level` VARCHAR(16)  NULL DEFAULT 'INFO' COMMENT '日志级别 INFO/WARN/ERROR',
    `sys_ai_usage_sync_log_source` VARCHAR(64) NULL DEFAULT 'schedule' COMMENT '来源（schedule/manual/parser）',
    `sys_ai_usage_sync_log_message` TEXT       NULL COMMENT '日志内容',
    `create_time`                  DATETIME    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`sys_ai_usage_sync_log_id`),
    KEY `idx_usage_sync_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 用量同步日志';
