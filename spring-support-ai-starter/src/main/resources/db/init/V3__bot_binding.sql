-- ============================================================================
-- V3__bot_binding.sql - 微信 iLink Bot 多绑定表
--
-- 对应实体：com.chua.starter.ai.support.entity.SysAiBotBinding
-- 说明：主键 BIGINT 自增（@TableId IdType.AUTO）；时间为 DATETIME。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `sys_ai_bot_binding` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '绑定 ID',
    `bot_id`           VARCHAR(128) NULL COMMENT 'Bot 唯一标识（iLink 分配的 botId）',
    `bot_token`        VARCHAR(512) NULL COMMENT '登录凭证 Token',
    `platform`         VARCHAR(32)  NULL COMMENT '平台类型（wechat / wecom）',
    `nickname`         VARCHAR(128) NULL COMMENT 'Bot 昵称（来自微信资料）',
    `avatar_url`       VARCHAR(512) NULL COMMENT '厂商 logo 地址',
    `gateway_port`     INT          NULL COMMENT '独立网关端口号',
    `status`           VARCHAR(32)  NULL DEFAULT 'offline' COMMENT '状态：online / offline / expired',
    `bind_source`      VARCHAR(32)  NULL COMMENT '绑定来源（qrcode_scan / manual_import）',
    `create_time`      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `last_active_time` DATETIME     NULL COMMENT '最后活跃时间',
    `update_time`      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_sys_ai_bot_binding_bot_id` (`bot_id`)
);
