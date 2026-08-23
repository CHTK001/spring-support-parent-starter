-- ============================================================================
-- V1.2__init_nomifun.sql
-- nomifun 前端会话/消息持久化表
--
-- 说明：
--  1. 表结构对齐 pages/ai 前端（Tauri + React）的会话与消息契约：
--     - 主键使用 VARCHAR(36) UUID 字符串（前端要求 UUID 格式）
--     - 时间戳使用 BIGINT 毫秒值（前端约定）
--     - JSON 字段（model/extra/content）使用 TEXT 存储
--  2. 由 spring-support-datasource-starter 的迁移器按版本顺序执行
--     （local profile 生效；H2 MEM 模式，MODE=MySQL，大写字段转小写）
-- ============================================================================

-- 会话表：对应前端 /api/conversations 契约
CREATE TABLE IF NOT EXISTS `nomifun_conversation` (
    `conversation_id`        VARCHAR(36)  NOT NULL COMMENT '会话ID（UUID）',
    `type`                   VARCHAR(32)  NOT NULL DEFAULT 'nomi' COMMENT '会话类型',
    `name`                   VARCHAR(255) NULL COMMENT '会话标题',
    `model`                  TEXT         NULL COMMENT '模型配置JSON（Map）',
    `extra`                  TEXT         NULL COMMENT '扩展字段JSON（Map）',
    `pinned`                 TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `pinned_at`              BIGINT       NULL COMMENT '置顶时间（毫秒）',
    `created_at`             BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `modified_at`            BIGINT       NOT NULL COMMENT '最后修改时间（毫秒）',
    `execution_template_id`  VARCHAR(64)  NULL COMMENT '执行模板ID',
    PRIMARY KEY (`conversation_id`)
);

-- 消息表：对应前端 /api/conversations/{id}/messages 契约
CREATE TABLE IF NOT EXISTS `nomifun_message` (
    `message_id`       VARCHAR(36)  NOT NULL COMMENT '消息ID（UUID）',
    `conversation_id`  VARCHAR(36)  NOT NULL COMMENT '所属会话ID（UUID）',
    `msg_id`           VARCHAR(36)  NULL COMMENT '消息对外ID' ,
    `type`             VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT '消息类型',
    `role`             VARCHAR(32)  NULL COMMENT '角色（user/assistant）',
    `content`          TEXT         NULL COMMENT '消息内容（文本或JSON）',
    `position`         VARCHAR(16)  NULL COMMENT '气泡位置（left/right）',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'finish' COMMENT '消息状态',
    `hidden`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否隐藏',
    `created_at`       BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    PRIMARY KEY (`message_id`),
    KEY `idx_nomifun_msg_conv` (`conversation_id`)
);