-- ============================================================
-- AI 模块初始化脚本（仅表结构）
-- 说明：
-- 本文件同时包含完整建表语句和初始化数据。
-- ============================================================

-- H2 兼容模式下跳过 MySQL 专属指令
-- SET NAMES utf8mb4;
-- SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. AI 模块定义表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_module`;
CREATE TABLE IF NOT EXISTS `sys_ai_module` (
  `sys_ai_module_id` INT NOT NULL AUTO_INCREMENT COMMENT '模块主键ID',
  `sys_ai_module_name` VARCHAR(255) NOT NULL COMMENT '模块名称',
  `sys_ai_module_code` VARCHAR(255) NOT NULL COMMENT '模块编码',
  `sys_ai_module_model_id` VARCHAR(255) DEFAULT NULL COMMENT '模型ID',
  `sys_ai_module_image` VARCHAR(1024) DEFAULT NULL COMMENT '模型图片',
  `sys_ai_module_type` VARCHAR(64) NOT NULL COMMENT '模块类型',
  `sys_ai_module_vendor` VARCHAR(64) DEFAULT NULL COMMENT '默认厂家编码',
  `sys_ai_module_manufacturers` VARCHAR(64) DEFAULT NULL COMMENT '可用厂家编码列表',
  `sys_ai_module_url` VARCHAR(512) DEFAULT NULL COMMENT '模块调用地址',
  `sys_ai_module_version` VARCHAR(64) DEFAULT NULL COMMENT '模块版本号',
  `sys_ai_module_status` INT NOT NULL DEFAULT 1 COMMENT '模块状态',
  `sys_ai_module_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `sys_ai_module_vlm` INT NOT NULL DEFAULT 0 COMMENT '是否支持视觉语言模型',
  `sys_ai_module_support_think` INT NOT NULL DEFAULT 0 COMMENT '是否支持思考模式',
  `sys_ai_module_support_optimize` INT NOT NULL DEFAULT 0 COMMENT '是否支持输入优化',
  `sys_ai_module_concurrency` INT DEFAULT 1 COMMENT '默认并发数',
  `sys_ai_module_local` INT NOT NULL DEFAULT 0 COMMENT '是否本地模型',
  `sys_ai_module_input_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '输入单价',
  `sys_ai_module_output_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '输出单价',
  `sys_ai_module_cache_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '缓存单价',
  `sys_ai_module_generate_limit` INT DEFAULT 0 COMMENT '生成次数限制',
  `sys_ai_module_support_template` INT NOT NULL DEFAULT 0 COMMENT '是否支持模板',
  `sys_ai_module_support_style` INT NOT NULL DEFAULT 0 COMMENT '是否支持风格',
  `sys_ai_module_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注说明',
	  `sys_ai_module_api_expose` INT NOT NULL DEFAULT 1 COMMENT '是否作为接口暴露：1暴露，0不暴露',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_module_id`),
  UNIQUE KEY `uk_sys_ai_module_code` (`sys_ai_module_code`)
) COMMENT='AI 模块定义表';

-- ============================================================
-- 2. AI 厂家定义表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_manufacturer`;
CREATE TABLE IF NOT EXISTS `sys_ai_manufacturer` (
  `sys_ai_manufacturer_id` INT NOT NULL AUTO_INCREMENT COMMENT '厂家主键ID',
  `sys_ai_manufacturer_code` VARCHAR(128) NOT NULL COMMENT '厂家编码',
  `sys_ai_manufacturer_name` VARCHAR(255) NOT NULL COMMENT '厂家名称',
  `sys_ai_manufacturer_factory` VARCHAR(128) DEFAULT NULL COMMENT '适配工厂编码',
  `sys_ai_manufacturer_base_url` VARCHAR(512) DEFAULT NULL COMMENT '基础调用地址',
  `sys_ai_manufacturer_app_key` VARCHAR(255) DEFAULT NULL COMMENT '应用Key',
  `sys_ai_manufacturer_app_secret` VARCHAR(255) DEFAULT NULL COMMENT '应用Secret',
  `sys_ai_manufacturer_icon` VARCHAR(1024) DEFAULT NULL COMMENT '图标地址',
  `sys_ai_manufacturer_logo` VARCHAR(1024) DEFAULT NULL COMMENT 'Logo地址',
  `sys_ai_manufacturer_website` VARCHAR(1024) DEFAULT NULL COMMENT '官网地址',
  `sys_ai_manufacturer_openai_compatible` INT NOT NULL DEFAULT 0 COMMENT '是否兼容OpenAI协议',
  `sys_ai_manufacturer_claude_compatible` INT NOT NULL DEFAULT 0 COMMENT '是否兼容Claude协议',
  `sys_ai_manufacturer_app_id` VARCHAR(255) DEFAULT NULL COMMENT '应用ID',
  `sys_ai_manufacturer_api_secret` VARCHAR(255) DEFAULT NULL COMMENT 'API密钥',
  `sys_ai_manufacturer_api_version` VARCHAR(128) DEFAULT NULL COMMENT 'API版本',
  `sys_ai_manufacturer_api_domain` VARCHAR(255) DEFAULT NULL COMMENT 'API域名',
  `sys_ai_manufacturer_memory_enabled` INT NOT NULL DEFAULT 0 COMMENT '是否启用上下文记忆',
  `sys_ai_manufacturer_compression_enabled` INT NOT NULL DEFAULT 0 COMMENT '是否启用会话压缩',
  `sys_ai_manufacturer_compression_threshold` INT NOT NULL DEFAULT 12 COMMENT '会话压缩阈值',
  `sys_ai_manufacturer_status` INT NOT NULL DEFAULT 1 COMMENT '厂家状态',
  `sys_ai_manufacturer_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `sys_ai_manufacturer_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注说明',
  `sys_ai_manufacturer_api_expose` INT NOT NULL DEFAULT 1 COMMENT '是否作为接口暴露：1暴露，0不暴露',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_manufacturer_id`),
  UNIQUE KEY `uk_sys_ai_manufacturer_code` (`sys_ai_manufacturer_code`)
) COMMENT='AI 厂家定义表';

-- ============================================================
-- 2.1 AI 会话压缩上下文表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_session_compression`;
CREATE TABLE IF NOT EXISTS `sys_ai_session_compression` (
  `sys_ai_session_compression_id` INT NOT NULL AUTO_INCREMENT COMMENT '压缩上下文主键ID',
  `sys_ai_session_id` INT NOT NULL COMMENT '会话ID',
  `sys_ai_manufacturer_code` VARCHAR(128) DEFAULT NULL COMMENT '厂家编码',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '模型编码',
  `sys_ai_compression_message_count` BIGINT DEFAULT 0 COMMENT '压缩时消息数量',
  `sys_ai_compression_from_message_id` INT DEFAULT NULL COMMENT '压缩基准消息ID',
  `sys_ai_compression_context` LONGTEXT COMMENT '压缩后的上下文',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_session_compression_id`),
  UNIQUE KEY `uk_ai_session_compression_session` (`sys_ai_session_id`)
) COMMENT='AI 会话压缩上下文表';

-- ============================================================
-- 3. AI 会话表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_session`;
CREATE TABLE IF NOT EXISTS `sys_ai_session` (
  `sys_ai_session_id` INT NOT NULL AUTO_INCREMENT COMMENT '会话主键ID',
  `sys_ai_session_title` VARCHAR(255) NOT NULL COMMENT '会话标题',
  `sys_ai_session_type` VARCHAR(64) NOT NULL DEFAULT 'CHAT' COMMENT '会话类型',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '关联模块编码',
  `sys_user_id` VARCHAR(255) NOT NULL COMMENT '关联用户ID',
  `sys_ai_session_status` INT NOT NULL DEFAULT 1 COMMENT '会话状态',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_session_id`),
  KEY `idx_ai_session_user_type` (`sys_user_id`, `sys_ai_session_type`)
) COMMENT='AI 会话表';

-- ============================================================
-- 4. AI 消息表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_message`;
CREATE TABLE IF NOT EXISTS `sys_ai_message` (
  `sys_ai_message_id` INT NOT NULL AUTO_INCREMENT COMMENT '消息主键ID',
  `sys_ai_session_id` INT NOT NULL COMMENT '会话ID',
  `sys_ai_message_role` VARCHAR(32) NOT NULL COMMENT '消息角色',
  `sys_ai_message_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型',
  `sys_ai_message_content` LONGTEXT COMMENT '消息内容',
  `sys_ai_message_attachment` VARCHAR(1024) DEFAULT NULL COMMENT '附件地址',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_message_id`),
  KEY `idx_ai_message_session` (`sys_ai_session_id`)
) COMMENT='AI 会话消息表';

-- ============================================================
-- 5. AI 任务表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_task`;
CREATE TABLE IF NOT EXISTS `sys_ai_task` (
  `sys_ai_task_id` INT NOT NULL AUTO_INCREMENT COMMENT '任务主键ID',
  `sys_ai_task_uid` VARCHAR(64) DEFAULT NULL COMMENT '任务唯一标识',
  `sys_ai_task_type` VARCHAR(64) NOT NULL COMMENT '任务类型',
  `sys_project_id` INT DEFAULT NULL COMMENT '项目ID',
  `sys_ai_module_id` INT DEFAULT NULL COMMENT '模块ID',
  `sys_ai_module_code` VARCHAR(255) DEFAULT NULL COMMENT '模块编码',
  `sys_ai_task_prompt` TEXT COMMENT '任务提示词',
  `sys_ai_task_condition` LONGTEXT COMMENT '任务条件JSON',
  `sys_ai_task_request` LONGTEXT COMMENT '任务请求JSON',
  `sys_ai_task_result` LONGTEXT COMMENT '任务结果JSON',
  `sys_ai_task_url` LONGTEXT COMMENT '输出资源地址',
  `sys_ai_task_local_url` LONGTEXT COMMENT '本地资源地址',
  `sys_ai_task_request_id` VARCHAR(128) DEFAULT NULL COMMENT '远程请求ID',
  `sys_ai_task_status` VARCHAR(64) NOT NULL DEFAULT 'SUCCESS' COMMENT '任务状态',
  `sys_ai_task_progress` INT DEFAULT 100 COMMENT '任务进度',
  `sys_user_id` VARCHAR(255) NOT NULL COMMENT '用户ID',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_task_id`),
  KEY `idx_ai_task_user_type` (`sys_user_id`, `sys_ai_task_type`)
) COMMENT='AI 任务表';

-- ============================================================
-- 6. AI 风格表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_style`;
CREATE TABLE IF NOT EXISTS `sys_ai_style` (
  `sys_ai_style_id` INT NOT NULL AUTO_INCREMENT COMMENT '风格主键ID',
  `sys_ai_module_id` INT NOT NULL COMMENT '模块ID',
  `sys_ai_style_name` VARCHAR(255) NOT NULL COMMENT '风格名称',
  `sys_ai_style_code` VARCHAR(255) NOT NULL COMMENT '风格编码',
  `sys_ai_style_image` VARCHAR(1024) DEFAULT NULL COMMENT '风格示意图',
  `sys_ai_style_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `sys_ai_style_status` INT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_style_id`),
  KEY `idx_ai_style_module` (`sys_ai_module_id`),
  UNIQUE KEY `uk_ai_style_code` (`sys_ai_module_id`, `sys_ai_style_code`)
) COMMENT='AI 风格表';

-- ============================================================
-- 7. AI 模板表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_template`;
CREATE TABLE IF NOT EXISTS `sys_ai_template` (
  `sys_ai_template_id` INT NOT NULL AUTO_INCREMENT COMMENT '模板主键ID',
  `sys_ai_module_id` INT NOT NULL COMMENT '模块ID',
  `sys_ai_template_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `sys_ai_template_name` VARCHAR(255) NOT NULL COMMENT '模板名称',
  `sys_ai_template_type` VARCHAR(64) DEFAULT NULL COMMENT '模板类型',
  `sys_ai_template_address` VARCHAR(1024) DEFAULT NULL COMMENT '模板地址',
  `sys_ai_template_remark` VARCHAR(512) DEFAULT NULL COMMENT '模板备注',
  `sys_ai_template_model_id` VARCHAR(255) DEFAULT NULL COMMENT '模型ID',
  `sys_ai_template_model_name` VARCHAR(255) DEFAULT NULL COMMENT '模型名称',
  `sys_ai_template_category` VARCHAR(255) DEFAULT NULL COMMENT '模板分类',
  `sys_ai_template_train_model` VARCHAR(255) DEFAULT NULL COMMENT '训练模型',
  `sys_ai_template_recommend_prompt` LONGTEXT COMMENT '推荐提示词',
  `sys_ai_template_resolutions` VARCHAR(512) DEFAULT NULL COMMENT '支持分辨率',
  `sys_ai_template_word` VARCHAR(255) DEFAULT NULL COMMENT '关键词',
  `sys_ai_template_status` INT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_template_id`),
  KEY `idx_ai_template_module` (`sys_ai_module_id`)
) COMMENT='AI 模板表';

