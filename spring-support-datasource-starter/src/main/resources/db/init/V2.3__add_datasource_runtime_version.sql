-- =====================================================================
-- V2.3__add_datasource_runtime_version.sql - 运行时配置乐观锁版本列（增量）
-- 通道: ADD 同步执行（按文件名+版本记录，仅执行一次）
-- 说明: 为 ds_calcite_federation / ds_sharding_rule 追加 version 列，
--   保存时以「SET version = version + 1 ... WHERE version = ?」做乐观锁校验，
--   并发编辑冲突时后端拒绝写入并提示刷新，避免相互覆盖。
--   存量行由 DEFAULT 0 补齐初始版本。
-- 写法约束: 与 V2.2 一致，MySQL 8 与 H2 MySQL 模式均可执行。
-- =====================================================================
ALTER TABLE ds_calcite_federation
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(更新成功时自增)';

ALTER TABLE ds_sharding_rule
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(更新成功时自增)';
