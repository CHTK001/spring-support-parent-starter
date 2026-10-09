-- ============================================================
-- 数据源中心升级补丁（Prometheus 自定义看板）
-- 模块: spring-support-datasource-hub-starter
-- 目录: db/migration —— 由 framework 的 plugin.datasource.script.migration-scan-mode
--       驱动，默认 ALWAYS，即每次启动都扫；重复执行靠 continueOnError 吞掉。
--
-- 为什么放 migration 而不是 init：init 只对空库执行一次，存量环境永远见不到这张表。
--
-- 表 ms_prometheus_dashboard：一行 = 一个 Prometheus 数据源的自定义布局看板。
--   - 布局形状由前端定义（卡片 + 位置 + 卡片配置），后端只存 JSON 文本，不解析；
--   - embed_token 供「生成给别的系统嵌入」的公开只读链接使用，一个看板一个令牌；
--   - datasource_id 唯一：当前模型是一个数据源一份看板，多份需要再来一轮设计。
--
-- 幂等性：CREATE TABLE IF NOT EXISTS，重复执行不报错。
-- ============================================================

CREATE TABLE IF NOT EXISTS `ms_prometheus_dashboard`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`    BIGINT       NOT NULL COMMENT '数据源 id（ms_datasource.id，prometheus 类型）',
    `dashboard_name`   VARCHAR(128) NULL DEFAULT '' COMMENT '看板名称',
    `dashboard_config` LONGTEXT     NULL COMMENT '看板布局配置（JSON 文本：widgets/grid/卡片配置）',
    `embed_token`      VARCHAR(64)  NULL DEFAULT '' COMMENT '嵌入令牌（公开只读链接用，随机十六进制）',
    `create_time`      DATETIME     NULL COMMENT '创建时间',
    `update_time`      DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_prometheus_dashboard_ds` (`datasource_id`),
    KEY `idx_ms_prometheus_dashboard_token` (`embed_token`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'Prometheus 自定义看板（布局 JSON + 嵌入令牌）';
