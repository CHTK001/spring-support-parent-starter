-- 为 sys_ai_provider 表添加 extra 扩展字段（幂等）
SET @dbname = DATABASE();
SET @tablename = 'sys_ai_provider';
SET @columnname = 'sys_ai_provider_extra';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `', @columnname, '` TEXT NULL COMMENT ''扩展配置JSON'' AFTER `sys_ai_provider_bedrock_config`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
