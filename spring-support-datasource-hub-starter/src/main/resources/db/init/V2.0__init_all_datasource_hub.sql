-- =====================================================================
-- V2.0__init_all_datasource_hub.sql - spring-support-datasource-hub-starter 全量表结构
-- 通道: INIT 同步建表（表结构先于种子数据就位）
-- 说明: 本文件是该模块唯一的建表入口。此前分散的
--   V1.0__init_all_datasource_hub.sql 已在本次收敛中并入本文件并删除。
-- 约束: MySQL 专属语法（ENGINE / COMMENT / DATETIME）；本模块不随 H2 执行。
-- =====================================================================
-- ============================================================
-- 数据源中心建表脚本（MySQL / H2 双库可执行子集）
-- 模块: spring-support-datasource-hub-starter
-- 说明: 数据源台账，供监控/采集等系统共享
-- ============================================================

CREATE TABLE IF NOT EXISTS `ms_datasource`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_name`        VARCHAR(128) NOT NULL COMMENT '数据源名称',
    `datasource_type`        VARCHAR(32)  NOT NULL COMMENT '数据源类型(jdbc/redis/influxdb/neo4j/nacos/zookeeper/kafka/nginx/etcd/prometheus/mongodb)',
    `datasource_version`     VARCHAR(16)  NULL DEFAULT '' COMMENT '服务端大版本（influxdb: 2/3）',
    `datasource_host`        VARCHAR(255) NULL DEFAULT '' COMMENT '主机地址',
    `datasource_port`        INT          NULL COMMENT '端口，空则按类型默认',
    `datasource_database`    VARCHAR(512) NULL DEFAULT '' COMMENT '库名/命名空间；jdbc 可存完整 JDBC URL',
    `datasource_username`    VARCHAR(128) NULL DEFAULT '' COMMENT '用户名',
    `datasource_password`    VARCHAR(512) NULL DEFAULT '' COMMENT '口令',
    `datasource_remark`      VARCHAR(512) NULL DEFAULT '' COMMENT '备注',
    `datasource_status`      INT          NULL DEFAULT 1 COMMENT '状态 - 0:停用, 1:启用；停用的数据源不允许被流程引用',
      -- 连通性指标（点「测试连接」时回写，覆盖写、不留历史）
      -- connect_latency：建立连接耗时（TCP + 协议握手）
      -- session_latency：已建立连接上一次往返耗时（jdbc=SELECT 1 / redis=PING / 其余=TCP 探活）
    `datasource_connect_latency_ms` BIGINT NULL COMMENT '连接延迟(毫秒)，最近一次测试回写',
    `datasource_session_latency_ms` BIGINT NULL COMMENT '会话延迟(毫秒)，最近一次测试回写',
    `datasource_last_test_time` DATETIME NULL COMMENT '最近一次连通性测试时间',
    `datasource_last_test_ok` INT NULL COMMENT '最近一次测试是否连通 - 0:失败, 1:成功',
      -- 进程控制方式（仅 nginx 类型使用）
      -- systemd：start/stop/restart 交给 systemctl，control_target 存托管单元名（如 nginx.service）
      -- local  ：直接用 nginx -s 与裸启动，永不 systemctl
      -- 空     ：自动探测（认得出单元且当前用户有免密 systemctl 权限才走 systemd）
      -- 老数据源保持为空即维持原行为，不受本次改动影响
      -- 注：本文件末尾的存量补列语句一律用「information_schema 判存 + 动态 DDL」，
      --   不依赖 MysqlScriptConverter 的方言展开 ——
      --   动态 DDL 在 MySQL 上原生可执行，脚本因此不依赖运行期装了哪个转换器，
      --   也不受「转换器在不在 classpath 上」的影响（转换器的注册文件漏发就会退化成原样执行）。
      -- 历史上这里写的是裸 ADD COLUMN，靠 continueOnError 吞掉 1060：
      --   代价是脚本被记 FAILED、每次启动重跑，且存量库上若前面某条语句失败，
      --   补列可能没执行 —— 列没建成要等到运行期 SELECT 才暴露成 Unknown column。
    `datasource_control_mode` VARCHAR(16) NULL DEFAULT '' COMMENT '进程控制方式 - systemd:systemctl托管, local:本地目录裸进程；空为自动探测',
    `datasource_control_target` VARCHAR(255) NULL DEFAULT '' COMMENT '托管单元名(control_mode=systemd 时使用)，空则自动探测',
    `datasource_driver_class_name` VARCHAR(255) NULL DEFAULT '' COMMENT 'JDBC 驱动类名，jdbc 类型非 MySQL 时可显式指定',
    `datasource_create_time` DATETIME     NULL COMMENT '创建时间',
    `datasource_update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) COMMENT '数据源台账';

