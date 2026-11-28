-- =====================================================================
-- V2.2__init_all_datasource.sql - spring-support-datasource-starter 全量表结构（Flyway 收敛版）
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是本模块唯一的建表入口。本次收敛把
--   V2.0__init_all_datasource.sql（全量建表，业务域表已按模块归位）与
--   V2.1__add_datasource_runtime.sql（运行时表增量）按版本顺序原样并入
--   本文件并删除原文件。本模块无种子数据。
-- 幂等: 建表一律 CREATE TABLE IF NOT EXISTS。FlywayLike 按文件名记账，
--   新文件名对存量库会补跑一次，重复执行均安全。
-- 顺序: 旧文件内容按 V2.0 -> V2.1 原序拼接，未做任何语句改写。
-- =====================================================================

-- >>>>>>> merged from V2.0__init_all_datasource.sql <<<<<<<

-- =====================================================================
-- V2.0__init_all_datasource.sql - spring-support-datasource-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。此前分散的
--   V1.0__init_all_datasource.sql 已在本次收敛中并入本文件并删除。
--   业务域表已按模块归位: datasync->datasync-starter, spider->spider-starter,
--   device->device-starter, job->job-starter, message->message-starter,
--   payment->payment-starter, project->project-starter, monitor->monitor-starter,
--   server->server-starter, ai->ai-starter, filesystem->filesystem-starter
-- 通道: 本模块当前无种子数据：edu_question 种子已按实际表归属归入
--   spring-api-support-common-eduai-starter，避免重复
-- =====================================================================
-- =====================================================================
-- V1.0__init_all_datasource.sql - datasource-starter 全量 DDL（仅本模块数据源基础设施表）
-- 业务域表已按模块归位: datasync->datasync-starter, spider->spider-starter,
--   device->device-starter, job->job-starter, message->message-starter,
--   payment->payment-starter, project->project-starter, monitor->monitor-starter,
--   server->server-starter, ai->ai-starter, filesystem->filesystem-starter
-- 通道: INIT 同步建表；初始化数据与结构同文件（原先拆分的
--   V1.0__initdata_all_datasource.sql 是空壳，现已并入本文件并删除）
--   本模块当前无种子数据：edu_question 种子已按实际表归属归入
--   spring-api-support-common-eduai-starter，避免重复
-- =====================================================================
-- 并发工具配置表：供 CircuitBreakerConfigRepository / RateLimiterConfigRepository /
-- DistributedLockConfigRepository 以 JdbcTemplate 直接读写（原 db/init/sqlite/
-- concurrent-config.sql，因目录不被扫描、文件名不带 V 前缀而永不执行，已归位至此）。
-- 写法约束：不使用 SQLite 专属默认值 datetime('now')，主键一律表级声明，MySQL 8 与
-- H2 MySQL 模式均可执行。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS concurrent_rate_limiter (
    name               VARCHAR(128) NOT NULL COMMENT '限流器名称',
    permits_per_second DOUBLE       NOT NULL DEFAULT 1.0 COMMENT '每秒许可数',
    warmup_period_ms   BIGINT       NOT NULL DEFAULT 0 COMMENT '预热时长(毫秒)',
    timeout_ms         BIGINT       NOT NULL DEFAULT 0 COMMENT '获取许可超时(毫秒)',
    enabled            INT          NOT NULL DEFAULT 1 COMMENT '是否启用(0否 1是)',
    created_at         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (name)
);

CREATE TABLE IF NOT EXISTS concurrent_circuit_breaker (
    name              VARCHAR(128) NOT NULL COMMENT '熔断器名称',
    failure_threshold INT          NOT NULL DEFAULT 5 COMMENT '失败阈值',
    success_threshold INT          NOT NULL DEFAULT 2 COMMENT '成功阈值',
    wait_duration_ms  BIGINT       NOT NULL DEFAULT 60000 COMMENT '熔断打开后等待时间(毫秒)',
    enabled           INT          NOT NULL DEFAULT 1 COMMENT '是否启用(0否 1是)',
    created_at        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (name)
);

