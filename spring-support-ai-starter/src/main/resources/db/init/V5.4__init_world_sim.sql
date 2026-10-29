-- =============================================================================
-- V5.4 —— 模拟人生（世界沙盒）六张表
--
-- 形态：比酒馆（tavern 群聊）更复杂的世界模拟。
--   世界（world）→ 场景（scene）→ 世界 NPC（npc，复用 sys_ai_role）+ 关系（relationship）+ 事件（event）+ 场景消息（message）
--
-- 玩法：创建世界并配置世界观/时代；场景是 NPC 的活动地点；在场景内"说话"触发 NPC 即时回应；
--       推进时间（advance）生成「世界动态」；NPC 互动后关系数值（好感度）自动增减并落库。
--
-- 全部语句幂等：CREATE TABLE IF NOT EXISTS，可反复执行。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 世界
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` VARCHAR(64) DEFAULT NULL COMMENT '世界名称',
  `description` TEXT COMMENT '世界观设定（背景/时代/氛围，注入推进与世界动态上下文）',
  `era` VARCHAR(32) DEFAULT '现代' COMMENT '时代背景：现代/古代/未来/奇幻/武侠…',
  `weather` VARCHAR(32) DEFAULT '晴' COMMENT '当前天气：晴/雨/雪/阴/多云…',
  `time_label` VARCHAR(64) DEFAULT '第 1 天 上午' COMMENT '世界当前时间标签（推进时更新）',
  `tick_count` INT DEFAULT 0 COMMENT '累计推进次数',
  `status` INT DEFAULT 1 COMMENT '状态：1 运行中 0 暂停',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户（按账号隔离）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_world_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟人生-世界';

-- ---------------------------------------------------------------------
-- 场景（世界内的地点，NPC 分布在各场景）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world_scene` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `world_id` BIGINT DEFAULT NULL COMMENT '世界ID',
  `name` VARCHAR(64) DEFAULT NULL COMMENT '场景名称',
  `description` TEXT COMMENT '场景描述（注入对话上下文）',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_world_scene_world` (`world_id`),
  KEY `idx_world_scene_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟人生-世界场景';

-- ---------------------------------------------------------------------
-- 世界 NPC（复用 sys_ai_role 角色库；本表记录它在这个世界的"身份/位置/配置"）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world_npc` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `world_id` BIGINT DEFAULT NULL COMMENT '世界ID',
  `role_id` INT DEFAULT NULL COMMENT '角色ID（sys_ai_role 主键）',
  `scene_id` BIGINT DEFAULT NULL COMMENT '当前所在场景ID',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '世界内昵称（覆盖角色名）',
  `avatar` VARCHAR(512) DEFAULT NULL COMMENT '世界内头像覆盖（可空）',
  `model_override` VARCHAR(255) DEFAULT NULL COMMENT '模型覆盖（选填）',
  `temperature` DOUBLE DEFAULT NULL COMMENT '温度覆盖（选填）',
  `persona` TEXT COMMENT '人设补充/覆盖（可空）',
  `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否参与推进与自动互动 1是0否',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_world_npc_world` (`world_id`),
  KEY `idx_world_npc_scene` (`scene_id`),
  KEY `idx_world_npc_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟人生-世界NPC';

-- ---------------------------------------------------------------------
-- 关系（NPC 间好感度 + 关系标签，互动后自动增减）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world_relationship` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `world_id` BIGINT DEFAULT NULL COMMENT '世界ID',
  `from_npc_id` BIGINT DEFAULT NULL COMMENT '关系发起方 NPC ID（sys_ai_world_npc.id）',
  `to_npc_id` BIGINT DEFAULT NULL COMMENT '关系对象 NPC ID（sys_ai_world_npc.id）',
  `affinity` INT DEFAULT 0 COMMENT '好感度 [-100,100]，互动后自动增减',
  `relation_tag` VARCHAR(32) DEFAULT NULL COMMENT '关系标签：好友/恋人/同事/敌对…（可人工维护）',
  `memo` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_world_rel_pair` (`world_id`, `from_npc_id`, `to_npc_id`),
  KEY `idx_world_rel_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟人生-NPC关系';

-- ---------------------------------------------------------------------
-- 世界动态事件时间线（推进/对话/系统 产生的事件流）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `world_id` BIGINT DEFAULT NULL COMMENT '世界ID',
  `scene_id` BIGINT DEFAULT NULL COMMENT '事件发生场景（可空）',
  `event_type` VARCHAR(16) DEFAULT 'event' COMMENT '类型：advance 世界动态 / chat 对话 / system 系统',
  `role_ids` VARCHAR(512) DEFAULT NULL COMMENT '参与角色ID（逗号分隔，sys_ai_world_npc.id）',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '事件标题',
  `content` TEXT COMMENT '事件正文',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_world_event_world` (`world_id`),
  KEY `idx_world_event_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟人生-世界事件时间线';

-- ---------------------------------------------------------------------
-- 场景对话消息（指定场景内的多角色对话历史）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_ai_world_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `world_id` BIGINT DEFAULT NULL COMMENT '世界ID',
  `scene_id` BIGINT DEFAULT NULL COMMENT '场景ID',
  `speaker_type` VARCHAR(16) DEFAULT NULL COMMENT '发言者类型 user/ai',
  `speaker_id` VARCHAR(64) DEFAULT NULL COMMENT '发言者ID（user=登录用户名, ai=角色ID）',
  `speaker_name` VARCHAR(255) DEFAULT NULL COMMENT '发言者显示名',
  `content` TEXT COMMENT '消息内容',
  `model_name` VARCHAR(255) DEFAULT NULL COMMENT '本条AI回复使用的模型',
  `at_role_id` INT DEFAULT NULL COMMENT '本条用户消息@的角色ID',
  `user_id` VARCHAR(64) DEFAULT NULL COMMENT '归属用户',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_world_msg_world_scene` (`world_id`, `scene_id`),
  KEY `idx_world_msg_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟人生-场景对话消息';