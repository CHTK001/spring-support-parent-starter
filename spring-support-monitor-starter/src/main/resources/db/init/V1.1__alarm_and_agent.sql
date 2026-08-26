-- =====================================================================
-- 监控模块增量迁移: 告警引擎 + Agent 推送 (spring-support-monitor-starter)
-- 目标库: MySQL 8.0+ / 兼容 H2 MySQL 模式
-- @author CH
-- @since 4.0.0.42
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 告警策略
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_policy (
    id                               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_policy_name     VARCHAR(64)  NOT NULL                COMMENT '策略名称',
    ms_monitor_alarm_policy_enabled  TINYINT      NOT NULL DEFAULT 1      COMMENT '启用状态 1启用 0停用',
    ms_monitor_alarm_policy_remark   VARCHAR(255) NULL                    COMMENT '备注',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警策略';

-- ---------------------------------------------------------------------
-- 2. 告警规则
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_rule (
    id                                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_rule_policy_id         BIGINT        NOT NULL              COMMENT '所属策略ID',
    ms_monitor_alarm_rule_monitor_id        BIGINT        NULL                  COMMENT '限定目标ID,空为全部目标',
    ms_monitor_alarm_rule_metric_name       VARCHAR(64)   NOT NULL              COMMENT '指标名',
    ms_monitor_alarm_rule_condition         VARCHAR(4)    NOT NULL DEFAULT 'GT' COMMENT '条件: GT/GE/EQ/LE/LT',
    ms_monitor_alarm_rule_threshold         DECIMAL(19,4) NOT NULL DEFAULT 0    COMMENT '阈值',
    ms_monitor_alarm_rule_consecutive_count INT           NOT NULL DEFAULT 1    COMMENT '连续触发次数',
    ms_monitor_alarm_rule_silence_minutes   INT           NOT NULL DEFAULT 10   COMMENT '静默期(分钟)',
    ms_monitor_alarm_rule_level             CHAR(2)       NULL DEFAULT 'P2'     COMMENT '级别: P0~P4',
    ms_monitor_alarm_rule_enabled           TINYINT       NOT NULL DEFAULT 1    COMMENT '启用状态 1启用 0停用',
    PRIMARY KEY (id),
    KEY idx_alarm_rule_policy (ms_monitor_alarm_rule_policy_id),
    KEY idx_alarm_rule_metric (ms_monitor_alarm_rule_metric_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警规则';

-- ---------------------------------------------------------------------
-- 3. 告警事件
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ms_monitor_alarm_event (
    id                                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    ms_monitor_alarm_event_monitor_id    BIGINT        NULL                  COMMENT '监控目标ID',
    ms_monitor_alarm_event_monitor_name  VARCHAR(64)   NULL                  COMMENT '目标名称(冗余)',
    ms_monitor_alarm_event_rule_id       BIGINT        NULL                  COMMENT '命中规则ID',
    ms_monitor_alarm_event_metric_name   VARCHAR(64)   NOT NULL              COMMENT '指标名',
    ms_monitor_alarm_event_alarm_value   DECIMAL(19,4) NULL                  COMMENT '实际值',
    ms_monitor_alarm_event_threshold     DECIMAL(19,4) NULL                  COMMENT '阈值',
    ms_monitor_alarm_event_level         CHAR(2)       NULL DEFAULT 'P2'     COMMENT '级别: P0~P4',
    ms_monitor_alarm_event_status        VARCHAR(12)   NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/PROCESSED/FALSE_ALARM',
    ms_monitor_alarm_event_handle_remark VARCHAR(512)  NULL                  COMMENT '处理备注',
    ms_monitor_alarm_event_trigger_time  DATETIME      NULL                  COMMENT '触发时间',
    ms_monitor_alarm_event_handle_time   DATETIME      NULL                  COMMENT '处理时间',
    PRIMARY KEY (id),
    KEY idx_alarm_event_monitor (ms_monitor_alarm_event_monitor_id),
    KEY idx_alarm_event_status (ms_monitor_alarm_event_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警事件';

-- ---------------------------------------------------------------------
-- 4. MsMonitor 追加探针标识列(Agent 推送鉴权)
-- ---------------------------------------------------------------------
ALTER TABLE ms_monitor ADD COLUMN IF NOT EXISTS ms_monitor_agent_key VARCHAR(64) NULL COMMENT '探针标识(Agent推送鉴权)';
