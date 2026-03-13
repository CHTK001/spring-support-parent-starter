-- 创建同步告警表
-- 作者: System
-- 日期: 2026-03-09

CREATE TABLE monitor_sync_alert (
    alert_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '告警ID',
    sync_task_id BIGINT NOT NULL COMMENT '任务ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型: ERROR/PERFORMANCE/MEMORY',
    alert_level VARCHAR(20) NOT NULL COMMENT '告警级别: INFO/WARNING/ERROR/CRITICAL',
    alert_message TEXT COMMENT '告警消息',
    alert_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    is_resolved TINYINT DEFAULT 0 COMMENT '是否已解决: 0否 1是',
    resolved_time DATETIME COMMENT '解决时间',
    INDEX idx_task_time (sync_task_id, alert_time),
    INDEX idx_resolved (is_resolved)
) COMMENT='同步告警表';