-- 索引：MySQL 不支持 CREATE INDEX IF NOT EXISTS（实测 8.4 报 1064），
-- 故同样走 information_schema 判存 + 动态 DDL，可反复执行。
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND INDEX_NAME = 'idx_ms_datasource_name'),
        'SELECT 1',
        'CREATE INDEX `idx_ms_datasource_name` ON `ms_datasource` (`datasource_name`)'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND INDEX_NAME = 'idx_ms_datasource_type'),
        'SELECT 1',
        'CREATE INDEX `idx_ms_datasource_type` ON `ms_datasource` (`datasource_type`)'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ------------------------------------------------------------
-- 存量库兼容 ALTER（新建库上方 CREATE TABLE 已含这些列，此处只对老库生效）
-- 连接性指标：点「测试连接」时回写，覆盖写、不留历史
-- connect_latency：建立连接耗时（TCP + 协议握手）
-- session_latency：已建立连接上一次往返耗时（jdbc=SELECT 1 / redis=PING / 其余=TCP 探活）
--
-- 存量库补列的写法见下方「原 V1.0 遗留的裸 ADD COLUMN」段：
-- 一律用 information_schema 判存 + 动态 DDL，不写裸 ADD COLUMN。
-- 裸写法在新库上必然报 1060（上方 CREATE TABLE 已建好同样的列），
-- 那条错误被 continueOnError 静默吞掉，代价是脚本被记 FAILED、每次启动重跑，
-- 且存量库上若前面某条语句失败、补列可能没执行 ——
-- 列没建成要等到运行期 SELECT 才暴露成 Unknown column。
-- ------------------------------------------------------------
-- 存量库补列（原 V1.0 遗留的裸 ADD COLUMN）
--
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态 DDL。
-- 每一列一条，可反复执行；列已存在时走 'SELECT 1' 分支，不重复建列。
--
-- 【为什么不用 ADD COLUMN IF NOT EXISTS】
--   那是 MariaDB 语法，MySQL 8.4 不支持（实测 8.4.9 报 1064），
--   MariaDB 11.8 同样拒绝。
--   用动态 DDL 而不是依赖 MysqlScriptConverter 的方言展开，是为了不把脚本的正确性
--   绑在「运行期装了哪个转换器」上 —— 转换器的注册文件漏发就会整体退化成原样执行。
--
-- 【为什么必须这么改】
--   裸 ADD COLUMN 在全新库上必然报 1060（Duplicate column）——
--   上方 CREATE TABLE 已经建好这些列了。这条错误被 continueOnError 静默吞掉，
--   但代价是本脚本被记为 FAILED、每次启动都重跑一遍（重跑结果还是缺那张表），
--   且真正需要补列的存量库上若前面某条语句失败，补列也可能没执行 ——
--   列没建成要等到运行期 SELECT 才暴露成 Unknown column。
--   改成判存动态 DDL 后：新库走空操作、老库正常补列、两种情况都算执行成功。
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_version'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_version` VARCHAR(16) NULL DEFAULT '''' COMMENT ''服务端大版本（influxdb: 2/3）'' AFTER `datasource_type`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_connect_latency_ms'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_connect_latency_ms` BIGINT NULL COMMENT ''连接延迟(毫秒)，最近一次测试回写'' AFTER `datasource_status`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_session_latency_ms'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_session_latency_ms` BIGINT NULL COMMENT ''会话延迟(毫秒)，最近一次测试回写'' AFTER `datasource_connect_latency_ms`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_last_test_time'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_last_test_time` DATETIME NULL COMMENT ''最近一次连通性测试时间'' AFTER `datasource_session_latency_ms`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_last_test_ok'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_last_test_ok` INT NULL COMMENT ''最近一次测试是否连通 - 0:失败, 1:成功'' AFTER `datasource_last_test_time`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ------------------------------------------------------------
-- 进程控制方式（仅 nginx 类型使用）
-- systemd：start/stop/restart 交给 systemctl，control_target 存托管单元名（如 nginx.service）
-- local  ：直接用 nginx -s 与裸启动，永不 systemctl
-- 空     ：自动探测（认得出单元且当前用户有免密 systemctl 权限才走 systemd）
-- 老数据源保持为空即维持原行为，不受本次改动影响
-- ------------------------------------------------------------
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_control_mode'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_control_mode` VARCHAR(16) NULL DEFAULT '''' COMMENT ''进程控制方式 - systemd:systemctl托管, local:本地目录裸进程；空为自动探测'' AFTER `datasource_last_test_ok`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_control_target'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_control_target` VARCHAR(255) NULL DEFAULT '''' COMMENT ''托管单元名(control_mode=systemd 时使用)，空则自动探测'' AFTER `datasource_control_mode`'));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ------------------------------------------------------------
-- SSH 隧道配置（高级配置）
-- 一个数据源至多一条配置；tunnel_type 对应 LOCAL(-L)/REMOTE(-R 反向)/DYNAMIC(SOCKS5)
-- 目标库在内网无法直连时，用反向隧道把远端可达的端口映射回本机再连
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ms_datasource_tunnel`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`   BIGINT       NOT NULL COMMENT '数据源 ID',
    `tunnel_name`     VARCHAR(128) NULL DEFAULT '' COMMENT '隧道名称',
    `tunnel_type`     VARCHAR(16)  NOT NULL COMMENT '隧道类型 - LOCAL:本地转发(-L), REMOTE:反向转发(-R), DYNAMIC:动态SOCKS5',
    `ssh_host`        VARCHAR(255) NOT NULL COMMENT 'SSH 服务器地址',
    `ssh_port`        INT          NULL COMMENT 'SSH 端口，空则 22',
    `ssh_username`    VARCHAR(128) NULL DEFAULT '' COMMENT 'SSH 用户名',
    `ssh_auth_type`   VARCHAR(16)  NULL DEFAULT 'PASSWORD' COMMENT '认证方式 - PASSWORD:口令, KEY:私钥',
    `ssh_password`    VARCHAR(512) NULL DEFAULT '' COMMENT 'SSH 口令（ssh_auth_type=PASSWORD 时使用）',
    `ssh_private_key` TEXT         NULL COMMENT 'SSH 私钥内容（ssh_auth_type=KEY 时使用）',
    `ssh_passphrase`  VARCHAR(512) NULL DEFAULT '' COMMENT '私钥口令',
    `local_port`      INT          NULL COMMENT '本地端口，空则自动分配',
    `remote_host`     VARCHAR(255) NULL DEFAULT '' COMMENT '远端目标主机（DYNAMIC 时为空）',
    `remote_port`     INT          NULL COMMENT '远端目标端口（DYNAMIC 时为空）',
    `bind_address`    VARCHAR(128) NULL DEFAULT '127.0.0.1' COMMENT '本地绑定地址',
    `tunnel_status`   INT          NULL DEFAULT 0 COMMENT '状态 - 0:停用, 1:启用',
    `create_time`     DATETIME     NULL COMMENT '创建时间',
    `update_time`     DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_datasource_tunnel_ds` (`datasource_id`)
) COMMENT '数据源 SSH 隧道配置（高级配置）';
-- ── JDBC 驱动 jar 库（需求：数据源支持上传驱动 jar，本机不可能有全部依赖包）────────────────
-- 为什么不内置全部驱动：每个 JDBC 驱动的许可、体积、传递依赖都不同，
--   打进产物等于强制所有部署都带上几十 MB 用不上的 jar；
--   而让用户「把 jar 放到某个目录」又不可控（版本、命名、驱动类名都得自己对）。
--   所以做成「上传 -> 落盘 -> 按 jar 建独立类加载器」的窄口子。
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
-- 写法：查 information_schema.COLUMNS 判存，再用 PREPARE/EXECUTE 执行动态 DDL
--（裸 ADD COLUMN 在新库上必然报 1060 Duplicate column，详见上方补列段说明）
SET @ddl = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ms_datasource'
          AND COLUMN_NAME = 'datasource_driver_jar_id'),
        'SELECT 1',
        'ALTER TABLE `ms_datasource` ADD COLUMN `datasource_driver_jar_id` BIGINT NULL COMMENT ''引用的驱动 jar(ms_jdbc_driver_jar.id)，空=用内置驱动'''));
