-- ===========================================
-- 学生端个人中心模块登记（我的试卷 / 我的答题 / 错题本）
--
-- 学生端首页的卡片**由「模块管理」(edu_fun_config) 驱动**：后台按学段增删改排序，
-- 学生端不改代码。因此这三个学生个人入口也登记为模块，纳入同一套管理。
--
-- 幂等：唯一索引 (module_code, fun_config_grade_level) 存在，但为兼容历史，
-- 仍用 WHERE NOT EXISTS 先判，重复执行不产生重复行（与 V1.1 的写法一致）。
--
-- module_path 置空：这三个是学生端**内嵌组件**（由前端 EMBEDDED 表按 module_code 渲染），
-- 没有独立路由，置空避免「菜单来自模块管理却跳错页」。
-- ===========================================

-- 小学：已有序号 1-6，接在后面 7/8/9
INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'my-papers', 'PRIMARY', '老师布置·班级共享', '#6366f1', '#a5b4fc',
       0, '我的试卷', '老师布置给本班的试卷', 'Document', NULL, 1, 7
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'my-papers' AND fun_config_grade_level = 'PRIMARY'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'my-records', 'PRIMARY', '看批改·查得分', '#0ea5e9', '#38bdf8',
       0, '我的答题', '查看老师批改后的答卷与得分', 'Notebook', NULL, 1, 8
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'my-records' AND fun_config_grade_level = 'PRIMARY'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'wrongbook', 'PRIMARY', '错题重练·订正', '#ef4444', '#f87171',
       1, '错题本', '把做错的题收进来反复练', 'Warning', NULL, 1, 9
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'wrongbook' AND fun_config_grade_level = 'PRIMARY'
);

-- 初中：已有序号 1-8，接 9/10/11
INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'my-papers', 'JUNIOR', '老师布置·班级共享', '#6366f1', '#a5b4fc',
       0, '我的试卷', '老师布置给本班的试卷', 'Document', NULL, 1, 9
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'my-papers' AND fun_config_grade_level = 'JUNIOR'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'my-records', 'JUNIOR', '看批改·查得分', '#0ea5e9', '#38bdf8',
       0, '我的答题', '查看老师批改后的答卷与得分', 'Notebook', NULL, 1, 10
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'my-records' AND fun_config_grade_level = 'JUNIOR'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'wrongbook', 'JUNIOR', '错题重练·订正', '#ef4444', '#f87171',
       1, '错题本', '把做错的题收进来反复练', 'Warning', NULL, 1, 11
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'wrongbook' AND fun_config_grade_level = 'JUNIOR'
);

-- 高中：已有序号 1-8，接 9/10/11
INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'my-papers', 'SENIOR', '老师布置·班级共享', '#6366f1', '#a5b4fc',
       0, '我的试卷', '老师布置给本班的试卷', 'Document', NULL, 1, 9
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'my-papers' AND fun_config_grade_level = 'SENIOR'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'my-records', 'SENIOR', '看批改·查得分', '#0ea5e9', '#38bdf8',
       0, '我的答题', '查看老师批改后的答卷与得分', 'Notebook', NULL, 1, 10
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'my-records' AND fun_config_grade_level = 'SENIOR'
);

INSERT INTO edu_fun_config
    (module_code, fun_config_grade_level, fun_config_subtitle, fun_config_color, fun_config_color_to,
     fun_config_show_badge, module_name, module_desc, module_icon, module_path, enabled, sort_order)
SELECT 'wrongbook', 'SENIOR', '错题重练·订正', '#ef4444', '#f87171',
       1, '错题本', '把做错的题收进来反复练', 'Warning', NULL, 1, 11
WHERE NOT EXISTS (
    SELECT 1 FROM edu_fun_config WHERE module_code = 'wrongbook' AND fun_config_grade_level = 'SENIOR'
);
