SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'os_type'),
    'ALTER TABLE `server_host` CHANGE COLUMN `os_type` `server_os_type` VARCHAR(64) DEFAULT NULL COMMENT ''操作系统''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'architecture'),
    'ALTER TABLE `server_host` CHANGE COLUMN `architecture` `server_architecture` VARCHAR(64) DEFAULT NULL COMMENT ''架构''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'host'),
    'ALTER TABLE `server_host` CHANGE COLUMN `host` `server_host` VARCHAR(255) DEFAULT NULL COMMENT ''主机地址''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'port'),
    'ALTER TABLE `server_host` CHANGE COLUMN `port` `server_port` INT DEFAULT NULL COMMENT ''端口''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'username'),
    'ALTER TABLE `server_host` CHANGE COLUMN `username` `server_username` VARCHAR(255) DEFAULT NULL COMMENT ''用户名''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'password'),
    'ALTER TABLE `server_host` CHANGE COLUMN `password` `server_password` VARCHAR(2048) DEFAULT NULL COMMENT ''密码密文''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'private_key'),
    'ALTER TABLE `server_host` CHANGE COLUMN `private_key` `server_private_key` LONGTEXT DEFAULT NULL COMMENT ''私钥密文''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'base_directory'),
    'ALTER TABLE `server_host` CHANGE COLUMN `base_directory` `server_base_directory` VARCHAR(1024) DEFAULT NULL COMMENT ''基础目录''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'tags'),
    'ALTER TABLE `server_host` CHANGE COLUMN `tags` `server_tags` VARCHAR(1024) DEFAULT NULL COMMENT ''标签''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'enabled'),
    'ALTER TABLE `server_host` CHANGE COLUMN `enabled` `server_enabled` TINYINT(1) DEFAULT 1 COMMENT ''是否启用''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'description'),
    'ALTER TABLE `server_host` CHANGE COLUMN `description` `server_description` VARCHAR(1024) DEFAULT NULL COMMENT ''描述''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_host' AND COLUMN_NAME = 'metadata_json'),
    'ALTER TABLE `server_host` CHANGE COLUMN `metadata_json` `server_metadata_json` LONGTEXT DEFAULT NULL COMMENT ''扩展元数据''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'service_code'),
    'ALTER TABLE `server_service` CHANGE COLUMN `service_code` `server_service_code` VARCHAR(255) DEFAULT NULL COMMENT ''服务编码''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'service_name'),
    'ALTER TABLE `server_service` CHANGE COLUMN `service_name` `server_service_name` VARCHAR(255) DEFAULT NULL COMMENT ''服务名称''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'service_type'),
    'ALTER TABLE `server_service` CHANGE COLUMN `service_type` `server_service_type` VARCHAR(64) DEFAULT NULL COMMENT ''服务类型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'soft_package_id'),
    'ALTER TABLE `server_service` CHANGE COLUMN `soft_package_id` `server_soft_package_id` INT DEFAULT NULL COMMENT ''关联软件ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'soft_package_version_id'),
    'ALTER TABLE `server_service` CHANGE COLUMN `soft_package_version_id` `server_soft_package_version_id` INT DEFAULT NULL COMMENT ''关联软件版本ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'soft_installation_id'),
    'ALTER TABLE `server_service` CHANGE COLUMN `soft_installation_id` `server_soft_installation_id` INT DEFAULT NULL COMMENT ''关联软件安装ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'install_path'),
    'ALTER TABLE `server_service` CHANGE COLUMN `install_path` `server_install_path` VARCHAR(1024) DEFAULT NULL COMMENT ''安装目录''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'runtime_status'),
    'ALTER TABLE `server_service` CHANGE COLUMN `runtime_status` `server_runtime_status` VARCHAR(64) DEFAULT NULL COMMENT ''运行状态''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'config_paths_json'),
    'ALTER TABLE `server_service` CHANGE COLUMN `config_paths_json` `server_config_paths_json` LONGTEXT DEFAULT NULL COMMENT ''配置路径JSON''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'log_paths_json'),
    'ALTER TABLE `server_service` CHANGE COLUMN `log_paths_json` `server_log_paths_json` LONGTEXT DEFAULT NULL COMMENT ''日志路径JSON''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'config_template'),
    'ALTER TABLE `server_service` CHANGE COLUMN `config_template` `server_config_template` LONGTEXT DEFAULT NULL COMMENT ''配置模板''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'init_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `init_script` `server_init_script` LONGTEXT DEFAULT NULL COMMENT ''初始化脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'install_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `install_script` `server_install_script` LONGTEXT DEFAULT NULL COMMENT ''安装脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'uninstall_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `uninstall_script` `server_uninstall_script` LONGTEXT DEFAULT NULL COMMENT ''卸载脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'detect_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `detect_script` `server_detect_script` LONGTEXT DEFAULT NULL COMMENT ''检测脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'register_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `register_script` `server_register_script` LONGTEXT DEFAULT NULL COMMENT ''注册脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'unregister_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `unregister_script` `server_unregister_script` LONGTEXT DEFAULT NULL COMMENT ''取消注册脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'start_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `start_script` `server_start_script` LONGTEXT DEFAULT NULL COMMENT ''启动脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'stop_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `stop_script` `server_stop_script` LONGTEXT DEFAULT NULL COMMENT ''停止脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'restart_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `restart_script` `server_restart_script` LONGTEXT DEFAULT NULL COMMENT ''重启脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'status_script'),
    'ALTER TABLE `server_service` CHANGE COLUMN `status_script` `server_status_script` LONGTEXT DEFAULT NULL COMMENT ''状态脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'enabled'),
    'ALTER TABLE `server_service` CHANGE COLUMN `enabled` `server_enabled` TINYINT(1) DEFAULT 1 COMMENT ''是否启用''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'description'),
    'ALTER TABLE `server_service` CHANGE COLUMN `description` `server_description` VARCHAR(1024) DEFAULT NULL COMMENT ''描述''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'metadata_json'),
    'ALTER TABLE `server_service` CHANGE COLUMN `metadata_json` `server_metadata_json` LONGTEXT DEFAULT NULL COMMENT ''扩展元数据''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'last_operation_time'),
    'ALTER TABLE `server_service` CHANGE COLUMN `last_operation_time` `server_last_operation_time` DATETIME DEFAULT NULL COMMENT ''最后操作时间''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service' AND COLUMN_NAME = 'last_operation_message'),
    'ALTER TABLE `server_service` CHANGE COLUMN `last_operation_message` `server_last_operation_message` VARCHAR(2048) DEFAULT NULL COMMENT ''最后操作说明''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'operation_type'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `operation_type` `server_operation_type` VARCHAR(64) DEFAULT NULL COMMENT ''操作类型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'success'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `success` `server_operation_success` TINYINT(1) DEFAULT 0 COMMENT ''是否成功''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'exit_code'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `exit_code` `server_exit_code` INT DEFAULT NULL COMMENT ''退出码''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'runtime_status'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `runtime_status` `server_runtime_status` VARCHAR(64) DEFAULT NULL COMMENT ''运行状态''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'operation_message'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `operation_message` `server_operation_message` VARCHAR(2048) DEFAULT NULL COMMENT ''操作说明''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'operation_output'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `operation_output` `server_operation_output` LONGTEXT DEFAULT NULL COMMENT ''操作输出''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'ai_reason'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `ai_reason` `server_ai_reason` LONGTEXT DEFAULT NULL COMMENT ''AI失败原因''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'ai_solution'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `ai_solution` `server_ai_solution` LONGTEXT DEFAULT NULL COMMENT ''AI处理方案''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'ai_fix_script'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `ai_fix_script` `server_ai_fix_script` LONGTEXT DEFAULT NULL COMMENT ''AI修复脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'ai_provider'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `ai_provider` `server_ai_provider` VARCHAR(64) DEFAULT NULL COMMENT ''AI提供商''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'ai_model'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `ai_model` `server_ai_model` VARCHAR(255) DEFAULT NULL COMMENT ''AI模型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'knowledge_id'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `knowledge_id` `server_knowledge_id` INT DEFAULT NULL COMMENT ''知识库ID''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_operation_log' AND COLUMN_NAME = 'expire_at'),
    'ALTER TABLE `server_service_operation_log` CHANGE COLUMN `expire_at` `server_expire_at` DATETIME DEFAULT NULL COMMENT ''过期时间''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'knowledge_key'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `knowledge_key` `server_knowledge_key` VARCHAR(255) DEFAULT NULL COMMENT ''知识键''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'service_name'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `service_name` `server_service_name` VARCHAR(255) DEFAULT NULL COMMENT ''服务名称''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'service_type'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `service_type` `server_service_type` VARCHAR(64) DEFAULT NULL COMMENT ''服务类型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'os_type'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `os_type` `server_os_type` VARCHAR(64) DEFAULT NULL COMMENT ''操作系统''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'reason'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `reason` `server_ai_reason` LONGTEXT DEFAULT NULL COMMENT ''失败原因''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'solution'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `solution` `server_ai_solution` LONGTEXT DEFAULT NULL COMMENT ''处理方案''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'fix_script'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `fix_script` `server_ai_fix_script` LONGTEXT DEFAULT NULL COMMENT ''修复脚本''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'provider'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `provider` `server_ai_provider` VARCHAR(64) DEFAULT NULL COMMENT ''AI提供商''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'model'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `model` `server_ai_model` VARCHAR(255) DEFAULT NULL COMMENT ''AI模型''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'sample_output'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `sample_output` `server_sample_output` LONGTEXT DEFAULT NULL COMMENT ''样例输出''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_service_ai_knowledge' AND COLUMN_NAME = 'metadata_json'),
    'ALTER TABLE `server_service_ai_knowledge` CHANGE COLUMN `metadata_json` `server_metadata_json` LONGTEXT DEFAULT NULL COMMENT ''扩展元数据''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_setting' AND COLUMN_NAME = 'setting_key'),
    'ALTER TABLE `server_setting` CHANGE COLUMN `setting_key` `server_setting_key` VARCHAR(255) DEFAULT NULL COMMENT ''配置项键''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'server_setting' AND COLUMN_NAME = 'setting_value'),
    'ALTER TABLE `server_setting` CHANGE COLUMN `setting_value` `server_setting_value` LONGTEXT DEFAULT NULL COMMENT ''配置项值''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
