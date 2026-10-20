-- =====================================================================
-- V2.0__init_all_software.sql - spring-support-software-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。此前分散的
--   V1.0__init_all_software.sql 已在本次收敛中并入本文件并删除。
-- =====================================================================
-- =====================================================
-- 软件管理模块 建表脚本（MySQL / H2 通用子集）
-- 归位自 src/main/resources/sql/init.sql
-- =====================================================
-- =====================================================
-- 软件管理模块 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 表前缀: soft_
-- 字段前缀: 表名简写 (software_, install_record_, config_, source_)
-- =====================================================

-- ----------------------------------------
-- 1. soft_software 软件信息表
-- ----------------------------------------
CREATE TABLE IF NOT EXISTS `soft_software` (
  `software_id` VARCHAR(64) NOT NULL COMMENT '主键ID',
  `software_name` VARCHAR(255) NOT NULL COMMENT '软件名称',
  `software_code` VARCHAR(100) NOT NULL COMMENT '软件编码(唯一)',
  `software_version` VARCHAR(50) DEFAULT NULL COMMENT '当前版本',
  `software_latest_version` VARCHAR(50) DEFAULT NULL COMMENT '最新版本',
  `software_description` TEXT COMMENT '软件描述',
  `software_icon` VARCHAR(500) DEFAULT NULL COMMENT '软件图标URL',
  `software_category` VARCHAR(100) DEFAULT NULL COMMENT '软件分类',
  `software_tags` VARCHAR(500) DEFAULT NULL COMMENT '标签，多个用逗号分隔',
  `software_download_url` VARCHAR(1000) DEFAULT NULL COMMENT '下载URL',
  `software_file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
  `software_md5` VARCHAR(64) DEFAULT NULL COMMENT '文件MD5',
  `software_sha256` VARCHAR(100) DEFAULT NULL COMMENT '文件SHA256',
  `software_min_os_version` VARCHAR(50) DEFAULT NULL COMMENT '最低支持系统版本',
  `software_cpu_arch` VARCHAR(50) DEFAULT NULL COMMENT '支持的CPU架构',
  `software_source_id` VARCHAR(64) DEFAULT NULL COMMENT '所属源ID',
  `software_install_count` INT DEFAULT 0 COMMENT '安装次数',
  `software_rating` DECIMAL(3,2) DEFAULT NULL COMMENT '评分',
  `software_is_official` TINYINT DEFAULT 0 COMMENT '是否官方软件',
  `software_is_recommended` TINYINT DEFAULT 0 COMMENT '是否推荐软件',
  `software_is_free` TINYINT DEFAULT 1 COMMENT '是否免费',
  `software_price` INT DEFAULT 0 COMMENT '价格(分)',
  `software_status` TINYINT DEFAULT 0 COMMENT '状态: 0正常 1已删除 2已禁用',
  `software_sort` INT DEFAULT 0 COMMENT '排序',
  `software_extra` JSON DEFAULT NULL COMMENT '扩展信息JSON',
  `software_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `software_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `software_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `software_update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`software_id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `soft_software_uk_code` ON `soft_software` (`software_code`);
CREATE INDEX IF NOT EXISTS `soft_software_source` ON `soft_software` (`software_source_id`);
CREATE INDEX IF NOT EXISTS `soft_software_category` ON `soft_software` (`software_category`);
CREATE INDEX IF NOT EXISTS `soft_software_status` ON `soft_software` (`software_status`);

-- ----------------------------------------
-- 2. soft_install_record 安装记录表
-- ----------------------------------------
CREATE TABLE IF NOT EXISTS `soft_install_record` (
  `install_record_id` VARCHAR(64) NOT NULL COMMENT '主键ID',
  `install_record_software_id` VARCHAR(64) NOT NULL COMMENT '软件ID',
  `install_record_user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
  `install_record_device_id` VARCHAR(64) DEFAULT NULL COMMENT '设备ID',
  `install_record_source_id` VARCHAR(64) DEFAULT NULL COMMENT '软件源ID',
  `install_record_version` VARCHAR(50) DEFAULT NULL COMMENT '操作版本',
  `install_record_target_version` VARCHAR(50) DEFAULT NULL COMMENT '目标版本(升级用)',
  `install_record_operation` VARCHAR(20) DEFAULT NULL COMMENT '操作类型: install/uninstall/update/rollback',
  `install_record_status` TINYINT DEFAULT 0 COMMENT '状态: 0未安装 1安装中 2已安装 3安装失败 4卸载中 5已卸载 6更新中 7更新失败',
  `install_record_progress` INT DEFAULT 0 COMMENT '进度(0-100)',
  `install_record_start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `install_record_end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `install_record_duration` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
  `install_record_error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `install_record_error_detail` TEXT COMMENT '错误详情',
  `install_record_log_path` VARCHAR(500) DEFAULT NULL COMMENT '日志路径',
  `install_record_download_path` VARCHAR(500) DEFAULT NULL COMMENT '下载文件路径',
  `install_record_install_path` VARCHAR(500) DEFAULT NULL COMMENT '安装路径',
  `install_record_rollback_version` VARCHAR(50) DEFAULT NULL COMMENT '回滚版本',
  `install_record_extra` JSON DEFAULT NULL COMMENT '扩展信息JSON',
  `install_record_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `install_record_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`install_record_id`)
);

