-- =====================================================================
-- V1.0__init_all_datasource_hub.sql - spring-support-datasource-hub-starter 全量表结构（H2 通道）
-- 通道: H2（宿主通过 plugin.h2.script-path 指到 db/h2 时执行）
-- 命名规范: 表名一律以 datasource_ 开头；业务列以「完整表名」作前缀；
--   审计列统一为 SysBase 通用六列
--   （create_name / create_by / create_time / update_name / update_by / update_time）。
-- 存量库: 旧表名（ms_datasource / ms_datasource_tunnel / ms_monitor_nginx_metric）
--   由 V3.0__rename_datasource_hub_tables.sql 迁移（FlywayLike 按「脚本名 + 版本」记账，
--   本文件改写后存量库不会重跑，因此 V3.0 里对新表做了完整的兜底建表 + 搬数）。
-- 语法: 仅用 H2 (MySQL 模式) 实测可执行的子集 —— ENGINE / CHARSET / 内联
--   UNIQUE KEY / KEY / COMMENT 建表，CREATE INDEX IF NOT EXISTS，
--   单列 ADD COLUMN IF NOT EXISTS；不写一条 ALTER 连多个 ADD COLUMN。
-- =====================================================================

-- ------------------------------------------------------------
-- 数据源台账（原 ms_datasource）
-- 供监控/采集等系统共享；连通性指标点「测试连接」时回写，覆盖写、不留历史
-- 进程控制方式（仅 nginx 类型使用）：
--   systemd：交给 systemctl，control_target 存托管单元名
--   local  ：直接用 nginx -s 与裸启动
--   空     ：自动探测
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_info`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_info_name`   VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `datasource_info_type`   VARCHAR(32)  NOT NULL COMMENT '数据源类型(jdbc/redis/influxdb/neo4j/nacos/zookeeper/kafka/nginx/etcd/prometheus/mongodb)',
    `datasource_info_version` VARCHAR(64) NULL DEFAULT '' COMMENT '版本：influxdb 存大版本 2/3；其余类型存探测到的服务端版本',
    `datasource_info_host`   VARCHAR(255) NULL DEFAULT '' COMMENT '主机地址',
    `datasource_info_port`   INT          NULL COMMENT '端口，空则按类型默认',
    `datasource_info_database` VARCHAR(512) NULL DEFAULT '' COMMENT '库名/命名空间；jdbc 可存完整 JDBC URL',
    `datasource_info_username` VARCHAR(128) NULL DEFAULT '' COMMENT '用户名',
    `datasource_info_password` VARCHAR(512) NULL DEFAULT '' COMMENT '口令',
    `datasource_info_remark` VARCHAR(512) NULL DEFAULT '' COMMENT '备注',
    `datasource_info_status` INT          NULL DEFAULT 1 COMMENT '状态 - 0:停用, 1:启用；停用的数据源不允许被流程引用',
    `datasource_info_connect_latency_ms` BIGINT NULL COMMENT '连接延迟(毫秒)，最近一次测试回写',
    `datasource_info_session_latency_ms` BIGINT NULL COMMENT '会话延迟(毫秒)，最近一次测试回写',
    `datasource_info_last_test_time` DATETIME NULL COMMENT '最近一次连通性测试时间',
    `datasource_info_last_test_ok` INT NULL COMMENT '最近一次测试是否连通 - 0:失败, 1:成功',
    `datasource_info_control_mode` VARCHAR(16) NULL DEFAULT '' COMMENT '进程控制方式 - systemd:systemctl托管, local:本地目录裸进程；空为自动探测',
    `datasource_info_control_target` VARCHAR(255) NULL DEFAULT '' COMMENT '托管单元名(control_mode=systemd 时使用)，空则自动探测',
    `datasource_info_driver_jar_id` BIGINT NULL COMMENT '引用的驱动 jar(datasource_jdbc_driver_jar.id)，空=用内置驱动',
    `datasource_info_top_flag` INT NULL DEFAULT 0 COMMENT '置顶标记 - 1:置顶, 0:普通',
    `datasource_info_driver_class_name` VARCHAR(255) NULL DEFAULT '' COMMENT 'JDBC 驱动类名，jdbc 类型非 MySQL 时可显式指定',
    `create_name`            VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`              INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`            DATETIME     NULL DEFAULT NULL COMMENT '创建时间',
    `update_name`            VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`              INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`            DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据源台账';

CREATE INDEX IF NOT EXISTS `idx_datasource_info_name` ON `datasource_info` (`datasource_info_name`);
CREATE INDEX IF NOT EXISTS `idx_datasource_info_type` ON `datasource_info` (`datasource_info_type`);

