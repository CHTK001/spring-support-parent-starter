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