PREPARE st FROM @ddl;
EXECUTE st;
DEALLOCATE PREPARE st;

-- ============================================================
-- 以下四表原属 db/migration（V1.2–V1.7），现并入 init：
--   单一初始化目录，新库一次建全，不再依赖「升级补丁只对存量库生效」的差别。
--   列定义直接取各补丁的最终形态（V1.6/V1.7 加在 ms_datasource_metric 上的
--   connection_count 与五个服务器状态列已折进建表语句，不再单独 ALTER）。
-- ============================================================

-- Prometheus 自定义看板：一行 = 一个 Prometheus 数据源的自定义布局看板
--   - 布局形状由前端定义（卡片 + 位置 + 卡片配置），后端只存 JSON 文本，不解析；
--   - embed_token 供「生成给别的系统嵌入」的公开只读链接使用，一个看板一个令牌；
--   - datasource_id 唯一：当前模型是一个数据源一份看板。
CREATE TABLE IF NOT EXISTS `ms_prometheus_dashboard`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`    BIGINT       NOT NULL COMMENT '数据源 id（ms_datasource.id，prometheus 类型）',
    `dashboard_name`   VARCHAR(128) NULL DEFAULT '' COMMENT '看板名称',
    `dashboard_config` LONGTEXT     NULL COMMENT '看板布局配置（JSON 文本：widgets/grid/卡片配置）',
    `embed_token`      VARCHAR(64)  NULL DEFAULT '' COMMENT '嵌入令牌（公开只读链接用，随机十六进制）',
    `create_time`      DATETIME     NULL COMMENT '创建时间',
    `update_time`      DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_prometheus_dashboard_ds` (`datasource_id`),
    KEY `idx_ms_prometheus_dashboard_token` (`embed_token`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'Prometheus 自定义看板（布局 JSON + 嵌入令牌）';

-- Nginx 自定义看板：结构与 Prometheus 看板一致
CREATE TABLE IF NOT EXISTS `ms_nginx_dashboard`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`    BIGINT       NOT NULL COMMENT '数据源 id（ms_datasource.id，nginx 类型）',
    `dashboard_name`   VARCHAR(128) NULL DEFAULT '' COMMENT '看板名称',
    `dashboard_config` LONGTEXT     NULL COMMENT '看板布局配置（JSON 文本：widgets/grid/卡片配置）',
    `embed_token`      VARCHAR(64)  NULL DEFAULT '' COMMENT '嵌入令牌（公开只读链接用，随机十六进制）',
    `create_time`      DATETIME     NULL COMMENT '创建时间',
    `update_time`      DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_nginx_dashboard_ds` (`datasource_id`),
    KEY `idx_ms_nginx_dashboard_token` (`embed_token`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = 'Nginx 自定义看板（布局 JSON + 嵌入令牌）';

-- 数据源操作日志：一行 = 一次操作
--   - datasource_id 非空：只有能定位到具体数据源的操作才入账；
--   - datasource_name 冗余存一份：数据源被删掉之后日志仍能读出当时动的是哪个；
--   - 保留窗口由调度器滚动清理，不在本表里做。
CREATE TABLE IF NOT EXISTS `ms_datasource_oper_log`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`  BIGINT       NOT NULL COMMENT '数据源 id（ms_datasource.id）',
    `datasource_name` VARCHAR(128) NULL DEFAULT '' COMMENT '数据源名称（冗余，数据源删除后仍可读）',
    `action`         VARCHAR(64)  NOT NULL COMMENT '操作码：CREATE/UPDATE/DELETE/TEST/TUNNEL_* /CONSOLE_* 等',
    `action_name`    VARCHAR(128) NULL DEFAULT '' COMMENT '操作中文名',
    `target`         VARCHAR(255) NULL DEFAULT '' COMMENT '操作对象（库名/表名/隧道名），可空',
    `detail`         TEXT         NULL COMMENT '补充说明',
    `success`        TINYINT      NULL COMMENT '1 成功 / 0 失败 / NULL 未判定',
    `cost_ms`        BIGINT       NULL COMMENT '耗时（毫秒）',
    `operator`       VARCHAR(64)  NULL DEFAULT '' COMMENT '操作人',
    `client_ip`      VARCHAR(64)  NULL DEFAULT '' COMMENT '客户端 IP',
    `request_url`    VARCHAR(512) NULL DEFAULT '' COMMENT '请求路径（用于回溯与排查）',
    `create_time`    DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_ms_datasource_oper_log_ds_time` (`datasource_id`, `create_time`),
    KEY `idx_ms_datasource_oper_log_action` (`datasource_id`, `action`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源中心操作日志';

-- 数据源连通性指标采样：一行 = 一次探测
--   - 采集是「有人看才采」：只有存在 SSE 订阅者的数据源才会被探测；
--   - success 允许为 NULL：探测被跳过时记「未判定」，不伪造一次成功；
--   - 延迟与连接数存 BIGINT：观测值本身是整数，浮点只会引入不存在的精度；
--   - 服务器状态列仅 MySQL/MariaDB 取得到，其余类型保持 NULL（界面显示「暂未采集」）。
CREATE TABLE IF NOT EXISTS `ms_datasource_metric`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `datasource_id`    BIGINT      NOT NULL COMMENT '数据源 id（ms_datasource.id）',
    `datasource_type`  VARCHAR(32) NULL DEFAULT '' COMMENT '数据类型（冗余，便于按类型聚合）',
    `success`          TINYINT     NULL COMMENT '1 连通 / 0 不通 / NULL 未判定',
    `connect_latency`  BIGINT      NULL COMMENT '建连耗时（毫秒）',
    `session_latency`  BIGINT      NULL COMMENT '取会话耗时（毫秒）',
    `connection_count` BIGINT      NULL COMMENT '采样时刻客户端连接数（取不到为 NULL）',
    `slow_queries`     BIGINT      NULL COMMENT '累计慢查询数（取不到为 NULL）',
    `threads_running`  BIGINT      NULL COMMENT '正在执行的线程数（取不到为 NULL）',
    `questions`        BIGINT      NULL COMMENT '累计提问次数（取不到为 NULL）',
    `uptime`           BIGINT      NULL COMMENT '服务器已运行秒数（取不到为 NULL）',
    `max_used_connections` BIGINT  NULL COMMENT '历史峰值客户端连接数（取不到为 NULL）',
    `tunneled`         TINYINT     NULL COMMENT '1 经 SSH 隧道 / 0 直连 / NULL 未判定',
    `error_message`    VARCHAR(512) NULL DEFAULT '' COMMENT '失败原因（成功时为空）',
    `create_time`      DATETIME    NOT NULL COMMENT '采样时刻',
    PRIMARY KEY (`id`),
    KEY `idx_ms_datasource_metric_ds_time` (`datasource_id`, `create_time`),
    KEY `idx_ms_datasource_metric_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='数据源连通性指标采样';

-- ============================================================
-- Nginx 时序指标（ms_monitor_nginx_metric）
--
-- 归位说明：本表原先建在 spring-support-monitor-starter 的
-- V1.0__init_all_monitor.sql 里，但它的实体 MsMonitorNginxMetric
-- 属于 spring-support-datasource-hub-starter —— 表随实体归位，
-- 本次收敛时把建表语句搬到本模块（表定义逐字未改）。
--
-- 写入方：Nginx 采集（按数据源 + 指标名 + 采集时刻幂等写入）
-- 读取方：监控台「Nginx 趋势」图
--
-- 指标值列刻意不叫 value：MyBatis-Plus 的 selectList 会把列名按属性名起别名，
--   生成 ms_monitor_nginx_metric_value AS value，而 value 是 H2 保留字，
--   H2 直接报语法错（101.200.205.64 走查时复现：「读趋势」500，趋势图永远空白，
--   而采集其实一直在写数据）。属性名改成 metricValue 后别名变成 metricValue，
--   两个库都安全，表结构与接口 JSON 字段都不变。
-- ============================================================
-- ============================================================
CREATE TABLE IF NOT EXISTS `ms_monitor_nginx_metric`
(
    `id`                             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ms_monitor_nginx_metric_datasource_id` BIGINT NOT NULL COMMENT 'ms_datasource.id',
    `ms_monitor_nginx_metric_name`   VARCHAR(64)  NOT NULL COMMENT '指标名: active/accepts/handled/requests/reading/writing/waiting',
    `ms_monitor_nginx_metric_value`  DOUBLE       NULL COMMENT '指标值（累计类为原始计数）',
    `ms_monitor_nginx_metric_collect_time` DATETIME NOT NULL COMMENT '采集时刻',
    PRIMARY KEY (`id`),
    -- 幂等写入：同一数据源同一指标同一时刻只留一行
    UNIQUE KEY `ms_monitor_nginx_metric_uk_point` (`ms_monitor_nginx_metric_datasource_id`,
                                                 `ms_monitor_nginx_metric_name`,
                                                 `ms_monitor_nginx_metric_collect_time`),
    -- 趋势查询主路径：按数据源 + 时间范围扫
    KEY `idx_ms_monitor_nginx_metric_ds_time` (`ms_monitor_nginx_metric_datasource_id`,
                                              `ms_monitor_nginx_metric_collect_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT 'Nginx 时序指标';