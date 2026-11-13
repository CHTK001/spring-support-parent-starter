-- 设备台账与分组关联表的排序规则必须一致。
-- 历史库里 device_info 继承了库默认的 utf8mb4_general_ci，而 device_group /
-- device_group_device 是 utf8mb4_unicode_ci，分组筛选的 EXISTS 会直接报
-- "Illegal mix of collations"（表现为设备列表 500、前端提示「加载设备列表失败」）。
-- 这里统一到 utf8mb4_unicode_ci；重复执行无副作用。
ALTER TABLE device_info CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE device_group CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE device_group_device CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
