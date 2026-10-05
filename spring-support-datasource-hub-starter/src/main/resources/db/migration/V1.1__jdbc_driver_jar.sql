-- ============================================================
-- 数据源中心升级补丁（JDBC 驱动 jar 库）
-- 模块: spring-support-datasource-hub-starter
-- 目录: db/migration —— 由 framework 的 plugin.datasource.script.migration-scan-mode
--       驱动，默认 ALWAYS，即每次启动都扫；重复执行靠 continueOnError 吞掉。
-- ============================================================
--
-- 为什么必须放 migration 而不是 init：
--   init 目录的语义是 ONCE —— 仅当库为空时执行一次。init 里追加的任何内容，
--   对已经建过库的存量环境都永远不会生效，且失败是静默的（1060 被吞），
--   要到运行期 SELECT 才暴露成 Unknown column。
--   本仓 b37764ad6（2026-09-29「init SQL 去 ALTER，列定义折进建表语句」）
--   就是在固化这条规矩；V1.0 里现存的 ALTER 属于违规残留。
--
-- 本补丁补的正是 d22492658（2026-10-05）塞进 init 尾部、
-- 但在 09-30 首次建库之后才加入、因此从未落地的那部分：
--   1) 表 ms_jdbc_driver_jar
--   2) 列 ms_datasource.datasource_driver_jar_id
-- 缺任一项，GET /collect/api/ms/datasource/list 就报
--   Unknown column 'datasource_driver_jar_id'
--
-- 幂等性：
--   建表用 IF NOT EXISTS，重复执行不报错；
--   ADD COLUMN 重复执行会报 1060，由 continueOnError 吞掉（与 init 脚本同策略）。
--   全新库会先跑 init 建好表和列，本补丁重复执行被吞，无副作用。

CREATE TABLE IF NOT EXISTS `ms_jdbc_driver_jar`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `jar_name`           VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
    `stored_name`        VARCHAR(255) NOT NULL COMMENT '落盘文件名（含内容指纹，避免同名覆盖）',
    `driver_class_name`  VARCHAR(255) NOT NULL COMMENT '驱动类全名，如 org.h2.Driver',
    `jar_size`           BIGINT       NULL COMMENT '字节数',
    `jar_sha256`         VARCHAR(64)  NULL DEFAULT '' COMMENT '内容 sha256',
    `jar_remark`         VARCHAR(512) NULL DEFAULT '' COMMENT '备注',
    `create_time`        DATETIME     NULL COMMENT '上传时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_jdbc_driver_jar_stored` (`stored_name`)
) COMMENT 'JDBC 驱动 jar 库';

-- 数据源引用哪个驱动 jar；为空则走应用 classpath 上的内置驱动
-- 重复执行会报 1060，已被 continueOnError 吞掉
ALTER TABLE `ms_datasource`
    ADD COLUMN `datasource_driver_jar_id` BIGINT NULL COMMENT '引用的驱动 jar(ms_jdbc_driver_jar.id)，空=用内置驱动';