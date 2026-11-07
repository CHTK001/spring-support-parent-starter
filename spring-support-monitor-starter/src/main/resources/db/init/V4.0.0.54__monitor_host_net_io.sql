-- 主机网络收发速率：台账补列 + 指标历史宽表补列（幂等，可重复执行）。
--
-- 背景：底部状态条要求实时展示「磁盘 IO / CPU / 网络 IO」，其中网络收发此前
-- 只在监控画像接口（HostInfoVO/HostMetricsVO 的 networkRxKb/networkTxKb）里
-- 出现过，台账与历史表都没有落库列，于是 socket 推送只能给 0。
--
-- 列名必须与后端字段严格对应：
--   台账   ms_server_host.serverHostNetRxKbs   → ms_server_host_net_rx_kbs
--   历史   HostMetricHistoryServiceImpl 的指标名 net_rx_kbs
--          → SeriesEngine 拼成 ms_host_metric_history_net_rx_kbs
DROP PROCEDURE IF EXISTS ms_host_net_io_migrate;
DELIMITER $$
CREATE PROCEDURE ms_host_net_io_migrate()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ms_server_host'
                     AND COLUMN_NAME = 'ms_server_host_net_rx_kbs') THEN
        ALTER TABLE `ms_server_host`
            ADD COLUMN `ms_server_host_net_rx_kbs` DECIMAL(12,2) NULL COMMENT '网络接收速率 KB/s(探测结果)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'ms_server_host'
                     AND COLUMN_NAME = 'ms_server_host_net_tx_kbs') THEN
        ALTER TABLE `ms_server_host`
            ADD COLUMN `ms_server_host_net_tx_kbs` DECIMAL(12,2) NULL COMMENT '网络发送速率 KB/s(探测结果)';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'ms_host_metric_history')
       AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'ms_host_metric_history'
                         AND COLUMN_NAME = 'ms_host_metric_history_net_rx_kbs') THEN
        ALTER TABLE `ms_host_metric_history`
            ADD COLUMN `ms_host_metric_history_net_rx_kbs` DOUBLE DEFAULT NULL COMMENT '网络接收(KB/s)';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'ms_host_metric_history')
       AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'ms_host_metric_history'
                         AND COLUMN_NAME = 'ms_host_metric_history_net_tx_kbs') THEN
        ALTER TABLE `ms_host_metric_history`
            ADD COLUMN `ms_host_metric_history_net_tx_kbs` DOUBLE DEFAULT NULL COMMENT '网络发送(KB/s)';
    END IF;
END$$
DELIMITER ;
CALL ms_host_net_io_migrate();
DROP PROCEDURE IF EXISTS ms_host_net_io_migrate;