-- ------------------------------------------------------------
-- 动态接口定义（原 ms_datasource_api：此前全仓从未建过表，本表为首次落地。
-- 一行 = 一个「绑定到某个数据源的 SQL 查询」的对外接口）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_api`
(
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_api_name`     VARCHAR(128) NOT NULL COMMENT '接口名',
    `datasource_api_path`     VARCHAR(255) NOT NULL COMMENT '对外地址段（同数据源下唯一），如 /order/summary',
    `datasource_api_method`   VARCHAR(16)  NULL DEFAULT 'GET' COMMENT 'HTTP 方法 - GET/POST/PUT',
    `datasource_api_datasource_id` BIGINT  NOT NULL DEFAULT 0 COMMENT '绑定的数据源 id(datasource_info.id)，0=应用默认主数据源',
    `datasource_api_description` VARCHAR(512) NULL DEFAULT '' COMMENT '描述',
    `datasource_api_sql_text` LONGTEXT     NULL COMMENT '保存的查询语句（接口本体）',
    `datasource_api_param_defs` TEXT       NULL COMMENT '参数定义（JSON 数组文本）：[{name,type,required,example}]',
    `datasource_api_auth_mode` VARCHAR(32) NULL DEFAULT 'none' COMMENT '鉴权方式 - oauth/appkey/none',
    `datasource_api_cache_ttl_seconds` INT NULL DEFAULT 0 COMMENT '结果缓存秒数；0=不缓存，>0 开启',
    `datasource_api_publish_mode` VARCHAR(16) NULL DEFAULT 'embedded' COMMENT '发布模式 - embedded:动态注册进当前应用, standalone:独立 Server',
    `datasource_api_standalone_port` INT NULL DEFAULT 0 COMMENT '独立 Server 监听端口（standalone 模式使用；0=默认端口）',
    `datasource_api_enabled`  INT          NULL DEFAULT 1 COMMENT '是否启用 - 0:停用, 1:启用',
    `create_name`             VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`               INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`             DATETIME     NULL DEFAULT NULL COMMENT '创建时间',
    `update_name`             VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`               INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`             DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据源动态接口定义';

CREATE INDEX IF NOT EXISTS `idx_datasource_api_ds` ON `datasource_api` (`datasource_api_datasource_id`);

-- ------------------------------------------------------------
-- SSH 隧道配置（高级配置）（原 ms_datasource_tunnel）
-- 一个数据源至多一条配置；tunnel_type 对应 LOCAL(-L)/REMOTE(-R 反向)/DYNAMIC(SOCKS5)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_tunnel`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_tunnel_datasource_id` BIGINT NOT NULL COMMENT '数据源 ID(datasource_info.id)',
    `datasource_tunnel_name` VARCHAR(128) NULL DEFAULT '' COMMENT '隧道名称',
    `datasource_tunnel_type` VARCHAR(16)  NOT NULL COMMENT '隧道类型 - LOCAL:本地转发(-L), REMOTE:反向转发(-R), DYNAMIC:动态SOCKS5',
    `datasource_tunnel_ssh_host` VARCHAR(255) NOT NULL COMMENT 'SSH 服务器地址',
    `datasource_tunnel_ssh_port` INT          NULL COMMENT 'SSH 端口，空则 22',
    `datasource_tunnel_ssh_username` VARCHAR(128) NULL DEFAULT '' COMMENT 'SSH 用户名',
    `datasource_tunnel_ssh_auth_type` VARCHAR(16) NULL DEFAULT 'PASSWORD' COMMENT '认证方式 - PASSWORD:口令, KEY:私钥',
    `datasource_tunnel_ssh_password` VARCHAR(512) NULL DEFAULT '' COMMENT 'SSH 口令（ssh_auth_type=PASSWORD 时使用）',
    `datasource_tunnel_ssh_private_key` TEXT     NULL COMMENT 'SSH 私钥内容（ssh_auth_type=KEY 时使用）',
    `datasource_tunnel_ssh_passphrase` VARCHAR(512) NULL DEFAULT '' COMMENT '私钥口令',
    `datasource_tunnel_local_port` INT          NULL COMMENT '本地端口，空则自动分配',
    `datasource_tunnel_remote_host` VARCHAR(255) NULL DEFAULT '' COMMENT '远端目标主机（DYNAMIC 时为空）',
    `datasource_tunnel_remote_port` INT          NULL COMMENT '远端目标端口（DYNAMIC 时为空）',
    `datasource_tunnel_bind_address` VARCHAR(128) NULL DEFAULT '127.0.0.1' COMMENT '本地绑定地址',
    `datasource_tunnel_status` INT          NULL DEFAULT 0 COMMENT '状态 - 0:停用, 1:启用',
    `create_name`     VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`       INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME     NULL DEFAULT NULL COMMENT '创建时间',
    `update_name`     VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`       INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_tunnel_ds` (`datasource_tunnel_datasource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据源 SSH 隧道配置（高级配置）';