CREATE INDEX IF NOT EXISTS `soft_install_record_software` ON `soft_install_record` (`install_record_software_id`);
CREATE INDEX IF NOT EXISTS `soft_install_record_user` ON `soft_install_record` (`install_record_user_id`);
CREATE INDEX IF NOT EXISTS `soft_install_record_status` ON `soft_install_record` (`install_record_status`);
CREATE INDEX IF NOT EXISTS `soft_install_record_device` ON `soft_install_record` (`install_record_device_id`);

-- ----------------------------------------
-- 3. soft_config 软件配置表
-- ----------------------------------------
CREATE TABLE IF NOT EXISTS `soft_config` (
  `config_id` VARCHAR(64) NOT NULL COMMENT '主键ID',
  `config_software_id` VARCHAR(64) NOT NULL COMMENT '软件ID',
  `config_user_id` VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
  `config_device_id` VARCHAR(64) DEFAULT NULL COMMENT '设备ID',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值',
  `config_type` VARCHAR(20) DEFAULT 'string' COMMENT '配置类型: string/number/boolean/json/array',
  `config_label` VARCHAR(100) DEFAULT NULL COMMENT '配置标签',
  `config_description` VARCHAR(500) DEFAULT NULL COMMENT '配置描述',
  `config_group` VARCHAR(50) DEFAULT NULL COMMENT '配置分组',
  `config_sort` INT DEFAULT 0 COMMENT '排序',
  `config_is_visible` TINYINT DEFAULT 1 COMMENT '是否可见',
  `config_is_editable` TINYINT DEFAULT 1 COMMENT '是否可编辑',
  `config_default_value` TEXT COMMENT '默认值',
  `config_range` JSON DEFAULT NULL COMMENT '取值范围JSON',
  `config_extra` JSON DEFAULT NULL COMMENT '扩展信息JSON',
  `config_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `config_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `config_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `config_update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`config_id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `soft_config_uk_software_key` ON `soft_config` (`config_software_id`, `config_key`, `config_user_id`, `config_device_id`);
CREATE INDEX IF NOT EXISTS `soft_config_software` ON `soft_config` (`config_software_id`);
CREATE INDEX IF NOT EXISTS `soft_config_user` ON `soft_config` (`config_user_id`);
CREATE INDEX IF NOT EXISTS `soft_config_group` ON `soft_config` (`config_group`);

-- ----------------------------------------
-- 4. soft_source 软件源表
-- ----------------------------------------
CREATE TABLE IF NOT EXISTS `soft_source` (
  `source_id` VARCHAR(64) NOT NULL COMMENT '主键ID',
  `source_name` VARCHAR(100) NOT NULL COMMENT '源名称',
  `source_code` VARCHAR(50) NOT NULL COMMENT '源编码(唯一)',
  `source_type` TINYINT DEFAULT 0 COMMENT '源类型: 0官方 1第三方 2自建 3本地',
  `source_url` VARCHAR(1000) DEFAULT NULL COMMENT '源地址',
  `source_api_url` VARCHAR(1000) DEFAULT NULL COMMENT 'API地址',
  `source_username` VARCHAR(100) DEFAULT NULL COMMENT '用户名',
  `source_password` VARCHAR(255) DEFAULT NULL COMMENT '密码(加密)',
  `source_icon` VARCHAR(500) DEFAULT NULL COMMENT '源图标',
  `source_description` VARCHAR(1000) DEFAULT NULL COMMENT '源描述',
  `source_priority` INT DEFAULT 0 COMMENT '优先级',
  `source_is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
  `source_is_default` TINYINT DEFAULT 0 COMMENT '是否默认源',
  `source_auto_sync` TINYINT DEFAULT 0 COMMENT '是否自动同步',
  `source_sync_interval` INT DEFAULT 60 COMMENT '同步间隔(分钟)',
  `source_last_sync_time` DATETIME DEFAULT NULL COMMENT '最后同步时间',
  `source_sync_status` TINYINT DEFAULT 0 COMMENT '同步状态',
  `source_software_count` INT DEFAULT 0 COMMENT '软件数量',
  `source_extra` JSON DEFAULT NULL COMMENT '扩展信息JSON',
  `source_status` TINYINT DEFAULT 0 COMMENT '状态: 0正常 1已删除 2已禁用',
  `source_sort` INT DEFAULT 0 COMMENT '排序',
  `source_create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `source_update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `source_create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `source_update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`source_id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `soft_source_uk_code` ON `soft_source` (`source_code`);
CREATE INDEX IF NOT EXISTS `soft_source_url` ON `soft_source` (`source_url`);
CREATE INDEX IF NOT EXISTS `soft_source_priority` ON `soft_source` (`source_priority`);
CREATE INDEX IF NOT EXISTS `soft_source_type` ON `soft_source` (`source_type`);
CREATE INDEX IF NOT EXISTS `soft_source_status` ON `soft_source` (`source_status`);