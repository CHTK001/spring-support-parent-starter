DROP TABLE IF EXISTS `proxy_server`;
CREATE TABLE `proxy_server`  (
  `proxy_server_id` int NOT NULL AUTO_INCREMENT COMMENT '系统服务器ID',
  `proxy_server_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器名称',
  `proxy_server_host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器主机',
  `proxy_server_port` int NULL DEFAULT NULL COMMENT '系统服务器端口',
  `proxy_server_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器类型',
  `proxy_server_context_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器上下文路径',
  `proxy_server_status` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '系统服务器启动状态',
  `proxy_server_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器描述',
  `proxy_server_config` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器配置',
  `proxy_server_auto_start` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器是否自动启动',
  `proxy_server_max_connections` int NULL DEFAULT NULL COMMENT '系统服务器最大连接数',
  `proxy_server_timeout` int NULL DEFAULT NULL COMMENT '系统服务器超时时间',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_log`;
CREATE TABLE `proxy_server_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `server_id` int NULL DEFAULT NULL COMMENT '系统服务器ID',
  `filter_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '过滤器类型（SPI类型或类名）',
  `process_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '处理状态（例如：IP限流、黑名单拦截、白名单通过等）',
  `client_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端IP',
  `client_geo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端地理位置信息',
  `access_time` datetime(6) NULL DEFAULT NULL COMMENT '访问时间',
  `duration_ms` bigint NULL DEFAULT NULL COMMENT '处理时长(毫秒)',
  `store_time` datetime(6) NULL DEFAULT NULL COMMENT '存储时间',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 190 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting`;
CREATE TABLE `proxy_server_setting`  (
  `proxy_server_setting_id` int NOT NULL AUTO_INCREMENT COMMENT '系统服务器配置ID',
  `proxy_server_setting_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器配置名称',
  `proxy_server_setting_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系统服务器配置类型',
  `proxy_server_setting_server_id` int NULL DEFAULT NULL COMMENT '关联的服务器ID',
  `proxy_server_setting_order` int NULL DEFAULT NULL COMMENT '排序字段',
  `proxy_server_setting_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置描述',
  `proxy_server_setting_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置状态',
  `proxy_server_setting_class_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置类名',
  `proxy_server_setting_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置版本',
  `proxy_server_setting_config` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置JSON数据',
  `proxy_server_setting_filter_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '启动的时候生成',
  `proxy_server_setting_https_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否启用HTTPS',
  `proxy_server_setting_https_cert_type` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '证书类型: PEM/PFX/JKS',
  `proxy_server_setting_https_pem_cert` longblob NULL COMMENT 'PEM证书(BLOB)',
  `proxy_server_setting_https_pem_key` longblob NULL COMMENT 'PEM私钥(BLOB)',
  `proxy_server_setting_https_pem_key_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'PEM私钥密码',
  `proxy_server_setting_https_keystore` longblob NULL COMMENT 'Keystore容器(BLOB)',
  `proxy_server_setting_https_keystore_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Keystore密码',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting_address_rate_limit`;
CREATE TABLE `proxy_server_setting_address_rate_limit`  (
  `proxy_server_setting_address_rate_limit_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `address_rate_limit_server_id` int NULL DEFAULT NULL COMMENT '服务器ID',
  `address_rate_limit_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地址(接口/路径)',
  `address_rate_limit_qps` int NULL DEFAULT NULL COMMENT '每秒请求阈值',
  `address_rate_limit_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型: RATE_LIMIT/WHITELIST/BLACKLIST',
  `address_rate_limit_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否启用',
  `address_rate_limit_setting_id` int NULL DEFAULT NULL COMMENT '关联的系统配置ID(SystemServerSettingId)',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_address_rate_limit_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting_file_storage`;
CREATE TABLE `proxy_server_setting_file_storage`  (
  `proxy_server_setting_file_storage_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_storage_server_id` int NULL DEFAULT NULL COMMENT '服务器ID',
  `file_storage_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储类型: LOCAL/S3/MINIO/OSS',
  `file_storage_base_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本地根路径',
  `file_storage_endpoint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '对象存储 Endpoint',
  `file_storage_bucket` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Bucket',
  `file_storage_access_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'AccessKey',
  `file_storage_secret_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SecretKey',
  `file_storage_region` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Region',
  `file_storage_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否启用',
  `file_storage_connection_timeout` bigint NULL DEFAULT NULL COMMENT '连接超时',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_file_storage_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting_ip_rate_limit`;
CREATE TABLE `proxy_server_setting_ip_rate_limit`  (
  `proxy_server_setting_ip_rate_limit_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `ip_rate_limit_server_id` int NULL DEFAULT NULL COMMENT '服务器ID',
  `ip_rate_limit_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IP地址(支持CIDR)',
  `ip_rate_limit_qps` int NULL DEFAULT NULL COMMENT '每秒请求阈值',
  `ip_rate_limit_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型: RATE_LIMIT/WHITELIST/BLACKLIST',
  `ip_rate_limit_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否启用',
  `ip_rate_limit_setting_id` int NULL DEFAULT NULL COMMENT '关联的系统配置ID(SystemServerSettingId)',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_ip_rate_limit_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting_item`;
CREATE TABLE `proxy_server_setting_item`  (
  `proxy_server_setting_item_id` int NOT NULL AUTO_INCREMENT COMMENT '系统服务器配置项ID',
  `proxy_server_setting_item_setting_id` int NULL DEFAULT NULL COMMENT '关联的配置ID',
  `proxy_server_setting_item_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置项名称',
  `proxy_server_setting_item_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置项值',
  `proxy_server_setting_item_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置项描述',
  `proxy_server_setting_item_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置项类型',
  `proxy_server_setting_item_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否必填',
  `proxy_server_setting_item_default_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认值',
  `proxy_server_setting_item_validation_rule` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '验证规则',
  `proxy_server_setting_item_order` int NULL DEFAULT NULL COMMENT '排序字段',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_item_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 115 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting_service_discovery`;
CREATE TABLE `proxy_server_setting_service_discovery`  (
  `proxy_server_setting_service_discovery_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_discovery_server_id` int NULL DEFAULT NULL COMMENT '服务器ID',
  `service_discovery_mode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务发现模式: MONITOR/SPRING/TABLE/HAZELCAST',
  `service_discovery_balance` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认负载均衡策略',
  `service_discovery_bean_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Spring容器中的ServiceDiscovery Bean名称',
  `service_discovery_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否启用',
  `service_discovery_monitor_host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `service_discovery_monitor_port` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_service_discovery_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `proxy_server_setting_service_discovery_mapping`;
CREATE TABLE `proxy_server_setting_service_discovery_mapping`  (
  `proxy_server_setting_service_discovery_mapping_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_discovery_server_id` int NULL DEFAULT NULL COMMENT '服务器ID',
  `service_discovery_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务名称',
  `service_discovery_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务地址',
  `service_discovery_weight` int NULL DEFAULT NULL COMMENT '服务权重',
  `service_discovery_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否启用',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_service_discovery_mapping_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft`;
CREATE TABLE `system_soft`  (
  `system_soft_id` int NOT NULL AUTO_INCREMENT COMMENT '系统软件ID',
  `system_soft_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '软件名称',
  `system_soft_code` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '软件代码',
  `system_soft_category` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '软件分类',
  `system_soft_icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '软件图标',
  `system_soft_tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '软件标签',
  `system_soft_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '软件描述',
  `system_soft_registry_id` int NULL DEFAULT NULL COMMENT '镜像仓库ID',
  `system_soft_docker_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Docker镜像名称',
  `system_soft_default_install_method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认安装方式',
  `system_soft_default_install_params` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认安装参数',
  `system_soft_status` int NULL DEFAULT NULL COMMENT '软件状态',
  `system_soft_is_official` int NULL DEFAULT NULL COMMENT '是否官方软件',
  `system_soft_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '软件备注',
  `system_soft_star_count` int NULL DEFAULT NULL COMMENT 'Star 数',
  `system_soft_pull_count` int NULL DEFAULT NULL COMMENT 'Pull 数',
  `system_soft_ext_field1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `system_soft_ext_field2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `system_soft_ext_field3` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段3',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`system_soft_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft_container`;
CREATE TABLE `system_soft_container`  (
  `system_soft_container_id` int NOT NULL AUTO_INCREMENT COMMENT '系统软件容器ID',
  `system_soft_id` int NULL DEFAULT NULL COMMENT '关联的软件ID',
  `system_soft_version_id` int NULL DEFAULT NULL COMMENT '关联的软件版本ID',
  `proxy_server_id` int NULL DEFAULT NULL COMMENT '关联的服务器ID',
  `system_soft_image_id` int NULL DEFAULT NULL COMMENT '关联的镜像ID',
  `system_soft_container_docker_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Docker容器ID',
  `system_soft_container_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器名称',
  `system_soft_container_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器镜像',
  `system_soft_container_image_tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器镜像标签',
  `system_soft_container_status` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '容器状态',
  `system_soft_container_ports` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器端口映射',
  `system_soft_container_env` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器环境变量',
  `system_soft_container_volumes` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器数据卷',
  `system_soft_container_networks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器网络',
  `system_soft_container_command` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器启动命令',
  `system_soft_container_args` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器启动参数',
  `system_soft_container_created_time` datetime(6) NULL DEFAULT NULL COMMENT '容器创建时间',
  `system_soft_container_started_time` datetime(6) NULL DEFAULT NULL COMMENT '容器启动时间',
  `system_soft_container_finished_time` datetime(6) NULL DEFAULT NULL COMMENT '容器结束时间',
  `system_soft_container_restart_count` int NULL DEFAULT NULL COMMENT '容器重启次数',
  `system_soft_container_config` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器配置信息',
  `system_soft_container_health_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器健康状态',
  `system_soft_container_auto_restart` int NULL DEFAULT NULL COMMENT '是否自动重启',
  `system_soft_container_cpu_limit` double NULL DEFAULT NULL COMMENT 'CPU使用限制',
  `system_soft_container_memory_limit` bigint NULL DEFAULT NULL COMMENT '内存使用限制',
  `system_soft_container_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器备注',
  `system_soft_container_ext_field1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `system_soft_container_ext_field2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `system_soft_container_ext_field3` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段3',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`system_soft_container_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft_container_stats`;
CREATE TABLE `system_soft_container_stats`  (
  `system_soft_container_stats_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `system_soft_container_stats_container_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器ID',
  `system_soft_container_stats_container_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '容器名称',
  `system_soft_container_stats_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态',
  `system_soft_container_stats_started_at` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '启动时间',
  `system_soft_container_stats_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像',
  `system_soft_container_stats_command` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '命令',
  `system_soft_container_stats_created` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建时间',
  `system_soft_container_stats_ports` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '端口映射',
  `system_soft_container_stats_labels` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签',
  `system_soft_container_stats_status_detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态详情',
  `system_soft_container_stats_record_time` datetime(6) NULL DEFAULT NULL COMMENT '记录创建时间',
  `system_soft_container_stats_disk_read` bigint NULL DEFAULT NULL COMMENT '磁盘读取字节数',
  `system_soft_container_stats_disk_write` bigint NULL DEFAULT NULL COMMENT '磁盘写入字节数',
  `system_soft_container_stats_cpu_percent` double NULL DEFAULT NULL COMMENT 'CPU使用率 (%)',
  `system_soft_container_stats_memory_usage` bigint NULL DEFAULT NULL COMMENT '内存使用字节数',
  `system_soft_container_stats_memory_limit` bigint NULL DEFAULT NULL COMMENT '内存限制字节数',
  `system_soft_container_stats_memory_percent` double NULL DEFAULT NULL COMMENT '内存使用率 (%)',
  `system_soft_container_stats_network_rx_bytes` bigint NULL DEFAULT NULL COMMENT '网络接收字节数',
  `system_soft_container_stats_network_tx_bytes` bigint NULL DEFAULT NULL COMMENT '网络发送字节数',
  `system_soft_container_id` int NULL DEFAULT NULL COMMENT '容器关联ID',
  `system_soft_image_id` int NULL DEFAULT NULL COMMENT '镜像关联ID',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`system_soft_container_stats_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft_image`;
CREATE TABLE `system_soft_image`  (
  `system_soft_image_id` int NOT NULL AUTO_INCREMENT COMMENT '系统软件镜像ID',
  `system_soft_id` int NULL DEFAULT NULL COMMENT '关联的软件ID',
  `system_soft_image_server_id` int NULL DEFAULT NULL COMMENT '关联的服务器ID',
  `system_soft_image_server_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务器名称',
  `system_soft_image_image_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Docker镜像ID',
  `system_soft_image_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像名称',
  `system_soft_image_tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像标签',
  `system_soft_image_full_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像完整名称',
  `system_soft_image_repository_id` int NULL DEFAULT NULL COMMENT '镜像仓库地址ID',
  `system_soft_image_size` bigint NULL DEFAULT NULL COMMENT '镜像大小',
  `system_soft_image_created` datetime(6) NULL DEFAULT NULL COMMENT '镜像创建时间',
  `system_soft_image_architecture` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像架构',
  `system_soft_image_os_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作系统类型',
  `system_soft_image_digest` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像摘要',
  `system_soft_image_status` varchar(11) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NULL DEFAULT NULL COMMENT '镜像状态',
  `system_soft_image_is_official` int NULL DEFAULT NULL COMMENT '是否官方镜像',
  `system_soft_image_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像描述',
  `system_soft_image_tag_count` int NULL DEFAULT NULL COMMENT '标签数量',
  `system_soft_image_pull_count` bigint NULL DEFAULT NULL COMMENT '拉取次数',
  `system_soft_image_star_count` bigint NULL DEFAULT NULL COMMENT '星标数量',
  `system_soft_image_last_pulled` datetime(6) NULL DEFAULT NULL COMMENT '最后拉取时间',
  `system_soft_image_last_pushed` datetime(6) NULL DEFAULT NULL COMMENT '最后推送时间',
  `system_soft_image_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像备注',
  `system_soft_image_ext_field1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `system_soft_image_ext_field2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `system_soft_image_ext_field3` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段3',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`system_soft_image_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft_record`;
CREATE TABLE `system_soft_record`  (
  `system_soft_record_id` int NOT NULL AUTO_INCREMENT COMMENT '系统软件记录ID',
  `system_soft_id` int NULL DEFAULT NULL COMMENT '关联的软件ID',
  `system_soft_version_id` int NULL DEFAULT NULL COMMENT '关联的软件版本ID',
  `proxy_server_id` int NULL DEFAULT NULL COMMENT '关联的服务器ID',
  `system_soft_record_operation_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作类型',
  `system_soft_record_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作消息',
  `system_soft_record_method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作方法',
  `system_soft_record_env_params` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '环境参数',
  `system_soft_record_params` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作参数',
  `system_soft_record_time` datetime(6) NULL DEFAULT NULL COMMENT '记录时间',
  `system_soft_record_status` int NULL DEFAULT NULL COMMENT '操作状态',
  `system_soft_record_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作用户',
  `system_soft_record_container_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联的容器ID',
  `system_soft_record_start_time` datetime(6) NULL DEFAULT NULL COMMENT '操作开始时间',
  `system_soft_record_end_time` datetime(6) NULL DEFAULT NULL COMMENT '操作结束时间',
  `system_soft_record_duration` bigint NULL DEFAULT NULL COMMENT '操作耗时',
  `system_soft_record_error_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `system_soft_record_result` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作结果',
  `system_soft_record_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '记录备注',
  `system_soft_record_ext_field1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `system_soft_record_ext_field2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `system_soft_record_ext_field3` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段3',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`system_soft_record_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft_registry`;
CREATE TABLE `system_soft_registry`  (
  `system_soft_registry_id` int NOT NULL AUTO_INCREMENT COMMENT '系统软件镜像仓库ID',
  `system_soft_registry_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库名称',
  `system_soft_registry_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库类型',
  `system_soft_registry_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库地址',
  `system_soft_registry_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库用户名',
  `system_soft_registry_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库密码',
  `system_soft_registry_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库邮箱',
  `system_soft_registry_server_id` int NULL DEFAULT NULL COMMENT '绑定服务器ID',
  `system_soft_registry_ssl_enabled` int NULL DEFAULT NULL COMMENT '是否启用SSL',
  `system_soft_registry_support_sync` int NULL DEFAULT NULL COMMENT '是否支持同步',
  `system_soft_registry_timeout` int NULL DEFAULT NULL COMMENT '连接超时时间(秒)',
  `system_soft_registry_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库描述',
  `system_soft_registry_config` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '仓库配置信息',
  `system_soft_registry_last_connect_time` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后连接时间',
  `system_soft_registry_connect_status` int NULL DEFAULT NULL COMMENT '连接状态',
  `system_soft_registry_error_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '连接错误信息',
  `system_soft_registry_sort` int NULL DEFAULT NULL COMMENT '排序号',
  `system_soft_registry_status` int NULL DEFAULT NULL COMMENT '状态(0:禁用, 1:启用)',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  `system_soft_registry_active` int NULL DEFAULT NULL COMMENT '是否激活，激活的仓库用于软件搜索',
  PRIMARY KEY (`system_soft_registry_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'docker软件库' ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `system_soft_version`;
CREATE TABLE `system_soft_version`  (
  `system_soft_version_id` int NOT NULL AUTO_INCREMENT COMMENT '系统软件版本ID',
  `system_soft_id` int NULL DEFAULT NULL COMMENT '关联的软件ID',
  `system_soft_version_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本名称',
  `sys_soft_version_digest` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `system_soft_version_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本号',
  `system_soft_version_download_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本下载地址',
  `system_soft_version_image_tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '镜像标签',
  `system_soft_version_install_template` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '安装模板',
  `system_soft_version_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本描述',
  `system_soft_version_size` bigint NULL DEFAULT NULL COMMENT '版本大小',
  `system_soft_version_status` int NULL DEFAULT NULL COMMENT '版本状态',
  `system_soft_version_is_latest` int NULL DEFAULT NULL COMMENT '是否为最新版本',
  `system_soft_version_is_stable` int NULL DEFAULT NULL COMMENT '是否为稳定版本',
  `system_soft_version_release_time` datetime(6) NULL DEFAULT NULL COMMENT '发布时间',
  `system_soft_version_min_requirements` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最小系统要求',
  `system_soft_version_recommended_requirements` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '推荐系统要求',
  `system_soft_version_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本备注',
  `system_soft_version_ext_field1` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段1',
  `system_soft_version_ext_field2` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段2',
  `system_soft_version_ext_field3` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '扩展字段3',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`system_soft_version_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'docker软件版本' ROW_FORMAT = Dynamic;



DROP TABLE IF EXISTS `maintenance_group`;
CREATE TABLE `maintenance_group`
(
    `maintenance_group_id`     int(11)                                                       NOT NULL AUTO_INCREMENT COMMENT '维护组ID',
    `maintenance_group_name`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '维护组名称',
    `maintenance_group_desc`   varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '维护组描述',
    `maintenance_group_status` int(11)                                                       NULL DEFAULT 1 COMMENT '状态,0:停用,1:启用',
    `create_time`              datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`              datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`                varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '创建者',
    `update_by`                varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '更新者',
    PRIMARY KEY (`maintenance_group_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '维护组表'
  ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `maintenance_host`;
CREATE TABLE `maintenance_host`
(
    `maintenance_host_id`       int(11)                                                       NOT NULL AUTO_INCREMENT COMMENT '维护主机ID',
    `maintenance_group_id`      int(11)                                                       NULL DEFAULT NULL COMMENT '维护组ID',
    `maintenance_host_address`  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主机地址',
    `maintenance_host_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '账号',
    `maintenance_host_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密码',
    `maintenance_host_port`     int(11)                                                       NULL DEFAULT 22 COMMENT '端口',
    `maintenance_host_status`   int(11)                                                       NULL DEFAULT 1 COMMENT '状态,0:停用,1:启用',
    `create_time`               datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`                 varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '创建者',
    `update_by`                 varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '更新者',
    PRIMARY KEY (`maintenance_host_id`) USING BTREE,
    INDEX `idx_group_id` (`maintenance_group_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '维护主机表'
  ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `maintenance_script`;
CREATE TABLE `maintenance_script`
(
    `maintenance_script_id`      int(11)                                                       NOT NULL AUTO_INCREMENT COMMENT '脚本ID',
    `maintenance_group_id`       int(11)                                                       NULL DEFAULT NULL COMMENT '维护组ID',
    `maintenance_script_name`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '脚本名称',
    `maintenance_script_path`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '脚本路径',
    `maintenance_script_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci         NULL COMMENT '脚本内容',
    `maintenance_script_desc`    varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '脚本描述',
    `maintenance_script_status`  int(11)                                                       NULL DEFAULT 1 COMMENT '状态,0:停用,1:启用',
    `create_time`                datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`                  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '创建者',
    `update_by`                  varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '更新者',
    PRIMARY KEY (`maintenance_script_id`) USING BTREE,
    INDEX `idx_group_id` (`maintenance_group_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '维护脚本表'
  ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `maintenance_file`;
CREATE TABLE `maintenance_file`
(
    `maintenance_file_id`          int(11)                                                       NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `maintenance_group_id`         int(11)                                                       NULL DEFAULT NULL COMMENT '维护组ID',
    `maintenance_file_name`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件名称',
    `maintenance_file_path`        varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',
    `maintenance_file_type`        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '文件类型',
    `maintenance_file_size`        bigint(20)                                                    NULL DEFAULT NULL COMMENT '文件大小',
    `maintenance_file_is_extract`  int(11)                                                       NULL DEFAULT 0 COMMENT '是否解压,0:否,1:是',
    `maintenance_file_is_override` int(11)                                                       NULL DEFAULT 0 COMMENT '是否覆盖,0:否,1:是',
    `maintenance_file_status`      int(11)                                                       NULL DEFAULT 1 COMMENT '状态,0:停用,1:启用',
    `create_time`                  datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                  datetime                                                      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`                    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '创建者',
    `update_by`                    varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci  NULL DEFAULT NULL COMMENT '更新者',
    PRIMARY KEY (`maintenance_file_id`) USING BTREE,
    INDEX `idx_group_id` (`maintenance_group_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '维护文件表'
  ROW_FORMAT = Dynamic;

DROP TABLE IF EXISTS `maintenance_task`;
CREATE TABLE `maintenance_task`
(
    `maintenance_task_id`     int(11)                                                      NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `maintenance_group_id`    int(11)                                                      NULL DEFAULT NULL COMMENT '维护组ID',
    `maintenance_host_id`     int(11)                                                      NULL DEFAULT NULL COMMENT '维护主机ID',
    `maintenance_script_id`   int(11)                                                      NULL DEFAULT NULL COMMENT '脚本ID',
    `maintenance_file_id`     int(11)                                                      NULL DEFAULT NULL COMMENT '文件ID',
    `maintenance_task_type`   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务类型,SCRIPT:脚本,FILE:文件',
    `maintenance_task_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务状态,PENDING:待执行,RUNNING:执行中,SUCCESS:成功,FAILED:失败',
    `maintenance_task_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci        NULL COMMENT '任务结果',
    `maintenance_task_error`  text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci        NULL COMMENT '错误信息',
    `create_time`             datetime                                                     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             datetime                                                     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`               varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建者',
    `update_by`               varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新者',
    PRIMARY KEY (`maintenance_task_id`) USING BTREE,
    INDEX `idx_group_id` (`maintenance_group_id`) USING BTREE,
    INDEX `idx_host_id` (`maintenance_host_id`) USING BTREE,
    INDEX `idx_script_id` (`maintenance_script_id`) USING BTREE,
    INDEX `idx_file_id` (`maintenance_file_id`) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '维护任务表'
  ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
-- ==================== 缺失表补充 ====================

DROP TABLE IF EXISTS `monitor_skywalking_config`;
CREATE TABLE `monitor_skywalking_config`  (
  `skywalking_config_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `skywalking_config_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置名称',
  `skywalking_config_host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务地址',
  `skywalking_config_port` int NULL DEFAULT NULL COMMENT '端口',
  `skywalking_config_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
  `skywalking_config_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密码',
  `skywalking_config_status` int NULL DEFAULT NULL COMMENT '状态 0:禁用 1:启用',
  `skywalking_config_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `skywalking_config_use_https` int NULL DEFAULT NULL COMMENT '是否使用HTTPS 0:否 1:是',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`skywalking_config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SkyWalking配置信息' ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `monitor_sys_file_share`;
CREATE TABLE `monitor_sys_file_share`  (
  `share_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享ID(UUID)',
  `server_id` bigint NULL DEFAULT NULL COMMENT '服务器ID',
  `bucket` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储桶',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件名',
  `share_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分享类型：SHARE-普通分享，FLASH-闪图',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间',
  `view_count` int NULL DEFAULT 0 COMMENT '查看次数',
  `max_view_count` int NULL DEFAULT -1 COMMENT '最大查看次数，-1表示无限制',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `is_deleted` int NULL DEFAULT 0 COMMENT '是否已删除：0-否，1-是',
  PRIMARY KEY (`share_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件分享记录' ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `monitor_sys_gen_config`;
CREATE TABLE `monitor_sys_gen_config`  (
  `monitor_sys_gen_config_id` int NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `monitor_sys_gen_config_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置键',
  `monitor_sys_gen_config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '配置值',
  `monitor_sys_gen_config_description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置描述',
  `monitor_sys_gen_config_env` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '环境',
  `monitor_sys_gen_config_status` int NULL DEFAULT NULL COMMENT '配置状态 0-禁用 1-启用',
  `monitor_sys_gen_config_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属应用',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`monitor_sys_gen_config_id`) USING BTREE,
  UNIQUE INDEX `uk_config_key_env`(`monitor_sys_gen_config_key`, `monitor_sys_gen_config_env`, `monitor_sys_gen_config_app`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置管理' ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `monitor_sys_gen_config_push_history`;
CREATE TABLE `monitor_sys_gen_config_push_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '历史ID',
  `monitor_sys_gen_config_id` int NULL DEFAULT NULL COMMENT '配置ID',
  `monitor_sys_gen_config_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置键',
  `monitor_sys_gen_config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '配置值',
  `monitor_sys_gen_server_id` int NULL DEFAULT NULL COMMENT '服务器ID',
  `monitor_sys_gen_server_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务器名称',
  `push_success` int NULL DEFAULT NULL COMMENT '是否成功 0-失败 1-成功',
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `push_time` datetime(6) NULL DEFAULT NULL COMMENT '推送时间',
  `operator` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作人',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_config_id`(`monitor_sys_gen_config_id`) USING BTREE,
  INDEX `idx_server_id`(`monitor_sys_gen_server_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置推送历史' ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `monitor_sys_gen_server_log_config`;
CREATE TABLE `monitor_sys_gen_server_log_config`  (
  `monitor_sys_gen_server_log_config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `monitor_sys_gen_server_id` int NOT NULL COMMENT '关联服务器ID',
  `monitor_sys_gen_server_log_retention_days` int NULL DEFAULT 30 COMMENT '日志保留天数',
  `monitor_sys_gen_server_log_enabled` int NULL DEFAULT 1 COMMENT '是否启用日志收集(0:否 1:是)',
  `monitor_sys_gen_server_log_level_filter` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '日志级别过滤器',
  `monitor_sys_gen_server_log_source_filter` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '日志来源过滤器',
  `monitor_sys_gen_server_log_max_size_mb` int NULL DEFAULT 100 COMMENT '单个日志文件最大大小(MB)',
  `monitor_sys_gen_server_log_max_files` int NULL DEFAULT 10 COMMENT '最大日志文件数量',
  `monitor_sys_gen_server_log_collection_interval` int NULL DEFAULT 60 COMMENT '日志收集间隔(秒)',
  `monitor_sys_gen_server_log_batch_size` int NULL DEFAULT 1000 COMMENT '批量处理大小',
  `monitor_sys_gen_server_log_compression_enabled` int NULL DEFAULT 1 COMMENT '是否启用压缩(0:否 1:是)',
  `monitor_sys_gen_server_log_real_time_enabled` int NULL DEFAULT 0 COMMENT '是否启用实时日志(0:否 1:是)',
  `monitor_sys_gen_server_log_file_patterns` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '日志文件匹配模式(JSON格式)',
  `monitor_sys_gen_server_log_exclude_patterns` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '排除模式(JSON格式)',
  `monitor_sys_gen_server_log_custom_parsers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '自定义解析器配置(JSON格式)',
  `monitor_sys_gen_server_log_alert_rules` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '告警规则配置(JSON格式)',
  `monitor_sys_gen_server_log_create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `monitor_sys_gen_server_log_update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `monitor_sys_gen_server_log_config_status` int NULL DEFAULT 1 COMMENT '配置状态(0:禁用 1:启用)',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`monitor_sys_gen_server_log_config_id`) USING BTREE,
  INDEX `idx_server_id`(`monitor_sys_gen_server_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '服务器日志配置' ROW_FORMAT = DYNAMIC;

DROP TABLE IF EXISTS `proxy_server_setting_preview_extension`;
CREATE TABLE `proxy_server_setting_preview_extension`  (
  `proxy_server_setting_preview_extension_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `preview_extension_server_id` int NOT NULL COMMENT '所属服务器ID',
  `preview_extension_disabled` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '禁用预览的扩展名列表（逗号分隔）',
  `preview_extension_allowed` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '允许预览的扩展名列表（逗号分隔）',
  `preview_extension_whitelist_mode` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用白名单模式',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`proxy_server_setting_preview_extension_id`) USING BTREE,
  INDEX `idx_server_id`(`preview_extension_server_id`) USING BTREE
 ) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '代理服务器预览扩展配置' ROW_FORMAT = DYNAMIC;

