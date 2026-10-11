-- =====================================================================================
-- V4.0.0.49 角色「提示词形态」：形态可自定义，build 恒存
--
-- 【本脚本取代 V1.3__add_role_prompt_by_mode.sql】
--   原脚本的 DDL 在 MySQL 上一直是好的：CREATE TABLE IF NOT EXISTS 与
--   INSERT IGNORE 都是合法 MySQL 语法，且天然幂等。**语句每次都执行成功了**
--   —— 两张表与 build/plan 两条内置形态种子都已在库里。
--   它唯一的问题是版本号 1.3 与 `sys_database_version` 里其它模块的
--   V1.3__add_model_aliases.sql 撞主键（该表主键是 version 且被所有模块共用），
--   记录执行结果时抛 DuplicateKeyException: Duplicate entry '1.3'，
--   迁移器把这个记账失败当成脚本失败，于是每次启动重跑一遍。
--
--   注意这类失败的日志措辞有误导性：写的是「执行迁移脚本失败」，
--   但语句其实跑完了，失败的只是「记一笔账」。排查时容易怀疑到 SQL 上去，
--   白花时间。与 V1.2 同型。
--
--   本脚本因此只换版本号，不改任何 DDL。
--
-- 背景：
--   原来角色只有 sys_ai_role.system_prompt 单条提示词。build / plan 两种形态写死在
--   前端 RunModePopover 与后端 request.mode 里，用户无法增加第三种（比如「评审」、
--   「只读排查」「汇报」）。角色配置里能编辑的形态，必须和会话里能切换的形态是同一份
--   数据，否则又是「配了 A 跑 B」。
--
-- 设计：
--   1) sys_ai_prompt_form —— 形态定义（归属用户）。key + 中文名 + 图标 + 描述 + 排序。
--      build 是内置形态且**恒存**：会话必须有兜底形态，任何角色在未配置形态时都落在
--      build 上，所以 build 不能被删，也不允许被改名改 key（改名会让历史会话里存的
--      runMode 失联）。
--   2) sys_ai_role_prompt —— 角色 × 形态的提示词正文。
--
-- 取值规则（SysAiRolePromptResolver）：
--   指定形态的本表记录 → 命中即用；
--   形态为 build 且未命中 → 回退 sys_ai_role.system_prompt（保持历史行为不变）；
--   再未命中 → 回退「设置中心 → 系统提示词」全局默认；
--   最后才是门面兜底。存量角色**不需要回填**。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE。
-- =====================================================================================

-- ---------- 1) 形态定义 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_prompt_form` (
    `sys_ai_prompt_form_id`     INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`                   VARCHAR(64)  DEFAULT NULL COMMENT '归属用户（多用户隔离）',
    `form_key`                  VARCHAR(32)  NOT NULL COMMENT '形态标识，如 build/plan/review；会话 runMode 存的就是它',
    `form_name`                 VARCHAR(64)  NOT NULL COMMENT '形态中文名，如 构建/规划',
    `form_icon`                 VARCHAR(64)  DEFAULT NULL COMMENT '图标标识（前端图标注册表 key）',
    `form_description`          VARCHAR(255) DEFAULT NULL COMMENT '形态说明，展示在切换器里',
    `sys_ai_prompt_form_builtin` INT         DEFAULT 0 COMMENT '1=内置形态（build 恒存，不可删除）',
    `form_sort`                 INT          DEFAULT 0 COMMENT '排序，小的在前',
    `sys_ai_prompt_form_status`  INT          DEFAULT 1 COMMENT '1启用 0禁用',
    `sys_ai_prompt_form_delete`  INT          DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    `create_name`               VARCHAR(255) DEFAULT NULL COMMENT '创建名称',
    `create_time`               DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_prompt_form_key` (`user_id`, `form_key`),
    KEY `idx_prompt_form_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提示词形态定义';

-- ---------- 2) 角色 × 形态的提示词 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_role_prompt` (
    `sys_ai_role_prompt_id`     INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`                   VARCHAR(64)  DEFAULT NULL COMMENT '归属用户（多用户隔离），与 sys_ai_role.user_id 一致',
    `role_id`                   INT          NOT NULL COMMENT '角色ID（sys_ai_role.sys_ai_role_id）',
    `prompt_mode`               VARCHAR(32)  NOT NULL COMMENT '形态标识，对应 sys_ai_prompt_form.form_key',
    `sys_ai_role_prompt_content` LONGTEXT    DEFAULT NULL COMMENT '该形态下的系统提示词正文',
    `sys_ai_role_prompt_status`  INT          DEFAULT 1 COMMENT '1启用 0禁用',
    `sys_ai_role_prompt_delete`  INT          DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    `create_name`               VARCHAR(255) DEFAULT NULL COMMENT '创建名称',
    `create_time`               DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_role_prompt_mode` (`role_id`, `prompt_mode`, `user_id`),
    KEY `idx_role_prompt_role` (`role_id`),
    KEY `idx_role_prompt_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 角色分形态提示词';

-- ---------- 3) 内置形态种子 ----------
-- user_id 用 'admin'：与 V1.0 种子数据的归属保持一致。内置形态按 user_id 存一份，
-- 读取时与该用户的自定义形态合并（内置优先，不允许被同名 key 覆盖）。
INSERT IGNORE INTO `sys_ai_prompt_form`
    (`user_id`, `form_key`, `form_name`, `form_icon`, `form_description`, `sys_ai_prompt_form_builtin`, `form_sort`, `sys_ai_prompt_form_status`, `sys_ai_prompt_form_delete`)
VALUES
    ('admin', 'build', '构建', 'flash',      '直接执行，边做边确认，适合日常任务',            1, 1, 1, 0),
    ('admin', 'plan',  '规划', 'clipboard',  '先出方案再动手，批准后才执行',                1, 2, 1, 0);