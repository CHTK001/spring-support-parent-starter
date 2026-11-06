-- =====================================================================
-- V1.0__initdata_lowcode.sql - spring-support-lowcode-starter 种子
-- 通道: INITDATA 幂等（只读执行：幂等写入一行一级目录）
-- =====================================================================

-- 根目录（所有低代码表单/页面的默认归属）
INSERT INTO `low_form_directory` (`low_form_directory_id`, `low_form_directory_name`, `low_form_directory_pid`,
                                    `low_form_directory_tree_id`, `low_form_directory_sort`, `low_form_directory_remark`,
                                    `create_name`, `create_by`, `create_time`)
VALUES (1, '默认目录', 0, '1', 1, 'lowcode 默认根目录', 'system', 0, NOW())
    ON DUPLICATE KEY UPDATE `low_form_directory`.`low_form_directory_name` = VALUES(`low_form_directory`.`low_form_directory_name`);