-- ============================================================
-- 8. AI 模块配置表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_setting`;
CREATE TABLE IF NOT EXISTS `sys_ai_setting` (
  `sys_ai_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT '模块配置主键ID',
  `sys_ai_module_id` INT NOT NULL COMMENT '模块ID',
  `sys_ai_setting_field` VARCHAR(128) NOT NULL COMMENT '配置字段',
  `sys_ai_setting_value` LONGTEXT COMMENT '配置值',
  `sys_ai_setting_value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '配置值类型',
  `sys_ai_setting_description` VARCHAR(512) DEFAULT NULL COMMENT '配置说明',
  `sys_ai_setting_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `sys_ai_setting_status` INT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_setting_id`),
  KEY `idx_ai_setting_module` (`sys_ai_module_id`),
  UNIQUE KEY `uk_ai_setting_field` (`sys_ai_module_id`, `sys_ai_setting_field`)
) COMMENT='AI 模块配置表';

-- ============================================================
-- 9. AI 全局配置表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_global_setting`;
CREATE TABLE IF NOT EXISTS `sys_ai_global_setting` (
  `sys_ai_global_setting_id` INT NOT NULL AUTO_INCREMENT COMMENT '全局配置主键ID',
  `sys_ai_global_setting_group` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '配置分组',
  `sys_ai_global_setting_field` VARCHAR(128) NOT NULL COMMENT '配置字段',
  `sys_ai_global_setting_value` LONGTEXT COMMENT '配置值',
  `sys_ai_global_setting_value_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '配置值类型',
  `sys_ai_global_setting_description` VARCHAR(512) DEFAULT NULL COMMENT '配置说明',
  `sys_ai_global_setting_sort` INT NOT NULL DEFAULT 0 COMMENT '排序值',
  `sys_ai_global_setting_status` INT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_global_setting_id`),
  UNIQUE KEY `uk_ai_global_setting_field` (`sys_ai_global_setting_group`, `sys_ai_global_setting_field`)
) COMMENT='AI 全局配置表';

-- ============================================================
-- 10. AI 应用密钥表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_app_key`;
CREATE TABLE IF NOT EXISTS `sys_ai_app_key` (
  `sys_ai_app_key_id` INT NOT NULL AUTO_INCREMENT COMMENT '应用密钥主键ID',
  `sys_ai_app_key_app_id` VARCHAR(128) DEFAULT NULL COMMENT '应用ID',
  `sys_ai_app_key_app_key` VARCHAR(128) NOT NULL COMMENT '访问Key',
  `sys_ai_app_key_secret` VARCHAR(256) NOT NULL COMMENT '访问Secret',
  `sys_ai_app_key_name` VARCHAR(255) DEFAULT NULL COMMENT '密钥名称',
  `sys_ai_app_key_value` VARCHAR(512) DEFAULT NULL COMMENT '密钥值',
  `sys_ai_app_key_provider_id` BIGINT DEFAULT NULL COMMENT '关联供应商 ID',
  `sys_ai_app_key_model` VARCHAR(255) DEFAULT NULL COMMENT '限定使用的模型',
  `sys_ai_app_key_max_use` BIGINT DEFAULT NULL COMMENT '次数上限（null 表示不限）',
  `sys_ai_app_key_enabled` INT DEFAULT 1 COMMENT '是否启用',
  `sys_ai_app_key_expire_datetime` DATETIME DEFAULT NULL COMMENT '过期时间（null 表示不限）',
  `sys_user_id` INT DEFAULT NULL COMMENT '所属用户ID',
  `sys_ai_app_key_status` INT NOT NULL DEFAULT 1 COMMENT '状态',
  `sys_ai_app_key_delete` INT NOT NULL DEFAULT 0 COMMENT '删除标记',
  `sys_ai_app_key_group_id` INT DEFAULT NULL COMMENT '分组 ID',
  `sys_ai_app_key_expire_time` BIGINT DEFAULT NULL COMMENT '过期时间戳',
  `sys_ai_app_key_valid_days` INT DEFAULT NULL COMMENT '有效天数',
  `sys_ai_app_key_ip_whitelist` VARCHAR(512) DEFAULT NULL COMMENT 'IP白名单',
  `sys_ai_app_key_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注说明',
  `sys_ai_app_key_last_used_time` BIGINT DEFAULT NULL COMMENT '最近使用时间戳',
  `sys_ai_app_key_use_count` INT DEFAULT 0 COMMENT '使用次数',
  `sys_ai_app_key_max_tokens` BIGINT DEFAULT NULL COMMENT 'Token 总量上限（null 表示不限）',
  `sys_ai_app_key_total_tokens` BIGINT DEFAULT 0 COMMENT '已使用 Token 累计值',
  `sys_ai_app_key_scope` VARCHAR(255) DEFAULT NULL COMMENT '授权范围',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_app_key_id`),
  UNIQUE KEY `uk_ai_app_key_value` (`sys_ai_app_key_app_key`)
) COMMENT='AI 应用密钥表';

-- ============================================================
-- 11. AI 人脸库表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_face_library`;
CREATE TABLE IF NOT EXISTS `sys_ai_face_library` (
  `sys_ai_face_library_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_face_library_name` VARCHAR(128) NOT NULL COMMENT '人脸名称/标签',
  `sys_ai_face_library_full_name` VARCHAR(128) DEFAULT NULL COMMENT '姓名',
  `sys_ai_face_library_id_card` VARCHAR(64) DEFAULT NULL COMMENT '身份证号',
  `sys_ai_face_library_vector_id` VARCHAR(128) NOT NULL COMMENT '向量存储中的向量ID',
  `sys_ai_face_library_thumbnail` LONGTEXT COMMENT '人脸缩略图(base64)',
  `sys_ai_face_library_group` VARCHAR(64) DEFAULT 'default' COMMENT '人脸分组',
  `sys_ai_face_library_meta` VARCHAR(2048) DEFAULT NULL COMMENT '扩展元数据(JSON)',
  `sys_ai_face_library_status` INT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_face_library_id`),
  UNIQUE KEY `uk_face_vector_id` (`sys_ai_face_library_vector_id`),
  KEY `idx_face_group` (`sys_ai_face_library_group`),
  KEY `idx_face_name` (`sys_ai_face_library_name`)
) COMMENT='AI 人脸库表';

-- ============================================================
-- 11.1 AI 人脸识别记录表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_face_recognize_record`;
CREATE TABLE IF NOT EXISTS `sys_ai_face_recognize_record` (
  `sys_ai_face_recognize_record_id` INT NOT NULL AUTO_INCREMENT,
  `sys_ai_face_recognize_record_thumbnail` LONGTEXT COMMENT '识别缩略图(base64)',
  `sys_ai_face_recognize_record_result` JSON COMMENT '识别结果JSON',
  `sys_ai_face_recognize_record_face_count` INT DEFAULT 0 COMMENT '检测到的人脸数',
  `sys_ai_face_recognize_record_match_count` INT DEFAULT 0 COMMENT '匹配到的人脸数',
  `sys_ai_face_recognize_record_group` VARCHAR(64) DEFAULT NULL COMMENT '识别时指定的分组',
  `sys_ai_face_recognize_record_cost` BIGINT DEFAULT 0 COMMENT '识别耗时(ms)',
  `sys_ai_face_recognize_record_status` INT DEFAULT 1,
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_face_recognize_record_id`),
  KEY `idx_record_create_time` (`create_time`)
) COMMENT='AI 人脸识别记录表';

-- ============================================================
-- 12. AI 人脸分组表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_face_group`;
CREATE TABLE IF NOT EXISTS `sys_ai_face_group` (
  `sys_ai_face_group_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_face_group_name` VARCHAR(64) NOT NULL COMMENT '分组名称',
  `sys_ai_face_group_sort` INT DEFAULT 0 COMMENT '排序号',
  `sys_ai_face_group_remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `sys_ai_face_group_status` INT DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_face_group_id`),
  UNIQUE KEY `uk_group_name` (`sys_ai_face_group_name`)
) COMMENT='AI 人脸分组表';

-- ============================================================
-- 13. AI 图像库表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_image_library`;
CREATE TABLE IF NOT EXISTS `sys_ai_image_library` (
  `sys_ai_image_library_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_image_library_name` VARCHAR(255) DEFAULT NULL COMMENT '图像名称/标签',
  `sys_ai_image_library_vector_id` VARCHAR(128) NOT NULL COMMENT '向量存储中的向量ID',
  `sys_ai_image_library_thumbnail` LONGTEXT COMMENT '缩略图(base64)',
  `sys_ai_image_library_url` VARCHAR(2048) DEFAULT NULL COMMENT '图像URL',
  `sys_ai_image_library_tags` VARCHAR(512) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `sys_ai_image_library_meta` JSON DEFAULT NULL COMMENT '扩展元数据',
  `sys_ai_image_library_width` INT DEFAULT 0 COMMENT '图像宽度',
  `sys_ai_image_library_height` INT DEFAULT 0 COMMENT '图像高度',
  `sys_ai_image_library_status` INT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_image_library_id`),
  UNIQUE KEY `uk_image_vector_id` (`sys_ai_image_library_vector_id`),
  KEY `idx_image_create_time` (`create_time`),
  KEY `idx_image_tags` (`sys_ai_image_library_tags`)
) COMMENT='AI 图像库表';

-- ============================================================
-- 14. AI 知识图谱实体表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_knowledge_graph_entity`;
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_entity` (
  `sys_ai_knowledge_graph_entity_id`       INT           NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_knowledge_graph_entity_uid`      VARCHAR(64)   NOT NULL COMMENT '实体唯一标识(UUID)',
  `sys_ai_knowledge_graph_library_id`      INT           NOT NULL DEFAULT 1 COMMENT '所属知识库ID',
  `sys_ai_knowledge_graph_entity_name`     VARCHAR(255)  NOT NULL COMMENT '实体名称',
  `sys_ai_knowledge_graph_entity_type`     VARCHAR(128)  NOT NULL DEFAULT 'default' COMMENT '实体类型(person/organization/location/product等)',
  `sys_ai_knowledge_graph_entity_properties` JSON        DEFAULT NULL COMMENT '实体属性(JSON)',
  `sys_ai_knowledge_graph_entity_source`   VARCHAR(32)   NOT NULL DEFAULT 'manual' COMMENT '来源(manual/llm_extracted/imported)',
  `sys_ai_knowledge_graph_entity_weight`   DOUBLE        DEFAULT 1.0 COMMENT '实体权重，重复添加时递增',
  `sys_ai_knowledge_graph_entity_logo`     VARCHAR(1024) DEFAULT NULL COMMENT '实体Logo图片URL',
  `sys_ai_knowledge_graph_entity_status`   INT           NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  `create_by`    INT          DEFAULT NULL COMMENT '创建人',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  `update_by`    INT          DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`sys_ai_knowledge_graph_entity_id`),
  UNIQUE KEY `uk_kg_entity_uid` (`sys_ai_knowledge_graph_entity_uid`),
  KEY `idx_kg_entity_type` (`sys_ai_knowledge_graph_entity_type`),
  KEY `idx_kg_entity_name` (`sys_ai_knowledge_graph_entity_name`),
  KEY `idx_kg_entity_library` (`sys_ai_knowledge_graph_library_id`)
) COMMENT='AI 知识图谱实体表';

-- ============================================================
-- 15. AI 知识图谱关系表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_knowledge_graph_relation`;
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_relation` (
  `sys_ai_knowledge_graph_relation_id`                 INT           NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_knowledge_graph_relation_uid`                VARCHAR(64)   NOT NULL COMMENT '关系唯一标识(UUID)',
  `sys_ai_knowledge_graph_relation_library_id`         INT           NOT NULL DEFAULT 1 COMMENT '所属知识库ID',
  `sys_ai_knowledge_graph_relation_source_entity_uid`  VARCHAR(64)   NOT NULL COMMENT '源实体UID',
  `sys_ai_knowledge_graph_relation_target_entity_uid`  VARCHAR(64)   NOT NULL COMMENT '目标实体UID',
  `sys_ai_knowledge_graph_relation_type`               VARCHAR(128)  NOT NULL COMMENT '关系类型',
  `sys_ai_knowledge_graph_relation_properties`         JSON          DEFAULT NULL COMMENT '关系属性(JSON)',
  `sys_ai_knowledge_graph_relation_weight`             DOUBLE        DEFAULT 1.0 COMMENT '关系权重',
  `sys_ai_knowledge_graph_relation_source`             VARCHAR(32)   NOT NULL DEFAULT 'manual' COMMENT '来源(manual/llm_extracted/imported)',
  `sys_ai_knowledge_graph_relation_status`             INT           NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  `create_by`    INT          DEFAULT NULL COMMENT '创建人',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  `update_by`    INT          DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`sys_ai_knowledge_graph_relation_id`),
  UNIQUE KEY `uk_kg_relation_uid` (`sys_ai_knowledge_graph_relation_uid`),
  KEY `idx_kg_relation_source` (`sys_ai_knowledge_graph_relation_source_entity_uid`),
  KEY `idx_kg_relation_target` (`sys_ai_knowledge_graph_relation_target_entity_uid`),
  KEY `idx_kg_relation_type` (`sys_ai_knowledge_graph_relation_type`),
  KEY `idx_kg_relation_library` (`sys_ai_knowledge_graph_relation_library_id`)
) COMMENT='AI 知识图谱关系表';

