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
