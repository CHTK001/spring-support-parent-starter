-- =====================================================================
-- V2.18__remove_generic_fun_config.sql - 清理学段为空的通用门户菜单
--
-- 说明：
--   1. 早期管理端的 5 条趣味模块通用行（fun_config_grade_level 为空）
--      在门户改成按学段读取后已经没有消费方，只会让「门户菜单」多出一组
--      没有实际作用的「通用」数据；
--   2. 只删这 5 个已知编码，避免误删管理员后来新增的通用行；
--   3. 幂等：重复执行不会报错。
-- =====================================================================

DELETE FROM `edu_fun_config`
WHERE `fun_config_grade_level` IS NULL
  AND `module_code` IN ('INSECT', 'IDIOM_GAME', 'poem-challenge', 'IDIOM_BANK', 'poem-game');
