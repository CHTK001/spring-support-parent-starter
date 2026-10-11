-- =====================================================================================
-- V4.0.0.48 修正两处「实体/表结构对不上」导致的功能 500
--
-- 【本脚本取代 V1.2__fix_appkey_provider_and_add_tavern_tables.sql】
--   原脚本的 DDL 在 MySQL 上一直是好的：CREATE TABLE IF NOT EXISTS 与
--   MODIFY COLUMN 都是合法 MySQL 语法，且天然幂等。**语句每次都执行成功了。**
--   它唯一的问题是版本号 1.2 与 `sys_database_version` 里其它模块的脚本撞主键
--   （该表主键是 version 且被所有模块共用），记录执行结果时抛
--   DuplicateKeyException: Duplicate entry '1.2'，迁移器把这个记录失败
--   当成脚本失败，于是每次启动重跑一遍。
--
--   换句话说：这里的报错日志写着「执行迁移脚本失败」，但语句其实跑完了，
--   失败的只是「记一笔账」。排查时容易被这句日志带偏，怀疑到 SQL 上去。
--
--   本脚本因此只换版本号，不改任何 DDL。
--
-- 1) sys_ai_app_key.provider_id 存的是供应商**编码**（openai / agnes / onnx），
--    与 sys_ai_provider_model、sys_ai_agent、sys_ai_provider_connection 等
--    其它 provider 引用列一致，全是 varchar。原定义成 BIGINT 是错的：
--    前端提交字符串编码，实体声明成 Long，三方对不上。
--    （实体 SysAiAppKey.appKeyProviderId / AiAppKeyDTO.provider 已同步改为 String）
--
-- 2) 酒馆三张表从未建过，但实体与 Mapper 都在线，
--    /v2/tavern/groups 直接 500：Table 'ai_test.sys_ai_tavern_group' doesn't exist。
--
-- 幂等：全部 CREATE TABLE IF NOT EXISTS；MODIFY 重复执行结果相同。
-- =====================================================================================

-- ---------- 1) provider_id 改回字符串编码 ----------
ALTER TABLE `sys_ai_app_key`
  MODIFY COLUMN `sys_ai_app_key_provider_id` VARCHAR(64) DEFAULT NULL COMMENT '供应商编码（openai/agnes/onnx 等）';

-- ---------- 2) 酒馆：群 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_tavern_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(255) DEFAULT NULL COMMENT '群名称',
  `avatar` VARCHAR(512) DEFAULT NULL COMMENT '群头像',
  `default_member_id` BIGINT DEFAULT NULL COMMENT '默认回复成员ID（sys_ai_tavern_member.id）',
  `auto_route` INT DEFAULT 0 COMMENT '未@时是否自动路由挑选回复者 0否1是',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '群主/归属用户（登录用户名）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tavern_group_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='酒馆群';

-- ---------- 3) 酒馆：群成员 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_tavern_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_id` BIGINT DEFAULT NULL COMMENT '群ID',
  `role_id` INT DEFAULT NULL COMMENT '角色ID（sys_ai_role 主键）',
  `model_override` VARCHAR(255) DEFAULT NULL COMMENT '成员模型覆盖（选填）',
  `random_reply_enabled` TINYINT(1) DEFAULT 0 COMMENT '是否启用随机发言',
  `random_probability` DOUBLE DEFAULT NULL COMMENT '随机发言命中概率百分比 [0,100]，0=从不',
  `random_context_count` INT DEFAULT NULL COMMENT '喂给该成员的上下文消息条数',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tavern_member_group` (`group_id`),
  KEY `idx_tavern_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='酒馆群成员';

-- ---------- 4) 酒馆：群消息 ----------
CREATE TABLE IF NOT EXISTS `sys_ai_tavern_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_id` BIGINT DEFAULT NULL COMMENT '群ID',
  `speaker_type` VARCHAR(16) DEFAULT NULL COMMENT '发言者类型 user/ai',
  `speaker_id` VARCHAR(64) DEFAULT NULL COMMENT '发言者ID（user=登录用户名, ai=角色ID）',
  `speaker_name` VARCHAR(255) DEFAULT NULL COMMENT '发言者显示名',
  `content` TEXT COMMENT '消息内容',
  `model_name` VARCHAR(255) DEFAULT NULL COMMENT '本条AI回复使用的模型',
  `at_role_id` INT DEFAULT NULL COMMENT '本条用户消息@的角色ID',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tavern_message_group` (`group_id`),
  KEY `idx_tavern_message_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='酒馆群消息';