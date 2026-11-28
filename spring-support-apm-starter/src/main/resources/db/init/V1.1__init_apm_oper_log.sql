-- ============================================================
-- APM 操作日志：ApmDispatcher 回调默认 ApmOperLogSink 落库
-- 只记写操作，保留窗口由 ApmOperLogScheduler 滚动清理。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_apm_oper_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作码：CONFIG_UPDATE/DETECT',
    `action_name` VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`      VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（本次改动的设置项）',
    `detail`      TEXT         NULL COMMENT '补充说明',
    `success`     TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`     BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`    VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`   VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url` VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time` DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_apm_oper_log_time` (`create_time`),
    KEY `idx_ms_apm_oper_log_action` (`action`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'APM 操作日志';
