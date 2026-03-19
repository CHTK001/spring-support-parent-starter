-- 支付系统数据库表结构
-- 创建时间: 2026-03-18
-- 说明: 包含商户、支付渠道、订单、状态流转、交易流水五张核心表

-- 1. 商户表
CREATE TABLE IF NOT EXISTS `merchant` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `merchant_no` VARCHAR(32) UNIQUE NOT NULL COMMENT '商户号',
  `merchant_name` VARCHAR(100) NOT NULL COMMENT '商户名称',
  `contact_name` VARCHAR(50) COMMENT '联系人',
  `contact_phone` VARCHAR(20) COMMENT '联系电话',
  `contact_email` VARCHAR(100) COMMENT '联系邮箱',
  `business_license` VARCHAR(200) COMMENT '营业执照',
  `legal_person` VARCHAR(50) COMMENT '法人',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0待审核 1已激活 2已停用 3已注销',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_merchant_no` (`merchant_no`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户表';

-- 2. 支付渠道配置表
CREATE TABLE IF NOT EXISTS `merchant_channel` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `channel_type` VARCHAR(20) NOT NULL COMMENT '渠道类型：WECHAT/ALIPAY/WALLET/COMPOSITE',
  `channel_sub_type` VARCHAR(20) COMMENT '子类型：MINI/H5/APP/JSAPI/NATIVE',
  `app_id` VARCHAR(100) COMMENT 'AppID',
  `merchant_no` VARCHAR(100) COMMENT '商户号',
  `api_key` VARCHAR(500) COMMENT 'API密钥（加密）',
  `private_key` TEXT COMMENT '私钥（加密）',
  `public_key` TEXT COMMENT '公钥',
  `cert_path` VARCHAR(200) COMMENT '证书路径',
  `sandbox_mode` TINYINT DEFAULT 0 COMMENT '沙盒模式：0正式 1沙盒',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_merchant_id` (`merchant_id`),
  INDEX `idx_channel_type` (`channel_type`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道配置表';

-- 3. 支付订单表
CREATE TABLE IF NOT EXISTS `payment_order` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `order_no` VARCHAR(32) UNIQUE NOT NULL COMMENT '订单号',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `user_id` BIGINT COMMENT '用户ID',
  `channel_type` VARCHAR(20) NOT NULL COMMENT '支付渠道',
  `channel_sub_type` VARCHAR(20) COMMENT '渠道子类型',
  `order_amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  `paid_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '实付金额',
  `discount_amount` DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING/PAYING/PAID/COMPLETED/CANCELLED/FAILED/REFUNDING/REFUNDED',
  `pay_time` DATETIME COMMENT '支付时间',
  `complete_time` DATETIME COMMENT '完成时间',
  `refund_time` DATETIME COMMENT '退款时间',
  `third_party_order_no` VARCHAR(100) COMMENT '第三方订单号',
  `remark` VARCHAR(500) COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX `idx_order_no` (`order_no`),
  INDEX `idx_merchant_id` (`merchant_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- 4. 订单状态流转日志表
CREATE TABLE IF NOT EXISTS `order_state_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `from_state` VARCHAR(20) COMMENT '原状态',
  `to_state` VARCHAR(20) NOT NULL COMMENT '目标状态',
  `event` VARCHAR(50) NOT NULL COMMENT '触发事件',
  `operator` VARCHAR(50) COMMENT '操作人',
  `remark` VARCHAR(500) COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志';

-- 5. 交易流水表
CREATE TABLE IF NOT EXISTS `transaction_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  `transaction_no` VARCHAR(32) UNIQUE NOT NULL COMMENT '流水号',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
  `transaction_type` VARCHAR(20) NOT NULL COMMENT '交易类型：PAY/REFUND',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '交易金额',
  `channel_type` VARCHAR(20) NOT NULL COMMENT '支付渠道',
  `third_party_transaction_no` VARCHAR(100) COMMENT '第三方流水号',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0失败 1成功',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX `idx_transaction_no` (`transaction_no`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_merchant_id` (`merchant_id`),
  INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易流水表';
