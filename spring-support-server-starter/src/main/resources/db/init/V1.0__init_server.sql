SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `server_host` (
  `server_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  `server_name` VARCHAR(255) DEFAULT NULL COMMENT '服务器名称',
  `server_code` VARCHAR(255) DEFAULT NULL COMMENT '服务器编码',
  `server_type` VARCHAR(64) DEFAULT NULL COMMENT '接入类型',
  `server_os_type` VARCHAR(64) DEFAULT NULL COMMENT '操作系统',
  `server_architecture` VARCHAR(64) DEFAULT NULL COMMENT '架构',
  `server_host` VARCHAR(255) DEFAULT NULL COMMENT '主机地址',
  `server_public_ip` VARCHAR(255) DEFAULT NULL COMMENT '公网IP',
  `server_port` INT DEFAULT NULL COMMENT '端口',
  `server_username` VARCHAR(255) DEFAULT NULL COMMENT '用户名',
  `server_password` VARCHAR(2048) DEFAULT NULL COMMENT '密码密文',
  `server_private_key` LONGTEXT DEFAULT NULL COMMENT '私钥密文',
  `server_base_directory` VARCHAR(1024) DEFAULT NULL COMMENT '基础目录',
  `server_tags` VARCHAR(1024) DEFAULT NULL COMMENT '标签',
  `server_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `server_description` VARCHAR(1024) DEFAULT NULL COMMENT '描述',
  `server_metadata_json` LONGTEXT DEFAULT NULL COMMENT '扩展元数据',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_id`),
  UNIQUE KEY `uk_server_host_code` (`server_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器主机';

CREATE TABLE IF NOT EXISTS `server_service` (
  `server_service_id` INT NOT NULL AUTO_INCREMENT COMMENT '服务器服务ID',
  `server_id` INT DEFAULT NULL COMMENT '服务器ID',
  `server_service_code` VARCHAR(255) DEFAULT NULL COMMENT '服务编码',
  `server_service_name` VARCHAR(255) DEFAULT NULL COMMENT '服务名称',
  `server_service_type` VARCHAR(64) DEFAULT NULL COMMENT '服务类型',
  `server_soft_package_id` INT DEFAULT NULL COMMENT '关联软件ID',
  `server_soft_package_version_id` INT DEFAULT NULL COMMENT '关联软件版本ID',
  `server_soft_installation_id` INT DEFAULT NULL COMMENT '关联软件安装ID',
  `server_install_path` VARCHAR(1024) DEFAULT NULL COMMENT '安装目录',
  `server_runtime_status` VARCHAR(64) DEFAULT NULL COMMENT '运行状态',
  `server_config_paths_json` LONGTEXT DEFAULT NULL COMMENT '配置路径JSON',
  `server_log_paths_json` LONGTEXT DEFAULT NULL COMMENT '日志路径JSON',
  `server_config_template` LONGTEXT DEFAULT NULL COMMENT '配置模板',
  `server_init_script` LONGTEXT DEFAULT NULL COMMENT '初始化脚本',
  `server_install_script` LONGTEXT DEFAULT NULL COMMENT '安装脚本',
  `server_uninstall_script` LONGTEXT DEFAULT NULL COMMENT '卸载脚本',
  `server_detect_script` LONGTEXT DEFAULT NULL COMMENT '检测脚本',
  `server_register_script` LONGTEXT DEFAULT NULL COMMENT '注册脚本',
  `server_unregister_script` LONGTEXT DEFAULT NULL COMMENT '取消注册脚本',
  `server_start_script` LONGTEXT DEFAULT NULL COMMENT '启动脚本',
  `server_stop_script` LONGTEXT DEFAULT NULL COMMENT '停止脚本',
  `server_restart_script` LONGTEXT DEFAULT NULL COMMENT '重启脚本',
  `server_status_script` LONGTEXT DEFAULT NULL COMMENT '状态脚本',
  `server_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  `server_description` VARCHAR(1024) DEFAULT NULL COMMENT '描述',
  `server_metadata_json` LONGTEXT DEFAULT NULL COMMENT '扩展元数据',
  `server_last_operation_time` DATETIME DEFAULT NULL COMMENT '最后操作时间',
  `server_last_operation_message` VARCHAR(2048) DEFAULT NULL COMMENT '最后操作说明',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_service_id`),
  UNIQUE KEY `uk_server_service_identity` (`server_id`, `server_service_name`),
  KEY `idx_server_service_installation` (`server_soft_installation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器服务';

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
  `server_alert_message_enabled` TINYINT(1) DEFAULT 0 COMMENT '是否同步消息中心',
  `server_alert_cpu_warning_percent` DOUBLE DEFAULT NULL COMMENT 'CPU预警阈值',
  `server_alert_cpu_danger_percent` DOUBLE DEFAULT NULL COMMENT 'CPU危险阈值',
  `server_alert_memory_warning_percent` DOUBLE DEFAULT NULL COMMENT '内存预警阈值',
  `server_alert_memory_danger_percent` DOUBLE DEFAULT NULL COMMENT '内存危险阈值',
  `server_alert_disk_warning_percent` DOUBLE DEFAULT NULL COMMENT '磁盘预警阈值',
  `server_alert_disk_danger_percent` DOUBLE DEFAULT NULL COMMENT '磁盘危险阈值',
  `server_alert_disk_io_warning_bytes_per_second` DOUBLE DEFAULT NULL COMMENT '磁盘IO预警阈值',
  `server_alert_disk_io_danger_bytes_per_second` DOUBLE DEFAULT NULL COMMENT '磁盘IO危险阈值',
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

CREATE TABLE IF NOT EXISTS `server_metrics_history` (
  `server_metrics_history_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '服务器指标历史ID',
  `server_id` INT DEFAULT NULL COMMENT '服务器ID',
  `server_code` VARCHAR(255) DEFAULT NULL COMMENT '服务器编码',
  `server_status` VARCHAR(64) DEFAULT NULL COMMENT '状态',
  `server_online` TINYINT(1) DEFAULT 0 COMMENT '是否在线',
  `server_latency_ms` INT DEFAULT NULL COMMENT '延迟毫秒',
  `server_cpu_usage` DOUBLE DEFAULT NULL COMMENT 'CPU 使用率',
  `server_cpu_cores` INT DEFAULT NULL COMMENT 'CPU 核数',
  `server_memory_usage` DOUBLE DEFAULT NULL COMMENT '内存使用率',
  `server_memory_total_bytes` BIGINT DEFAULT NULL COMMENT '内存总量',
  `server_memory_used_bytes` BIGINT DEFAULT NULL COMMENT '内存已用',
  `server_disk_usage` DOUBLE DEFAULT NULL COMMENT '磁盘使用率',
  `server_disk_total_bytes` BIGINT DEFAULT NULL COMMENT '磁盘总量',
  `server_disk_used_bytes` BIGINT DEFAULT NULL COMMENT '磁盘已用',
  `server_disk_read_bps` DOUBLE DEFAULT NULL COMMENT '磁盘读取吞吐 B/s',
  `server_disk_write_bps` DOUBLE DEFAULT NULL COMMENT '磁盘写入吞吐 B/s',
  `server_io_read_bps` DOUBLE DEFAULT NULL COMMENT '网络读取吞吐 B/s',
  `server_io_write_bps` DOUBLE DEFAULT NULL COMMENT '网络写入吞吐 B/s',
  `server_network_rx_pps` DOUBLE DEFAULT NULL COMMENT '接收包速率',
  `server_network_tx_pps` DOUBLE DEFAULT NULL COMMENT '发送包速率',
  `server_collect_timestamp` BIGINT DEFAULT NULL COMMENT '采集时间戳',
  `server_detail_message` VARCHAR(2048) DEFAULT NULL COMMENT '采集说明',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`server_metrics_history_id`),
  KEY `idx_server_metrics_history_server` (`server_id`, `server_collect_timestamp`),
  KEY `idx_server_metrics_history_collect` (`server_collect_timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器指标历史';

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

ALTER TABLE `server_host`
  ADD COLUMN IF NOT EXISTS `server_public_ip` VARCHAR(255) DEFAULT NULL COMMENT '公网IP' AFTER `server_host`;

ALTER TABLE `server_metrics_history`
  ADD COLUMN IF NOT EXISTS `server_disk_read_bps` DOUBLE DEFAULT NULL COMMENT '磁盘读取吞吐 B/s' AFTER `server_disk_used_bytes`,
  ADD COLUMN IF NOT EXISTS `server_disk_write_bps` DOUBLE DEFAULT NULL COMMENT '磁盘写入吞吐 B/s' AFTER `server_disk_read_bps`;

ALTER TABLE `server_alert_setting`
  ADD COLUMN IF NOT EXISTS `server_alert_disk_io_warning_bytes_per_second` DOUBLE DEFAULT NULL COMMENT '磁盘IO预警阈值' AFTER `server_alert_disk_danger_percent`,
  ADD COLUMN IF NOT EXISTS `server_alert_disk_io_danger_bytes_per_second` DOUBLE DEFAULT NULL COMMENT '磁盘IO危险阈值' AFTER `server_alert_disk_io_warning_bytes_per_second`;

INSERT INTO `server_host` (
  `server_name`,
  `server_code`,
  `server_type`,
  `server_os_type`,
  `server_architecture`,
  `server_host`,
  `server_port`,
  `server_username`,
  `server_password`,
  `server_private_key`,
  `server_base_directory`,
  `server_tags`,
  `server_enabled`,
  `server_description`,
  `server_metadata_json`,
  `create_time`,
  `update_time`
)
SELECT
  '本机 Windows',
  'f32a4ba8642dc68c0ff5042a572fcdcf',
  'LOCAL',
  'WINDOWS',
  'AMD64',
  '127.0.0.1',
  0,
  'yemen',
  NULL,
  NULL,
  'H:/workspace/2/tmp/soft-runtime/local',
  'soft,local,windows',
  1,
  'soft-test 本机真实联调入口',
  '{"softEnabled":true,"displayMode":"default","source":"soft-test-bootstrap"}',
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `server_host`
  WHERE `server_code` = 'f32a4ba8642dc68c0ff5042a572fcdcf'
);

INSERT INTO `server_host` (
  `server_name`,
  `server_code`,
  `server_type`,
  `server_os_type`,
  `server_architecture`,
  `server_host`,
  `server_port`,
  `server_username`,
  `server_password`,
  `server_private_key`,
  `server_base_directory`,
  `server_tags`,
  `server_enabled`,
  `server_description`,
  `server_metadata_json`,
  `create_time`,
  `update_time`
)
SELECT
  '远程 Linux 172.16.0.40',
  '20a860f7f008190a23da131959bb0580',
  'SSH',
  'LINUX',
  'AMD64',
  '172.16.0.40',
  22,
  'root',
  'd57a246d45bbdaa0392dfb33d18d97fa',
  'dff1a552dea2f3164f0f6c7101fe0caa',
  '/opt',
  'soft,remote,linux',
  1,
  'soft-test 远程 Linux 真实联调入口',
  '{"softEnabled":true,"displayMode":"default","source":"soft-test-bootstrap","hostAlias":"remote-linux-17216040"}',
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `server_host`
  WHERE `server_code` = '20a860f7f008190a23da131959bb0580'
);

SET FOREIGN_KEY_CHECKS = 1;
