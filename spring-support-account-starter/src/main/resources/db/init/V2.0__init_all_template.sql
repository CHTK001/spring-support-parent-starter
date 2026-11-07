-- =====================================================================
-- V2.0__init_all_template.sql - spring-support-template-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。
--
-- 合并方式:
--   1. 原来靠 ALTER 补的列，一律折进建表语句 —— 新库一次建全，
--      不再依赖「先建表再补列」的两段式；
--   2. 存量库补列的 ALTER 仍原样保留在文件末尾（information_schema 判存 + 动态 DDL），
--      否则老库缺列；新建库上这些语句会因列已存在而跳过。
--
-- 约束: MySQL 专属语法（COMMENT 内联注释 / ENGINE=InnoDB / SET @var + PREPARE 动态 DDL）。
--   MySQL 8.4 不支持 ADD COLUMN IF NOT EXISTS、MariaDB 11.8 同样拒绝，
--   故存量补列一律沿用 information_schema 判存的动态 DDL 写法。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 模板信息表（hiprint 模板描述以 JSON 存取，按语言/类型/状态/删除建索引）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS template_info (

    template_info_id VARCHAR(32) PRIMARY KEY COMMENT '模板主键 ID',
    template_info_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    template_info_subtitle VARCHAR(200) COMMENT '模板副标题',
    template_info_language VARCHAR(8) NOT NULL COMMENT '模板语言：html、vue、jsx、json',
    template_info_type VARCHAR(32) NOT NULL COMMENT '模板类型：document、sms、email、label、other',
    template_info_status VARCHAR(16) DEFAULT 'draft' COMMENT '模板状态：draft、online、offline',
    template_info_content TEXT COMMENT '模板 JSON 描述（设计器产物）',
    template_info_remark VARCHAR(500) COMMENT '备注',
    template_info_creator VARCHAR(64) COMMENT '创建者',
    template_info_modifier VARCHAR(64) COMMENT '最后修改者',
    template_info_deleted TINYINT DEFAULT 0 COMMENT '软删除：0 正常、1 删除',
    template_info_created_at DATETIME COMMENT '创建时间',
    template_info_updated_at DATETIME COMMENT '更新时间',

    KEY `idx_template_info_language` (`template_info_language`),
    KEY `idx_template_info_type` (`template_info_type`),
    KEY `idx_template_info_status` (`template_info_status`),
    KEY `idx_template_info_deleted` (`template_info_deleted`, `template_info_updated_at`),
    KEY `idx_template_info_creator` (`template_info_creator`)

) COMMENT '模板信息';

-- 模板名称唯一（防止重名模板破坏列表遍历时取错数据）
CREATE UNIQUE INDEX uk_template_info_name
    ON template_info (template_info_name);
