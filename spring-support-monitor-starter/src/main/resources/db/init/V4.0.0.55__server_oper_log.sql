-- ============================================================
-- 服务管理节点操作日志（补丁脚本）
--
-- 为什么单独成文件而不并入 V2.1__init_all_monitor.sql：
--   INIT 脚本按「文件名+版本」记账，V2.1 在存量库里早已 success=true，
--   之后再改内容只会触发 checksum 告警并跳过执行 —— 新表永远建不出来。
--   本文件是 NORMAL 脚本，首次启动执行一次，之后稳定跳过。
--
-- 一行 = 一次操作，由 @SysLog 切面发事件、ServerOperLogListener 落库。
--   - node_id 非空：只有能定位到具体节点的操作才入账；
--   - node_name 冗余存一份：节点被改名/删除之后日志仍能读出当时动的是哪个；
--   - 保留窗口由 ServerOperLogScheduler 滚动清理，不在本表里做。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_server_oper_log`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `node_id`     BIGINT       NOT NULL COMMENT '节点 id（ms_server_node.id）',
    `node_name`   VARCHAR(128) NULL DEFAULT '' COMMENT '节点名称（冗余，节点删除后仍可读）',
    `action`      VARCHAR(64)  NOT NULL COMMENT '操作码：CREATE/UPDATE/HOT_UPDATE/FILTER_*/START/STOP/DELETE',
    `action_name` VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`      VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（协议与监听地址摘要），可空',
    `detail`      TEXT         NULL COMMENT '补充说明',
    `success`     TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`     BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`    VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`   VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url` VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time` DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_server_oper_log_node_time` (`node_id`, `create_time`),
    KEY `idx_ms_server_oper_log_action` (`node_id`, `action`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='服务管理节点操作日志';
