-- ============================================================
-- 数据源中心：新增 datasource_version 列
-- InfluxDB v2/v3 语法不同（Flux/Bucket vs SQL/InfluxQL/Database），
-- 新增表单必须把版本落库，管理页才能从一开始明确查询语法。
-- 老数据源保持空值，管理页探测成功后仍可展示实际版本。
-- ============================================================
ALTER TABLE `ms_datasource`
    ADD COLUMN `datasource_version` VARCHAR(16) NULL DEFAULT '' COMMENT '服务端大版本（influxdb: 2/3）' AFTER `datasource_type`;
