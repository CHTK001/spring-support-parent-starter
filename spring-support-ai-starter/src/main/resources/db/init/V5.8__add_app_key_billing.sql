-- =====================================================================
-- V5.8__add_app_key_billing.sql
-- 通道: INIT 建表
-- 说明: 密钥计费（对标 Sub2API Channel Rate/Billing）：密钥余额 + 套餐 + 透支开关 + 每分钟单价 + 累计费用，
--   新增充值/消费/退款/调整流水表（sys_ai_app_key_topup_log），计费守卫在请求级阅读余额与透支开关。
-- =====================================================================
-- 1. 密钥表：增加余额/套餐/透支开关/每分钟单价/累计费用

ALTER TABLE `sys_ai_app_key`
  ADD COLUMN `sys_ai_app_key_balance` DECIMAL(12,6) DEFAULT 0.000000 COMMENT '账户余额（元）' AFTER `sys_ai_app_key_total_tokens`,
  ADD COLUMN `sys_ai_app_key_plan_id` BIGINT DEFAULT 0 COMMENT '所属套餐 ID，0 表示无套餐' AFTER `sys_ai_app_key_balance`,
  ADD COLUMN `sys_ai_app_key_overdraft_enabled` TINYINT DEFAULT 0 COMMENT '允许透支（0=不允许，1=允许）' AFTER `sys_ai_app_key_plan_id`,
  ADD COLUMN `sys_ai_app_key_minute_rate` DECIMAL(10,6) DEFAULT 0.000000 COMMENT '每分钟单价' AFTER `sys_ai_app_key_overdraft_enabled`,
  ADD COLUMN `sys_ai_app_key_total_cost` DECIMAL(12,4) DEFAULT 0.000000 COMMENT '累计费用' AFTER `sys_ai_app_key_minute_rate`;

-- 2. 充值/消费/退款/调整流水账

CREATE TABLE IF NOT EXISTS `sys_ai_app_key_topup_log` (
    `sys_ai_app_key_topup_log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水 ID',
    `sys_ai_app_key_topup_log_app_key_id` BIGINT NOT NULL COMMENT 'AppKey 主键',
    `sys_ai_app_key_topup_log_app_key` VARCHAR(128) NOT NULL COMMENT 'AppKey',
    `sys_ai_app_key_topup_log_type` VARCHAR(20) NOT NULL COMMENT 'RECHARGE 充值 / CONSUME 消费 / REFUND 退款 / ADJUST 调整',
    `sys_ai_app_key_topup_log_amount` DECIMAL(12,6) NOT NULL COMMENT '金额（充值/退款为正，消费/调整为负）',
    `sys_ai_app_key_topup_log_balance_before` DECIMAL(12,6) NOT NULL COMMENT '变动前余额',
    `sys_ai_app_key_topup_log_balance_after` DECIMAL(12,6) NOT NULL COMMENT '变动后余额',
    `sys_ai_app_key_topup_log_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
    `sys_ai_app_key_topup_log_created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '流水时间',
    PRIMARY KEY (`sys_ai_app_key_topup_log_id`),
    KEY `idx_topup_log_app_key` (`sys_ai_app_key_topup_log_app_key`),
    KEY `idx_topup_log_app_key_id` (`sys_ai_app_key_topup_log_app_key_id`),
    KEY `idx_topup_log_created_at` (`sys_ai_app_key_topup_log_created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AppKey 充值/消费 流水账';

-- 3. 初始数据：所有现有密钥余额清零、流水账表预置摘要行（无）

UPDATE `sys_ai_app_key` SET `sys_ai_app_key_balance` = 0.000000, `sys_ai_app_key_plan_id` = 0,
    `sys_ai_app_key_overdraft_enabled` = 0, `sys_ai_app_key_minute_rate` = 0.000000,
    `sys_ai_app_key_total_cost` = 0.000000
WHERE `sys_ai_app_key_balance` IS NULL OR `sys_ai_app_key_balance` = 0;
