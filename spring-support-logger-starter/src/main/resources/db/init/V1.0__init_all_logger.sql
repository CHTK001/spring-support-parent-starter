-- ----------------------------------------------------------
-- logger starter：日志事件速率时序表
-- KvLoggerEventStore 经 DataSourceSeriesEngine 以
-- target=logger_eps / metric=count 写入并查询每秒事件速率曲线，
-- 列名遵循 DataSourceSeriesEngine 的「表名前缀 + 字段名」规约
-- （{target}_monitor_id / {target}_count / {target}_collect_time）。
-- CREATE TABLE IF NOT EXISTS：存量库重复执行安全。
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `logger_eps` (
    `id`                      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `logger_eps_monitor_id`   BIGINT   NOT NULL               COMMENT '应用标识(应用名hash)',
    `logger_eps_count`        DOUBLE   DEFAULT NULL           COMMENT '每秒事件数',
    `logger_eps_collect_time` DATETIME NOT NULL               COMMENT '采集时间',
    PRIMARY KEY (`id`),
    KEY `logger_eps_monitor_time` (`logger_eps_monitor_id`, `logger_eps_collect_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日志事件速率时序';
