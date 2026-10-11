-- ============================================================
-- 数据源中心升级补丁（操作日志）
-- 模块: spring-support-datasource-hub-starter
-- 目录: db/migration —— framework（plugin.datasource.script.migration-scan-mode
--       驱动，默认 ALWAYS，即每次启动都扫；重复执行靠 continueOnError 吞掉）
-- 为什么必须落这张表：@SysLog 切面把 SysLoggerInfo 通过 ApplicationContext
--       发成事件，但全仓没有任何监听者 —— 事件被发出去就没人接。查询端
--       GET /v2/log/page 也找不到对应表，实测稳定返回
--       S0503C0000「资源不存在」。也就是说「操作日志」这个功能此前
--       既不写、也不读，只有一个入口。
-- ms_datasource_oper_log：数据源中心的操作留痕，一行 = 一次操作
--   - datasource_id 非空：只有能定位到具体数据源的操作才入账（全中心的
--     「列出数据源」这类查询不入账，否则日志里全是自己看的噪音）
--   - datasource_name 冗余存一份：数据源被删掉之后，日志仍要能读出
--     「当时动的是哪个」，否则改名/删除会让历史日志失去主语
--   - 保留窗口由调度器按 ms_exec_log 的做法滚动清理，不在本表里做
-- 幂等性：CREATE TABLE IF NOT EXISTS，重复执行不报错
-- ============================================================

CREATE TABLE IF NOT EXISTS `ms_datasource_oper_log`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`  BIGINT       NOT NULL COMMENT '数据源 id（ms_datasource.id）',
    `datasource_name` VARCHAR(128) NULL DEFAULT '' COMMENT '数据源名称（冗余，数据源删除后仍可读）',
    `action`         VARCHAR(64)  NOT NULL COMMENT '操作码：CREATE/UPDATE/DELETE/TEST/TUNNEL_* /CONSOLE_* 等',
    `action_name`    VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`         VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（库名/表名/隧道名），可空',
    `detail`         TEXT         NULL COMMENT '补充说明',
    `success`        TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`        BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`       VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`      VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url`    VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time`    DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_datasource_oper_log_ds_time` (`datasource_id`, `create_time`),
    KEY `idx_ms_datasource_oper_log_action` (`datasource_id`, `action`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源中心操作日志';