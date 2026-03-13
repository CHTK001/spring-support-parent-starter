-- 创建数据转换规则表
-- 作者: System
-- 日期: 2026-03-09

CREATE TABLE monitor_sync_transform_rule (
    rule_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则ID',
    rule_name VARCHAR(255) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(50) NOT NULL COMMENT '规则类型: MAPPING/FILTER/MASKING/SCRIPT',
    rule_config TEXT NOT NULL COMMENT '规则配置JSON',
    rule_desc VARCHAR(500) COMMENT '规则描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='数据转换规则表';
