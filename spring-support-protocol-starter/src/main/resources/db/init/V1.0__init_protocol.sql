-- 协议管理 DDL（system_server 系列）。由 plugin.datasource.script 扫描 classpath*:db/init/*.sql 执行。
-- 方言：MySQL 8（含表/列 COMMENT）。若使用 H2 等库请另备脚本或关闭脚本执行。

CREATE TABLE IF NOT EXISTS system_server (
  system_server_id INT NOT NULL AUTO_INCREMENT COMMENT '主键，协议服务器实例 ID',
  system_server_name VARCHAR(255) DEFAULT NULL COMMENT '服务器显示名称',
  system_server_host VARCHAR(255) DEFAULT NULL COMMENT '监听主机/绑定地址',
  system_server_port INT DEFAULT NULL COMMENT '监听端口',
  system_server_type VARCHAR(255) DEFAULT NULL COMMENT '服务器类型（如 HTTP/SOCKS 等）',
  system_server_context_path VARCHAR(255) DEFAULT NULL COMMENT '上下文路径（如适用）',
  system_server_status VARCHAR(32) DEFAULT NULL COMMENT '运行状态（RUNNING/STOPPED/ERROR 等）',
  system_server_description VARCHAR(500) DEFAULT NULL COMMENT '描述',
  system_server_config VARCHAR(2000) DEFAULT NULL COMMENT '扩展 JSON/配置串',
  system_server_auto_start VARCHAR(1) DEFAULT NULL COMMENT '是否随应用启动（Y/N）',
  system_server_max_connections INT DEFAULT NULL COMMENT '最大连接数',
  system_server_timeout INT DEFAULT NULL COMMENT '超时（秒或毫秒，与业务约定一致）',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='协议/代理服务器主表';

CREATE TABLE IF NOT EXISTS system_server_log (
  system_server_log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  system_server_log_server_id INT DEFAULT NULL COMMENT '关联 system_server.system_server_id',
  system_server_log_filter_type VARCHAR(255) DEFAULT NULL COMMENT '过滤器类型（SPI 标识或类名）',
  system_server_log_action_status VARCHAR(16) DEFAULT NULL COMMENT '通行/拒绝（PASS/DENY）',
  system_server_log_process_status VARCHAR(255) DEFAULT NULL COMMENT '处理结果状态（如黑名单拦截、白名单通过等）',
  system_server_log_client_ip VARCHAR(255) DEFAULT NULL COMMENT '客户端 IP',
  system_server_log_client_geo VARCHAR(255) DEFAULT NULL COMMENT '客户端地理位置摘要',
  system_server_log_user_agent VARCHAR(1000) DEFAULT NULL COMMENT 'User-Agent',
  system_server_log_request_id VARCHAR(128) DEFAULT NULL COMMENT '请求ID/TraceId',
  system_server_log_method VARCHAR(32) DEFAULT NULL COMMENT '请求方法/命令',
  system_server_log_path VARCHAR(1000) DEFAULT NULL COMMENT '路径/资源',
  system_server_log_rule_category VARCHAR(32) DEFAULT NULL COMMENT '规则分类（RATE_LIMIT/BLACKLIST/WHITELIST/UNKNOWN）',
  system_server_log_decision_reason VARCHAR(2000) DEFAULT NULL COMMENT '命中原因（人类可读）',
  system_server_log_access_time TIMESTAMP NULL DEFAULT NULL COMMENT '请求进入过滤器时间',
  system_server_log_duration_ms BIGINT DEFAULT NULL COMMENT '处理耗时（毫秒）',
  system_server_log_store_time TIMESTAMP NULL DEFAULT NULL COMMENT '落库时间',
  -- 兼容历史字段命名（旧库/旧脚本可能使用 id/server_id/...；新代码统一使用 system_server_log_*）
  id BIGINT GENERATED ALWAYS AS (system_server_log_id) STORED COMMENT '兼容旧主键字段（只读映射）',
  server_id INT GENERATED ALWAYS AS (system_server_log_server_id) STORED COMMENT '兼容旧 server_id 字段（只读映射）',
  filter_type VARCHAR(255) GENERATED ALWAYS AS (system_server_log_filter_type) STORED COMMENT '兼容旧 filter_type 字段（只读映射）',
  action_status VARCHAR(16) GENERATED ALWAYS AS (system_server_log_action_status) STORED COMMENT '兼容旧 action_status 字段（只读映射）',
  process_status VARCHAR(255) GENERATED ALWAYS AS (system_server_log_process_status) STORED COMMENT '兼容旧 process_status 字段（只读映射）',
  client_ip VARCHAR(255) GENERATED ALWAYS AS (system_server_log_client_ip) STORED COMMENT '兼容旧 client_ip 字段（只读映射）',
  client_geo VARCHAR(255) GENERATED ALWAYS AS (system_server_log_client_geo) STORED COMMENT '兼容旧 client_geo 字段（只读映射）',
  user_agent VARCHAR(1000) GENERATED ALWAYS AS (system_server_log_user_agent) STORED COMMENT '兼容旧 user_agent 字段（只读映射）',
  request_id VARCHAR(128) GENERATED ALWAYS AS (system_server_log_request_id) STORED COMMENT '兼容旧 request_id 字段（只读映射）',
  method VARCHAR(32) GENERATED ALWAYS AS (system_server_log_method) STORED COMMENT '兼容旧 method 字段（只读映射）',
  path VARCHAR(1000) GENERATED ALWAYS AS (system_server_log_path) STORED COMMENT '兼容旧 path 字段（只读映射）',
  rule_category VARCHAR(32) GENERATED ALWAYS AS (system_server_log_rule_category) STORED COMMENT '兼容旧 rule_category 字段（只读映射）',
  decision_reason VARCHAR(2000) GENERATED ALWAYS AS (system_server_log_decision_reason) STORED COMMENT '兼容旧 decision_reason 字段（只读映射）',
  access_time TIMESTAMP GENERATED ALWAYS AS (system_server_log_access_time) STORED COMMENT '兼容旧 access_time 字段（只读映射）',
  duration_ms BIGINT GENERATED ALWAYS AS (system_server_log_duration_ms) STORED COMMENT '兼容旧 duration_ms 字段（只读映射）',
  store_time TIMESTAMP GENERATED ALWAYS AS (system_server_log_store_time) STORED COMMENT '兼容旧 store_time 字段（只读映射）',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统服务器过滤器访问与处理日志';

CREATE TABLE IF NOT EXISTS system_server_setting (
  system_server_setting_id INT NOT NULL AUTO_INCREMENT COMMENT '主键，过滤器/设置项 ID',
  system_server_setting_name VARCHAR(255) DEFAULT NULL COMMENT '设置名称',
  system_server_setting_type VARCHAR(255) DEFAULT NULL COMMENT '设置类型/SPI 类型',
  system_server_setting_server_id INT DEFAULT NULL COMMENT '所属 system_server_id',
  system_server_setting_order INT DEFAULT NULL COMMENT '排序号',
  system_server_setting_description VARCHAR(500) DEFAULT NULL COMMENT '描述',
  system_server_setting_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用（Y/N）',
  system_server_setting_class_name VARCHAR(500) DEFAULT NULL COMMENT '实现类全名',
  system_server_setting_version VARCHAR(255) DEFAULT NULL COMMENT '版本',
  system_server_setting_config VARCHAR(2000) DEFAULT NULL COMMENT '配置 JSON/参数',
  system_server_setting_filter_id VARCHAR(255) DEFAULT NULL COMMENT '过滤器唯一标识',
  system_server_setting_https_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用 HTTPS（Y/N）',
  system_server_setting_https_cert_type VARCHAR(32) DEFAULT NULL COMMENT '证书类型（PEM/KEYSTORE 等）',
  system_server_setting_https_pem_cert BLOB DEFAULT NULL COMMENT 'PEM 证书内容',
  system_server_setting_https_pem_key BLOB DEFAULT NULL COMMENT 'PEM 私钥内容',
  system_server_setting_https_pem_key_password VARCHAR(255) DEFAULT NULL COMMENT 'PEM 私钥口令',
  system_server_setting_https_keystore BLOB DEFAULT NULL COMMENT 'Java Keystore 二进制',
  system_server_setting_https_keystore_password VARCHAR(255) DEFAULT NULL COMMENT 'Keystore 口令',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='某台服务器下的过滤器/协议设置';

CREATE TABLE IF NOT EXISTS system_server_setting_item (
  system_server_setting_item_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  system_server_setting_item_setting_id INT DEFAULT NULL COMMENT '关联 system_server_setting_id',
  system_server_setting_item_name VARCHAR(255) DEFAULT NULL COMMENT '配置项名称/键',
  system_server_setting_item_value VARCHAR(2000) DEFAULT NULL COMMENT '配置项值',
  system_server_setting_item_description VARCHAR(500) DEFAULT NULL COMMENT '说明',
  system_server_setting_item_type VARCHAR(255) DEFAULT NULL COMMENT '值类型（字符串/数字等）',
  system_server_setting_item_required VARCHAR(1) DEFAULT NULL COMMENT '是否必填（Y/N）',
  system_server_setting_item_default_value VARCHAR(2000) DEFAULT NULL COMMENT '默认值',
  system_server_setting_item_validation_rule VARCHAR(500) DEFAULT NULL COMMENT '校验规则描述或表达式',
  system_server_setting_item_order INT DEFAULT NULL COMMENT '展示/加载顺序',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='过滤器配置项键值明细';

CREATE TABLE IF NOT EXISTS system_server_setting_file_storage (
  system_server_setting_file_storage_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  file_storage_server_id INT DEFAULT NULL COMMENT '关联 system_server_id',
  file_storage_type VARCHAR(50) DEFAULT NULL COMMENT '存储类型（本地/OSS 等）',
  file_storage_base_path VARCHAR(500) DEFAULT NULL COMMENT '本地根路径或前缀',
  file_storage_endpoint VARCHAR(255) DEFAULT NULL COMMENT '对象存储 Endpoint',
  file_storage_bucket VARCHAR(128) DEFAULT NULL COMMENT 'Bucket',
  file_storage_access_key VARCHAR(200) DEFAULT NULL COMMENT 'AccessKey',
  file_storage_secret_key VARCHAR(200) DEFAULT NULL COMMENT 'SecretKey',
  file_storage_region VARCHAR(100) DEFAULT NULL COMMENT '区域',
  file_storage_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用（Y/N）',
  file_storage_connection_timeout BIGINT DEFAULT NULL COMMENT '连接超时（毫秒）',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_file_storage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器级文件存储（过滤器用）配置';

CREATE TABLE IF NOT EXISTS system_server_setting_service_discovery (
  system_server_setting_service_discovery_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  service_discovery_server_id INT DEFAULT NULL COMMENT '关联 system_server_id',
  service_discovery_mode VARCHAR(50) DEFAULT NULL COMMENT '发现模式',
  service_discovery_balance VARCHAR(255) DEFAULT NULL COMMENT '负载均衡策略',
  service_discovery_bean_name VARCHAR(255) DEFAULT NULL COMMENT 'Spring Bean 名',
  service_discovery_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用（Y/N）',
  service_discovery_monitor_host VARCHAR(255) DEFAULT NULL COMMENT '监控/注册主机',
  service_discovery_monitor_port VARCHAR(255) DEFAULT NULL COMMENT '监控/注册端口',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_service_discovery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务发现总配置';

CREATE TABLE IF NOT EXISTS system_server_setting_service_discovery_mapping (
  system_server_setting_service_discovery_mapping_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  service_discovery_server_id INT DEFAULT NULL COMMENT '关联 system_server_id',
  service_discovery_name VARCHAR(255) DEFAULT NULL COMMENT '服务名',
  service_discovery_address VARCHAR(255) DEFAULT NULL COMMENT '实例地址',
  service_discovery_weight INT DEFAULT NULL COMMENT '权重',
  service_discovery_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用（Y/N）',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_service_discovery_mapping_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务发现实例映射表';

CREATE TABLE IF NOT EXISTS system_server_setting_ip_rate_limit (
  system_server_setting_ip_rate_limit_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  ip_rate_limit_server_id INT DEFAULT NULL COMMENT '关联 system_server_id',
  ip_rate_limit_ip VARCHAR(255) DEFAULT NULL COMMENT '限流 IP',
  ip_rate_limit_qps INT DEFAULT NULL COMMENT 'QPS 阈值',
  ip_rate_limit_type VARCHAR(255) DEFAULT NULL COMMENT '限流类型',
  ip_rate_limit_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用（Y/N）',
  ip_rate_limit_setting_id INT DEFAULT NULL COMMENT '关联 system_server_setting_id',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_ip_rate_limit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按 IP 的限流规则';

CREATE TABLE IF NOT EXISTS system_server_setting_address_rate_limit (
  system_server_setting_address_rate_limit_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  address_rate_limit_server_id INT DEFAULT NULL COMMENT '关联 system_server_id',
  address_rate_limit_address VARCHAR(255) DEFAULT NULL COMMENT '限流地址/路径模式',
  address_rate_limit_qps INT DEFAULT NULL COMMENT 'QPS 阈值',
  address_rate_limit_type VARCHAR(255) DEFAULT NULL COMMENT '限流类型',
  address_rate_limit_enabled VARCHAR(1) DEFAULT NULL COMMENT '是否启用（Y/N）',
  address_rate_limit_setting_id INT DEFAULT NULL COMMENT '关联 system_server_setting_id',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_address_rate_limit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按地址/路径的限流规则';

CREATE TABLE IF NOT EXISTS system_server_setting_preview_extension (
  system_server_setting_preview_extension_id INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  preview_extension_server_id INT NOT NULL COMMENT '关联 system_server_id',
  preview_extension_disabled VARCHAR(2000) DEFAULT NULL COMMENT '禁用列表/规则',
  preview_extension_allowed VARCHAR(2000) DEFAULT NULL COMMENT '允许列表/规则',
  preview_extension_whitelist_mode TINYINT(1) DEFAULT 0 COMMENT '是否白名单模式（0/1）',
  create_name VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  create_by INT DEFAULT NULL COMMENT '创建人用户 ID',
  create_time TIMESTAMP NULL DEFAULT NULL COMMENT '创建时间',
  update_time TIMESTAMP NULL DEFAULT NULL COMMENT '更新时间',
  update_name VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  update_by INT DEFAULT NULL COMMENT '更新人用户 ID',
  PRIMARY KEY (system_server_setting_preview_extension_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预览扩展（黑白名单等）配置';
