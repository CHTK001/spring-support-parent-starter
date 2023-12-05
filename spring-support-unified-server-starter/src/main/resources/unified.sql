/*
 Navicat Premium Data Transfer

 Source Server         : 172.16.2.226
 Source Server Type    : MySQL
 Source Server Version : 80033
 Source Host           : 172.16.2.226:3306
 Source Schema         : unified

 Target Server Type    : MySQL
 Target Server Version : 80033
 File Encoding         : 65001

 Date: 30/11/2023 09:54:23
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for unified_component
-- ----------------------------
DROP TABLE IF EXISTS `unified_component`;
CREATE TABLE `unified_component`  (
  `unified_component_id` int NOT NULL AUTO_INCREMENT,
  `unified_component_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'component名称',
  `unified_component_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_component_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_component
-- ----------------------------

-- ----------------------------
-- Table structure for unified_component_item
-- ----------------------------
DROP TABLE IF EXISTS `unified_component_item`;
CREATE TABLE `unified_component_item`  (
  `unified_component_item_id` int NOT NULL AUTO_INCREMENT,
  `unified_component_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组件ID',
  `unified_appname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '安装的应用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_component_item_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_component_item
-- ----------------------------

-- ----------------------------
-- Table structure for unified_config
-- ----------------------------
DROP TABLE IF EXISTS `unified_config`;
CREATE TABLE `unified_config`  (
  `unified_config_id` int NOT NULL AUTO_INCREMENT,
  `unfied_config_from` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置数据来源',
  `unified_config_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置名称',
  `unified_config_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置值',
  `unified_config_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `unified_config_status` int NULL DEFAULT 1 COMMENT '状态: 0： 禁用',
  `unified_config_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置环境',
  `unified_appname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '应用名称',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 111 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_config
-- ----------------------------
INSERT INTO `unified_config` VALUES (107, NULL, 'test', '233', NULL, 1, 'dev', 'common', '2023-11-20 15:42:18', '2023-11-20 11:33:52');

-- ----------------------------
-- Table structure for unified_executer
-- ----------------------------
DROP TABLE IF EXISTS `unified_executer`;
CREATE TABLE `unified_executer`  (
  `unified_executer_id` int NOT NULL AUTO_INCREMENT,
  `unified_executer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '执行器名称',
  `unified_executer_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '执行器地址类型：0=自动注册、1=手动录入',
  `unified_appname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '执行器应用名称',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_executer_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '执行器' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_executer
-- ----------------------------
INSERT INTO `unified_executer` VALUES (2, 'oss', '0', 'oss', '2023-11-20 09:34:09', '2023-11-30 09:12:34');
INSERT INTO `unified_executer` VALUES (5, '公共服务', '1', 'common', '2023-11-20 21:17:45', NULL);
INSERT INTO `unified_executer` VALUES (6, 'zxb-school-attendance', '0', 'zxb-school-attendance', '2023-11-27 14:10:11', NULL);

-- ----------------------------
-- Table structure for unified_executer_item
-- ----------------------------
DROP TABLE IF EXISTS `unified_executer_item`;
CREATE TABLE `unified_executer_item`  (
  `unified_executer_item_id` int NOT NULL AUTO_INCREMENT,
  `unified_executer_id` int NULL DEFAULT NULL COMMENT '执行器ID',
  `unified_executer_item_host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地址',
  `unified_executer_item_port` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '端口',
  `unified_executer_item_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '环境',
  `unified_executer_item_protocol` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '协议',
  `unified_executer_item_subscribe` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订阅数据',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_executer_item_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '执行器子项' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_executer_item
-- ----------------------------
INSERT INTO `unified_executer_item` VALUES (49, 2, '192.168.110.6', '18765', 'dev', 'http', '{\"CONFIG\":{\"autoConfig\":false,\"subscribe\":[\"common\"]},\"MYBATIS\":{\"autoConfig\":false,\"subscribe\":[\"common\"]},\"ACTUATOR\":{\"autoConfig\":false,\"ext\":{\"port\":\"19180\",\"endpointsUrl\":\"/actuator\",\"contextPath\":\"/oauth\"}}}', '2023-11-30 09:45:30', '2023-11-30 09:45:30');

-- ----------------------------
-- Table structure for unified_log
-- ----------------------------
DROP TABLE IF EXISTS `unified_log`;
CREATE TABLE `unified_log`  (
  `unified_log_id` int NOT NULL AUTO_INCREMENT,
  `unified_log_module_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模块类型',
  `unified_log_app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '应用名称',
  `unified_log_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '应用环境',
  `unified_log_cost` int NULL DEFAULT NULL COMMENT '耗时(ms)',
  `unified_log_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '日志信息',
  `unified_log_res` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '响应',
  `unified_log_req` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '请求',
  `unified_log_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_log_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 83 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_log
-- ----------------------------

-- ----------------------------
-- Table structure for unified_mybatis
-- ----------------------------
DROP TABLE IF EXISTS `unified_mybatis`;
CREATE TABLE `unified_mybatis`  (
  `unified_mybatis_id` int NOT NULL AUTO_INCREMENT,
  `unified_mybatis_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置名称',
  `unified_mybatis_sql` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置SQL',
  `unified_mybatis_sql_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SQL类型； sql, xml',
  `unified_mybatic_model_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实体类',
  `unified_mybatic_mapper_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Mapper类型',
  `unified_mybatis_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '环境',
  `unified_appname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '应用名称',
  `unified_mybatis_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_mybatis_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_mybatis
-- ----------------------------
INSERT INTO `unified_mybatis` VALUES (1, 'test', 'select * from sys_user where 1 = 1', 'sql', 'com.chua.starter.sso.impl.SysUser', 'com.chua.starter.sso.impl.SysUserMapper', 'dev', 'common', '测试', '2023-11-21 09:26:44', '2023-11-21 09:53:26');

-- ----------------------------
-- Table structure for unified_patch
-- ----------------------------
DROP TABLE IF EXISTS `unified_patch`;
CREATE TABLE `unified_patch`  (
  `unified_patch_id` int NOT NULL AUTO_INCREMENT,
  `unified_patch_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '补丁英文名称',
  `unified_patch_chinese_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '补丁中文名称',
  `unified_patch_pack` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '补丁包名称',
  `unified_patch_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `unified_patch_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_patch_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '补丁管理' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_patch
-- ----------------------------
INSERT INTO `unified_patch` VALUES (2, 'test', '测试1', 'test-v202311231512.zip', '1、新增一个 /test21请求地址, \n2、修改原有的test的返回值\n3、原有的类添加 /test0 请求地址', 'v202311231512', '2023-11-23 15:12:32', '2023-11-29 17:02:08');

-- ----------------------------
-- Table structure for unified_patch_item
-- ----------------------------
DROP TABLE IF EXISTS `unified_patch_item`;
CREATE TABLE `unified_patch_item`  (
  `unified_patch_item_id` int NOT NULL AUTO_INCREMENT,
  `unified_executer_id` int NULL DEFAULT NULL COMMENT '执行器ID',
  `unified_patch_id` int NULL DEFAULT NULL COMMENT '补丁包id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`unified_patch_item_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 54 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '补丁管理' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_patch_item
-- ----------------------------
INSERT INTO `unified_patch_item` VALUES (64, 2, 2, '2023-11-30 09:47:17', NULL);

-- ----------------------------
-- Table structure for unified_profile
-- ----------------------------
DROP TABLE IF EXISTS `unified_profile`;
CREATE TABLE `unified_profile`  (
  `unified_profile_id` int NOT NULL AUTO_INCREMENT,
  `unified_profile_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '名称',
  `unified_profile_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型, ENV',
  `unified_profile_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`unified_profile_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '配置字典' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of unified_profile
-- ----------------------------
INSERT INTO `unified_profile` VALUES (1, 'dev', 'ENV', '开发环境', '2023-11-17 14:44:23');
INSERT INTO `unified_profile` VALUES (2, 'prod', 'ENV', '生产环境', '2023-11-17 14:44:23');
INSERT INTO `unified_profile` VALUES (3, 'test', 'ENV', '测试环境', '2023-11-17 14:44:27');

SET FOREIGN_KEY_CHECKS = 1;
