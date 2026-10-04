-- ===========================================
-- 1) 新增「成语接龙」模块（学生端模块卡，题目由服务端自动生成）
-- 2) 修正上一版把 module_icon 写成「图标名」的三行（应为 emoji，与其它游戏模块一致：
--    前端把 module_icon 当**文本**渲染，写成 Document/Notebook/Warning 会把英文单词显示在图标位上）
--
-- 幂等：INSERT 用 WHERE NOT EXISTS；UPDATE 只改这三行的 module_icon，重复执行结果一致。
-- ===========================================

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'idiom-chain', 'PRIMARY', '首尾相接·越接越顺', '#8b5cf6', '#a78bfa',
       0, '成语接龙', '下一个成语的首字接上当前成语的末字，题目自动生成', '🔗', '/edu/fun/idiom-chain', 1, 10
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'idiom-chain' AND fun_config_grade_level = 'PRIMARY'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'idiom-chain', 'JUNIOR', '首尾相接·越接越顺', '#8b5cf6', '#a78bfa',
       0, '成语接龙', '下一个成语的首字接上当前成语的末字，题目自动生成', '🔗', '/edu/fun/idiom-chain', 1, 12
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'idiom-chain' AND fun_config_grade_level = 'JUNIOR'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'idiom-chain', 'SENIOR', '首尾相接·越接越顺', '#8b5cf6', '#a78bfa',
       0, '成语接龙', '下一个成语的首字接上当前成语的末字，题目自动生成', '🔗', '/edu/fun/idiom-chain', 1, 12
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'idiom-chain' AND fun_config_grade_level = 'SENIOR'
);

-- 图标名 → emoji（前端按文本渲染 module_icon）
UPDATE edu_fun_config SET module_icon = '📄' WHERE module_code = 'my-papers'  AND module_icon = 'Document';
UPDATE edu_fun_config SET module_icon = '📝' WHERE module_code = 'my-records' AND module_icon = 'Notebook';
UPDATE edu_fun_config SET module_icon = '🗂️' WHERE module_code = 'wrongbook'  AND module_icon = 'Warning';