-- ------------------------------------------------------------
-- JDBC 驱动 jar 库（原 ms_jdbc_driver_jar）
-- 上传 -> 落盘 -> 按 jar 建独立类加载器的窄口子；
-- JDBC 驱动与「单 jar 的中间件客户端」共用这张表，由 kind 区分。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_jdbc_driver_jar`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_jdbc_driver_jar_jar_name` VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
    `datasource_jdbc_driver_jar_stored_name` VARCHAR(255) NOT NULL COMMENT '落盘文件名（含内容指纹，避免同名覆盖）',
    `datasource_jdbc_driver_jar_driver_class_name` VARCHAR(255) NOT NULL COMMENT '驱动类全名，如 org.h2.Driver；中间件 jar 为 SPI 实现类全名',
    `datasource_jdbc_driver_jar_kind` VARCHAR(32) NULL DEFAULT 'jdbc' COMMENT 'jar 类别：jdbc / middleware（单 jar 的中间件客户端）',
    `datasource_jdbc_driver_jar_provider_name` VARCHAR(64) NULL DEFAULT '' COMMENT '中间件类型名，如 kafka；仅 kind=middleware 有值',
    `datasource_jdbc_driver_jar_size` BIGINT       NULL COMMENT '字节数',
    `datasource_jdbc_driver_jar_sha256` VARCHAR(64) NULL DEFAULT '' COMMENT '内容 sha256',
    `datasource_jdbc_driver_jar_remark` VARCHAR(512) NULL DEFAULT '' COMMENT '备注',
    `create_name`        VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`          INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`        DATETIME     NULL DEFAULT NULL COMMENT '上传时间',
    `update_name`        VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`          INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`        DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_jdbc_driver_jar_stored` (`datasource_jdbc_driver_jar_stored_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'JDBC 驱动 jar 库';

-- 存量行兜底：老数据全部是 JDBC 驱动，NULL 会让按类别过滤的查询漏掉它们。
UPDATE `datasource_jdbc_driver_jar` SET `datasource_jdbc_driver_jar_kind` = 'jdbc' WHERE `datasource_jdbc_driver_jar_kind` IS NULL;

-- ------------------------------------------------------------
-- Nginx 自定义看板（原 ms_nginx_dashboard）
-- 布局 JSON 由前端定义与解析，后端只存取；embed_token 供公开只读链接。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_nginx_dashboard`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_nginx_dashboard_datasource_id` BIGINT NOT NULL COMMENT '数据源 id（datasource_info.id，nginx 类型）',
    `datasource_nginx_dashboard_name` VARCHAR(128) NULL DEFAULT '' COMMENT '看板名称',
    `datasource_nginx_dashboard_config` LONGTEXT   NULL COMMENT '看板布局配置（JSON 文本：widgets/grid/卡片配置）',
    `datasource_nginx_dashboard_embed_token` VARCHAR(64) NULL DEFAULT '' COMMENT '嵌入令牌（公开只读链接用，随机十六进制）',
    `create_name`      VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`        INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NULL DEFAULT NULL COMMENT '创建时间',
    `update_name`      VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`        INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_nginx_dashboard_ds` (`datasource_nginx_dashboard_datasource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Nginx 自定义看板（布局 JSON + 嵌入令牌）';

CREATE INDEX IF NOT EXISTS `idx_datasource_nginx_dashboard_token` ON `datasource_nginx_dashboard` (`datasource_nginx_dashboard_embed_token`);

-- ------------------------------------------------------------
-- Prometheus 自定义看板（原 ms_prometheus_dashboard）：结构与 Nginx 看板一致
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_prometheus_dashboard`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_prometheus_dashboard_datasource_id` BIGINT NOT NULL COMMENT '数据源 id（datasource_info.id，prometheus 类型）',
    `datasource_prometheus_dashboard_name` VARCHAR(128) NULL DEFAULT '' COMMENT '看板名称',
    `datasource_prometheus_dashboard_config` LONGTEXT NULL COMMENT '看板布局配置（JSON 文本：widgets/grid/卡片配置）',
    `datasource_prometheus_dashboard_embed_token` VARCHAR(64) NULL DEFAULT '' COMMENT '嵌入令牌（公开只读链接用，随机十六进制）',
    `create_name`      VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`        INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NULL DEFAULT NULL COMMENT '创建时间',
    `update_name`      VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`        INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_prometheus_dashboard_ds` (`datasource_prometheus_dashboard_datasource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Prometheus 自定义看板（布局 JSON + 嵌入令牌）';

