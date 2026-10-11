-- ============================================================
-- 数据源中心升级补丁（监控指标）
-- 模块: spring-support-datasource-hub-starter
-- 目录: db/migration —— framework（plugin.datasource.script.migration-scan-mode
--       驱动，默认 ALWAYS，即每次启动都扫；重复执行靠 continueOnError 吞掉）
-- ms_datasource_metric：数据源连通性指标采样点，一行 = 一次探测
--   - 采集是「有人看才采」：只有存在 SSE 订阅者的数据源才会被探测，
--     没人看的数据源不产生任何探测流量。这里存的是被观测过的那部分，
--     不是全量台账的健康档案 —— 两者的区别在应用侧也有对应说明。
--   - success 允许为 NULL：探测被跳过（例如该类型没有 probe 实现）时
--     记一条「未判定」而不是伪造一次成功，否则曲线会把「没测」画成「正常」。
--   - latency 存 BIGINT 而非 DOUBLE：毫秒级延迟的观测值本来就是整数，
--     用浮点存只会引入 0.1 这种不存在的精度。
-- 保留窗口：滚动清理由调度器负责（与 ms_monitor_nginx_metric 同一套做法）。
-- 幂等性：CREATE TABLE IF NOT EXISTS，重复执行不报错
-- ============================================================

CREATE TABLE IF NOT EXISTS `ms_datasource_metric`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`    BIGINT      NOT NULL COMMENT '数据源 id（ms_datasource.id）',
    `datasource_type`  VARCHAR(32) NULL DEFAULT '' COMMENT '数据类型（冗余，便于按类型聚合）',
    `success`          TINYINT     NULL COMMENT '1 连通 / 0 不通 / NULL 未判定',
    `connect_latency`  BIGINT      NULL COMMENT '建连耗时（毫秒）',
    `session_latency`  BIGINT      NULL COMMENT '取会话耗时（毫秒）',
    `tunneled`         TINYINT     NULL COMMENT '1 经 SSH 隧道 / 0 直连 / NULL 未判定',
    `error_message`    VARCHAR(512) NULL DEFAULT '' COMMENT '失败原因（成功时为空）',
    `create_time`      DATETIME    NOT NULL COMMENT '采样时刻',
    PRIMARY KEY (`id`),
    KEY `idx_ms_datasource_metric_ds_time` (`datasource_id`, `create_time`),
    KEY `idx_ms_datasource_metric_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源连通性指标采样';