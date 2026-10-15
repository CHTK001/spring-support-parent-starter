-- ============================================================
-- 数据源中心升级补丁（监控指标 · 服务器运行指标）
-- 模块: spring-support-datasource-hub-starter
-- 目录: db/migration —— framework（plugin.datasource.script.migration-scan-mode
--       驱动，默认 ALWAYS，即每次启动都扫；重复执行靠 continueOnError 吞掉）
-- ms_datasource_metric 增加一组 MySQL/MariaDB 服务器状态列：
--   - slow_queries          累计慢查询数（Long，界面「慢查询数」）
--   - threads_running       正在执行的线程数（Long，负载近似指标）
--   - questions             累计提问次数（Long）
--   - uptime                服务器已运行秒数（Long）
--   - max_used_connections  历史峰值客户端连接数（Long）
-- 说明：
--   - MySQL 协议不暴露宿主机 CPU，不以任何形式伪造 CPU 百分比；
--     Threads_running 是 SQL 侧负载最接近的公开口径，前端如实标注。
--   - 其余类型（非 jdbc MySQL/MariaDB）该组列保持 NULL，面板显示「暂未采集」。
--   - BIGINT 而非 INT：与同表延迟口径保持一致的数值宽度，避免后续混用。
-- 幂等性：重复执行报 Duplicate column，由 continueOnError 吞掉
-- ============================================================

ALTER TABLE `ms_datasource_metric`
    ADD COLUMN `slow_queries` BIGINT NULL COMMENT '累计慢查询数（取不到为 NULL）' AFTER `connection_count`,
    ADD COLUMN `threads_running` BIGINT NULL COMMENT '正在执行的线程数（取不到为 NULL）' AFTER `slow_queries`,
    ADD COLUMN `questions` BIGINT NULL COMMENT '累计提问次数（取不到为 NULL）' AFTER `threads_running`,
    ADD COLUMN `uptime` BIGINT NULL COMMENT '服务器已运行秒数（取不到为 NULL）' AFTER `questions`,
    ADD COLUMN `max_used_connections` BIGINT NULL COMMENT '历史峰值客户端连接数（取不到为 NULL）' AFTER `uptime`;