-- 告警策略归属主机：存量库补列 + 补索引（幂等，可重复执行）。
-- MySQL 的 SET 变量写法里不要出现 PowerShell 的 at 别名占位（历史事故：被替换成字母 at 导致语法错误），
-- 这里直接用存储过程风格的条件执行，避免变量名。
DROP PROCEDURE IF EXISTS ms_alarm_policy_host_migrate;
DELIMITER $$
CREATE PROCEDURE ms_alarm_policy_host_migrate()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ms_monitor_alarm_policy'
                     AND COLUMN_NAME = 'ms_server_host_id') THEN
        ALTER TABLE `ms_monitor_alarm_policy`
            ADD COLUMN `ms_server_host_id` BIGINT NULL COMMENT '归属主机ID,空为全局策略';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ms_monitor_alarm_policy'
                     AND INDEX_NAME = 'idx_ms_monitor_alarm_policy_host') THEN
        ALTER TABLE `ms_monitor_alarm_policy`
            ADD KEY `idx_ms_monitor_alarm_policy_host` (`ms_server_host_id`);
    END IF;
END$$
DELIMITER ;
CALL ms_alarm_policy_host_migrate();
DROP PROCEDURE IF EXISTS ms_alarm_policy_host_migrate;
