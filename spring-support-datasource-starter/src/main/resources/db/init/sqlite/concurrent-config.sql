-- SQLite 并发工具配置初始化脚本
-- 自动执行于 spring-support-datasource-starter 初始化阶段

CREATE TABLE IF NOT EXISTS concurrent_rate_limiter (
    name            VARCHAR(128) PRIMARY KEY NOT NULL,
    permits_per_second REAL NOT NULL DEFAULT 1.0,
    warmup_period_ms BIGINT NOT NULL DEFAULT 0,
    timeout_ms      BIGINT NOT NULL DEFAULT 0,
    enabled         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP DEFAULT (datetime('now')),
    updated_at      TIMESTAMP DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS concurrent_circuit_breaker (
    name             VARCHAR(128) PRIMARY KEY NOT NULL,
    failure_threshold   INTEGER NOT NULL DEFAULT 5,
    success_threshold   INTEGER NOT NULL DEFAULT 2,
    wait_duration_ms  BIGINT NOT NULL DEFAULT 60000,
    enabled           INTEGER NOT NULL DEFAULT 1,
    created_at        TIMESTAMP DEFAULT (datetime('now')),
    updated_at        TIMESTAMP DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS concurrent_distributed_lock (
    name         VARCHAR(128) PRIMARY KEY NOT NULL,
    lock_type    VARCHAR(64) NOT NULL DEFAULT 'object',
    fair         INTEGER NOT NULL DEFAULT 0,
    wait_time_ms BIGINT NOT NULL DEFAULT 0,
    lease_time_ms BIGINT NOT NULL DEFAULT -1,
    enabled      INTEGER NOT NULL DEFAULT 1,
    created_at   TIMESTAMP DEFAULT (datetime('now')),
    updated_at   TIMESTAMP DEFAULT (datetime('now'))
);
