-- =====================================================================
-- V6.3__init_terminal_session.sql
-- 类型: CREATE TABLE
-- 说明: 终端会话持久化元信息表。
--
--   终端进程本身运行在服务端内存中（pty4j），本表只保存「账号 + 聊天会话」维度的
--   会话元信息：shell、初始工作目录、运行状态、退出码、尺寸、累计输出字节。
--   输出正文不落库，按账号写到 workspaceBaseDir/<账号>/terminal/<hash>.log，
--   由 TerminalSessionStore 统一读写，避免大文本撑爆业务表。
--
--   唯一键 (sys_user_id, chat_session_id)：同一账号的同一聊天会话只保留一条会话记录，
--   重新打开终端时复用原行（更新为 RUNNING），而不是不断累积历史行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_terminal_session` (
    `sys_ai_terminal_session_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sys_user_id` VARCHAR(255) NOT NULL COMMENT '所属账号标识',
    `chat_session_id` VARCHAR(255) NOT NULL COMMENT '聊天会话标识',
    `terminal_shell` VARCHAR(128) DEFAULT NULL COMMENT '终端 shell（pwsh/cmd/bash/sh）',
    `terminal_cwd` VARCHAR(1024) DEFAULT NULL COMMENT '终端初始工作目录',
    `terminal_status` VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING 运行中 / EXITED 已退出 / STOPPED 已终止',
    `terminal_exit_code` INT DEFAULT NULL COMMENT '进程退出码，未结束时为空',
    `terminal_cols` INT NOT NULL DEFAULT 120 COMMENT '终端列数',
    `terminal_rows` INT NOT NULL DEFAULT 32 COMMENT '终端行数',
    `terminal_output_bytes` BIGINT NOT NULL DEFAULT 0 COMMENT '累计输出字节数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`sys_ai_terminal_session_id`),
    UNIQUE KEY `uk_terminal_user_chat` (`sys_user_id`, `chat_session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 终端会话';
