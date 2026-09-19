-- =====================================================
-- 软件管理模块 初始数据
-- 归位自 src/main/resources/sql/init.sql 末尾与 data-h2.sql
-- =====================================================
INSERT IGNORE INTO `soft_source` (`source_id`, `source_name`, `source_code`, `source_type`, `source_url`, `source_is_enabled`, `source_is_default`, `source_description`)
VALUES ('source_default', '默认官方源', 'default', 0, 'https://packages.example.com', 1, 1, '系统默认官方软件源');
INSERT IGNORE INTO `soft_source` (`source_id`, `source_name`, `source_code`, `source_type`, `source_url`, `source_is_enabled`, `source_is_default`, `source_description`)
VALUES ('source_third_party', '第三方软件源', 'third_party', 1, 'https://third-party.example.com', 1, 0, '社区第三方软件源');
INSERT IGNORE INTO `soft_source` (`source_id`, `source_name`, `source_code`, `source_type`, `source_url`, `source_is_enabled`, `source_is_default`, `source_description`)
VALUES ('source_local', '本地系统源', 'LOCAL_SYSTEM_SOURCE', 3, NULL, 1, 0, '本地系统软件源');
