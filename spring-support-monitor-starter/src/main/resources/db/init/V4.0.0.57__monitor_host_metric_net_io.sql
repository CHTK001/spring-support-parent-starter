-- ---------------------------------------------------------------------
-- V4.0.0.57: 主机网络收发列 - 存量库补列（历史宽表 + 台账）
-- 新库由 V2.1（ms_server_host / ms_host_metric_history）的全量建表覆盖，
-- 本文件仅对已有库补列。
--
-- 背景：V4.0.0.54 用 DROP/CREATE PROCEDURE + DELIMITER 做幂等判列，但
-- FlywayLike（FlywayScripts.splitStatements）只按语句外分号切分，不认
-- DELIMITER 与 BEGIN...END：整个过程体被切成碎片，每条都语法错，
-- continue-on-error 全部吞掉，脚本还被记成「已执行成功」，于是这些列
-- 在存量库上永远加不上。表现：
--   1) 底部指标条点开「网络收发」历史曲线恒空
--      GET /ms/monitor/host/{id}/metric-history
--      → [ts, cpu, mem, disk, read, write, null, null]
--   2) 台账若也缺列，socket 推送的 netRx/netTx 只能给 0
--
-- 写入侧列名口径（HostMetricHistoryServiceImpl → DataSourceSeriesEngine.writePoint
-- 把「表名_指标名」当列名）：
--   ms_host_metric_history_net_rx_kbs / ms_host_metric_history_net_tx_kbs
-- 列缺失时 INSERT 报错，记录侧退化成「不带网络列重试」，网络两列被静默丢弃。
--
-- 写法约束：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；存储过程 / DELIMITER
-- 又会被 FlywayLike 拆坏。这里改用 information_schema 判存在 + PREPARE
-- 执行动态 DDL：列已存在时执行 DO 0，脚本可重复执行并记为成功；
-- 不用存储过程，也不依赖 DELIMITER。
-- ---------------------------------------------------------------------

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_host_metric_history')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_host_metric_history'
             AND COLUMN_NAME = 'ms_host_metric_history_net_rx_kbs'),
    'ALTER TABLE `ms_host_metric_history` ADD COLUMN `ms_host_metric_history_net_rx_kbs` DOUBLE DEFAULT NULL',
    'DO 0'));
PREPARE ms_host_net_io_stmt FROM @ddl;
EXECUTE ms_host_net_io_stmt;
DEALLOCATE PREPARE ms_host_net_io_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_host_metric_history')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_host_metric_history'
             AND COLUMN_NAME = 'ms_host_metric_history_net_tx_kbs'),
    'ALTER TABLE `ms_host_metric_history` ADD COLUMN `ms_host_metric_history_net_tx_kbs` DOUBLE DEFAULT NULL',
    'DO 0'));
PREPARE ms_host_net_io_stmt FROM @ddl;
EXECUTE ms_host_net_io_stmt;
DEALLOCATE PREPARE ms_host_net_io_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_server_host')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_server_host'
             AND COLUMN_NAME = 'ms_server_host_net_rx_kbs'),
    'ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_net_rx_kbs` DECIMAL(12,2) NULL',
    'DO 0'));
PREPARE ms_host_net_io_stmt FROM @ddl;
EXECUTE ms_host_net_io_stmt;
DEALLOCATE PREPARE ms_host_net_io_stmt;

SET @ddl = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_server_host')
    AND NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'ms_server_host'
             AND COLUMN_NAME = 'ms_server_host_net_tx_kbs'),
    'ALTER TABLE `ms_server_host` ADD COLUMN `ms_server_host_net_tx_kbs` DECIMAL(12,2) NULL',
    'DO 0'));
PREPARE ms_host_net_io_stmt FROM @ddl;
EXECUTE ms_host_net_io_stmt;
DEALLOCATE PREPARE ms_host_net_io_stmt;
