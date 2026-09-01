-- ============================================================================
-- V9__mcp_server.sql - MCP 服务器档案表
--
-- 对应实体：com.chua.starter.ai.support.manage.store.SysAiMcpServer
-- 说明：主键为 VARCHAR(36) UUIDv7 字符串，transport 以 JSON 文本存储。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_mcp_server` (
    `sys_ai_mcp_server_id`          VARCHAR(36)  NOT NULL COMMENT 'MCP 服务器主键（UUIDv7）',
    `sys_ai_mcp_server_name`        VARCHAR(255) NOT NULL COMMENT '服务器显示名称',
    `sys_ai_mcp_server_description` VARCHAR(512) NULL COMMENT '描述',
    `sys_ai_mcp_server_transport`   TEXT         NOT NULL COMMENT '传输配置 JSON（type/url/command/args/env/headers）',
    `sys_ai_mcp_server_original_json` TEXT       NULL COMMENT '原始 JSON 配置（用于编辑时准确回显）',
    `sys_ai_mcp_server_builtin`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置（1=内置，隐藏编辑/删除）',
    `sys_ai_mcp_server_enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否默认启用（新会话默认勾选）',
    `sys_ai_mcp_server_last_test_status` VARCHAR(32) NULL DEFAULT 'disconnected' COMMENT '最近测试状态：connected/disconnected/error/testing',
    `sys_ai_mcp_server_last_connected_at` BIGINT  NULL COMMENT '最近连接成功时间（毫秒）',
    `sys_ai_mcp_server_created_at`  BIGINT       NOT NULL COMMENT '创建时间（毫秒）',
    `sys_ai_mcp_server_updated_at`  BIGINT       NOT NULL COMMENT '更新时间（毫秒）',
    PRIMARY KEY (`sys_ai_mcp_server_id`),
    KEY `idx_sys_ai_mcp_server_name` (`sys_ai_mcp_server_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务器档案';
