-- ---------------------------------------------------------------------
-- V4.0.0.58: 告警策略归属主机列 - 修复 V4.0.0.53 未生效
--
-- 背景：V4.0.0.53 用 DROP/CREATE PROCEDURE + DELIMITER 做幂等判列，
-- 但 FlywayLike（FlywayScripts.splitStatements）只按语句外分号切分，不认
-- DELIMITER 与 BEGIN...END：过程体被切成碎片，每条语法错，continue-on-error
-- 吞掉后脚本仍被记为成功，导致 ms_monitor_alarm_policy.ms_server_host_id
-- 在存量库上缺失，告警规则缓存加载报 Unknown column。
--
-- 写法约束：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；存储过程 / DELIMITER
-- 会被 FlywayLike 拆坏。这里改用 information_schema 判存在 + PREPARE
-- 执行动态 DDL：列已存在时执行 DO 0，脚本可重复执行并记为成功。
-- ---------------------------------------------------------------------

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_monitor_alarm_policy')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_monitor_alarm_policy'
             AND COLUMN_NAME = 'ms_server_host_id'),
    'ALTER TABLE `ms_monitor_alarm_policy` ADD COLUMN `ms_server_host_id` BIGINT NULL COMMENT ''归属主机ID,空为全局策略''',
    'DO 0'));
PREPARE ms_alarm_policy_host_stmt FROM @ddl;
EXECUTE ms_alarm_policy_host_stmt;
DEALLOCATE PREPARE ms_alarm_policy_host_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_monitor_alarm_policy')
    AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_monitor_alarm_policy'
             AND INDEX_NAME = 'idx_ms_monitor_alarm_policy_host'),
    'ALTER TABLE `ms_monitor_alarm_policy` ADD KEY `idx_ms_monitor_alarm_policy_host` (`ms_server_host_id`)',
    'DO 0'));
PREPARE ms_alarm_policy_host_idx_stmt FROM @ddl;
EXECUTE ms_alarm_policy_host_idx_stmt;
DEALLOCATE PREPARE ms_alarm_policy_host_idx_stmt;
