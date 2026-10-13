-- =====================================================================================
-- V4.0.0.52 补建「AI 对外服务」家族缺失的两张表：访问令牌 + 访问日志
--
-- 【为什么需要】
--   ServiceApiController 里有三个管理端点，共用同一个库、同一个连接池、同一个事务边界：
--     GET /v2/service/instances  -> 查 sys_ai_service_instance  -> 200 正常
--     GET /v2/service/tokens     -> 查 sys_ai_service_token     -> 500
--     GET /v2/service/logs       -> 查 sys_ai_service_access_log -> 500
--   实测（ai-starter，库 ai_test）后两个稳定返回：
--     HTTP 500  {"code":"S0500C0001","msg":"数据操作失败，请稍后重试"}
--
--   根因不是连接、不是权限、也不是缺 Mapper，而是**表根本没建**：
--   实体 SysAiServiceToken(@TableName "sys_ai_service_token") 与
--   SysAiServiceAccessLog(@TableName "sys_ai_service_access_log") 都在，
--   BaseMapper 也都在（无需 XML，MyBatis-Plus 自带 CRUD），
--   但全仓 db/init 下 8 个版本脚本里没有任何一张建过这两张表。
--   于是 MyBatis 发出 SELECT 时数据库回
--     Table 'ai_test.sys_ai_service_access_log' doesn't exist
--   异常经 Spring 翻译为 BadSqlGrammarException（DataAccessException 子类），
--   被 ApiExceptionAdvice.handleDataAccessException 捕获；因为运行在生产档
--   （isProduction=true），SQL 技术细节被有意屏蔽，统一返回 DATABASE_ERROR
--   的 S0500C0001「数据操作失败，请稍后重试」——所以前端看不到「表不存在」，
--   只看到一个无从下手的通用 500。
--
--   为什么此前没人发现：写访问日志的 ServiceInstanceManager.writeAccessLog
--   用 try/catch 把异常吞掉只打 warn，而令牌鉴权 authenticate 同样在缺表时
--   必然失败——请求根本走不到写日志那一步。两条链路都静默失败，
--   只有「管理端来查这张表」这一下会响。
--
--   与 sys_ai_service_instance 是同一类历史欠账：那张表的 DDL 也是后来才补进
--   V1.0 的（见 V1.0 中该表上方的注释）。本脚本沿用同一补救方式。
--
-- 【为什么不追加到 V1.0 末尾】
--   V1.0 自 2026-09-30 起已记入 sys_database_version（success=true），
--   DataSourceFlyway 按**完整文件名**去重、命中即整份跳过，追加在末尾的语句
--   对存量库是永不执行的空操作（V4.0.0.50 的注释已详细记录该教训）。
--   新脚本按版本号顺序执行，新库与存量库都会跑到，因此只发独立版本脚本。
--
-- 【字段口径：严格对齐实体，不多不少】
--   两张表都是「实体有字段、表里必须有对应列」，列名取驼峰转下划线
--   （MyBatis-Plus 默认 mapUnderscoreToCamelCase=true）：
--     SysAiServiceToken     -> token_id / instance_id / token / token_name / enabled /
--                              user_id / create_time / token_type / expires_at /
--                              max_requests / used_requests / max_tokens / used_tokens
--     SysAiServiceAccessLog -> log_id / instance_id / endpoint / method / model /
--                              protocol / status / prompt_tokens / completion_tokens /
--                              total_tokens / latency_ms / client_ip / user_id / create_time
--   两个实体都用 @TableId(type = IdType.AUTO)，故主键必须 AUTO_INCREMENT；
--   全局 db-config 的 id-type: ASSIGN_ID 管不到它们（实体上显式声明优先）。
--
-- 【create_time 必须带 DEFAULT CURRENT_TIMESTAMP】
--   写入方都不设置该字段：
--     ServiceInstanceManager.writeAccessLog 只 set 了 12 个业务字段，没有 setCreateTime；
--     ServiceApiController.createToken 同样没有 setCreateTime。
--   所以默认值是这条链路能否落库的前提，不是可选的装饰。
--   前端「访问日志」表还按 create_time 倒序取前 N 条（orderByDesc + LIMIT），
--   该列有值才排得出来。
--
-- 【索引：按真实查询路径建，不是按外键习惯建】
--   访问日志：ServiceApiController.listLogs 的唯一形态是
--     WHERE instance_id = ? ORDER BY create_time DESC LIMIT n
--   故用 (instance_id, create_time) 复合索引，让过滤与排序同时走索引，避免
--   filesort。token(191)/255 级别的 utf8mb4 索引在 DYNAMIC 行格式下远低于
--   3072 字节上限。
--   令牌：ServiceInstanceManager.authenticate 的唯一形态是
--     WHERE instance_id = ? AND enabled = 1 AND token = ?
--   故用 (instance_id, enabled, token) 复合索引。注意 token 是请求携带的
--   凭据，等值查询高频且带 user_id 过滤之外的唯一性要求，单列索引不合适。
--
-- 【幂等】
--   两张表都用 CREATE TABLE IF NOT EXISTS：重复执行不报错、不改动已有表。
--   本脚本不含 INSERT / ALTER / DROP，不写入任何种子数据，对已有数据零影响。
-- =====================================================================================

