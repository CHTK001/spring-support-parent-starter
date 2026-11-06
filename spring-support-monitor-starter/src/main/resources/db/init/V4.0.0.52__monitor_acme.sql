-- ===========================================================
-- ACME 证书管理建表脚本
--
-- 证书页与主机资产同属主后端进程（spring-support-monitor-starter 依赖
-- spring-api-support-monitor-starter），因此 acme 三张表与其它监控表同库。
-- 语句全部 IF NOT EXISTS，存量库重复执行安全。
-- ===========================================================

CREATE TABLE IF NOT EXISTS `monitor_acme_account` (
  `acme_account_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `acme_account_email` varchar(255) NULL DEFAULT NULL COMMENT '账号邮箱',
  `acme_account_server` varchar(255) NULL DEFAULT NULL COMMENT 'ACME 服务端点',
  `acme_account_provider_type` varchar(32) NOT NULL DEFAULT 'acme' COMMENT '证书服务商类型(acme=ACME协议/acme4j、cas=阿里云CAS)',
  `acme_account_status` varchar(255) NULL DEFAULT NULL COMMENT '账户状态',
  `acme_account_private_key_pem` text NULL DEFAULT NULL COMMENT '账户私钥（PEM）',
  `acme_account_eab_kid` varchar(255) NULL DEFAULT NULL COMMENT 'EAB Key ID (ZeroSSL 等需要)',
  `acme_account_eab_hmac_key` varchar(512) NULL DEFAULT NULL COMMENT 'EAB HMAC Key (Base64 编码)',
  `acme_account_timeout_seconds` int NULL DEFAULT 60 COMMENT 'ACME 网络请求超时（秒）',
  `acme_account_remark` varchar(255) NULL DEFAULT NULL COMMENT '备注',
  `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`acme_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACME 账户表';

CREATE TABLE IF NOT EXISTS `monitor_acme_certificate` (
  `acme_cert_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `acme_account_id` int NULL DEFAULT NULL COMMENT '账户ID',
  `acme_cert_primary_domain` varchar(255) NULL DEFAULT NULL COMMENT '主域名',
  `acme_cert_san` varchar(255) NULL DEFAULT NULL COMMENT '备用域名(SAN)，逗号分隔',
  `acme_cert_challenge_type` varchar(255) NULL DEFAULT NULL COMMENT '挑战类型（HTTP-01/DNS-01）',
  `acme_cert_status` varchar(255) NULL DEFAULT NULL COMMENT '证书状态',
  `acme_cert_not_before` varchar(255) NULL DEFAULT NULL COMMENT '有效期起',
  `acme_cert_not_after` varchar(255) NULL DEFAULT NULL COMMENT '有效期止',
  `acme_cert_renew_at` varchar(255) NULL DEFAULT NULL COMMENT '计划续签日期',
  `acme_cert_chain_pem` text NULL COMMENT '证书PEM（含链）',
  `acme_cert_key_pem` text NULL COMMENT '私钥PEM',
  `acme_cert_last_error` varchar(255) NULL DEFAULT NULL COMMENT '最近错误',
  `acme_cert_deploy_mode` varchar(20) NULL DEFAULT 'manual' COMMENT '部署模式：local(本机自动)/manual(手动)',
  `acme_cert_webroot_path` varchar(255) NULL DEFAULT NULL COMMENT '本机模式webroot路径',
  `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`acme_cert_id`),
  KEY `monitor_acme_certificate_idx_account_id` (`acme_account_id`),
  KEY `monitor_acme_certificate_idx_domain` (`acme_cert_primary_domain`),
  KEY `monitor_acme_certificate_idx_status` (`acme_cert_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACME 证书表';

CREATE TABLE IF NOT EXISTS `monitor_acme_order` (
  `acme_order_id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `acme_account_id` int NULL DEFAULT NULL COMMENT '账户ID',
  `acme_cert_id` int NULL DEFAULT NULL COMMENT '证书ID',
  `acme_order_url` varchar(255) NULL DEFAULT NULL COMMENT '订单URL',
  `acme_authz_urls` text NULL COMMENT '授权URL列表(JSON)',
  `acme_order_status` varchar(255) NULL DEFAULT NULL COMMENT '状态',
  `acme_certificate_url` varchar(255) NULL DEFAULT NULL COMMENT '最终证书URL',
  `create_name` varchar(255) NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`acme_order_id`),
  KEY `monitor_acme_order_idx_account_id` (`acme_account_id`),
  KEY `monitor_acme_order_idx_cert_id` (`acme_cert_id`),
  KEY `monitor_acme_order_idx_status` (`acme_order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACME 订单表';
