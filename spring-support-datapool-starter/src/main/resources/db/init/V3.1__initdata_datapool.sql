-- =====================================================================
-- V3.1__initdata_datapool.sql - spring-support-datapool-starter 全量种子
-- 通道: INITDATA 表结构之后执行
-- 版本递进原因: FlywayLike 按「文件名 + 版本」记账，无成功记录即执行；
--   本文件收敛取代 V2.0__initdata_datapool.sql，并承接原建表脚本里
--   唯一的数据修复语句（驱动 jar 类别回填），使 INIT 通道只保留 DDL。
-- 约束: 全部语句必须幂等，可反复执行；禁止任何 DDL。
-- =====================================================================

-- 驱动 jar 类别回填（幂等）：老数据全部是 JDBC 驱动，NULL 会让按类别过滤的
-- 查询漏掉它们。用 IS NULL 而不是 '' 判断：DEFAULT 只作用于新插入的行，
-- 老的 NULL 行不会被回填。
UPDATE `datasource_jdbc_driver_jar`
SET `datasource_jdbc_driver_jar_kind` = 'jdbc'
WHERE `datasource_jdbc_driver_jar_kind` IS NULL;

-- 数据源台账属于运维配置，由管理端显式维护，不预置任何连接凭据，
-- 避免初始化阶段写入默认账号口令。除上面的回填外无其它种子数据。

-- 注意: 不能是纯注释空文件 —— Spring 的 ScriptUtils.splitSqlScript 对
--       无语句的脚本会抛 `'script' must not be null or empty` 且该异常
--       在脚本级、continue-on-error 拦不住，导致 monitorSchemaInitializer
--       装配时整个应用启动失败（实测 2026-10-22）。这里保留一行幂等占位语句。
SELECT 1;
