-- ============================================================
-- 数据源中心：新增 datasource_version 列
-- InfluxDB v2/v3 语法不同（Flux/Bucket vs SQL/InfluxQL/Database），
-- 新增表单必须把版本落库，管理页才能从一开始明确查询语法。
--
-- 与 V1.1 同一策略：
--   列定义已折进 db/init/V1.0 的 CREATE TABLE（新库建表即带上）；
--   存量库靠本补丁 ALTER 补列，ADD COLUMN 重复执行报 1060 由
--   continueOnError 逐语句吞掉，无副作用。
-- 老数据源版本列保持为空，管理页探测成功后仍可展示实际版本。
-- ============================================================
ALTER TABLE `ms_datasource`
    ADD COLUMN `datasource_version` VARCHAR(16) NULL DEFAULT '' COMMENT '服务端大版本（influxdb: 2/3）' AFTER `datasource_type`;
