-- ============================================================================
-- V1.3__add_model_aliases.sql
-- sys_ai_model 增加模型别名列（JSON 数组，请求时别名自动映射到主模型）
-- ============================================================================

SET @dbname = DATABASE();

SET @tablename = 'sys_ai_model';

SET @columnname = 'sys_ai_model_aliases';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` VARCHAR(512) DEFAULT NULL COMMENT ''模型别名(JSON数组)'' AFTER `sys_ai_model_cache_price`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
