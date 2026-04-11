SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'name'),
    'ALTER TABLE `server_host` CHANGE COLUMN `name` `server_name` VARCHAR(255) DEFAULT NULL COMMENT ''服务器名称''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'code'),
    'ALTER TABLE `server_host` CHANGE COLUMN `code` `server_code` VARCHAR(255) DEFAULT NULL COMMENT ''服务器编码''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'type'),
    'ALTER TABLE `server_host` CHANGE COLUMN `type` `server_type` VARCHAR(64) DEFAULT NULL COMMENT ''接入类型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'type'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `type` `server_type` VARCHAR(64) DEFAULT NULL COMMENT ''接入类型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