-- ---------- 1) 对外服务访问令牌 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_service_token` (
    `token_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `instance_id`    VARCHAR(64)  DEFAULT NULL COMMENT '所属服务实例标识（sys_ai_service_instance.instance_id）',
    `token`          VARCHAR(128) DEFAULT NULL COMMENT '访问令牌明文（对外服务请求头 Bearer 携带）',
    `token_name`     VARCHAR(128) DEFAULT NULL COMMENT '令牌名称',
    `enabled`        TINYINT      DEFAULT 1 COMMENT '是否启用：1 启用，0 停用',
    `user_id`        VARCHAR(64)  DEFAULT NULL COMMENT '所属用户ID（创建者）',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `token_type`     VARCHAR(32)  DEFAULT NULL COMMENT '令牌类型：permanent 永久 / temporary 临时',
    `expires_at`     DATETIME     DEFAULT NULL COMMENT '过期时间（仅临时令牌）；为空表示不过期',
    `max_requests`   BIGINT       DEFAULT NULL COMMENT '最大请求次数；为空表示不限',
    `used_requests`  BIGINT       DEFAULT 0 COMMENT '已用请求次数',
    `max_tokens`     BIGINT       DEFAULT NULL COMMENT '最大可用 Token 数量；为空表示不限',
    `used_tokens`    BIGINT       DEFAULT 0 COMMENT '已用 Token 数量',
    PRIMARY KEY (`token_id`),
    -- 鉴权主路径：instance_id + enabled + token 等值命中
    KEY `sys_ai_service_token_auth` (`instance_id`, `enabled`, `token`),
    -- 令牌列表按实例筛选
    KEY `sys_ai_service_token_instance` (`instance_id`),
    -- 配额清理等按令牌定位
    UNIQUE KEY `sys_ai_service_token_token` (`token`),
    KEY `sys_ai_service_token_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对外服务访问令牌（鉴权/配额）';

-- ---------- 2) 对外服务访问日志 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_service_access_log` (
    `log_id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `instance_id`       VARCHAR(64)  DEFAULT NULL COMMENT '所属服务实例标识（sys_ai_service_instance.instance_id）',
    `endpoint`          VARCHAR(255) DEFAULT NULL COMMENT '请求端点路径',
    `method`            VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法',
    `model`             VARCHAR(255) DEFAULT NULL COMMENT '本次调用使用的模型',
    `protocol`          VARCHAR(32)  DEFAULT NULL COMMENT '协议：openai / claude / gemini / unknown',
    `status`            INT          DEFAULT NULL COMMENT '响应状态码',
    `prompt_tokens`     INT          DEFAULT 0 COMMENT '输入 Token 数',
    `completion_tokens` INT          DEFAULT 0 COMMENT '输出 Token 数',
    `total_tokens`      INT          DEFAULT 0 COMMENT 'Token 总数（输入+输出）',
    `latency_ms`        BIGINT       DEFAULT NULL COMMENT '处理耗时（毫秒）',
    `client_ip`         VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    `user_id`           VARCHAR(64)  DEFAULT NULL COMMENT '实例归属用户ID',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（写入方不赋值，由默认值填充）',
    PRIMARY KEY (`log_id`),
    -- 访问日志列表主查询：按实例过滤 + 按时间倒序 LIMIT，避免 filesort
    KEY `sys_ai_service_access_log_instance` (`instance_id`, `create_time`),
    KEY `sys_ai_service_access_log_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对外服务访问日志（端点/协议/模型/用量/耗时）';