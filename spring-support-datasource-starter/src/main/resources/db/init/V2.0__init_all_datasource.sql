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
--   spring-support-eduai-starter，避免重复
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
--   spring-support-eduai-starter，避免重复
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
-- spring-support-eduai-starter，避免重复。
-- 后续新增种子数据直接追加在本节下方即可（紧跟结构、同步执行，
-- 因此表与列必然已存在，不会出现「种子先于建表」的顺序问题）。