CREATE TABLE IF NOT EXISTS concurrent_distributed_lock (
    name          VARCHAR(128) NOT NULL COMMENT '锁名称',
    lock_type     VARCHAR(64)  NOT NULL DEFAULT 'object' COMMENT '锁类型',
    fair          INT          NOT NULL DEFAULT 0 COMMENT '是否公平锁(0否 1是)',
    wait_time_ms  BIGINT       NOT NULL DEFAULT 0 COMMENT '加锁等待时间(毫秒)',
    lease_time_ms BIGINT       NOT NULL DEFAULT -1 COMMENT '锁租期(毫秒, -1 为不自动释放)',
    enabled       INT          NOT NULL DEFAULT 1 COMMENT '是否启用(0否 1是)',
    created_at    DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (name)
);

-- ---------------------------------------------------------------------
-- 初始化数据（与结构同文件执行）
-- ---------------------------------------------------------------------
-- 当前本模块无种子数据：edu_question 种子已按实际表归属归入
-- spring-api-support-common-eduai-starter，避免重复。
-- 后续新增种子数据直接追加在本节下方即可（紧跟结构、同步执行，
-- 因此表与列必然已存在，不会出现「种子先于建表」的顺序问题）。


-- >>>>>>> merged from V2.1__add_datasource_runtime.sql <<<<<<<

-- =====================================================================
-- V2.1__add_datasource_runtime.sql - 数据源运行时管理配置表（增量）
-- 通道: ADD 同步执行（按文件名+版本记录，仅执行一次）
-- 说明: 为 com.chua.starter.datasource.runtime.* 提供运行期配置存储：
--   Calcite 联邦（ds_calcite_federation）与分库分表规则（ds_sharding_rule）。
--   页面实时配置、保存即重建数据源，不重启生效。
--   物理分表 DDL 仅开放幂等补建与追加列，见 ShardingPhysicalTableService。
-- 写法约束: 与 V2.0 一致，MySQL 8 与 H2 MySQL 模式均可执行。
-- =====================================================================
CREATE TABLE IF NOT EXISTS ds_calcite_federation (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    schema_name    VARCHAR(128) NOT NULL COMMENT '联邦对外 schema 名',
    default_schema VARCHAR(128) NULL COMMENT '默认 schema 名(SQL 未限定 schema 时使用)',
    enabled        INT          NOT NULL DEFAULT 1 COMMENT '是否启用(0否 1是)',
    members        TEXT         NULL COMMENT '成员连接参数 JSON [{schemaName,url,username,password,driverClassName}]',
    remark         VARCHAR(512) NULL COMMENT '备注',
    created_at     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_ds_calcite_federation_schema UNIQUE (schema_name)
);

CREATE TABLE IF NOT EXISTS ds_sharding_rule (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    logic_table     VARCHAR(128) NOT NULL COMMENT '逻辑表名(同时作为物理表名前缀)',
    sharding_column VARCHAR(128) NOT NULL COMMENT '分片列',
    algorithm       VARCHAR(32)  NOT NULL DEFAULT 'MOD' COMMENT '分片算法(MOD/INTERVAL)',
    shard_count     INT          NOT NULL DEFAULT 16 COMMENT '分片数',
    time_unit       VARCHAR(16)  NULL COMMENT '时间粒度(YEAR/QUARTER/MONTH/WEEK/DAY/HOUR, INTERVAL 生效)',
    time_lower      VARCHAR(32)  NULL COMMENT '时间下界(yyyy-MM-dd, INTERVAL 生效)',
    time_upper      VARCHAR(32)  NULL COMMENT '时间上界(yyyy-MM-dd, INTERVAL 生效)',
    database_members TEXT        NULL COMMENT '分库成员 JSON [{name,url,username,password,driverClassName}](列名避开 MySQL 保留字 DATABASES)',
    enabled         INT          NOT NULL DEFAULT 1 COMMENT '是否启用(0否 1是)',
    remark          VARCHAR(512) NULL COMMENT '备注',
    created_at      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_ds_sharding_rule_logic_table UNIQUE (logic_table)
);