-- ============================================================
-- 16. AI 知识库表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_knowledge_library`;
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_id`                      INT           NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_knowledge_library_name`                    VARCHAR(128)  NOT NULL COMMENT '知识库名称',
  `sys_ai_knowledge_library_description`             VARCHAR(512)  DEFAULT NULL COMMENT '知识库描述',
  `sys_ai_knowledge_library_summary`                 TEXT          DEFAULT NULL COMMENT '知识库概要，用于AI提取知识图谱实体和关系的指导说明',
  `sys_ai_knowledge_library_llm_provider`            VARCHAR(64)   DEFAULT NULL COMMENT 'LLM提供商(留空使用全局默认)',
  `sys_ai_knowledge_library_llm_model`               VARCHAR(128)  DEFAULT NULL COMMENT 'LLM模型编码(留空使用全局默认)',
  `sys_ai_knowledge_library_temperature`             DOUBLE        DEFAULT NULL COMMENT '温度参数(留空使用全局默认)',
  `sys_ai_knowledge_library_max_tokens`              INT           DEFAULT NULL COMMENT '最大输出token数(留空使用全局默认)',
  `sys_ai_knowledge_library_prompt_template`         TEXT          DEFAULT NULL COMMENT '实体抽取提示词模板(留空使用全局默认)',
  `sys_ai_knowledge_library_relation_extraction_enabled` INT       DEFAULT 1 COMMENT '启用关系抽取',
  `sys_ai_knowledge_library_max_entities_per_doc`    INT           DEFAULT 50 COMMENT '单文档最大实体数',
  `sys_ai_knowledge_library_web_search_enabled`     INT           DEFAULT 0 COMMENT '是否启用联网搜索辅助抽取：1启用，0禁用',
  `sys_ai_knowledge_library_thinking_rounds`       INT           DEFAULT 4 COMMENT 'AI提取思考轮次(1-5)，默认4轮',
  `sys_ai_knowledge_library_sort`                    INT           DEFAULT 0 COMMENT '排序号',
  `sys_ai_knowledge_library_status`                  INT           NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name`  VARCHAR(255) DEFAULT NULL COMMENT '创建人姓名',
  `create_by`    INT          DEFAULT NULL COMMENT '创建人',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name`  VARCHAR(255) DEFAULT NULL COMMENT '更新人姓名',
  `update_by`    INT          DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`sys_ai_knowledge_library_id`),
  UNIQUE KEY `uk_lib_name` (`sys_ai_knowledge_library_name`)
) COMMENT='AI 知识库表';

-- ============================================================
-- 17. AI 行人重识别（ReID）库表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_reid_library`;
CREATE TABLE IF NOT EXISTS `sys_ai_reid_library` (
  `sys_ai_reid_library_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_ai_reid_library_face_id` INT DEFAULT NULL COMMENT '关联的人脸库ID',
  `sys_ai_reid_library_name` VARCHAR(128) NOT NULL COMMENT '重识别名称/标签',
  `sys_ai_reid_library_face_name` VARCHAR(128) DEFAULT NULL COMMENT '关联的人脸名称（冗余）',
  `sys_ai_reid_library_vector_id` VARCHAR(128) NOT NULL COMMENT '向量存储中的向量ID',
  `sys_ai_reid_library_thumbnail` LONGTEXT COMMENT '重识别人物缩略图(base64)',
  `sys_ai_reid_library_group` VARCHAR(64) DEFAULT 'default' COMMENT '人脸分组（复用 face_group）',
  `sys_ai_reid_library_meta` VARCHAR(2048) DEFAULT NULL COMMENT '扩展元数据(JSON)',
  `sys_ai_reid_library_status` INT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_reid_library_id`),
  UNIQUE KEY `uk_reid_vector_id` (`sys_ai_reid_library_vector_id`),
  KEY `idx_reid_group` (`sys_ai_reid_library_group`),
  KEY `idx_reid_face_id` (`sys_ai_reid_library_face_id`),
  KEY `idx_reid_name` (`sys_ai_reid_library_name`)
) COMMENT='AI 行人重识别（ReID）库表';

-- ============================================================
-- 18. AI 知识图谱实体属性表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_knowledge_graph_entity_attribute`;
CREATE TABLE IF NOT EXISTS `sys_ai_knowledge_graph_entity_attribute` (
  `attr_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '属性主键ID',
  `entity_uid`       VARCHAR(64)  NOT NULL COMMENT '所属实体UUID',
  `library_id`       INT          NOT NULL DEFAULT 1 COMMENT '所属知识库ID',
  `attr_name`        VARCHAR(200) NOT NULL COMMENT '属性名（如：字、号、入蜀、官职、startYear、endYear）',
  `attr_value`       TEXT         DEFAULT NULL COMMENT '属性值',
  `attr_type`        VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT '属性类型：text/number/date/event',
  `attr_sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  `status`           INT          NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_name`      VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by`        INT          DEFAULT NULL COMMENT '创建人ID',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name`      VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by`        INT          DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`attr_id`),
  KEY `idx_attr_entity` (`entity_uid`),
  KEY `idx_attr_library` (`library_id`),
  KEY `idx_attr_name` (`attr_name`)
) COMMENT='AI 知识图谱实体属性表';

-- ============================================================
-- 19. 厂家多 API Key 表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_manufacturer_key`;
CREATE TABLE IF NOT EXISTS `sys_ai_manufacturer_key` (
  `sys_ai_manufacturer_key_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sys_ai_manufacturer_code` VARCHAR(255) DEFAULT NULL COMMENT '厂家编码',
  `sys_ai_manufacturer_name` VARCHAR(255) DEFAULT NULL COMMENT '厂家名称',
  `sys_ai_manufacturer_key_value` VARCHAR(512) DEFAULT NULL COMMENT 'API Key',
  `sys_ai_manufacturer_key_secret` VARCHAR(512) DEFAULT NULL COMMENT 'API Secret',
  `sys_ai_manufacturer_key_status` INT DEFAULT 1 COMMENT '状态: 1=启用, 0=停用, -1=限流',
  `sys_ai_manufacturer_key_weight` INT DEFAULT 0 COMMENT '权重',
  `sys_ai_manufacturer_key_rate_limited_at` BIGINT DEFAULT NULL COMMENT '限流时间戳',
  `sys_ai_manufacturer_key_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
  `create_by` INT DEFAULT NULL COMMENT '创建人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL COMMENT '更新人名称',
  `update_by` INT DEFAULT NULL COMMENT '更新人ID',
  PRIMARY KEY (`sys_ai_manufacturer_key_id`),
  KEY `idx_mfr_key_code` (`sys_ai_manufacturer_code`)
) COMMENT='AI 厂家多 API Key 表';

-- ============================================================
-- 20. AI 令牌表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_token`;
CREATE TABLE IF NOT EXISTS `sys_ai_token` (
  `sys_ai_token_id` INT NOT NULL AUTO_INCREMENT COMMENT '令牌主键',
  `sys_ai_token_name` VARCHAR(128) DEFAULT NULL COMMENT '令牌名称',
  `sys_ai_token_key` VARCHAR(128) NOT NULL COMMENT '令牌密钥',
  `sys_ai_token_key_prefix` VARCHAR(32) DEFAULT NULL COMMENT '令牌密钥前缀',
  `sys_ai_token_key_hash` VARCHAR(64) NOT NULL COMMENT '令牌密钥哈希',
  `sys_user_id` INT DEFAULT NULL COMMENT '归属用户ID',
  `sys_ai_app_key_id` INT DEFAULT NULL COMMENT '绑定AppKey ID',
  `sys_ai_token_group_id` INT DEFAULT NULL COMMENT '分组 ID',
  `sys_ai_token_status` INT DEFAULT 1 COMMENT '状态：1启用0禁用',
  `sys_ai_token_expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `sys_ai_token_input_fee` DECIMAL(18,4) DEFAULT NULL COMMENT '输入费用',
  `sys_ai_token_output_fee` DECIMAL(18,4) DEFAULT NULL COMMENT '输出费用',
  `sys_ai_token_total_input_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '累计输入费用',
  `sys_ai_token_total_output_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '累计输出费用',
  `sys_ai_token_total_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '累计总费用',
  `sys_ai_token_use_count` BIGINT DEFAULT 0 COMMENT '累计使用次数',
  `sys_ai_token_total_tokens` BIGINT DEFAULT 0 COMMENT '累计使用token数',
  `sys_ai_token_today_fee` DECIMAL(18,6) DEFAULT 0 COMMENT '今日费用',
  `sys_ai_token_today_date` VARCHAR(10) DEFAULT NULL COMMENT '今日费用统计日期',
  `sys_ai_token_last_used_time` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `sys_ai_token_delete` INT DEFAULT 0 COMMENT '删除标记：0未删除1已删除',
  `sys_ai_token_allowed_models` VARCHAR(512) DEFAULT NULL COMMENT '允许的模型列表',
  `sys_ai_token_rate_limit` INT DEFAULT NULL COMMENT '限流：每分钟最大请求数',
  `sys_ai_token_rate_count` INT DEFAULT 0 COMMENT '限流计数器',
  `sys_ai_token_rate_window_start` DATETIME DEFAULT NULL COMMENT '限流窗口开始时间',
  `sys_ai_token_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_token_id`),
  KEY `idx_token_user` (`sys_user_id`),
  KEY `idx_token_key_hash` (`sys_ai_token_key_hash`),
  KEY `idx_token_status` (`sys_ai_token_status`)
) COMMENT='AI 令牌表';

-- ============================================================
-- 21. AI 用户余额表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_balance`;
CREATE TABLE IF NOT EXISTS `sys_ai_balance` (
  `sys_ai_balance_id` INT NOT NULL AUTO_INCREMENT COMMENT '余额主键',
  `sys_user_id` INT DEFAULT NULL COMMENT '归属用户ID',
  `sys_ai_balance_amount` DECIMAL(18,4) DEFAULT 0 COMMENT '账户余额',
  `sys_ai_balance_total_recharge` DECIMAL(18,4) DEFAULT 0 COMMENT '累计充值金额',
  `sys_ai_balance_total_consume` DECIMAL(18,4) DEFAULT 0 COMMENT '累计消费金额',
  `sys_ai_balance_gift` DECIMAL(18,4) DEFAULT 0 COMMENT '赠送余额',
  `sys_ai_balance_warning_threshold` DECIMAL(18,4) DEFAULT NULL COMMENT '余额预警阈值',
  `sys_ai_balance_expire_time` DATETIME DEFAULT NULL COMMENT '余额过期时间',
  `sys_ai_balance_status` INT DEFAULT 1 COMMENT '状态：1正常0冻结',
  `sys_ai_balance_currency` VARCHAR(10) DEFAULT 'CNY' COMMENT '货币类型',
  `sys_ai_balance_last_consume_time` DATETIME DEFAULT NULL COMMENT '最后消费时间',
  `sys_ai_balance_delete` INT DEFAULT 0 COMMENT '删除标记',
  `sys_ai_balance_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `create_name` VARCHAR(255) DEFAULT NULL,
  `create_by` INT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_name` VARCHAR(255) DEFAULT NULL,
  `update_by` INT DEFAULT NULL,
  PRIMARY KEY (`sys_ai_balance_id`),
  KEY `idx_balance_user` (`sys_user_id`),
  KEY `idx_balance_status` (`sys_ai_balance_status`)
) COMMENT='AI 用户余额表';

-- ============================================================
-- 22. AI 模型分组表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_group`;
CREATE TABLE IF NOT EXISTS `sys_ai_group` (
    `sys_ai_group_id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '分组主键',
    `sys_ai_group_name` VARCHAR(128) NOT NULL COMMENT '分组名称',
    `sys_ai_group_desc` VARCHAR(256) DEFAULT '' COMMENT '分组描述',
    `sys_ai_group_models` TEXT DEFAULT NULL COMMENT '关联模型列表(JSON)',
    `sys_ai_group_app_key_ids` TEXT DEFAULT NULL COMMENT '绑定 AppKey ID 列表(JSON)',
    `sys_ai_group_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `sys_ai_group_sort` INT DEFAULT 0 COMMENT '排序值',
    `sys_ai_group_status` INT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `sys_ai_group_delete` INT DEFAULT 0 COMMENT '删除标记：0 未删除，1 已删除',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_time` TIMESTAMP COMMENT '更新时间',
    `update_by` VARCHAR(64) COMMENT '更新人'
) COMMENT='AI 模型分组表';

-- ============================================================
-- 23. 分组-模型关联表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_group_model`;
CREATE TABLE IF NOT EXISTS `sys_ai_group_model` (
    `sys_ai_group_model_id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `sys_ai_group_id` INT NOT NULL COMMENT '分组 ID',
    `sys_ai_module_id` INT NOT NULL COMMENT '模块 ID',
    `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_group_module` (`sys_ai_group_id`, `sys_ai_module_id`)
) COMMENT='分组-模型关联表';

-- ============================================================
-- 24. 模型供应商表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_provider`;
CREATE TABLE IF NOT EXISTS `sys_ai_provider` (
    `sys_ai_provider_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '供应商主键',
    `sys_ai_provider_name` VARCHAR(255) NOT NULL COMMENT '供应商名称',
    `sys_ai_provider_type` VARCHAR(64) NOT NULL COMMENT '供应商类型(openai/anthropic/gemini/qwen/custom)',
    `sys_ai_provider_base_url` VARCHAR(512) DEFAULT NULL COMMENT '接入 Base URL',
    `sys_ai_provider_api_key` VARCHAR(512) DEFAULT NULL COMMENT 'API Key',
    `sys_ai_provider_logo` VARCHAR(1024) DEFAULT NULL COMMENT 'Logo 图片地址',
    `sys_ai_provider_models` TEXT DEFAULT NULL COMMENT '可用模型列表(JSON)',
    `sys_ai_provider_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `sys_ai_provider_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    `create_name` VARCHAR(64) DEFAULT NULL COMMENT '创建人名称',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `update_name` VARCHAR(64) DEFAULT NULL COMMENT '更新人名称',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    UNIQUE KEY `uk_sys_ai_provider_name` (`sys_ai_provider_name`)
) COMMENT='模型供应商表';

