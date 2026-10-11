-- ============================================================
-- 数据源中心升级补丁（监控指标 · 连接数）
-- 模块: spring-support-datasource-hub-starter
-- 目录: db/migration —— framework（plugin.datasource.script.migration-scan-mode
--       驱动，默认 ALWAYS，即每次启动都扫；重复执行靠 continueOnError 吞掉）
-- ms_datasource_metric 增加 connection_count 列：
--   - 采样时刻目标服务上的客户端连接数（瞬时值）；
--   - 目前仅 jdbc(MySQL/MariaDB/PostgreSQL) 与 redis 两类能取到，
--     其余类型 NULL（图上表现为该点缺格，不伪造数据）；
--   - BIGINT 而非 INT：与同表延迟口径保持一致的数值宽度，避免后续混用。
-- 幂等性：重复执行报 Duplicate column，由 continueOnError 吞掉
-- ============================================================

ALTER TABLE `ms_datasource_metric`
    ADD COLUMN `connection_count` BIGINT NULL COMMENT '采样时刻客户端连接数（取不到为 NULL）' AFTER `session_latency`;
