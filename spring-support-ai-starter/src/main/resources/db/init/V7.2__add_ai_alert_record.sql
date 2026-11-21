-- =============================================================================
-- V7.2 — 告警规则推送：sys_ai_alert_record 触发留痕表
-- 背景：告警规则（sys_ai_alert）此前只有规则定义，定时评估触发后没有留痕，
--       无法回答「这条规则今天推没推过、实际值是多少」。本表在投递成功后
--       追加一行，并以 (alert_id, stat_date) 唯一键做「同规则同日只推一次」
--       的去重闸；投递失败不写——下轮评估重试。
-- 幂等纪律：CREATE TABLE IF NOT EXISTS，可反复执行。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_alert_record` (
    `sys_ai_alert_record_id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `sys_ai_alert_record_alert_id`      BIGINT        NOT NULL COMMENT '告警规则 ID（sys_ai_alert.sys_ai_alert_id）',
    `sys_ai_alert_record_alert_name`    VARCHAR(128)  NULL COMMENT '规则名称快照',
    `sys_ai_alert_record_stat_date`     DATE          NOT NULL COMMENT '统计日期（同规则同日唯一）',
    `sys_ai_alert_record_metric`        VARCHAR(32)   NULL COMMENT '触发指标（daily_cost / daily_tokens / daily_requests / error_rate）',
    `sys_ai_alert_record_threshold`     BIGINT        NULL COMMENT '触发时阈值快照',
    `sys_ai_alert_record_actual`        DOUBLE        NULL COMMENT '触发时实际值',
    `sys_ai_alert_record_scope`         VARCHAR(128)  NULL COMMENT '告警范围（全局 / appKey 名称）',
    `sys_ai_alert_record_receivers`     VARCHAR(500)  NULL COMMENT '接收人用户 ID（逗号分隔，空=全局广播）',
    `sys_ai_alert_record_channels`      VARCHAR(128)  NULL COMMENT '送达渠道（inbox=站内信 / email=邮件 / webhook=机器人 Webhook，逗号分隔）',
    `sys_ai_alert_record_title`         VARCHAR(200)  NULL COMMENT '消息标题（与推送进消息中心的标题一致）',
    `sys_ai_alert_record_content`       VARCHAR(1000) NULL COMMENT '消息摘要（与推送进消息中心的内容一致）',
    `sys_ai_alert_record_pushed_at`     BIGINT        NOT NULL COMMENT '推送时间（毫秒）',
    `create_time`                       BIGINT        NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`sys_ai_alert_record_id`),
    UNIQUE KEY `uk_ai_alert_record_rule_date` (`sys_ai_alert_record_alert_id`, `sys_ai_alert_record_stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 告警规则触发留痕（投递成功后追加一行，同规则同日唯一）';
