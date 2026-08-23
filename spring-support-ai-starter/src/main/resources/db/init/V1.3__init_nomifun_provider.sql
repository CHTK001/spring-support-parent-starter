-- ============================================================================
-- V1.3__init_nomifun_provider.sql
-- nomifun 前端提供商管理持久化表
--
-- 说明：
--  1. 表结构对齐 pages/ai 前端（Tauri + React）的提供商契约：
--     - 主键使用 VARCHAR(64) UUID 字符串（前端要求 UUID 格式）
--     - 时间戳使用 BIGINT 毫秒值（前端约定）
--     - JSON 字段（models/connections/credentials/bedrock_config）使用 TEXT 存储
--  2. 由 spring-support-datasource-starter 的迁移器按版本顺序执行
--     （local profile 生效；H2 MEM 模式，MODE=MySQL，大写字段转小写）
-- ============================================================================

-- 提供商表：对应前端 /api/providers 契约
CREATE TABLE IF NOT EXISTS `sys_nomifun_provider` (
    `provider_id`        VARCHAR(64)  NOT NULL COMMENT '提供商ID（UUID）',
    `platform`           VARCHAR(64)  NOT NULL COMMENT '平台类型（openai/anthropic/google等）',
    `name`               VARCHAR(255) NOT NULL COMMENT '提供商名称',
    `base_url`           VARCHAR(512) NOT NULL COMMENT 'API基础URL',
    `auth_scheme`        VARCHAR(32)  NOT NULL DEFAULT 'api_key' COMMENT '认证方式（api_key/bearer/oauth/none）',
    `credentials`        TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `enabled`            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `bedrock_config`     TEXT         NULL COMMENT 'AWS Bedrock配置JSON',
    `sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`provider_id`)
);

-- 提供商模型表：对应前端 /api/providers/{id}/models 契约
CREATE TABLE IF NOT EXISTS `sys_nomifun_provider_model` (
    `provider_id`        VARCHAR(64)  NOT NULL COMMENT '所属提供商ID',
    `model`              VARCHAR(255) NOT NULL COMMENT '模型名称',
    `enabled`            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sort_order`         INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `description`        TEXT         NULL COMMENT '模型描述',
    `capabilities`       TEXT         NULL COMMENT '任务能力JSON数组',
    `created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`provider_id`, `model`)
);

-- 提供商连接表：对应前端 /api/providers/{id}/connections 契约
CREATE TABLE IF NOT EXISTS `sys_nomifun_provider_connection` (
    `connection_id`      VARCHAR(64)  NOT NULL COMMENT '连接ID（UUID）',
    `provider_id`        VARCHAR(64)  NOT NULL COMMENT '所属提供商ID',
    `role`               VARCHAR(64)  NOT NULL COMMENT '连接角色（default/backup等）',
    `label`              VARCHAR(255) NULL COMMENT '连接标签',
    `base_url`           VARCHAR(512) NULL COMMENT '覆盖的基础URL',
    `auth_scheme`        VARCHAR(32)  NULL COMMENT '覆盖的认证方式',
    `credentials`        TEXT         NULL COMMENT '凭证JSON（写入后不回显）',
    `extra`              TEXT         NULL COMMENT '扩展字段JSON',
    `created_at`         BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `updated_at`         BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`connection_id`),
    KEY `idx_nomifun_prov_conn_pid` (`provider_id`)
);