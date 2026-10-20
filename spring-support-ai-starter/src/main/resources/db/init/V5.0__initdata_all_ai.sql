-- =====================================================================
-- V5.0__initdata_all_ai.sql - spring-support-ai-starter 全量种子
-- 通道: INITDATA 虚拟线程异步；表结构就位后执行。
-- 说明: 本文件是该模块唯一的种子数据入口。此前分散的
--   V1.0__initdata_all_ai.sql（空占位）与 V4.0.0.49 / V4.0.0.55 的种子
--   已在本次收敛中并入本文件并删除。
-- 约束: 全部语句必须幂等（INSERT IGNORE / 条件 UPDATE），可反复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 内置提示词形态（原 V4.0.0.49）
-- build 恒存、不可删除；前端切换器按 form_sort 排。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_ai_prompt_form`
    (`user_id`, `form_key`, `form_name`, `form_icon`, `form_description`, `sys_ai_prompt_form_builtin`, `form_sort`, `sys_ai_prompt_form_status`, `sys_ai_prompt_form_delete`)
VALUES
    ('admin', 'build', '构建', 'flash',      '直接执行，边做边确认，适合日常任务',            1, 1, 1, 0),
    ('admin', 'plan',  '规划', 'clipboard',  '先出方案再动手，批准后才执行',                1, 2, 1, 0);

-- ---------------------------------------------------------------------
-- 通用助手角色复位（原 V4.0.0.55）
-- 绑定器补列时顺带把内置角色改回启用 —— 早期版本建角色时没置启用位，
-- 导致「通用助手」在角色列表里选不到。
-- ---------------------------------------------------------------------
UPDATE `sys_ai_role`
SET `sys_ai_role_delete` = 0,
    `sys_ai_role_status` = 1
WHERE `sys_ai_role_id` = 1
  AND `sys_ai_role_code` = 'general-assistant';
