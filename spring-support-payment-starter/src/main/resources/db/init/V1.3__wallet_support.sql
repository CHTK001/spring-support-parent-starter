CREATE TABLE IF NOT EXISTS `wallet_account` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `available_balance` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '可用余额',
  `frozen_balance` DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冻结余额',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用 0禁用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY `uk_wallet_account_merchant_user` (`merchant_id`, `user_id`),
  INDEX `idx_wallet_account_merchant` (`merchant_id`),
  INDEX `idx_wallet_account_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户表';

CREATE TABLE IF NOT EXISTS `wallet_account_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型:RECHARGE/PAY/REFUND',
  `biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
  `change_type` VARCHAR(16) NOT NULL COMMENT '变动方向:IN/OUT',
  `change_amount` DECIMAL(18,2) NOT NULL COMMENT '变动金额',
  `balance_before` DECIMAL(18,2) NOT NULL COMMENT '变动前余额',
  `balance_after` DECIMAL(18,2) NOT NULL COMMENT '变动后余额',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY `uk_wallet_log_merchant_user_biz` (`merchant_id`, `user_id`, `biz_type`, `biz_no`),
  INDEX `idx_wallet_log_merchant_user` (`merchant_id`, `user_id`),
  INDEX `idx_wallet_log_biz` (`biz_type`, `biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户流水表';
