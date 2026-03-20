-- 支付系统数据库表结构
-- 更新时间: 2026-03-19
-- 说明: 商户、支付方式、订单、状态日志、交易流水

CREATE TABLE IF NOT EXISTS `merchant` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `merchant_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '商户号',
  `merchant_name` VARCHAR(100) NOT NULL COMMENT '商户名称',
  `contact_name` VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `contact_email` VARCHAR(100) DEFAULT NULL COMMENT '联系邮箱',
  `business_license` VARCHAR(200) DEFAULT NULL COMMENT '营业执照',
  `legal_person` VARCHAR(50) DEFAULT NULL COMMENT '法人',
  `default_notify_url` VARCHAR(255) DEFAULT NULL COMMENT '默认支付回调地址',
  `default_return_url` VARCHAR(255) DEFAULT NULL COMMENT '默认支付返回地址',
  `wallet_enabled` TINYINT DEFAULT 0 COMMENT '钱包能力开关',
  `composite_enabled` TINYINT DEFAULT 0 COMMENT '综合支付能力开关',
  `auto_close_enabled` TINYINT DEFAULT 1 COMMENT '自动关单开关',
  `auto_close_minutes` INT DEFAULT 30 COMMENT '自动关单分钟数',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `status` TINYINT DEFAULT 0 COMMENT '状态:0待审核 1已激活 2已停用 3已注销',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_merchant_no` (`merchant_no`),
  INDEX `idx_merchant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付商户表';

CREATE TABLE IF NOT EXISTS `merchant_channel` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `channel_type` VARCHAR(32) NOT NULL COMMENT '渠道类型',
  `channel_sub_type` VARCHAR(32) NOT NULL COMMENT '渠道子类型',
  `channel_name` VARCHAR(100) NOT NULL COMMENT '渠道名称',
  `app_id` VARCHAR(120) DEFAULT NULL COMMENT '应用ID',
  `merchant_no` VARCHAR(120) DEFAULT NULL COMMENT '商户号/PID/路由标识',
  `api_key` TEXT DEFAULT NULL COMMENT 'API Key(加密)',
  `private_key` TEXT DEFAULT NULL COMMENT '私钥(加密)',
  `public_key` TEXT DEFAULT NULL COMMENT '公钥',
  `cert_path` VARCHAR(255) DEFAULT NULL COMMENT '证书路径',
  `sandbox_mode` TINYINT DEFAULT 0 COMMENT '沙箱模式:0正式 1沙箱',
  `notify_url` VARCHAR(255) DEFAULT NULL COMMENT '渠道专属回调地址',
  `return_url` VARCHAR(255) DEFAULT NULL COMMENT '渠道专属返回地址',
  `onboarding_status` VARCHAR(32) DEFAULT 'NOT_STARTED' COMMENT '开通状态',
  `onboarding_link` VARCHAR(255) DEFAULT NULL COMMENT '开通链接',
  `status` TINYINT DEFAULT 0 COMMENT '状态:0禁用 1启用',
  `ext_config` TEXT DEFAULT NULL COMMENT '扩展配置JSON',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_channel_merchant_id` (`merchant_id`),
  INDEX `idx_channel_type` (`channel_type`),
  INDEX `idx_channel_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户支付方式配置表';

CREATE TABLE IF NOT EXISTS `payment_order` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `order_no` VARCHAR(40) NOT NULL UNIQUE COMMENT '平台订单号',
  `business_order_no` VARCHAR(64) DEFAULT NULL COMMENT '业务订单号',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `channel_id` BIGINT DEFAULT NULL COMMENT '支付方式ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `channel_type` VARCHAR(32) NOT NULL COMMENT '支付渠道类型',
  `channel_sub_type` VARCHAR(32) DEFAULT NULL COMMENT '支付渠道子类型',
  `order_amount` DECIMAL(18,2) NOT NULL COMMENT '订单金额',
  `paid_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '实付金额',
  `refund_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '已退金额',
  `discount_amount` DECIMAL(18,2) DEFAULT 0 COMMENT '优惠金额',
  `currency` VARCHAR(16) DEFAULT 'CNY' COMMENT '币种',
  `status` VARCHAR(32) DEFAULT 'PENDING' COMMENT '订单状态',
  `subject` VARCHAR(200) DEFAULT NULL COMMENT '订单标题',
  `body` VARCHAR(500) DEFAULT NULL COMMENT '订单描述',
  `notify_url` VARCHAR(255) DEFAULT NULL COMMENT '支付回调地址',
  `return_url` VARCHAR(255) DEFAULT NULL COMMENT '返回地址',
  `expire_time` DATETIME DEFAULT NULL COMMENT '过期时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `complete_time` DATETIME DEFAULT NULL COMMENT '完成时间',
  `refund_time` DATETIME DEFAULT NULL COMMENT '退款时间',
  `third_party_order_no` VARCHAR(100) DEFAULT NULL COMMENT '第三方订单号',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `deleted` TINYINT DEFAULT 0 COMMENT '删除标记:0未删 1已删',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_payment_order_no` (`order_no`),
  UNIQUE KEY `uk_payment_order_merchant_business_no` (`merchant_id`, `business_order_no`),
  INDEX `idx_payment_merchant_id` (`merchant_id`),
  INDEX `idx_payment_status` (`status`),
  INDEX `idx_payment_deleted` (`deleted`),
  INDEX `idx_payment_timeout_scan` (`status`, `deleted`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

CREATE TABLE IF NOT EXISTS `order_state_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `from_state` VARCHAR(32) DEFAULT NULL COMMENT '原状态',
  `to_state` VARCHAR(32) NOT NULL COMMENT '目标状态',
  `event` VARCHAR(50) NOT NULL COMMENT '触发事件',
  `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_state_log_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志表';

CREATE TABLE IF NOT EXISTS `transaction_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `transaction_no` VARCHAR(40) NOT NULL UNIQUE COMMENT '流水号',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(40) NOT NULL COMMENT '订单号',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `channel_id` BIGINT DEFAULT NULL COMMENT '支付方式ID',
  `transaction_type` VARCHAR(32) NOT NULL COMMENT '交易类型',
  `amount` DECIMAL(18,2) NOT NULL COMMENT '交易金额',
  `channel_type` VARCHAR(32) NOT NULL COMMENT '渠道类型',
  `third_party_transaction_no` VARCHAR(100) DEFAULT NULL COMMENT '第三方流水号',
  `status` TINYINT DEFAULT 2 COMMENT '状态:0失败 1成功 2处理中',
  `request_payload` TEXT DEFAULT NULL COMMENT '请求快照',
  `response_payload` TEXT DEFAULT NULL COMMENT '响应快照',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_transaction_no` (`transaction_no`),
  INDEX `idx_transaction_order_no` (`order_no`),
  INDEX `idx_transaction_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水表';

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
