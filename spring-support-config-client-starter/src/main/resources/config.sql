/*
 Navicat Premium Data Transfer

 Source Server         : 127.0.0.1
 Source Server Type    : MySQL
 Source Server Version : 80033
 Source Host           : 127.0.0.1:3306
 Source Schema         : config

 Target Server Type    : MySQL
 Target Server Version : 80033
 File Encoding         : 65001

 Date: 08/09/2023 20:26:37
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for configuration_application_info
-- ----------------------------
DROP TABLE IF EXISTS `configuration_application_info`;
CREATE TABLE `configuration_application_info`  (
  `app_id` int NOT NULL AUTO_INCREMENT,
  `app_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '注册的程序名称',
  `app_host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '注册程序的主机',
  `app_port` int NULL DEFAULT NULL COMMENT '注册程序的端口',
  `app_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '注册程序的环境',
  `app_spring_port` int NULL DEFAULT NULL COMMENT 'spring端口',
  `app_context_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路径',
  `app_actuator` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'actuator路径',
  PRIMARY KEY (`app_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of configuration_application_info
-- ----------------------------
INSERT INTO `configuration_application_info` VALUES (12, 'system', '127.0.0.1', 23987, 'dev', 18170, '/api/system', '/actuator');

-- ----------------------------
-- Table structure for configuration_bean_info
-- ----------------------------
DROP TABLE IF EXISTS `configuration_bean_info`;
CREATE TABLE `configuration_bean_info`  (
  `bean_id` int NOT NULL AUTO_INCREMENT,
  `bean_application_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属应用',
  `bean_file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件目录',
  `bean_marker` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `bean_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置环境',
  `bean_type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型',
  `disable` int NULL DEFAULT 0 COMMENT '是否禁用, 0: 开启',
  `bean_definition` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '额外定义',
  `bean_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'beanName',
  PRIMARY KEY (`bean_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of configuration_bean_info
-- ----------------------------
INSERT INTO `configuration_bean_info` VALUES (1, 'system', 'Z:\\workspace\\utils-support-parent-starter\\utils-support-example-starter\\target\\classes\\TDemoInfoImpl.groovy', '测试', 'dev', 'com.chua.example.dynamic.TDemoInfo', 0, NULL, 'test');

-- ----------------------------
-- Table structure for configuration_center_info
-- ----------------------------
DROP TABLE IF EXISTS `configuration_center_info`;
CREATE TABLE `configuration_center_info`  (
  `config_id` int NOT NULL AUTO_INCREMENT,
  `config_application_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属配置模块',
  `config_condition` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '处理条件',
  `config_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置项描述',
  `config_mapping_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置所在配置名称',
  `config_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置项名称',
  `config_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置环境',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '配置项值',
  `disable` int NULL DEFAULT NULL COMMENT '是否禁用, 0: 开启',
  PRIMARY KEY (`config_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of configuration_center_info
-- ----------------------------

-- ----------------------------
-- Table structure for configuration_subscribe_info
-- ----------------------------
DROP TABLE IF EXISTS `configuration_subscribe_info`;
CREATE TABLE `configuration_subscribe_info`  (
  `subscribe_id` int NOT NULL AUTO_INCREMENT,
  `subscribe_application_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订阅的应用',
  `subscribe_profile` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订阅环境',
  `subscribe_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订阅类型, config, bean',
  PRIMARY KEY (`subscribe_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of configuration_subscribe_info
-- ----------------------------
INSERT INTO `configuration_subscribe_info` VALUES (1, NULL, 'dev', 'bean');
INSERT INTO `configuration_subscribe_info` VALUES (2, NULL, 'dev', 'config');
INSERT INTO `configuration_subscribe_info` VALUES (3, NULL, 'dev', 'bean');
INSERT INTO `configuration_subscribe_info` VALUES (4, NULL, 'dev', 'config');
INSERT INTO `configuration_subscribe_info` VALUES (5, NULL, 'dev', 'config');
INSERT INTO `configuration_subscribe_info` VALUES (6, NULL, 'dev', 'config');
INSERT INTO `configuration_subscribe_info` VALUES (7, NULL, 'dev', 'config');

-- ----------------------------
-- Table structure for hibernate_sequence
-- ----------------------------
DROP TABLE IF EXISTS `hibernate_sequence`;
CREATE TABLE `hibernate_sequence`  (
  `next_val` bigint NULL DEFAULT NULL
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of hibernate_sequence
-- ----------------------------
INSERT INTO `hibernate_sequence` VALUES (1);

SET FOREIGN_KEY_CHECKS = 1;
