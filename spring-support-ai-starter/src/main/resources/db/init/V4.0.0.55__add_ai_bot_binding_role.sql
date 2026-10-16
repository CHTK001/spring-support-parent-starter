-- 连接器增加角色字段：连接器自身 systemPrompt 不再作为提示词来源，
-- 提示词统一由角色（默认通用角色 id=1）提供。
ALTER TABLE `sys_ai_bot_binding`
    ADD COLUMN `sys_ai_bot_binding_role_id` INT NULL COMMENT '绑定的 AI 角色ID（sys_ai_role.sys_ai_role_id）' AFTER `sys_ai_bot_binding_model`;

-- 通用助手被历史逻辑删除标记误置为 1 时会同时从默认角色回退与角色选择器消失，
-- 这里恢复内置角色可见。
UPDATE `sys_ai_role`
SET `sys_ai_role_delete` = 0,
    `sys_ai_role_status` = 1
WHERE `sys_ai_role_id` = 1
  AND `sys_ai_role_code` = 'general-assistant';