-- ============================================================
-- 26. AI 模型定义表（关联供应商，含能力标签）
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_model`;
CREATE TABLE IF NOT EXISTS `sys_ai_model` (
    `sys_ai_model_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模型主键',
    `sys_ai_model_name` VARCHAR(255) NOT NULL COMMENT '模型显示名称',
    `sys_ai_model_code` VARCHAR(255) NOT NULL COMMENT '模型标识(gpt-4o, deepseek-chat)',
    `sys_ai_model_provider_id` BIGINT DEFAULT NULL COMMENT '关联供应商 ID',
    `sys_ai_model_capabilities` VARCHAR(512) DEFAULT NULL COMMENT '能力标签(逗号分隔: chat,vision,think,search)',
    `sys_ai_model_description` VARCHAR(512) DEFAULT NULL COMMENT '模型描述',
    `sys_ai_model_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `sys_ai_model_sort` INT DEFAULT 0 COMMENT '排序值',
    `sys_ai_model_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `sys_ai_model_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `sys_ai_model_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    `sys_ai_model_input_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '输入单价(每1k tokens)',
    `sys_ai_model_output_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '输出单价(每1k tokens)',
    `sys_ai_model_cache_price` DECIMAL(12,4) DEFAULT 0.0000 COMMENT '缓存单价(每1k tokens)',
    UNIQUE KEY `uk_sys_ai_model_code` (`sys_ai_model_code`)
) COMMENT='AI 模型定义表';

-- ============================================================
-- 29. AI 服务运行状态表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_service_status`;
CREATE TABLE IF NOT EXISTS `sys_ai_service_status` (
    `sys_ai_service_status_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '状态主键',
    `sys_ai_service_status_service_name` VARCHAR(255) NOT NULL COMMENT '服务名称',
    `sys_ai_service_status_running` INT DEFAULT 0 COMMENT '是否运行中',
    `sys_ai_service_status_pid` BIGINT DEFAULT NULL COMMENT '进程 PID',
    `sys_ai_service_status_start_time` DATETIME DEFAULT NULL COMMENT '启动时间',
    `sys_ai_service_status_uptime_sec` BIGINT DEFAULT 0 COMMENT '运行时长(秒)',
    `sys_ai_service_status_update_time` DATETIME DEFAULT NULL COMMENT '更新时间'
) COMMENT='AI 服务运行状态表';

-- ============================================================
-- 31. AI 告警规则表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_alert`;
CREATE TABLE IF NOT EXISTS `sys_ai_alert` (
    `sys_ai_alert_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '告警主键',
    `sys_ai_alert_name` VARCHAR(255) NOT NULL COMMENT '告警名称',
    `sys_ai_alert_app_key_id` BIGINT DEFAULT NULL COMMENT '关联 AppKey ID',
    `sys_ai_alert_condition` VARCHAR(128) NOT NULL COMMENT '告警条件',
    `sys_ai_alert_threshold` BIGINT NOT NULL COMMENT '告警阈值',
    `sys_ai_alert_channels` VARCHAR(512) DEFAULT NULL COMMENT '通知渠道',
    `sys_ai_alert_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `sys_ai_alert_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `sys_ai_alert_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `sys_ai_alert_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记'
) COMMENT='AI 告警规则表';

-- ============================================================
-- 30. AI 用量明细表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_usage`;
CREATE TABLE IF NOT EXISTS `sys_ai_usage` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用量主键',
    `group_name` VARCHAR(64) DEFAULT NULL COMMENT '分组：chat/image/video/colorize/vision/rag',
    `provider` VARCHAR(64) DEFAULT NULL COMMENT '供应商',
    `model` VARCHAR(255) NOT NULL COMMENT '模型名称',
    `call_type` VARCHAR(32) DEFAULT NULL COMMENT '调用类型：sync/async',
    `prompt_tokens` INT DEFAULT 0 COMMENT '提示 tokens',
    `completion_tokens` INT DEFAULT 0 COMMENT '生成 tokens',
    `total_tokens` INT DEFAULT 0 COMMENT '总 tokens',
    `input_cost` DOUBLE DEFAULT 0 COMMENT '输入费用(元)',
    `output_cost` DOUBLE DEFAULT 0 COMMENT '输出费用(元)',
    `total_cost` DOUBLE DEFAULT 0 COMMENT '总费用(元)',
    `request_id` VARCHAR(64) DEFAULT NULL COMMENT '请求 ID',
    `user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户标识',
    `call_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='AI 用量明细表';

-- ============================================================
-- 30.1 AI 用量日汇总表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_usage_daily`;
CREATE TABLE IF NOT EXISTS `sys_ai_usage_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `stat_date` DATE DEFAULT NULL COMMENT '统计日期',
    `group_name` VARCHAR(64) DEFAULT NULL COMMENT '分组',
    `provider` VARCHAR(64) DEFAULT NULL COMMENT '供应商',
    `model` VARCHAR(255) NOT NULL COMMENT '模型名称',
    `total_calls` INT DEFAULT 0 COMMENT '总调用次数',
    `total_prompt_tokens` INT DEFAULT 0 COMMENT '提示 tokens',
    `total_completion_tokens` INT DEFAULT 0 COMMENT '生成 tokens',
    `total_tokens` INT DEFAULT 0 COMMENT '总 tokens',
    `total_cost` DOUBLE DEFAULT 0 COMMENT '总费用(元)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='AI 用量日汇总表';

-- ============================================================
-- 31. AI 模型设置表
-- ============================================================
DROP TABLE IF EXISTS `sys_ai_model_setting`;
CREATE TABLE IF NOT EXISTS `sys_ai_model_setting` (
    `sys_ai_model_setting_id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '模型设置主键',
    `module_id` INT DEFAULT NULL COMMENT '所属模块 ID',
    `model_code` VARCHAR(255) DEFAULT NULL COMMENT '模型编码',
    `model_name` VARCHAR(255) DEFAULT NULL COMMENT '模型名称',
    `model_provider` VARCHAR(128) DEFAULT NULL COMMENT '模型厂商',
    `model_value` VARCHAR(512) DEFAULT NULL COMMENT '模型值（如 baseUrl / model 标识）',
    `status` INT DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    `sort` INT DEFAULT 0 COMMENT '排序值',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='AI 模型设置表';

-- ============================================================
-- 32. 清理旧 ai_* 冗余表（存在则删除）
-- ============================================================
DROP TABLE IF EXISTS `ai_provider`;
DROP TABLE IF EXISTS `ai_group`;
DROP TABLE IF EXISTS `ai_model`;
DROP TABLE IF EXISTS `ai_app_key`;
DROP TABLE IF EXISTS `ai_global_setting`;
DROP TABLE IF EXISTS `ai_service_status`;
DROP TABLE IF EXISTS `ai_usage`;
DROP TABLE IF EXISTS `ai_alert`;

-- ============================================================
-- ============ 以下为合并自其他脚本的内容 ============
-- ============================================================

-- ============================================================
-- V1.0__initdata_ai.sql
-- AI 模块初始化数据（异步执行）
-- 合并自：V1.0 S1-S7 + V1.3 S8-S9 + V1.4
-- ============================================================

-- ============================================================
-- S1. 默认人脸分组
-- ============================================================
INSERT IGNORE INTO `sys_ai_face_group` (`sys_ai_face_group_name`, `sys_ai_face_group_sort`)
VALUES ('default', 0);

-- ============================================================
-- S2. 默认知识库
-- ============================================================
INSERT IGNORE INTO `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_name`, `sys_ai_knowledge_library_description`, `sys_ai_knowledge_library_sort`
) VALUES ('默认知识库', '系统默认知识库', 0);

