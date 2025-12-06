/*
 租户模块初始化 SQL

 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27)
 File Encoding         : 65001

 Date: 06/12/2025
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_tenant
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant`  (
  `sys_tenant_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `sys_tenant_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `sys_tenant_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号',
  `sys_tenant_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码',
  `sys_tenant_sign` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '签名',
  `sys_tenant_corporation` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '公司',
  `sys_tenant_delete` int(1) NULL DEFAULT 0 COMMENT '是否删除; 0:正常',
  `sys_tenant_status` int(1) NULL DEFAULT 0 COMMENT '是否禁用; 0:正常',
  `sys_tenant_contact` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系人',
  `sys_tenant_phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `sys_tenant_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '地址',
  `sys_tenant_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮件',
  `sys_tenant_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '唯一编码',
  `sys_tenant_gid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户对于配置唯一ID',
  `sys_tenant_home_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户访问地址',
  `sys_tenant_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人姓名',
  `create_by` int(11) NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人姓名',
  `update_by` int(11) NULL DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`sys_tenant_id`) USING BTREE,
  UNIQUE KEY `uk_sys_tenant_code` (`sys_tenant_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_tenant_service
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_service`;
CREATE TABLE `sys_tenant_service`  (
  `sys_tenant_service_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sys_tenant_id` int(11) NULL DEFAULT NULL COMMENT '租户ID',
  `sys_service_id` int(11) NULL DEFAULT NULL COMMENT '服务ID',
  `sys_tenant_service_valid_time` datetime NULL DEFAULT NULL COMMENT '到期时间',
  PRIMARY KEY (`sys_tenant_service_id`) USING BTREE,
  KEY `idx_sys_tenant_id` (`sys_tenant_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租户 - 服务关联表' ROW_FORMAT = Dynamic;
