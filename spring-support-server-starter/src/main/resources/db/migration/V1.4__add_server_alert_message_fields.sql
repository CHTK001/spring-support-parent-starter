SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_alert_setting')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_alert_setting' AND COLUMN_NAME = 'server_alert_message_enabled'),
    'ALTER TABLE `server_alert_setting` ADD COLUMN `server_alert_message_enabled` TINYINT(1) DEFAULT 0 COMMENT ''是否同步消息中心'' AFTER `server_alert_enabled`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