-- ============================================================
-- S2a. 三国演义知识库（AI 提取示例）
-- ============================================================
INSERT IGNORE INTO `sys_ai_knowledge_library` (
  `sys_ai_knowledge_library_name`, `sys_ai_knowledge_library_description`,
  `sys_ai_knowledge_library_summary`, `sys_ai_knowledge_library_sort`
) VALUES ('三国演义', '《三国演义》历史知识图谱 - 184年黄巾起义至280年西晋统一', '## 提取范围

三国时期（184年-280年）是中国历史上一个群雄割据的动荡时期。请提取以下各类实体和关系。

## 核心人物（按阵营）

### 蜀汉（刘备阵营）
- 刘备（字玄德，蜀汉开国皇帝）
- 关羽（字云长，五虎上将之首）
- 张飞（字翼德，五虎上将）
- 诸葛亮（字孔明，丞相）
- 赵云（字子龙，五虎上将）
- 马超（字孟起，五虎上将）
- 黄忠（字汉升，五虎上将）
- 魏延（字文长，汉中太守）
- 姜维（字伯约，蜀汉大将军）
- 庞统（字士元，军师中郎将）
- 法正（字孝直，尚书令）
- 马谡（字幼常，参军）

### 曹魏（曹操阵营）
- 曹操（字孟德，魏武帝）
- 曹丕（字子桓，魏文帝）
- 曹植（字子建，陈王）
- 司马懿（字仲达，太傅）
- 夏侯惇（字元让，大将军）
- 张辽（字文远，前将军）
- 许褚（字仲康，武卫将军）
- 郭嘉（字奉孝，军师祭酒）
- 荀彧（字文若，尚书令）
- 贾诩（字文和，太尉）
- 典韦（曹操近卫统领）
- 徐晃（字公明，右将军）

### 东吴（孙权阵营）
- 孙坚（字文台，破虏将军）
- 孙策（字伯符，讨逆将军）
- 孙权（字仲谋，吴大帝）
- 周瑜（字公瑾，大都督）
- 鲁肃（字子敬，都督）
- 吕蒙（字子明，都督）
- 陆逊（字伯言，丞相）
- 黄盖（字公覆，偏将军）
- 甘宁（字兴霸，折冲将军）
- 太史慈（字子义，建昌都尉）

### 群雄/其他
- 吕布（字奉先，温侯）
- 董卓（字仲颖，太师）
- 袁绍（字本初，大将军）
- 袁术（字公路，仲家皇帝）
- 貂蝉（王允义女）
- 汉献帝刘协（东汉末代皇帝）
- 王允（字子师，司徒）
- 刘表（字景升，荆州牧）
- 张角（太平道创始人）

## 需要提取的关系类型

- 君臣（served_as）：人物服务于某君主或政权
- 父子（father_of）：父子血缘关系
- 兄弟（brother_of）：兄弟关系（含结义）
- 夫妻（married_to）：配偶关系
- 盟友（allied_with）：军事/政治同盟
- 敌对（enemy_of）：战场敌对关系
- 师徒（teacher_of）：传授关系
- 举荐（recommended）：推荐人才
- 击败（defeated）：在战役中击败对手
- 继承（succeeded_by）：职位/政权继承
- 暗杀（assassinated）：刺杀行为
- 投降（surrendered_to）：投降某势力

## 重要事件时间线

- 184年 黄巾起义爆发
- 189年 董卓入京乱政
- 190年 关东诸侯讨董
- 192年 王允连环计杀董卓
- 196年 曹操迎汉献帝迁都许昌
- 200年 官渡之战（曹操大败袁绍）
- 207年 三顾茅庐请诸葛亮
- 208年 赤壁之战（孙刘联军败曹操）
- 214年 刘备入蜀夺取益州
- 219年 关羽水淹七军，吕蒙白衣渡江取荆州
- 220年 曹丕称帝建魏，汉朝灭亡
- 221年 刘备称帝建蜀汉，发动夷陵之战
- 222年 陆逊火烧连营，蜀汉大败
- 225年 诸葛亮七擒孟获平定南中
- 228年 诸葛亮第一次北伐（失街亭斩马谡）
- 228-234年 诸葛亮六出祁山
- 234年 五丈原诸葛亮病逝
- 249年 高平陵之变司马懿夺权
- 263年 邓艾钟会伐蜀，蜀汉灭亡
- 265年 司马炎建立西晋
- 280年 西晋灭吴统一天下

## 势力阵营

蜀汉、曹魏、东吴三方鼎立，每个角色应标注所属势力。', 1);

-- ============================================================
-- S3. LLM 厂家
-- ============================================================
INSERT INTO `sys_ai_manufacturer` (
  `sys_ai_manufacturer_code`, `sys_ai_manufacturer_name`,
  `sys_ai_manufacturer_factory`, `sys_ai_manufacturer_base_url`,
  `sys_ai_manufacturer_app_key`,
  `sys_ai_manufacturer_openai_compatible`,
  `sys_ai_manufacturer_claude_compatible`,
  `sys_ai_manufacturer_status`, `sys_ai_manufacturer_sort`,
  `sys_ai_manufacturer_remark`
)
SELECT * FROM (
  -- ── 本地 / 代理厂家 ──────────────────────────────
  SELECT 'local-onnx' AS code, '本地ONNX模型' AS name,
    NULL AS factory, NULL AS base_url,
    NULL AS app_key,
    0 AS openai_compat, 0 AS claude_compat, 1 AS status, 5 AS sort,
    '本地 ONNX 推理模型（人脸检测/特征提取/OCR/ReID 等）' AS remark
  UNION ALL
  SELECT 'baishan', '白山智算',
    NULL, 'https://api.edgefn.net/v1',
    'sk-REPLACE_WITH_BAISHAN_KEY',
    1, 0, 1, 10,
    '白山智算 API，支持 KAT-Coder / DeepSeek-R1 等模型'
  UNION ALL
  SELECT 'infer-ai', 'Infer AI',
    NULL, 'https://newapis.xyz/v1',
    'sk-REPLACE_WITH_INFER_KEY',
    1, 1, 1, 20,
    'Claude Code 使用的 Infer AI 代理，支持 deepseek-v4-pro（Claude 协议）'
  UNION ALL
  SELECT 'claude-test', 'Claude 测试',
    NULL, 'https://newapis.xyz/v1',
    'sk-REPLACE_WITH_INFER_KEY',
    1, 1, 1, 25,
    'Claude 协议测试厂家，支持 deepseek-v4-pro'
  UNION ALL
  SELECT 'modelscope', 'ModelScope',
    NULL, 'https://api-inference.modelscope.cn/v1',
    'ms-REPLACE_WITH_MODELSCOPE_KEY',
    1, 0, 1, 30,
    '阿里 ModelScope 推理 API，支持 Qwen3 等模型'
  UNION ALL
  -- ── OpenAI ───────────────────────────────────────
  SELECT 'openai', 'OpenAI',
    NULL, 'https://api.openai.com/v1',
    '',
    1, 0, 1, 100,
    'OpenAI API，支持 GPT-4o / GPT-4o-mini / o3 等模型'
  UNION ALL
  -- ── SiliconFlow ──────────────────────────────────
  SELECT 'siliconflow', '硅基流动（SiliconFlow）',
    NULL, 'https://api.siliconflow.cn/v1',
    '',
    1, 0, 1, 110,
    'SiliconFlow 开源模型 API 服务，支持 DeepSeek-V3 / Qwen2.5 / Llama 等'
  UNION ALL
  -- ── DeepSeek ─────────────────────────────────────
  SELECT 'deepseek', 'DeepSeek',
    NULL, 'https://api.deepseek.com',
    '',
    1, 0, 1, 115,
    'DeepSeek 官方 API，支持 deepseek-chat / deepseek-reasoner'
  UNION ALL
  -- ── 阿里云通义千问 ──────────────────────────────
  SELECT 'aliyun', '阿里云通义千问',
    NULL, 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    '',
    1, 0, 1, 120,
    '阿里云 DashScope API，支持 Qwen-Plus / Qwen-Max 等通义千问模型'
  UNION ALL
  -- ── Ollama ───────────────────────────────────────
  SELECT 'ollama', 'Ollama（本地）',
    NULL, 'http://localhost:11434',
    '',
    1, 0, 1, 130,
    'Ollama 本地大模型服务，支持 Llama / Qwen / DeepSeek 等本地运行'
  UNION ALL
  -- ── 豆包 ─────────────────────────────────────────
  SELECT 'doubao', '豆包大模型',
    NULL, 'https://ark.cn-beijing.volces.com/api/v3',
    '',
    1, 0, 1, 140,
    '字节跳动豆包大模型 API，支持 doubao-pro / doubao-lite 系列'
  UNION ALL
  -- ── 腾讯混元 ─────────────────────────────────────
  SELECT 'tencent', '腾讯混元',
    NULL, 'https://api.hunyuan.cloud.tencent.com/v1',
    '',
    1, 0, 1, 145,
    '腾讯混元大模型 API，支持 hunyuan-pro / hunyuan-standard'
  UNION ALL
  -- ── Kimi ─────────────────────────────────────────
  SELECT 'kimi', '月之暗面（Kimi）',
    NULL, 'https://api.moonshot.cn/v1',
    '',
    1, 0, 1, 150,
    '月之暗面 Kimi API，支持 moonshot-v1 / kimi-k2 系列'
  UNION ALL
  -- ── MiniMax ──────────────────────────────────────
  SELECT 'minimax', 'MiniMax',
    NULL, 'https://api.minimax.chat/v1',
    '',
    1, 0, 1, 155,
    'MiniMax API，支持 abab6.5 系列模型'
  UNION ALL
  -- ── 智谱 ─────────────────────────────────────────
  SELECT 'zhipu', '智谱（Zhipu AI）',
    NULL, 'https://open.bigmodel.cn/api/paas/v4',
    '',
    1, 0, 1, 160,
    '智谱 AI 开放平台，支持 GLM-4 系列模型'
  UNION ALL
  -- ── Google Gemini ────────────────────────────────
  SELECT 'google', 'Google Gemini',
    NULL, 'https://generativelanguage.googleapis.com/v1beta',
    '',
    0, 0, 1, 165,
    'Google Gemini API，支持 gemini-2.0-flash / gemini-2.0-pro'
  UNION ALL
  -- ── 华为云盘古 ──────────────────────────────────
  SELECT 'huawei', '华为云盘古',
    NULL, 'https://pangu.cn-north-4.myhuaweicloud.com/v1',
    '',
    1, 0, 1, 170,
    '华为云盘古大模型 API，支持 pangu-text 系列'
  UNION ALL
  -- ── Claude ───────────────────────────────────────
  SELECT 'claude', 'Anthropic Claude',
    NULL, 'https://api.anthropic.com',
    '',
    0, 1, 1, 175,
    'Anthropic Claude API，支持 Claude Opus / Sonnet 系列'
  UNION ALL
  -- ── 讯飞星火 ─────────────────────────────────────
  SELECT 'xunfei', '讯飞星火',
    NULL, 'https://spark-api.xf-yun.com/v3.5',
    '',
    0, 0, 1, 180,
    '讯飞星火大模型 API，支持 Spark Pro / Max / V3.5 系列'
  UNION ALL
  -- ── Z.AI（智谱生态） ────────────────────────────
  SELECT 'zai', 'Z.AI（智谱生态）',
    NULL, 'https://api.z.ai/api/paas/v4',
    'REPLACE_WITH_ZHIPU_KEY',
    1, 0, 1, 185,
    'Z.AI API（智谱生态），提供 GLM-4.7 / GLM-4.6 / Cogview / CogVideo 等旗舰模型'
  UNION ALL
  -- ── OpenRouter ─────────────────────────────────────
  SELECT 'openrouter', 'OpenRouter',
    NULL, 'https://openrouter.ai/api/v1/chat/completions',
    'sk-or-REPLACE_WITH_OPENROUTER_KEY',
    1, 0, 1, 195,
    'OpenRouter API 聚合平台，提供多种开源/商业模型的统一访问入口'
  UNION ALL
  -- ── DeepSeek 本地 ─────────────────────────────────────
  SELECT 'deepseek-default', 'DeepSeek 本地',
    NULL, 'http://127.0.0.1:27777/v1',
    'sk-REPLACE_WITH_DEEPSEEK_KEY',
    1, 0, 1, 200,
    '本地部署的 DeepSeek 模型，兼容 OpenAI API'
  UNION ALL
  -- ── Sixthsense（Claude Code 兼容） ──────────────────
  SELECT 'sixthsense', 'Sixthsense LLM',
    NULL, 'https://sixthsense-llm.com',
    'sk-REPLACE_WITH_SIXTHSENSE_KEY',
    0, 1, 1, 205,
    'Sixthsense LLM API，Claude Code 兼容协议，支持 deepseek-v4-flash-l'
  UNION ALL
  -- ── SafeTensors（本地 Python 推理） ──────────────────
  SELECT 'safetensors', 'SafeTensors（本地Python）',
    NULL, NULL,
    NULL,
    0, 0, 1, 210,
    'SafeTensors 本地 Python 推理引擎，支持 Qwen2.5 / Llama3.2 / MiniMind2 等 safetensors 格式模型'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_manufacturer`
  WHERE `sys_ai_manufacturer_code` = seed.code
);

-- 更新已有 infer-ai 厂家：修正 base_url、开启 claude_compatible
UPDATE `sys_ai_manufacturer`
SET `sys_ai_manufacturer_base_url` = 'https://newapis.xyz/v1',
    `sys_ai_manufacturer_claude_compatible` = 1
WHERE `sys_ai_manufacturer_code` = 'infer-ai'
  AND (`sys_ai_manufacturer_claude_compatible` = 0 OR `sys_ai_manufacturer_base_url` = 'https://newapis.xyz');

-- ============================================================
-- S4. LLM 模型
-- ============================================================
INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_status`, `sys_ai_module_sort`,
  `sys_ai_module_vlm`, `sys_ai_module_support_think`,
  `sys_ai_module_concurrency`,
  `sys_ai_module_input_price`, `sys_ai_module_output_price`,
  `sys_ai_module_remark`
)
SELECT * FROM (
  -- ── 本地代理 / 私有模型 ──────────────────────────
  SELECT 'KAT-Coder-Exp-72B-1010' AS name, 'KAT-Coder-Exp-72B-1010' AS code,
    'CHAT' AS type, 'baishan' AS vendor, 1 AS status, 10 AS sort,
    0 AS vlm, 0 AS think,
    1 AS concurrency,
    0.0000 AS in_price, 0.0000 AS out_price,
    '白山智算 - KAT Coder 72B 实验版' AS remark
  UNION ALL
  SELECT 'DeepSeek-R1-0528-Qwen3-8B', 'DeepSeek-R1-0528-Qwen3-8B',
    'CHAT', 'baishan', 1, 20,
    0, 1,
    1,
    0.0000, 0.0000,
    '白山智算 - DeepSeek R1 推理模型(Qwen3-8B 蒸馏版)'
  UNION ALL
  SELECT 'DeepSeek-V4-Pro', 'deepseek-v4-pro',
    'CHAT', 'infer-ai', 1, 30,
    0, 0,
    1,
    0.0000, 0.0000,
    'Infer AI / Claude 测试 - DeepSeek V4 Pro（Claude 协议）'
  UNION ALL
  SELECT 'Qwen3-32B', 'Qwen/Qwen3-32B',
    'CHAT', 'modelscope', 1, 40,
    0, 0,
    1,
    0.0000, 0.0000,
    'ModelScope - 通义千问3 32B'
  UNION ALL
  SELECT 'DeepSeek-V4-Flash (ModelScope)', 'deepseek-ai/DeepSeek-V4-Flash',
    'CHAT', 'modelscope', 1, 45,
    0, 0,
    1,
    0.0000, 0.0000,
    'ModelScope - DeepSeek V4 Flash（免费，1M上下文）'

  UNION ALL
  -- ── OpenAI ─────────────────────────────────────────────
  SELECT 'GPT-4o', 'gpt-4o',
    'CHAT', 'openai', 1, 101,
    1, 0,
    1,
    2.5000, 10.0000,
    'OpenAI - GPT-4o 多模态通用模型，支持图像和文本'
  UNION ALL
  SELECT 'GPT-4o-mini', 'gpt-4o-mini',
    'CHAT', 'openai', 1, 102,
    1, 0,
    1,
    0.1500, 0.6000,
    'OpenAI - GPT-4o-mini 轻量多模态模型，速度快成本低'
  UNION ALL
  SELECT 'GPT-5.2', 'gpt-5.2',
    'CHAT', 'openai', 1, 100,
    0, 1,
    1,
    1.7500, 14.0000,
    'OpenAI - GPT-5.2 最新旗舰模型'

  UNION ALL
  -- ── SiliconFlow ────────────────────────────────────────
  SELECT 'DeepSeek-V3 (SiliconFlow)', 'deepseek-ai/DeepSeek-V3',
    'CHAT', 'siliconflow', 1, 111,
    0, 0,
    1,
    0.2800, 0.4200,
    'SiliconFlow - DeepSeek V3 高性能模型'
  UNION ALL
  SELECT 'Qwen2.5-72B-Instruct (SiliconFlow)', 'Qwen/Qwen2.5-72B-Instruct',
    'CHAT', 'siliconflow', 1, 112,
    0, 0,
    1,
    0.6000, 0.6000,
    'SiliconFlow - Qwen2.5 72B 高性能开源模型'
  UNION ALL
  SELECT 'Llama-3.1-8B-Instruct (SiliconFlow)', 'meta-llama/Meta-Llama-3.1-8B-Instruct',
    'CHAT', 'siliconflow', 1, 113,
    0, 0,
    1,
    0.1000, 0.1000,
    'SiliconFlow - Meta Llama 3.1 8B 轻量模型'

  UNION ALL
  -- ── DeepSeek ───────────────────────────────────────────
  SELECT 'DeepSeek Chat', 'deepseek-chat',
    'CHAT', 'deepseek', 1, 116,
    0, 0,
    1,
    0.1400, 0.2800,
    'DeepSeek - 通用对话模型，中文表现优秀'
  UNION ALL
  SELECT 'DeepSeek Reasoner', 'deepseek-reasoner',
    'CHAT', 'deepseek', 1, 117,
    0, 1,
    1,
    0.5500, 2.1900,
    'DeepSeek - 推理强化模型，深度思考能力'
  UNION ALL
  SELECT 'DeepSeek Coder', 'deepseek-coder',
    'CHAT', 'deepseek', 1, 118,
    0, 0,
    1,
    0.1400, 0.2800,
    'DeepSeek - 代码专用模型，编程能力强'

  UNION ALL
  -- ── 阿里云通义千问 ──────────────────────────────────────
  SELECT 'Qwen-Plus', 'qwen-plus',
    'CHAT', 'aliyun', 1, 121,
    0, 0,
    1,
    0.1150, 0.4600,
    '阿里云 - Qwen-Plus 高性能对话模型，平衡性能与成本'
  UNION ALL
  SELECT 'Qwen-Max', 'qwen-max',
    'CHAT', 'aliyun', 1, 122,
    0, 0,
    1,
    1.2000, 6.0000,
    '阿里云 - Qwen-Max 旗舰模型，最强性能'
  UNION ALL
  SELECT 'Qwen-Turbo', 'qwen-turbo',
    'CHAT', 'aliyun', 1, 123,
    0, 0,
    1,
    0.0350, 0.1400,
    '阿里云 - Qwen-Turbo 轻量快速模型'

  UNION ALL
  -- ── Ollama ─────────────────────────────────────────────
  SELECT 'Ollama Llama 3.2', 'llama3.2',
    'CHAT', 'ollama', 1, 131,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - Meta Llama 3.2 本地免费运行'
  UNION ALL
  SELECT 'Ollama Qwen 2.5', 'qwen2.5',
    'CHAT', 'ollama', 1, 132,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - Qwen 2.5 本地免费运行'
  UNION ALL
  SELECT 'Ollama DeepSeek R1', 'deepseek-r1',
    'CHAT', 'ollama', 1, 133,
    0, 0,
    1,
    0.0000, 0.0000,
    'Ollama - DeepSeek R1 本地免费运行'

  UNION ALL
  -- ── 豆包 ───────────────────────────────────────────────
  SELECT 'Doubao Pro 32K', 'doubao-pro-32k',
    'CHAT', 'doubao', 1, 141,
    0, 0,
    1,
    0.8000, 2.0000,
    '字节跳动 - doubao-pro-32k 专业版长上下文模型'
  UNION ALL
  SELECT 'Doubao Lite 32K', 'doubao-lite-32k',
    'CHAT', 'doubao', 1, 142,
    0, 0,
    1,
    0.4000, 1.0000,
    '字节跳动 - doubao-lite-32k 轻量版长上下文模型'

  UNION ALL
  -- ── 腾讯混元 ───────────────────────────────────────────
  SELECT 'Hunyuan Pro', 'hunyuan-pro',
    'CHAT', 'tencent', 1, 146,
    0, 0,
    1,
    0.5000, 2.0000,
    '腾讯混元 - hunyuan-pro 专业版模型'
  UNION ALL
  SELECT 'Hunyuan Standard', 'hunyuan-standard',
    'CHAT', 'tencent', 1, 147,
    0, 0,
    1,
    0.2000, 0.8000,
    '腾讯混元 - hunyuan-standard 标准版模型'

  UNION ALL
  -- ── Kimi ───────────────────────────────────────────────
  SELECT 'Moonshot v1 32K', 'moonshot-v1-32k',
    'CHAT', 'kimi', 1, 151,
    0, 0,
    1,
    12.0000, 12.0000,
    '月之暗面 - moonshot-v1-32k 长上下文模型（按 ¥/百万tokens 计费）'
  UNION ALL
  SELECT 'Kimi K2 Thinking', 'kimi-k2-thinking',
    'CHAT', 'kimi', 1, 152,
    0, 1,
    1,
    0.0000, 0.0000,
    '月之暗面 - kimi-k2-thinking 推理强化模型，MoE 架构'

  UNION ALL
  -- ── MiniMax ────────────────────────────────────────────
  SELECT 'MiniMax abab6.5s', 'abab6.5s-chat',
    'CHAT', 'minimax', 1, 156,
    0, 0,
    1,
    0.0000, 0.0000,
    'MiniMax - abab6.5s-chat 通用对话模型，快速响应'
  UNION ALL
  SELECT 'MiniMax abab6.5 Pro', 'abab6.5-pro',
    'CHAT', 'minimax', 1, 157,
    0, 0,
    1,
    0.0000, 0.0000,
    'MiniMax - abab6.5-pro 专业版模型，最强性能'

  UNION ALL
  -- ── 智谱 ───────────────────────────────────────────────
  SELECT 'GLM-4-Plus', 'glm-4-plus',
    'CHAT', 'zhipu', 1, 161,
    0, 0,
    1,
    0.5000, 2.0000,
    '智谱 - GLM-4-Plus 旗舰模型'
  UNION ALL
  SELECT 'GLM-4-Flash', 'glm-4-flash',
    'CHAT', 'zhipu', 1, 162,
    0, 0,
    1,
    0.0000, 0.0000,
    '智谱 - GLM-4-Flash 免费模型'
  UNION ALL
  SELECT 'GLM-4-Air', 'glm-4-air',
    'CHAT', 'zhipu', 1, 163,
    0, 0,
    1,
    0.0100, 0.0100,
    '智谱 - GLM-4-Air 轻量模型'

  UNION ALL
  -- ── Google Gemini ──────────────────────────────────────
  SELECT 'Gemini 2.0 Flash', 'gemini-2.0-flash',
    'CHAT', 'google', 1, 166,
    1, 0,
    1,
    0.1000, 0.4000,
    'Google - Gemini 2.0 Flash 快速多模态模型'
  UNION ALL
  SELECT 'Gemini 2.0 Pro', 'gemini-2.0-pro',
    'CHAT', 'google', 1, 167,
    1, 0,
    1,
    1.0000, 4.0000,
    'Google - Gemini 2.0 Pro 旗舰多模态模型'

  UNION ALL
  -- ── 华为盘古 ───────────────────────────────────────────
  SELECT '盘古 Text Chat', 'pangu-text-chat',
    'CHAT', 'huawei', 1, 171,
    0, 0,
    1,
    0.0080, 0.0080,
    '华为云 - pangu-text-chat 通用对话模型（按 ¥/千tokens 计费）'
  UNION ALL
  SELECT '盘古 Text Turbo', 'pangu-text-turbo',
    'CHAT', 'huawei', 1, 172,
    0, 0,
    1,
    0.0050, 0.0050,
    '华为云 - pangu-text-turbo 极速响应模型（按 ¥/千tokens 计费）'

  UNION ALL
  -- ── Claude ─────────────────────────────────────────────
  SELECT 'Claude Opus 4.7', 'claude-opus-4-7',
    'CHAT', 'claude', 1, 176,
    1, 0,
    1,
    15.0000, 75.0000,
    'Anthropic - Claude Opus 4.7 旗舰智能模型'
  UNION ALL
  SELECT 'Claude Sonnet 4.6', 'claude-sonnet-4-6',
    'CHAT', 'claude', 1, 177,
    1, 0,
    1,
    3.0000, 15.0000,
    'Anthropic - Claude Sonnet 4.6 最佳性价比模型'
  UNION ALL
  SELECT 'Claude Haiku 4.5', 'claude-haiku-4-5-20251001',
    'CHAT', 'claude', 1, 178,
    1, 0,
    1,
    1.0000, 5.0000,
    'Anthropic - Claude Haiku 4.5 轻量快速模型'

  UNION ALL
  -- ── 讯飞星火 ───────────────────────────────────────────
  SELECT 'Spark Pro', 'Spark Pro',
    'CHAT', 'xunfei', 1, 181,
    0, 0,
    1,
    0.0080, 0.0080,
    '讯飞 - Spark Pro 专业版模型（按 ¥/千tokens 计费）'
  UNION ALL
  SELECT 'Spark Max', 'Spark Max',
    'CHAT', 'xunfei', 1, 182,
    0, 0,
    1,
    0.0120, 0.0120,
    '讯飞 - Spark Max 旗舰模型（按 ¥/千tokens 计费）'

  UNION ALL
  -- ── Z.AI ───────────────────────────────────────────────
  SELECT 'GLM-4.7 (Z.AI)', 'GLM-4.7',
    'CHAT', 'zai', 1, 186,
    0, 0,
    1,
    0.6000, 2.2000,
    'Z.AI - GLM-4.7 最新旗舰文本模型（最佳性能）'
  UNION ALL
  SELECT 'GLM-4.7-Flash (Z.AI)', 'GLM-4.7-Flash',
    'CHAT', 'zai', 1, 187,
    0, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - GLM-4.7-Flash 免费文本模型'
  UNION ALL
  SELECT 'GLM-4.6V-Flash', 'GLM-4.6V-Flash',
    'CHAT', 'zai', 1, 188,
    1, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - GLM-4.6V-Flash 免费视觉模型（价格请以官方为准）'
  UNION ALL
  SELECT 'GLM-4.1V-Thinking-Flash', 'GLM-4.1V-Thinking-Flash',
    'CHAT', 'zai', 1, 189,
    1, 1,
    5,
    0.3000, 0.9000,
    'Z.AI - GLM-4.1V-Thinking-Flash 视觉推理模型（价格请以官方为准）'
  UNION ALL
  SELECT 'GLM-4V-Flash', 'GLM-4V-Flash',
    'CHAT', 'zai', 1, 190,
    1, 0,
    10,
    0.0000, 0.0000,
    'Z.AI - GLM-4V-Flash 视觉模型，高并发'
  UNION ALL
  SELECT 'GLM-5.1', 'GLM-5.1',
    'CHAT', 'zai', 1, 191,
    0, 1,
    10,
    2.0000, 8.0000,
    'Z.AI - GLM-5.1 最新旗舰文本模型（价格请以官方为准）'
  UNION ALL
  SELECT 'Cogview-3-Flash', 'Cogview-3-Flash',
    'IMAGE', 'zai', 1, 192,
    0, 0,
    1,
    0.0000, 0.0000,
    'Z.AI - Cogview-3-Flash 图像生成模型，支持 1024x1024 / 768x1344 / 864x1152 / 1344x768 / 1152x864 / 1440x720 / 720x1440 等多种分辨率'
  UNION ALL
  SELECT 'CogVideoX-Flash', 'CogVideoX-Flash',
    'VIDEO', 'zai', 1, 193,
    0, 0,
    3,
    0.0000, 0.0000,
    'Z.AI - CogVideoX-Flash 视频生成模型'
  UNION ALL
  -- ── OpenRouter ────────────────────────────────────────────────
  SELECT 'Nemotron-3-Nano-Omni-30B-A3B-Reasoning (OpenRouter)', 'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    'CHAT', 'openrouter', 1, 196,
    0, 1,
    5,
    0.0000, 0.0000,
    'OpenRouter - Nvidia Nemotron 3 Nano Omni 30B A3B Reasoning（免费，256K上下文）'
  UNION ALL
  SELECT 'DeepSeek-V4-Flash (OpenRouter)', 'deepseek/deepseek-v4-flash:free',
    'CHAT', 'openrouter', 1, 197,
    0, 0,
    10,
    0.0000, 0.0000,
    'OpenRouter - DeepSeek V4 Flash（免费，1M上下文）'
  UNION ALL
  SELECT 'GLM-5.1 (OpenRouter)', 'z-ai/glm-5.1',
    'CHAT', 'openrouter', 1, 198,
    0, 1,
    10,
    0.0000, 0.0000,
    'OpenRouter - Z.AI GLM-5.1 旗舰文本模型（203K上下文，免费）'
  UNION ALL
  SELECT 'Gemma-4-26B-A4B-IT (OpenRouter)', 'google/gemma-4-26b-a4b-it:free',
    'CHAT', 'openrouter', 1, 199,
    0, 0,
    5,
    0.0000, 0.0000,
    'OpenRouter - Google Gemma 4 26B A4B IT（免费，262K上下文）'
  UNION ALL
  -- ── DeepSeek 本地 ─────────────────────────────────────────────
  SELECT 'DeepSeek-Default', 'deepseek-default',
    'CHAT', 'deepseek-default', 1, 200,
    0, 0,
    10,
    0.0000, 0.0000,
    '本地 DeepSeek 默认模型（http://127.0.0.1:27777）'
  UNION ALL
  -- ── Sixthsense ─────────────────────────────────────────────────
  SELECT 'DeepSeek-V4-Flash-L', 'deepseek-v4-flash-l',
    'CHAT', 'sixthsense', 1, 201,
    0, 0,
    5,
    0.0000, 0.0000,
    'Sixthsense LLM - DeepSeek V4 Flash L（Claude Code 兼容协议）'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);

-- 更新已有 Z.AI 模型的并发数
UPDATE `sys_ai_module`
SET `sys_ai_module_concurrency` = 1
WHERE `sys_ai_module_code` IN ('GLM-4.7-Flash', 'GLM-4.7', 'GLM-4.6V', 'GLM-4.6')
  AND (`sys_ai_module_concurrency` IS NULL OR `sys_ai_module_concurrency` = 0);

-- ============================================================
-- S5. 本地 ONNX 模型
-- ============================================================
INSERT INTO `sys_ai_module` (
  `sys_ai_module_name`, `sys_ai_module_code`, `sys_ai_module_model_id`, `sys_ai_module_type`,
  `sys_ai_module_vendor`, `sys_ai_module_version`, `sys_ai_module_local`, `sys_ai_module_status`,
  `sys_ai_module_sort`, `sys_ai_module_remark`
)
SELECT * FROM (
  -- ── 图像上色 ────────────────────────────────────────
  SELECT '本地 ONNX 图像上色 (DDColor)' AS name, 'local-onnx-colorization' AS code,
    'traced_ddcolor' AS model_id, 'COLORIZATION' AS type,
    'local-onnx' AS vendor, 'onnx' AS ver, 1 AS local, 1 AS status, 10 AS sort,
    '内置本地 ONNX DDColor 图像上色模块' AS remark
  UNION ALL
  -- ── 分辨率增强 ──────────────────────────────────────
  SELECT '本地 ONNX 图片增强 (Real-ESRGAN)', 'local-onnx-resolution',
    'real_esrgan', 'RESOLUTION',
    'local-onnx', 'onnx', 1, 1, 20,
    '内置本地 ONNX Real-ESRGAN 图片增强模块'
  UNION ALL
  -- ── 人脸增强 ────────────────────────────────────────
  SELECT '本地 ONNX 人脸增强 (GFPGAN)', 'local-onnx-face-enhance',
    'gfpgan', 'FACE',
    'local-onnx', 'onnx', 1, 1, 30,
    '内置本地 ONNX GFPGAN 人脸增强模块'
  UNION ALL
  SELECT '本地 ONNX 人脸增强 (RestoreFormer++)', 'local-onnx-face-enhance-restoreformer',
    'restoreformer_plusplus', 'FACE',
    'local-onnx', 'onnx', 1, 1, 31,
    '内置本地 ONNX RestoreFormer++ 人脸增强模块'
  UNION ALL
  -- ── 人脸检测 ────────────────────────────────────────
  SELECT '本地 ONNX 人脸检测 (Slim)', 'local-onnx-face-detect-slim',
    'slim', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 40,
    '内置本地 ONNX UltraFace Slim 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (MobileNet)', 'local-onnx-face-detect-mobilenet',
    'mobilenet', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 41,
    '内置本地 ONNX UltraFace MobileNet 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (RFB)', 'local-onnx-face-detect-rfb',
    'RFB', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 42,
    '内置本地 ONNX UltraFace RFB 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (SCRFD)', 'local-onnx-face-detect-scrfd',
    'scrfd_2_5g_bnkps', 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 43,
    '内置本地 ONNX SCRFD 2.5G 人脸检测模块'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (RetinaFace 640)', 'local-onnx-face-retina-640',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 44,
    '基于 RetinaFace 的 ONNX 人脸检测模型（640px）'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (RetinaFace 1080x720)', 'local-onnx-face-retina-1080x720',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 45,
    '基于 RetinaFace 的 ONNX 人脸检测模型（1080x720）'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (UltraLight)', 'local-onnx-face-ultralight',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 46,
    'UltraLight 快速通用 ONNX 人脸检测模型'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (YOLOv5 320)', 'local-onnx-face-yolov5-320',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 47,
    '基于 YOLOv5 的 ONNX 人脸检测模型（320px）'
  UNION ALL
  SELECT '本地 ONNX 人脸检测 (YOLOv5 640)', 'local-onnx-face-yolov5-640',
    NULL, 'FACE_DETECT',
    'local-onnx', 'onnx', 1, 1, 48,
    '基于 YOLOv5 的 ONNX 人脸检测模型（640px）'
  UNION ALL
  -- ── 人脸特征提取 ────────────────────────────────────
  SELECT '本地 ONNX 人脸特征 (ArcFace 512D)', 'local-onnx-face-feature-arcface',
    'face_feature', 'FACE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 50,
    '内置本地 ONNX ArcFace 512D 人脸特征提取模块'
  UNION ALL
  SELECT '本地 ONNX 人脸特征 (ResNet-50 512D)', 'local-onnx-face-feature-r50',
    'face_feature_r50', 'FACE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 51,
    '内置本地 ONNX ResNet-50 512D 人脸特征提取模块'
  UNION ALL
  -- ── 抠图 ────────────────────────────────────────────
  SELECT '本地 ONNX 抠图 (U2Net)', 'local-onnx-matting-u2net',
    'u2net', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 60,
    '内置本地 ONNX U2Net 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (U2Net-P 轻量)', 'local-onnx-matting-u2netp',
    'u2netp', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 61,
    '内置本地 ONNX U2Net-P 轻量级图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (U2Net_UNet)', 'local-onnx-matting-u2net-unet',
    'u2net_unet', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 62,
    '内置本地 ONNX U2Net_UNet 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Anime 动漫)', 'local-onnx-matting-anime',
    'anime', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 63,
    '内置本地 ONNX Anime 动漫风格图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Human 人像)', 'local-onnx-matting-human',
    'human', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 64,
    '内置本地 ONNX Human 人像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Silueta)', 'local-onnx-matting-silueta',
    'silueta', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 65,
    '内置本地 ONNX Silueta 图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (Cloth 衣物)', 'local-onnx-matting-cloth',
    'cloth', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 66,
    '内置本地 ONNX Cloth 衣物抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (ISNet 通用)', 'local-onnx-matting-isnet-general-use',
    'isnet-general-use', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 67,
    '内置本地 ONNX ISNet 通用图像抠图模块'
  UNION ALL
  SELECT '本地 ONNX 抠图 (RMBG-2.0)', 'local-onnx-matting-rmbg',
    'RMBG-2.0', 'MATTING',
    'local-onnx', 'onnx', 1, 1, 68,
    '内置本地 ONNX RMBG-2.0 高精度图像抠图模块'
  UNION ALL
  -- ── 图像特征提取（以图搜图） ────────────────────────
  SELECT '本地 ONNX 图像特征 (CLIP ViT-B-32)', 'local-onnx-image-feature-clip-b32',
    'CLIP-ViT-B-32-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 70,
    '内置本地 ONNX CLIP ViT-B-32 图像特征提取模块（512维，用于以图搜图）'
  UNION ALL
  SELECT '本地 ONNX 图像特征 (CLIP ViT-B-16)', 'local-onnx-image-feature-clip-b16',
    'CLIP-ViT-B-16-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 71,
    '内置本地 ONNX CLIP ViT-B-16 图像特征提取模块（512维，更高精度）'
  UNION ALL
  SELECT '本地 ONNX 图像特征 (DINOv2)', 'local-onnx-image-feature-dinov2',
    'DINOv2-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 72,
    '内置本地 ONNX DINOv2 图像特征提取模块（视觉基础模型）'
  UNION ALL
  SELECT '本地 ONNX 图像特征 (MobileCLIP)', 'local-onnx-image-feature-mobileclip',
    'MobileCLIP-IMAGE', 'IMAGE_FEATURE',
    'local-onnx', 'onnx', 1, 1, 73,
    '内置本地 ONNX MobileCLIP 轻量级图像特征提取模块'
  UNION ALL
  -- ── 文字识别 OCR ────────────────────────────────────
  SELECT '本地 ONNX 文字识别 (PP-OCRv4)', 'local-onnx-ocr-ppv4',
    'PP-OCRv4_mobile_det_infer', 'OCR',
    'local-onnx', 'onnx', 1, 1, 80,
    '内置本地 ONNX PP-OCRv4 文字检测识别模块（中英文）'
  UNION ALL
  SELECT '本地 ONNX 文字识别 (PP-OCRv5)', 'local-onnx-ocr-ppv5',
    'PP-OCRv5_mobile_det_infer', 'OCR',
    'local-onnx', 'onnx', 1, 1, 81,
    '内置本地 ONNX PP-OCRv5 文字检测识别模块（中英文）'
  UNION ALL
  SELECT '本地 ONNX 通用文字识别 (LightweightEdge)', 'local-onnx-ocr-lightweight',
    'cv_LightweightEdge_ocr-recognitoin-general_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 82,
    '内置本地 ONNX LightweightEdge 通用文字识别模块'
  UNION ALL
  SELECT '本地 ONNX 手写文字识别 (ConvNext)', 'local-onnx-ocr-handwritten',
    'cv_convnextTiny_ocr-recognition-handwritten_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 83,
    '内置本地 ONNX ConvNext 手写文字识别模块'
  UNION ALL
  SELECT '本地 ONNX 车牌识别 (ConvNext)', 'local-onnx-ocr-licenseplate',
    'cv_convnextTiny_ocr-recognition-licenseplate_damo', 'OCR',
    'local-onnx', 'onnx', 1, 1, 84,
    '内置本地 ONNX ConvNext 车牌识别模块'
  UNION ALL
  -- ── 图像分类（动漫检测） ────────────────────────────
  SELECT '本地 ONNX 动漫/真人二分类', 'local-onnx-cls-anime-real',
    'anime_real_cls', 'CLASSIFICATION',
    'local-onnx', 'onnx', 1, 1, 90,
    '内置本地 ONNX 动漫/真人二分类模型'
  UNION ALL
  SELECT '本地 ONNX 动漫图片分类 (CL Tagger)', 'local-onnx-cls-anime-image',
    'anime_image_classifier', 'CLASSIFICATION',
    'local-onnx', 'onnx', 1, 1, 91,
    '内置本地 ONNX CLIP + CL-Tagger 轻量动漫图片二分类器'
  UNION ALL
  -- ── 行人重识别 ReID ─────────────────────────────────
  SELECT '本地 ONNX 行人重识别 (YouTu ReID 768D)', 'local-onnx-person-reid-youtu',
    NULL, 'REID',
    'local-onnx', 'onnx', 1, 1, 100,
    '基于 YouTu ReID 的本地 ONNX 行人重识别模型（768d）'
  UNION ALL
  SELECT '本地 ONNX 行人重识别 (OSNet-AIN 512D)', 'local-onnx-person-reid-osnet',
    NULL, 'REID',
    'local-onnx', 'onnx', 1, 1, 101,
    '基于 OSNet-AIN 的本地 ONNX 行人重识别模型（512d）'
  UNION ALL
  -- ── SafeTensors：Qwen2.5 系列 ────────────────────────
  SELECT '本地 SafeTensor Qwen2.5-0.5B', 'local-safetensor-qwen2.5-0.5b',
    'qwen2.5-0.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 201,
    'Qwen2.5 0.5B 超轻量文本生成模型，适合低资源环境'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-1.5B', 'local-safetensor-qwen2.5-1.5b',
    'qwen2.5-1.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 202,
    'Qwen2.5 1.5B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-3B', 'local-safetensor-qwen2.5-3b',
    'qwen2.5-3b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 203,
    'Qwen2.5 3B 标准文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-7B', 'local-safetensor-qwen2.5-7b',
    'qwen2.5-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 204,
    'Qwen2.5 7B 高性能文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-14B', 'local-safetensor-qwen2.5-14b',
    'qwen2.5-14b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 205,
    'Qwen2.5 14B 大容量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2.5-Coder-7B', 'local-safetensor-qwen2.5-coder-7b',
    'qwen2.5-coder-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 206,
    'Qwen2.5 Coder 7B 代码生成专用模型'
  UNION ALL
  -- ── SafeTensors：Qwen2 系列 ──────────────────────────
  SELECT '本地 SafeTensor Qwen2-0.5B', 'local-safetensor-qwen2-0.5b',
    'qwen2-0.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 207,
    'Qwen2 0.5B 超轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2-1.5B', 'local-safetensor-qwen2-1.5b',
    'qwen2-1.5b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 208,
    'Qwen2 1.5B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Qwen2-7B', 'local-safetensor-qwen2-7b',
    'qwen2-7b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 209,
    'Qwen2 7B 标准文本生成模型'
  UNION ALL
  -- ── SafeTensors：Llama3.2 系列 ───────────────────────
  SELECT '本地 SafeTensor Llama3.2-1B', 'local-safetensor-llama3.2-1b',
    'llama3.2-1b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 211,
    'Meta Llama 3.2 1B 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor Llama3.2-3B', 'local-safetensor-llama3.2-3b',
    'llama3.2-3b', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 212,
    'Meta Llama 3.2 3B 高效文本生成模型'
  UNION ALL
  -- ── SafeTensors：MiniMind2 系列 ──────────────────────
  SELECT '本地 SafeTensor MiniMind2-104M', 'local-safetensor-minimind2-104m',
    'minimind2-104m', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 213,
    'MiniMind2 104M 极致轻量文本生成模型，适合教学/实验场景'
  UNION ALL
  SELECT '本地 SafeTensor MiniMind2-Small', 'local-safetensor-minimind2-small',
    'minimind2-small', 'CHAT',
    'safetensors', 'safetensor', 1, 1, 214,
    'MiniMind2 Small 轻量文本生成模型'
  UNION ALL
  SELECT '本地 SafeTensor MiniMind-3o (Omni)', 'local-safetensor-minimind-3o',
    'minimind-3o', 'ASR',
    'safetensors', 'safetensor', 1, 1, 215,
    'MiniMind-3o Omni 多模态模型，支持 ASR 语音转文字 / TTS / 图文理解'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_module`
  WHERE `sys_ai_module_code` = seed.code
);

-- ============================================================
-- S6. 全局配置种子数据
-- ============================================================
INSERT INTO `sys_ai_global_setting` (
  `sys_ai_global_setting_group`,
  `sys_ai_global_setting_field`,
  `sys_ai_global_setting_value`,
  `sys_ai_global_setting_value_type`,
  `sys_ai_global_setting_description`,
  `sys_ai_global_setting_sort`,
  `sys_ai_global_setting_status`,
  `create_name`,
  `create_by`,
  `update_name`,
  `update_by`
)
SELECT * FROM (
  -- runtime 组
  SELECT 'runtime' AS grp, 'enabled' AS fld, 'true' AS val, 'BOOLEAN' AS vtype,
    '是否启用 AI ChatClient' AS descr, 5 AS sort, 1 AS st, 'system' AS cn, 1 AS cb, 'system' AS un, 1 AS ub
  UNION ALL
  SELECT 'runtime', 'factory', 'default', 'STRING', 'ChatClient SPI 工厂名称', 8, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider', 'zhipu', 'STRING', '默认 LLM 提供商', 9, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'default_model', 'GLM-4V-Flash', 'STRING', '默认聊天模型编码', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'system_prompt', '', 'STRING', '默认系统提示词', 15, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'temperature', '0.7', 'NUMBER', '默认采样温度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'max_tokens', '4096', 'NUMBER', '默认最大输出 token', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'timeout_millis', '30000', 'NUMBER', '默认超时时间(毫秒)', 35, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'app_key_enabled', 'true', 'BOOLEAN', '是否启用 AI AppKey 校验', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'input_optimization_enabled', 'false', 'BOOLEAN', '是否启用输入优化', 45, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_enabled', 'false', 'BOOLEAN', '是否启用上下文压缩', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_threshold', '12', 'NUMBER', '触发上下文压缩的历史阈值', 55, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'context_compression_retain_messages', '6', 'NUMBER', '压缩后保留的消息数量', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider_base_url', 'https://open.bigmodel.cn/api/paas/v4/', 'STRING', '智谱 AI 基础地址（必须以 / 结尾）', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'provider_api_key', '', 'STRING', '智谱 API Key', 71, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'charge_enabled', 'false', 'BOOLEAN', '每次请求扣减额度', 41, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'runtime', 'stop_when_balance_lt_0', 'true', 'BOOLEAN', '余额不足时停止服务', 42, 1, 'system', 1, 'system', 1
  -- face_recognition 组
  UNION ALL
  SELECT 'face_recognition', 'default_face_detect_model', 'local-onnx-face-detect-slim', 'STRING', '默认人脸检测模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'default_face_feature_model', 'local-onnx-face-feature-arcface', 'STRING', '默认特征提取模型', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'default_face_enhance_model', 'local-onnx-face-enhance', 'STRING', '默认人脸增强模型', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'feature_dimension', '512', 'INT', '特征向量维度', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'min_similarity', '0.5', 'FLOAT', '最低相似度阈值', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'vector_storage_engine', 'jvector', 'STRING', '向量存储引擎(memory/jvector/milvus/mysql)', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'animation_face_filter_enabled', 'true', 'BOOLEAN', '动漫人脸过滤(开启后可防止动漫/插画图片通过人脸识别)', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'face_recognition', 'anime_face_detect_model', '', 'STRING', '动漫人脸检测模型(留空则自动查找)', 80, 1, 'system', 1, 'system', 1
  -- image_search 组
  UNION ALL
  SELECT 'image_search', 'default_image_feature_model', 'CLIP-ViT-B-32-IMAGE', 'STRING', '默认图像特征提取模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'feature_dimension', '512', 'INT', '特征向量维度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'min_similarity', '0.3', 'FLOAT', '最低相似度阈值', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'vector_storage_engine', 'jvector', 'STRING', '向量存储引擎(memory/jvector/milvus/mysql)', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'max_image_size', '1024', 'INT', '图像最大边长(超此值压缩)', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'image_search', 'face_enhance_enabled', 'false', 'BOOLEAN', '是否启用图像增强', 60, 1, 'system', 1, 'system', 1
  -- ocr 组
  UNION ALL
  SELECT 'ocr', 'default_ocr_model', 'local-onnx-ocr-ppv5', 'STRING', '默认OCR模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_language', 'ch', 'STRING', '识别语言(ch/en/jp)', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_confidence_threshold', '0.5', 'FLOAT', '置信度阈值', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'ocr_max_image_size', '2048', 'INT', '图像最大边长', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_contrast', 'true', 'BOOLEAN', '对比度增强', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_brightness', '0', 'INT', '亮度调整(-100~100)', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_denoise', 'true', 'BOOLEAN', '降噪处理', 70, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_binarize', 'false', 'BOOLEAN', '二值化处理', 80, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'ocr', 'enhance_deskew', 'true', 'BOOLEAN', '倾斜校正', 90, 1, 'system', 1, 'system', 1
  -- knowledge_graph 组
  UNION ALL
  SELECT 'knowledge_graph', 'enabled', 'true', 'BOOLEAN', '是否启用知识图谱功能', 5, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'extraction_prompt_template', '', 'STRING', '实体抽取提示词模板(留空使用默认)', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'relation_extraction_enabled', 'true', 'BOOLEAN', '是否启用关系抽取', 40, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'max_entities_per_doc', '50', 'INT', '单文档最大抽取实体数', 50, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'embedding_dimension', '512', 'INT', '实体嵌入向量维度', 60, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'knowledge_graph', 'vector_storage_engine', 'jvector', 'STRING', '实体嵌入向量存储引擎', 70, 1, 'system', 1, 'system', 1
  -- person_reid 组
  UNION ALL
  SELECT 'person_reid', 'default_reid_model', 'local-onnx-person-reid-youtu', 'STRING',
    '默认 ReID 模型', 10, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_feature_dimension', '768', 'INT',
    'ReID 特征向量维度', 20, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_min_similarity', '0.5', 'FLOAT',
    'ReID 最低相似度阈值', 30, 1, 'system', 1, 'system', 1
  UNION ALL
  SELECT 'person_reid', 'reid_vector_storage_engine', 'jvector', 'STRING',
    'ReID 向量存储引擎(memory/jvector/milvus/mysql)', 40, 1, 'system', 1, 'system', 1
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_ai_global_setting`
  WHERE `sys_ai_global_setting_group` = seed.grp
    AND `sys_ai_global_setting_field` = seed.fld
);

-- ============================================================
-- S7. 模型数据修复：删除旧 LLM 类型数据，修正为 CHAT
-- ============================================================
DELETE FROM `sys_ai_module`
WHERE `sys_ai_module_type` = 'LLM'
  AND `sys_ai_module_code` NOT IN (
    'KAT-Coder-Exp-72B-1010', 'DeepSeek-R1-0528-Qwen3-8B',
    'deepseek-v4-pro', 'Qwen/Qwen3-32B', 'deepseek-ai/DeepSeek-V4-Flash',
    'gpt-4o', 'gpt-4o-mini', 'gpt-5.2',
    'deepseek-ai/DeepSeek-V3', 'Qwen/Qwen2.5-72B-Instruct',
    'meta-llama/Meta-Llama-3.1-8B-Instruct',
    'deepseek-chat', 'deepseek-reasoner', 'deepseek-coder',
    'qwen-plus', 'qwen-max', 'qwen-turbo',
    'llama3.2', 'qwen2.5', 'deepseek-r1',
    'doubao-pro-32k', 'doubao-lite-32k',
    'hunyuan-pro', 'hunyuan-standard',
    'moonshot-v1-32k', 'kimi-k2-thinking',
    'abab6.5s-chat', 'abab6.5-pro',
    'glm-4-plus', 'glm-4-flash', 'glm-4-air',
    'gemini-2.0-flash', 'gemini-2.0-pro',
    'pangu-text-chat', 'pangu-text-turbo',
    'claude-opus-4-7', 'claude-sonnet-4-6', 'claude-haiku-4-5-20251001',
    'Spark Pro', 'Spark Max',
    'GLM-4.7', 'GLM-4.7-Flash', 'GLM-4.6V-Flash',
    'GLM-4.1V-Thinking-Flash', 'GLM-4V-Flash', 'GLM-5.1',
    'Cogview-3-Flash', 'CogVideoX-Flash',
    'nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',
    'deepseek/deepseek-v4-flash:free', 'z-ai/glm-5.1',
    'google/gemma-4-26b-a4b-it:free',
    'deepseek-default', 'deepseek-v4-flash-l'
  );

UPDATE `sys_ai_module`
SET `sys_ai_module_type` = 'CHAT'
WHERE `sys_ai_module_type` = 'LLM';

-- ============================================================
-- S8. 默认分组
-- ============================================================
INSERT IGNORE INTO `sys_ai_group` (`sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`, `sys_ai_group_sort`, `sys_ai_group_status`)
VALUES (1, '全部模型', '可访问所有已启用的模型', 0, 1);



-- ============================================================
-- AI 角色（人设）表
-- 角色是一段预设的系统提示词人物设定，让 AI 以特定身份应答。
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_ai_role` (
    `sys_ai_role_id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '角色主键',
    `sys_ai_role_name` VARCHAR(128) NOT NULL COMMENT '角色名称',
    `sys_ai_role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `sys_ai_role_description` VARCHAR(512) DEFAULT NULL COMMENT '角色描述',
    `sys_ai_role_system_prompt` TEXT NOT NULL COMMENT '系统提示词（人设核心）',
    `sys_ai_role_model` VARCHAR(128) DEFAULT NULL COMMENT '绑定默认模型',
    `sys_ai_role_temperature` DOUBLE DEFAULT NULL COMMENT '采样温度',
    `sys_ai_role_sort` INT DEFAULT 0 COMMENT '排序值',
    `sys_ai_role_status` INT DEFAULT 1 COMMENT '状态：1启用0禁用',
    `sys_ai_role_delete` INT DEFAULT 0 COMMENT '删除标记：0未删除1已删除',
    `create_name` VARCHAR(255) DEFAULT NULL COMMENT '创建人名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_sys_ai_role_code` (`sys_ai_role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 角色（人设）表';

-- 插入默认角色
INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('通用助手', 'default', '通用 AI 助手，无特殊人设', '你是一个智能 AI 助手，请用中文友好地回答用户的问题。', 0, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('Java 开发助理', 'java-dev', '专业的 Java 后端开发工程师', '你是一个资深的 Java 后端开发工程师，精通 Spring Boot、MyBatis、微服务架构。请用中文回答技术问题，提供代码示例时遵循最佳实践。', 1, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);

INSERT INTO `sys_ai_role` (`sys_ai_role_name`, `sys_ai_role_code`, `sys_ai_role_description`, `sys_ai_role_system_prompt`, `sys_ai_role_sort`, `sys_ai_role_status`, `sys_ai_role_delete`)
VALUES ('UI 设计师', 'ui-designer', '前端 UI/UX 设计师', '你是一个优秀的前端 UI/UX 设计师，精通 Vue 3、Element Plus、Tailwind CSS。设计风格简洁现代，参考 Vercel/Linear 设计语言。', 2, 1, 0)
ON DUPLICATE KEY UPDATE `sys_ai_role_name` = VALUES(`sys_ai_role_name`);


-- ============================================================
-- AI 测试种子数据（异步 INITDATA 脚本）
-- 包含：供应商、模型、分组、AppKey 等测试数据
-- 使用 INSERT IGNORE 保证幂等
-- ============================================================

-- 1. 模型供应商（MockOpenAI）
INSERT IGNORE INTO `sys_ai_provider` (
    `sys_ai_provider_id`, `sys_ai_provider_name`, `sys_ai_provider_type`,
    `sys_ai_provider_base_url`, `sys_ai_provider_api_key`,
    `sys_ai_provider_models`, `sys_ai_provider_enabled`,
    `sys_ai_provider_deleted`, `create_name`, `create_time`, `update_time`
) VALUES (
    1, 'MockOpenAI', 'openai',
    'http://127.0.0.1:9901/v1', 'sk-mock',
    '["mock-gpt-4o"]', 1, 0, 'system',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 2. 模型定义（mock-gpt-4o）
INSERT IGNORE INTO `sys_ai_model` (
    `sys_ai_model_id`, `sys_ai_model_name`, `sys_ai_model_code`,
    `sys_ai_model_provider_id`, `sys_ai_model_capabilities`,
    `sys_ai_model_enabled`, `sys_ai_model_sort`,
    `sys_ai_model_create_time`, `sys_ai_model_update_time`
) VALUES (
    1, 'MockGPT-4o', 'mock-gpt-4o',
    1, 'chat', 1, 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 3. 模型分组（研发组）
INSERT IGNORE INTO `sys_ai_group` (
    `sys_ai_group_id`, `sys_ai_group_name`, `sys_ai_group_desc`,
    `sys_ai_group_models`, `sys_ai_group_enabled`,
    `sys_ai_group_delete`, `create_time`, `update_time`
) VALUES (
    1, '研发组', '测试分组',
    '["mock-gpt-4o"]', 1, 0,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

-- 4. 分组-模型关联
INSERT IGNORE INTO `sys_ai_group_model` (
    `sys_ai_group_model_id`, `sys_ai_group_id`, `sys_ai_module_id`, `create_time`
) VALUES (
    1, 1, 1, CURRENT_TIMESTAMP
);

-- 5. AppKey（sk-chat-verify）
INSERT IGNORE INTO `sys_ai_app_key` (
    `sys_ai_app_key_id`, `sys_ai_app_key_app_key`, `sys_ai_app_key_secret`,
    `sys_ai_app_key_name`, `sys_ai_app_key_provider_id`, `sys_ai_app_key_model`,
    `sys_ai_app_key_group_id`, `sys_ai_app_key_status`, `sys_ai_app_key_delete`,
    `sys_ai_app_key_use_count`, `sys_ai_app_key_max_use`,
    `sys_ai_app_key_max_tokens`, `sys_ai_app_key_total_tokens`,
    `create_name`, `create_time`, `update_time`
) VALUES (
    1, 'sk-chat-verify', 'sk-chat-verify',
    '测试应用', 1, 'mock-gpt-4o',
    1, 1, 0,
    0, NULL, NULL, 0,
    'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
