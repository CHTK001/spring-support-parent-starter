SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `server_service_operation_log` (
  `server_service_operation_log_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器服务操作日志ID',
  `server_service_id` INT DEFAULT NULL COMMENT '服务器服务ID',
  `server_id` INT DEFAULT NULL COMMENT '服务器ID',
  `server_operation_type` VARCHAR(64) DEFAULT NULL COMMENT '操作类型',
  `server_operation_success` TINYINT(1) DEFAULT 0 COMMENT '是否成功',
  `server_exit_code` INT DEFAULT NULL COMMENT '退出码',
  `server_runtime_status` VARCHAR(64) DEFAULT NULL COMMENT '运行状态',
  `server_operation_message` VARCHAR(2048) DEFAULT NULL COMMENT '操作说明',
  `server_operation_output` LONGTEXT DEFAULT NULL COMMENT '操作输出',
  `server_ai_reason` LONGTEXT DEFAULT NULL COMMENT 'AI失败原因',
  `server_ai_solution` LONGTEXT DEFAULT NULL COMMENT 'AI处理方案',
  `server_ai_fix_script` LONGTEXT DEFAULT NULL COMMENT 'AI修复脚本',
  `server_ai_provider` VARCHAR(64) DEFAULT NULL COMMENT 'AI提供商',
  `server_ai_model` VARCHAR(255) DEFAULT NULL COMMENT 'AI模型',
  `server_knowledge_id` INT DEFAULT NULL COMMENT '知识库ID',
  `server_expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_service_operation_log_id`),
  KEY `idx_server_service_operation_service` (`server_service_id`, `create_time`),
  KEY `idx_server_service_operation_server` (`server_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器服务操作日志';

CREATE TABLE IF NOT EXISTS `server_service_ai_knowledge` (
  `server_service_ai_knowledge_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器服务AI知识ID',
  `server_knowledge_key` VARCHAR(255) DEFAULT NULL COMMENT '知识键',
  `server_service_name` VARCHAR(255) DEFAULT NULL COMMENT '服务名称',
  `server_service_type` VARCHAR(64) DEFAULT NULL COMMENT '服务类型',
  `server_type` VARCHAR(64) DEFAULT NULL COMMENT '接入类型',
  `server_os_type` VARCHAR(64) DEFAULT NULL COMMENT '操作系统',
  `server_ai_reason` LONGTEXT DEFAULT NULL COMMENT '失败原因',
  `server_ai_solution` LONGTEXT DEFAULT NULL COMMENT '处理方案',
  `server_ai_fix_script` LONGTEXT DEFAULT NULL COMMENT '修复脚本',
  `server_ai_provider` VARCHAR(64) DEFAULT NULL COMMENT 'AI提供商',
  `server_ai_model` VARCHAR(255) DEFAULT NULL COMMENT 'AI模型',
  `server_sample_output` LONGTEXT DEFAULT NULL COMMENT '样例输出',
  `server_metadata_json` LONGTEXT DEFAULT NULL COMMENT '扩展元数据',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_service_ai_knowledge_id`),
  UNIQUE KEY `uk_server_service_ai_knowledge_key` (`server_knowledge_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器服务AI知识库';

CREATE TABLE IF NOT EXISTS `server_alert_setting` (
  `server_alert_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器预警规则ID',
  `server_id` INT DEFAULT NULL COMMENT '服务器ID，空表示全局',
  `server_alert_inherit_global` TINYINT(1) DEFAULT 0 COMMENT '是否继承全局',
  `server_alert_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用预警',
  `server_alert_cpu_warning_percent` DOUBLE DEFAULT NULL COMMENT 'CPU预警阈值',
  `server_alert_cpu_danger_percent` DOUBLE DEFAULT NULL COMMENT 'CPU危险阈值',
  `server_alert_memory_warning_percent` DOUBLE DEFAULT NULL COMMENT '内存预警阈值',
  `server_alert_memory_danger_percent` DOUBLE DEFAULT NULL COMMENT '内存危险阈值',
  `server_alert_disk_warning_percent` DOUBLE DEFAULT NULL COMMENT '磁盘预警阈值',
  `server_alert_disk_danger_percent` DOUBLE DEFAULT NULL COMMENT '磁盘危险阈值',
  `server_alert_io_warning_bytes_per_second` DOUBLE DEFAULT NULL COMMENT 'IO预警阈值',
  `server_alert_io_danger_bytes_per_second` DOUBLE DEFAULT NULL COMMENT 'IO危险阈值',
  `server_alert_latency_warning_ms` INT DEFAULT NULL COMMENT '延迟预警阈值',
  `server_alert_latency_danger_ms` INT DEFAULT NULL COMMENT '延迟危险阈值',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_alert_setting_id`),
  UNIQUE KEY `uk_server_alert_setting_server` (`server_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器预警规则';

CREATE TABLE IF NOT EXISTS `server_alert_event` (
  `server_alert_event_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器预警事件ID',
  `server_id` INT DEFAULT NULL COMMENT '服务器ID',
  `server_code` VARCHAR(255) DEFAULT NULL COMMENT '服务器编码',
  `server_alert_metric_type` VARCHAR(64) DEFAULT NULL COMMENT '指标类型',
  `server_alert_severity` VARCHAR(32) DEFAULT NULL COMMENT '告警级别',
  `server_alert_metric_value` DOUBLE DEFAULT NULL COMMENT '当前值',
  `server_alert_warning_threshold` DOUBLE DEFAULT NULL COMMENT '预警阈值',
  `server_alert_danger_threshold` DOUBLE DEFAULT NULL COMMENT '危险阈值',
  `server_alert_snapshot_json` LONGTEXT DEFAULT NULL COMMENT '指标快照',
  `server_alert_message` VARCHAR(2048) DEFAULT NULL COMMENT '告警消息',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_alert_event_id`),
  KEY `idx_server_alert_event_server` (`server_id`, `create_time`),
  KEY `idx_server_alert_event_metric` (`server_alert_metric_type`, `server_alert_severity`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器预警事件';

CREATE TABLE IF NOT EXISTS `server_setting` (
  `server_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器配置项ID',
  `server_setting_key` VARCHAR(255) DEFAULT NULL COMMENT '配置项键',
  `server_setting_value` LONGTEXT DEFAULT NULL COMMENT '配置项值',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_setting_id`),
  UNIQUE KEY `uk_server_setting_key` (`server_setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器配置项';

SET FOREIGN_KEY_CHECKS = 1;
