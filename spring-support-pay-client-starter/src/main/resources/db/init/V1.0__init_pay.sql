/*
 Navicat Premium Dump SQL

 Source Server         : 172.16.0.40
 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27)
 Source Host           : 172.16.0.40:3306
 Source Schema         : pay

 Target Server Type    : MySQL
 Target Server Version : 80027 (8.0.27)
 File Encoding         : 65001

 Date: 15/10/2025 14:38:19
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for pay_merchant
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant`;
CREATE TABLE `pay_merchant`  (
  `pay_merchant_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商户名称',
  `pay_merchant_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商户编码',
  `pay_merchant_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `pay_merchant_status` int NULL DEFAULT NULL COMMENT '是否启用 0-未启用',
  `pay_merchant_delete` int NULL DEFAULT NULL COMMENT '是否删除 0-未删除',
  `pay_merchant_open_wallet` int NULL DEFAULT NULL COMMENT '是否启用钱包 0-未启用',
  `pay_merchant_open_timeout` int NULL DEFAULT NULL COMMENT '是否开启订单超时功能',
  `pay_merchant_open_timeout_time` int NULL DEFAULT NULL COMMENT '订单超时时间(min)',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付商户管理' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_merchant
-- ----------------------------
INSERT INTO `pay_merchant` VALUES (1, '浙江力电宝网络科技有限公司', 'M0000000001', NULL, 1, 0, 1, NULL, NULL, NULL, NULL, NULL, '2025-04-08 10:26:10', 'sa', 1);

-- ----------------------------
-- Table structure for pay_merchant_config_wechat
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant_config_wechat`;
CREATE TABLE `pay_merchant_config_wechat`  (
  `pay_merchant_config_wechat_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_config_wechat_app_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '微信appId',
  `pay_merchant_config_wechat_mch_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '机器ID',
  `pay_merchant_config_wechat_mch_serial_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '序列',
  `pay_merchant_config_wechat_app_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '微信Secret',
  `pay_merchant_config_wechat_trade_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付类型',
  `pay_merchant_config_wechat_notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '回调地址',
  `pay_merchant_config_wechat_api_key_v3` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'api v3Key',
  `pay_merchant_config_wechat_private_key_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '私有key',
  `pay_merchant_config_wechat_payment_point_service_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付分服务ID',
  `pay_merchant_config_wechat_payment_point_service_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付分服务名称',
  `pay_merchant_config_wechat_payment_point_risk_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '支付分风险金额度(单位:分)',
  `pay_merchant_config_wechat_payment_point_rule` json NULL COMMENT '支付分规则',
  `pay_merchant_config_status` int NULL DEFAULT NULL COMMENT '是否启用 0-禁用',
  `pay_merchant_config_test_account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '测试账号',
  `pay_merchant_id` int NULL DEFAULT NULL COMMENT '商户ID',
  `pay_merchant_config_wechat_pay_notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付回调地址',
  `pay_merchant_config_wechat_refund_notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退款回调地址',
  `pay_merchant_config_wechat_transfer_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转账回调地址',
  `pay_merchant_config_wechat_payment_point_notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付分回调地址',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_config_wechat_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付微信支付配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_merchant_config_wechat
-- ----------------------------
INSERT INTO `pay_merchant_config_wechat` VALUES (1, 'wx2da714f6cd21a629', '1711350868', '69EFC0C06F6A16D4B7053F65756A526836CBFFAD', 'a8706f64dc2153ccca178e1414d3dba6', 'js_api', 'https://www.tzldb.cn/system/api/v2/pay/callback/wechat/order/notify', 'ZJLDBWLKJyxgs9999999999999999999', '/home/1711350868_20250408_cert/apiclient_key.pem', '00003004000000174799053186024780', NULL, NULL, NULL, 1, 'oKAK06xfO9fY7DXpWK3OK-gPazUo', 1, NULL, NULL, 'https://www.tzldb.cn/system/api/v2/pay/callback/wechat/transfer/notify', 'https://www.tzldb.cn/system/api/v2/pay/callback/wechat/payment/point/notify', NULL, NULL, NULL, '2025-01-07 10:37:25', 'sa', 1);
INSERT INTO `pay_merchant_config_wechat` VALUES (2, 'wxf6f3579ac14de31a', '1674207483', '5BFB79BA0B32A8EE322B45271F056E4778AB704E', '9076beda7aa95ec6f04d14be4fc4d15d', 'js_api', 'https://www.qirenit.com/pay/api/v2/pay/callback/wechat/order/notify', '24c09f7647dcafbfe4b21d8e6ca124b9', 'E:\\1711350868_20250408_cert/apiclient_key.pem', NULL, NULL, NULL, NULL, 1, 'oKAK06xfO9fY7DXpWK3OK-gPazUo', 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2025-01-07 10:37:25', 'sa', 1);

-- ----------------------------
-- Table structure for pay_merchant_failure_record
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant_failure_record`;
CREATE TABLE `pay_merchant_failure_record`  (
  `pay_merchant_failure_record_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_failure_record_body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '消息体',
  `pay_merchant_failure_record_signature` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签名',
  `pay_merchant_failure_record_signature_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签名类型',
  `pay_merchant_failure_record_nonce` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '单次请求ID',
  `pay_merchant_failure_record_serial` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '证书',
  `pay_merchant_merchant_order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单编号',
  `pay_merchant_failure_record_timestamp` int NULL DEFAULT NULL COMMENT '时间戳',
  `pay_merchant_failure_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `pay_merchant_failure_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败类型',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_failure_record_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付失败记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_merchant_failure_record
-- ----------------------------

-- ----------------------------
-- Table structure for pay_merchant_order
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant_order`;
CREATE TABLE `pay_merchant_order`  (
  `pay_merchant_order_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单编号',
  `pay_merchant_order_amount` decimal(20, 4) NULL DEFAULT NULL COMMENT '订单实际支付金额(保留4位，实际支付四舍)',
  `pay_merchant_order_origin_amount` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单原始金额(保留4位，实际支付四舍)',
  `pay_merchant_id` int NULL DEFAULT NULL COMMENT '商户ID',
  `pay_merchant_order_openid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '微信openID',
  `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '账号ID',
  `user_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '账号类型',
  `pay_merchant_order_type` int NULL DEFAULT NULL COMMENT '订单类型',
  `pay_merchant_trade_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '交易方式',
  `pay_merchant_current_wallet_amount` decimal(20, 4) NULL DEFAULT NULL COMMENT '当时钱包余额',
  `pay_merchant_order_project` int NULL DEFAULT NULL COMMENT '订单项目',
  `pay_merchant_order_pay_time` datetime NULL DEFAULT NULL COMMENT '支付时间',
  `pay_merchant_order_finished_time` datetime NULL DEFAULT NULL COMMENT '订单完成时间',
  `pay_merchant_order_create_time` datetime NULL DEFAULT NULL COMMENT '订单创建时间',
  `pay_merchant_order_failure_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单失败原因',
  `pay_merchant_order_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单状态',
  `pay_merchant_order_invoiced` int NULL DEFAULT NULL COMMENT '是否开票 0-未开票',
  `pay_merchant_order_invoiced_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '开票ID',
  `pay_merchant_order_origin_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产生订单的原始数据ID',
  `pay_merchant_order_transaction_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付服务提供商订单号',
  `pay_merchant_order_browser_system` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求系统',
  `pay_merchant_order_browser` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求消息头',
  `pay_merchant_order_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求客户端IP',
  `pay_merchant_order_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `pay_merchant_order_attach` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '附加参数',
  `pay_merchant_order_refund_user_received_account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退款入账账户 说明：取当前退款单的退款入账方，有以下几种情况： 1）退回银行卡：{银行名称}{卡类型}{卡尾号} 2）退回支付用户零钱:支付用户零钱* 3）退还商户:商户基本账户商户结算银行账户 4）退回支付用户零钱通:支付用户零钱通',
  `pay_merchant_order_refund_success_time` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退款成功时间',
  `pay_merchant_order_refund_create_time` datetime NULL DEFAULT NULL COMMENT '退款创建时间',
  `pay_merchant_order_refund_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退款订单号',
  `pay_merchant_order_refund_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退款原因',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_order_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_merchant_order
-- ----------------------------

-- ----------------------------
-- Table structure for pay_merchant_order_water
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant_order_water`;
CREATE TABLE `pay_merchant_order_water`  (
  `pay_merchant_order_water_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_order_water_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流水编号',
  `pay_merchant_order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单编号',
  `pay_merchant_order_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单状态',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_order_water_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_merchant_order_water
-- ----------------------------

-- ----------------------------
-- Table structure for pay_merchant_transfer_record
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant_transfer_record`;
CREATE TABLE `pay_merchant_transfer_record`  (
  `pay_merchant_transfer_record_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_transfer_record_user_openid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转账用户',
  `pay_merchant_transfer_record_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '金额',
  `pay_merchant_transfer_record_real_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '真实姓名(部分要使用)',
  `pay_merchant_transfer_record_phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `pay_merchant_transfer_record_status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态',
  `pay_merchant_transfer_record_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转账编码',
  `pay_merchant_transfer_record_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  `pay_merchant_id` int NULL DEFAULT NULL COMMENT '商户ID',
  `pay_merchant_transfer_record_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `pay_merchant_transfer_record_finish_time` datetime NULL DEFAULT NULL COMMENT '转账完成时间',
  `pay_merchant_transfer_record_create_time` datetime NULL DEFAULT NULL COMMENT '转账创建时间',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_transfer_record_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '转账记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_merchant_transfer_record
-- ----------------------------

-- ----------------------------
-- Table structure for pay_user_wallet
-- ----------------------------
DROP TABLE IF EXISTS `pay_user_wallet`;
CREATE TABLE `pay_user_wallet`  (
  `pay_user_wallet_id` int NOT NULL AUTO_INCREMENT,
  `pay_user_wallet_amount` decimal(20, 4) NULL DEFAULT NULL COMMENT '金额',
  `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '账号ID',
  `user_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '账号类型',
  `pay_user_wallet_last_time` datetime NULL DEFAULT NULL COMMENT '最后使用时间',
  `pay_user_wallet_last_order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后一次订单号',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_user_wallet_id`) USING BTREE,
  INDEX `idx_unique`(`user_id` ASC, `user_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户钱包' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_user_wallet
-- ----------------------------

-- ----------------------------
-- Table structure for pay_merchant_config_alipay
-- ----------------------------
DROP TABLE IF EXISTS `pay_merchant_config_alipay`;
CREATE TABLE `pay_merchant_config_alipay`  (
  `pay_merchant_config_alipay_id` int NOT NULL AUTO_INCREMENT,
  `pay_merchant_config_alipay_app_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付宝应用ID',
  `pay_merchant_config_alipay_private_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '商户私钥',
  `pay_merchant_config_alipay_public_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '支付宝公钥',
  `pay_merchant_config_alipay_trade_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付类型',
  `pay_merchant_config_alipay_pay_notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付回调地址',
  `pay_merchant_config_alipay_refund_notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '退款回调地址',
  `pay_merchant_config_status` int NULL DEFAULT NULL COMMENT '是否启用 0-禁用',
  `pay_merchant_config_test_account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '测试账号',
  `pay_merchant_id` int NULL DEFAULT NULL COMMENT '商户ID',
  `pay_merchant_config_alipay_sign_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签名类型',
  `pay_merchant_config_alipay_charset` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '字符编码格式',
  `pay_merchant_config_alipay_gateway_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '网关地址',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`pay_merchant_config_alipay_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付支付宝配置' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
