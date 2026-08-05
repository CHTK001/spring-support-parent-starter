-- ============================================================
-- AI 模块初始化脚本（仅表结构）
-- 说明：
-- 本文件同时包含完整建表语句和初始化数据。
-- ============================================================

-- H2 兼容模式下跳过 MySQL 专属指令



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
  `sys_ai_compression_context` LONGVARCHAR COMMENT '压缩后的上下文',
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
  `sys_ai_message_content` LONGVARCHAR COMMENT '消息内容',
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
  `sys_ai_task_condition` LONGVARCHAR COMMENT '任务条件JSON',
  `sys_ai_task_request` LONGVARCHAR COMMENT '任务请求JSON',
  `sys_ai_task_result` LONGVARCHAR COMMENT '任务结果JSON',
  `sys_ai_task_url` LONGVARCHAR COMMENT '输出资源地址',
  `sys_ai_task_local_url` LONGVARCHAR COMMENT '本地资源地址',
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
  `sys_ai_template_recommend_prompt` LONGVARCHAR COMMENT '推荐提示词',
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
  `sys_ai_setting_value` LONGVARCHAR COMMENT '配置值',
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
  `sys_ai_global_setting_value` LONGVARCHAR COMMENT '配置值',
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
  `sys_ai_face_library_thumbnail` LONGVARCHAR COMMENT '人脸缩略图(base64)',
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
  `sys_ai_face_recognize_record_thumbnail` LONGVARCHAR COMMENT '识别缩略图(base64)',
  `sys_ai_face_recognize_record_result` TEXT COMMENT '识别结果JSON',
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
  `sys_ai_image_library_thumbnail` LONGVARCHAR COMMENT '缩略图(base64)',
  `sys_ai_image_library_url` VARCHAR(2048) DEFAULT NULL COMMENT '图像URL',
  `sys_ai_image_library_tags` VARCHAR(512) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `sys_ai_image_library_meta` TEXT DEFAULT NULL COMMENT '扩展元数据',
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
  `sys_ai_knowledge_graph_entity_properties` TEXT        DEFAULT NULL COMMENT '实体属性(JSON)',
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
  `sys_ai_knowledge_graph_relation_properties`         TEXT          DEFAULT NULL COMMENT '关系属性(JSON)',
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
  `sys_ai_reid_library_thumbnail` LONGVARCHAR COMMENT '重识别人物缩略图(base64)',
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
-- 24. AI 供应商表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_provider`;
CREATE TABLE IF NOT EXISTS `ai_provider` (
    `provider_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '供应商主键',
    `provider_name` VARCHAR(255) NOT NULL COMMENT '供应商名称',
    `provider_type` VARCHAR(64) NOT NULL COMMENT '供应商类型(openai/anthropic/gemini/qwen/custom)',
    `provider_base_url` VARCHAR(512) DEFAULT NULL COMMENT '接入 Base URL',
    `provider_api_key` VARCHAR(512) DEFAULT NULL COMMENT 'API Key',
    `provider_logo` VARCHAR(1024) DEFAULT NULL COMMENT 'Logo 图片地址',
    `provider_models` TEXT DEFAULT NULL COMMENT '可用模型列表(JSON)',
    `provider_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `provider_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `provider_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `provider_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY `uk_ai_provider_name` (`provider_name`)
) COMMENT='模型供应商表';

-- ============================================================
-- 25. AI 模型分组表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_group`;
CREATE TABLE IF NOT EXISTS `ai_group` (
    `group_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分组主键',
    `group_name` VARCHAR(128) NOT NULL COMMENT '分组名称',
    `group_description` VARCHAR(256) DEFAULT NULL COMMENT '分组描述',
    `group_models` TEXT DEFAULT NULL COMMENT '分组关联模型列表(JSON)',
    `group_app_key_ids` TEXT DEFAULT NULL COMMENT '绑定 AppKey ID 列表(JSON)',
    `group_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `group_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `group_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `group_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY `uk_ai_group_name` (`group_name`)
) COMMENT='模型分组表';