CREATE INDEX IF NOT EXISTS `idx_datasource_prometheus_dashboard_token` ON `datasource_prometheus_dashboard` (`datasource_prometheus_dashboard_embed_token`);

-- ------------------------------------------------------------
-- 数据源操作日志（原 ms_datasource_oper_log）：一行 = 一次操作
--   - 只有能定位到具体数据源的操作才入账；
--   - datasource_name 冗余存一份，数据源被删后日志仍能读出当时动的是哪个。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_oper_log`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_oper_log_datasource_id` BIGINT NOT NULL COMMENT '数据源 id（datasource_info.id）',
    `datasource_oper_log_datasource_name` VARCHAR(128) NULL DEFAULT '' COMMENT '数据源名称（冗余，数据源删除后仍可读）',
    `datasource_oper_log_action` VARCHAR(64) NOT NULL COMMENT '操作码：CREATE/UPDATE/DELETE/TEST/TUNNEL_* /CONSOLE_* 等',
    `datasource_oper_log_action_name` VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `datasource_oper_log_target` VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（库名/表名/隧道名），可空',
    `datasource_oper_log_detail` TEXT         NULL COMMENT '补充说明',
    `datasource_oper_log_success` TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `datasource_oper_log_cost_ms` BIGINT       NULL COMMENT '耗时（毫秒）',
    `datasource_oper_log_operator` VARCHAR(64) NULL DEFAULT '' COMMENT '操作人',
    `datasource_oper_log_client_ip` VARCHAR(64) NULL DEFAULT '' COMMENT '客户端 IP',
    `datasource_oper_log_request_url` VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_name`    VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`      INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL COMMENT '操作时间',
    `update_name`    VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`      INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据源中心操作日志';

CREATE INDEX IF NOT EXISTS `idx_datasource_oper_log_ds_time` ON `datasource_oper_log` (`datasource_oper_log_datasource_id`, `create_time`);
CREATE INDEX IF NOT EXISTS `idx_datasource_oper_log_action` ON `datasource_oper_log` (`datasource_oper_log_datasource_id`, `datasource_oper_log_action`);

-- ------------------------------------------------------------
-- 数据源连通性指标采样（原 ms_datasource_metric）：一行 = 一次探测
--   - 采集是「有人看才采」：只有存在 SSE 订阅者的数据源才会被探测；
--   - success 允许为 NULL：探测被跳过时记「未判定」，不伪造一次成功。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_metric`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_metric_datasource_id` BIGINT NOT NULL COMMENT '数据源 id（datasource_info.id）',
    `datasource_metric_datasource_type` VARCHAR(32) NULL DEFAULT '' COMMENT '数据类型（冗余，便于按类型聚合）',
    `datasource_metric_success` TINYINT     NULL COMMENT '1 连通 / 0 不通 / NULL 未判定',
    `datasource_metric_connect_latency` BIGINT NULL COMMENT '建连耗时（毫秒）',
    `datasource_metric_session_latency` BIGINT NULL COMMENT '取会话耗时（毫秒）',
    `datasource_metric_connection_count` BIGINT NULL COMMENT '采样时刻客户端连接数（取不到为 NULL）',
    `datasource_metric_slow_queries` BIGINT     NULL COMMENT '累计慢查询数（取不到为 NULL）',
    `datasource_metric_threads_running` BIGINT NULL COMMENT '正在执行的线程数（取不到为 NULL）',
    `datasource_metric_questions` BIGINT         NULL COMMENT '累计提问次数（取不到为 NULL）',
    `datasource_metric_uptime` BIGINT             NULL COMMENT '服务器已运行秒数（取不到为 NULL）',
    `datasource_metric_max_used_connections` BIGINT NULL COMMENT '历史峰值客户端连接数（取不到为 NULL）',
    `datasource_metric_instance_memory` BIGINT    NULL COMMENT '实例内存占用字节数（取不到为 NULL）',
    `datasource_metric_tunneled` TINYINT          NULL COMMENT '1 经 SSH 隧道 / 0 直连 / NULL 未判定',
    `datasource_metric_error_message` VARCHAR(512) NULL DEFAULT '' COMMENT '失败原因（成功时为空）',
    `create_name`      VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`        INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL COMMENT '采样时刻',
    `update_name`      VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`        INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据源连通性指标采样';

CREATE INDEX IF NOT EXISTS `idx_datasource_metric_ds_time` ON `datasource_metric` (`datasource_metric_datasource_id`, `create_time`);
CREATE INDEX IF NOT EXISTS `idx_datasource_metric_time` ON `datasource_metric` (`create_time`);

-- ------------------------------------------------------------
-- Nginx 时序指标（原 ms_monitor_nginx_metric，随实体归位到本模块）
-- 写入方：Nginx 采集（按数据源 + 指标名 + 采集时刻幂等写入）
-- 读取方：监控台「Nginx 趋势」图
-- 指标值列刻意不叫 value：MyBatis-Plus 会生成 `... AS value`，
--   而 value 是 H2 保留字，直接语法错；属性名 metricValue 规避之。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_nginx_metric`
(
    `id`                             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_nginx_metric_datasource_id` BIGINT NOT NULL COMMENT '数据源 id（datasource_info.id）',
    `datasource_nginx_metric_name`   VARCHAR(64)  NOT NULL COMMENT '指标名: active/accepts/handled/requests/reading/writing/waiting',
    `datasource_nginx_metric_value`  DOUBLE       NULL COMMENT '指标值（累计类为原始计数）',
    `datasource_nginx_metric_collect_time` DATETIME NOT NULL COMMENT '采集时刻',
    `create_name`                    VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`                      INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`                    DATETIME     NULL DEFAULT NULL COMMENT '创建时间',
    `update_name`                    VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`                      INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`                    DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 幂等写入：同一数据源同一指标同一时刻只留一行
    UNIQUE KEY `datasource_nginx_metric_uk_point` (`datasource_nginx_metric_datasource_id`,
                                                  `datasource_nginx_metric_name`,
                                                  `datasource_nginx_metric_collect_time`),
    -- 趋势查询主路径：按数据源 + 时间范围扫
    KEY `idx_datasource_nginx_metric_ds_time` (`datasource_nginx_metric_datasource_id`,
                                              `datasource_nginx_metric_collect_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Nginx 时序指标';

-- ------------------------------------------------------------
-- 表结构版本快照（原 ms_schema_snapshot）
-- 一套完整可回滚的结构快照 = SHOW CREATE TABLE 的 DDL + 列/表选项/字符集。
-- 注意：database 列历史上就是 BIGINT（多图数据源的库 ID），实体属性是 String
--   属历史现状，本次仅改名不改类型。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `datasource_schema_snapshot`
(
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_schema_snapshot_ds_id` BIGINT NOT NULL COMMENT '数据源 ID（datasource_info.id）',
    `datasource_schema_snapshot_database` BIGINT NULL DEFAULT 0 COMMENT '数据库 ID（multi-database 数据源用，空=当前库）',
    `datasource_schema_snapshot_table` VARCHAR(255) NOT NULL COMMENT '表名',
    `datasource_schema_snapshot_name` VARCHAR(255) NOT NULL COMMENT '快照名称（手动拍时由用户填写，自动拍时后端生成）',
    `datasource_schema_snapshot_creator` VARCHAR(64) NULL DEFAULT 'SYSTEM' COMMENT '创建人（手动拍；自动拍=SYSTEM）',
    `datasource_schema_snapshot_snapshot` TEXT   NOT NULL COMMENT '表结构快照（JSON：ddl/indexes/foreignKeys/columns/options）',
    `datasource_schema_snapshot_deleted` INT     NULL DEFAULT 0 COMMENT '逻辑删除 0/1',
    `create_name`             VARCHAR(64)  NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by`               INT          NULL DEFAULT NULL COMMENT '创建人',
    `create_time`             DATETIME     NOT NULL COMMENT '快照创建时间（拍快照时刻）',
    `update_name`             VARCHAR(64)  NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by`               INT          NULL DEFAULT NULL COMMENT '更新人',
    `update_time`             DATETIME     NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表结构版本快照（DDL 快照 + 回滚）';

CREATE INDEX IF NOT EXISTS `idx_datasource_schema_snapshot_ds_table_time` ON `datasource_schema_snapshot` (`datasource_schema_snapshot_ds_id`, `datasource_schema_snapshot_table`, `create_time`);
CREATE INDEX IF NOT EXISTS `idx_datasource_schema_snapshot_creator` ON `datasource_schema_snapshot` (`datasource_schema_snapshot_creator`);
CREATE INDEX IF NOT EXISTS `idx_datasource_schema_snapshot_table_created` ON `datasource_schema_snapshot` (`datasource_schema_snapshot_table`, `create_time`);
