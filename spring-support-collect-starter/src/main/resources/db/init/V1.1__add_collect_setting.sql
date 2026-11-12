-- =====================================================================
-- V1.1__add_collect_setting.sql - 采集平台运行参数（界面可配）
-- 只增不改：V1.0 已被 sys_database_version 记录，改动会触发 checksum 校验失败。
-- 参数缺省值仍以 yml 的 plugin.collect.* 为准，本表只存界面覆盖值。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `collect_setting` (
    `collect_setting_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `collect_setting_key` varchar(128) NOT NULL COMMENT '配置键（plugin.collect.* 的短名）',
    `collect_setting_value` varchar(2000) NULL DEFAULT NULL COMMENT '配置值，统一按字符串存储',
    `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人',
    `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`collect_setting_id`),
    UNIQUE INDEX `collect_setting_uk_key` (`collect_setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据采集-平台运行参数';