-- ============================================================
-- 26. AI 模型定义表（关联供应商，含能力标签）
-- ============================================================
DROP TABLE IF EXISTS `ai_model`;
CREATE TABLE IF NOT EXISTS `ai_model` (
    `model_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模型主键',
    `model_name` VARCHAR(255) NOT NULL COMMENT '模型显示名称',
    `model_code` VARCHAR(255) NOT NULL COMMENT '模型标识(gpt-4o, deepseek-chat)',
    `model_provider_id` BIGINT DEFAULT NULL COMMENT '关联供应商 ID',
    `model_capabilities` VARCHAR(512) DEFAULT NULL COMMENT '能力标签(逗号分隔: chat,vision,think,search)',
    `model_description` VARCHAR(512) DEFAULT NULL COMMENT '模型描述',
    `model_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `model_sort` INT DEFAULT 0 COMMENT '排序值',
    `model_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `model_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `model_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE KEY `uk_ai_model_code` (`model_code`)
) COMMENT='AI 模型定义表';

-- ============================================================
-- 27. AI AppKey 表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_app_key`;
CREATE TABLE IF NOT EXISTS `ai_app_key` (
    `app_key_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'AppKey 主键',
    `app_key_name` VARCHAR(255) NOT NULL COMMENT 'AppKey 名称',
    `app_key_value` VARCHAR(512) NOT NULL COMMENT 'AppKey 密钥值',
    `app_key_provider_id` BIGINT DEFAULT NULL COMMENT '关联供应商 ID',
    `app_key_model` VARCHAR(255) DEFAULT NULL COMMENT '限定使用的模型',
    `app_key_group_id` BIGINT DEFAULT NULL COMMENT '绑定分组 ID（分组的 key 只能用分组下的模型）',
    `app_key_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `app_key_use_count` BIGINT DEFAULT 0 COMMENT '已使用次数',
    `app_key_expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
    `app_key_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `app_key_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `app_key_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记'
) COMMENT='AI AppKey 表';

-- ============================================================
-- 28. AI 全局设置表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_global_setting`;
CREATE TABLE IF NOT EXISTS `ai_global_setting` (
    `setting_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设置主键',
    `setting_chat_client_enabled` INT DEFAULT 1 COMMENT '是否启用 ChatClient 聚合',
    `setting_usage_interval_sec` INT DEFAULT 60 COMMENT '用量自动刷新间隔(秒)',
    `setting_enable_usage_analysis` INT DEFAULT 1 COMMENT '是否启用用量分析',
    `setting_default_model` VARCHAR(255) DEFAULT NULL COMMENT '默认模型',
    `setting_temperature` DECIMAL(4,2) DEFAULT 0.70 COMMENT '默认温度',
    `setting_max_tokens` INT DEFAULT 2048 COMMENT '默认最大 tokens',
    `setting_timeout` INT DEFAULT 30 COMMENT '默认请求超时(秒)',
    `setting_enable_cache` INT DEFAULT 1 COMMENT '是否启用缓存',
    `setting_stream` INT DEFAULT 1 COMMENT '是否流式输出',
    `setting_update_time` DATETIME DEFAULT NULL COMMENT '更新时间'
) COMMENT='AI 全局设置表';

-- ============================================================
-- 29. AI 服务运行状态表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_service_status`;
CREATE TABLE IF NOT EXISTS `ai_service_status` (
    `status_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '状态主键',
    `status_service_name` VARCHAR(255) NOT NULL COMMENT '服务名称',
    `status_running` INT DEFAULT 0 COMMENT '是否运行中',
    `status_pid` BIGINT DEFAULT NULL COMMENT '进程 PID',
    `status_start_time` DATETIME DEFAULT NULL COMMENT '启动时间',
    `status_uptime_sec` BIGINT DEFAULT 0 COMMENT '运行时长(秒)',
    `status_update_time` DATETIME DEFAULT NULL COMMENT '更新时间'
) COMMENT='AI 服务运行状态表';

-- ============================================================
-- 30. AI 用量统计表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_usage`;
CREATE TABLE IF NOT EXISTS `ai_usage` (
    `usage_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用量主键',
    `usage_model` VARCHAR(255) NOT NULL COMMENT '模型名称',
    `usage_app_key_id` BIGINT DEFAULT NULL COMMENT '关联的 AppKey ID',
    `usage_request_count` BIGINT DEFAULT 0 COMMENT '请求次数',
    `usage_prompt_tokens` BIGINT DEFAULT 0 COMMENT '提示 tokens',
    `usage_completion_tokens` BIGINT DEFAULT 0 COMMENT '生成 tokens',
    `usage_total_tokens` BIGINT DEFAULT 0 COMMENT '总 tokens',
    `usage_timestamp` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '统计时间戳'
) COMMENT='AI 用量统计表';

-- ============================================================
-- 31. AI 告警规则表（新设置体系）
-- ============================================================
DROP TABLE IF EXISTS `ai_alert`;
CREATE TABLE IF NOT EXISTS `ai_alert` (
    `alert_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '告警主键',
    `alert_name` VARCHAR(255) NOT NULL COMMENT '告警名称',
    `alert_app_key_id` BIGINT DEFAULT NULL COMMENT '关联 AppKey ID',
    `alert_condition` VARCHAR(128) NOT NULL COMMENT '告警条件',
    `alert_threshold` BIGINT NOT NULL COMMENT '告警阈值',
    `alert_channels` VARCHAR(512) DEFAULT NULL COMMENT '通知渠道',
    `alert_enabled` INT DEFAULT 1 COMMENT '是否启用',
    `alert_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `alert_update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `alert_deleted` INT DEFAULT 0 COMMENT '逻辑删除标记'
) COMMENT='AI 告警规则表';
