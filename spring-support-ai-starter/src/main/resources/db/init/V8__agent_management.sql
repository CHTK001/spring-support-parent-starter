-- ============================================================================
-- V8__agent_management.sql - AI Agent 管理表结构
--
-- 命名规范与 V1__ai_manage_schema.sql 一致：
--   1. 表名 sys_ai_*；列名以「表名」作为前缀
--   2. 主键 VARCHAR(64) UUIDv7；时间戳 BIGINT 毫秒；JSON 用 TEXT
--
-- 设计说明：
--   sys_ai_agent 一行 = 一个 Agent（主 Agent 或子 Agent）。
--   主 Agent：parent_agent_id 为空；子 Agent：parent_agent_id 指向主 Agent。
--   每个 Agent 可独立配置模型（provider_id + model）、MCP（mcp_ids JSON）、
--   Skill（skill_ids JSON）、SystemPrompt（system_prompt）、策略（strategy JSON）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_agent` (
    `sys_ai_agent_id`            VARCHAR(64)  NOT NULL COMMENT 'Agent ID（UUIDv7）',
    `sys_ai_agent_parent_id`     VARCHAR(64)  NULL COMMENT '所属主 Agent ID（空表示主 Agent）',
    `sys_ai_agent_name`          VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    `sys_ai_agent_description`   VARCHAR(512) NULL COMMENT 'Agent 描述',
    `sys_ai_agent_role`          VARCHAR(255) NULL COMMENT 'Agent 角色',
    `sys_ai_agent_instruction`   TEXT         NULL COMMENT '系统指令（system prompt）',
    `sys_ai_agent_system_prompt` TEXT         NULL COMMENT '完整系统提示词（为空则自动生成）',
    `sys_ai_agent_provider_id`   VARCHAR(64)  NULL COMMENT '关联提供商 ID（model 使用）',
    `sys_ai_agent_model`         VARCHAR(255) NULL COMMENT '关联模型名称',
    `sys_ai_agent_mcp_ids`       TEXT         NULL COMMENT '关联 MCP 列表 JSON（数组）',
    `sys_ai_agent_skill_ids`     TEXT         NULL COMMENT '关联 Skill 列表 JSON（数组）',
    `sys_ai_agent_strategy`      TEXT         NULL COMMENT '策略 JSON（mode/planning/memory/max_tool_iterations/temperature/max_tokens 等）',
    `sys_ai_agent_enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sys_ai_agent_sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `sys_ai_agent_create_time`   BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_agent_update_time`   BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_agent_id`),
    KEY `idx_ai_agent_parent` (`sys_ai_agent_parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent 定义';
