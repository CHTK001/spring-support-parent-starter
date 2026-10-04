-- ===========================================
-- 新增「汉字识字」模块（学生端模块卡 + 趣味中心卡片）
--
-- 该模块消费「数据配置 → 汉字库」维护的字段：字画（图片或字形）、读音（选择题）、
-- 笔画数（选择题），答完展示笔顺与释义。题目由服务端自动生成，前端不造题。
--
-- 幂等：INSERT 用 WHERE NOT EXISTS，重复执行结果一致。
-- ===========================================

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'hanzi-learn', 'PRIMARY', '看字画·读读音·数笔画', '#0ea5e9', '#38bdf8',
       0, '汉字识字', '认汉字、读拼音、数笔画，答完讲笔顺与释义', '✍️', '/edu/fun/hanzi', 1, 11
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'hanzi-learn' AND fun_config_grade_level = 'PRIMARY'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'hanzi-learn', 'JUNIOR', '看字画·读读音·数笔画', '#0ea5e9', '#38bdf8',
       0, '汉字识字', '认汉字、读拼音、数笔画，答完讲笔顺与释义', '✍️', '/edu/fun/hanzi', 1, 13
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'hanzi-learn' AND fun_config_grade_level = 'JUNIOR'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'hanzi-learn', 'SENIOR', '看字画·读读音·数笔画', '#0ea5e9', '#38bdf8',
       0, '汉字识字', '认汉字、读拼音、数笔画，答完讲笔顺与释义', '✍️', '/edu/fun/hanzi', 1, 13
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'hanzi-learn' AND fun_config_grade_level = 'SENIOR'
);
