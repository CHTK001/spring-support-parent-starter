-- =====================================================================
-- V1.0__init_lowcode.sql - spring-support-lowcode-starter 全量 DDL
-- 通道: INIT 同步建表（MySQL 8 语法，H2 MODE=MySQL 回放），索引按 INDEX `表名_idx_列` 内联声明。
-- 公共审计列沿用 SysBase（create_*/update_*）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 低代码表单管理- 目录（树形，文件管理器风格的目录列表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_form_directory` (
    `low_form_directory_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `low_form_directory_name` varchar(64) NULL DEFAULT NULL COMMENT '目录名称',
    `low_form_directory_pid` INT NULL DEFAULT 0 COMMENT '父目录ID，顶级为0',
    `low_form_directory_tree_id` varchar(255) NULL DEFAULT NULL COMMENT '路径链，根到当前节点的ID路径',
    `low_form_directory_sort` INT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    `low_form_directory_remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`low_form_directory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码—目录';

-- ---------------------------------------------------------------------
-- 低代码-表单定义（formId 自增，formCode 唯一，user 在（前端）手工输入）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_form` (
    `low_form_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID，内部标识',
    `low_form_code` varchar(64) NULL DEFAULT NULL COMMENT '表单编码，唯一，人工输入',
    `low_form_name` varchar(128) NULL DEFAULT NULL COMMENT '表单名称',
    `low_form_desc` varchar(500) NULL DEFAULT NULL COMMENT '表单描述',
    `low_form_directory_id` INT NULL DEFAULT 0 COMMENT '所属目录ID',
    `low_form_schema` longtext NULL DEFAULT NULL COMMENT '表单 Schema（JSON：fields / layout / model）',
    `low_form_status` INT NULL DEFAULT 0 COMMENT '表单状态 - 0:停用, 1:启用',
    `low_form_publish_status` INT NULL DEFAULT 0 COMMENT '发布状态 - 0:未发布, 1:已发布',
    `low_form_version` INT NULL DEFAULT 0 COMMENT '当前版本号',
    `low_form_remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`low_form_id`),
    UNIQUE INDEX `low_form_uk_code` (`low_form_code`),
    INDEX `low_form_idx_directory` (`low_form_directory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码—表单定义';

-- ---------------------------------------------------------------------
-- 低代码-表单版本（每次发布时生成一条历史版本快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_form_version` (
    `low_form_version_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `low_form_id` INT NULL DEFAULT 0 COMMENT '所属表单ID',
    `low_form_code` varchar(64) NULL DEFAULT NULL COMMENT '所属表单编码',
    `low_form_version_no` INT NULL DEFAULT 0 COMMENT '版本号',
    `low_form_version_schema` longtext NULL DEFAULT NULL COMMENT '版本 Schema（JSON）',
    `low_form_version_status` INT NULL DEFAULT 0 COMMENT '版本状态 - 0:停用, 1:启用',
    `low_form_version_publish_status` INT NULL DEFAULT 0 COMMENT '发布状态 - 0:未发布, 1:已发布',
    `low_form_version_remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`low_form_version_id`),
    INDEX `low_form_version_idx_form` (`low_form_id`),
    INDEX `low_form_version_idx_code` (`low_form_code`),
    INDEX `low_form_version_idx_no` (`low_form_version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码—表单版本';

-- ---------------------------------------------------------------------
-- 低代码-页面定义（pageId 自增，pageCode 唯一，user 在（前端）手工输入）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_page` (
    `low_page_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID，内部标识',
    `low_page_code` varchar(64) NULL DEFAULT NULL COMMENT '页面编码，唯一，人工输入',
    `low_page_name` varchar(128) NULL DEFAULT NULL COMMENT '页面名称',
    `low_page_desc` varchar(500) NULL DEFAULT NULL COMMENT '页面描述',
    `low_page_directory_id` INT NULL DEFAULT 0 COMMENT '所属目录ID',
    `low_page_schema` longtext NULL DEFAULT NULL COMMENT '页面 Schema（JSON：nodes / edges / 全局参数）',
    `low_page_status` INT NULL DEFAULT 0 COMMENT '页面状态 - 0:停用, 1:启用',
    `low_page_publish_status` INT NULL DEFAULT 0 COMMENT '发布状态 - 0:未发布, 1:已发布',
    `low_page_version` INT NULL DEFAULT 0 COMMENT '当前版本号',
    `low_page_remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`low_page_id`),
    UNIQUE INDEX `low_page_uk_code` (`low_page_code`),
    INDEX `low_page_idx_directory` (`low_page_directory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码—页面定义';

-- ---------------------------------------------------------------------
-- 低代码-页面版本（每次发布时生成一条历史版本快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `low_page_version` (
    `low_page_version_id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `low_page_id` INT NULL DEFAULT 0 COMMENT '所属页面ID',
    `low_page_code` varchar(64) NULL DEFAULT NULL COMMENT '所属页面编码',
    `low_page_version_no` INT NULL DEFAULT 0 COMMENT '版本号',
    `low_page_version_schema` longtext NULL DEFAULT NULL COMMENT '版本 Schema（JSON）',
    `low_page_version_status` INT NULL DEFAULT 0 COMMENT '版本状态 - 0:停用, 1:启用',
    `low_page_version_publish_status` INT NULL DEFAULT 0 COMMENT '发布状态 - 0:未发布, 1:已发布',
    `low_page_version_remark` varchar(500) NULL DEFAULT NULL COMMENT '备注',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`low_page_version_id`),
    INDEX `low_page_version_idx_page` (`low_page_id`),
    INDEX `low_page_version_idx_code` (`low_page_code`),
    INDEX `low_page_version_idx_no` (`low_page_version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码—页面版本';
