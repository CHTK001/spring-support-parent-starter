/*
 Navicat Premium Data Transfer

 Source Server         : 127.0.0.1
 Source Server Type    : MySQL
 Source Server Version : 80033
 Source Host           : 127.0.0.1:3306
 Source Schema         : gen

 Target Server Type    : MySQL
 Target Server Version : 80033
 File Encoding         : 65001

 Date: 10/10/2023 20:52:35
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_gen
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen`;
CREATE TABLE `sys_gen`  (
  `gen_id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gen_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '名称',
  `gen_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'url',
  `gen_user` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户名',
  `gen_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密码',
  `gen_driver` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '驱动包',
  `gen_database` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '数据库',
  `dbc_id` int NULL DEFAULT NULL COMMENT '配置ID',
  `gen_database_file` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '数据库文件目录',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`gen_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen
-- ----------------------------
INSERT INTO `sys_gen` VALUES (1, '本地数据库', 'jdbc:mysql://127.0.0.1:3306/config?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true', 'root', 'root', 'com.mysql.cj.jdbc.Driver', 'config', 1, NULL, 'guest', '2023-09-28 21:56:40', '2023-10-09 15:20:20');
INSERT INTO `sys_gen` VALUES (2, '本地redis', '172.16.2.226:6379', '', '', NULL, NULL, 4, NULL, 'guest', '2023-09-30 18:45:34', '2023-10-09 13:11:18');
INSERT INTO `sys_gen` VALUES (3, '本地zk', '127.0.0.1:2181', '', '', NULL, NULL, 8, NULL, 'guest', '2023-10-10 09:11:31', NULL);
INSERT INTO `sys_gen` VALUES (4, 'nginx', '192.168.110.100:22', 'root', 'boren1818', NULL, NULL, 9, NULL, 'guest', '2023-10-10 20:33:22', '2023-10-10 20:33:22');

-- ----------------------------
-- Table structure for sys_gen_column
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_column`;
CREATE TABLE `sys_gen_column`  (
  `col_id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tab_id` int NULL DEFAULT NULL COMMENT '表ID',
  `col_column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
  `col_column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列描述',
  `col_column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列类型',
  `col_column_decimal` int NULL DEFAULT NULL COMMENT '小数点',
  `col_column_length` int NULL DEFAULT NULL COMMENT '字段长度',
  `col_java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
  `col_java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA字段名',
  `col_is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否主键（1是）',
  `col_is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否自增（1是）',
  `col_is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否必填（1是）',
  `col_is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `col_is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `col_is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否列表字段（1是）',
  `col_is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否查询字段（1是）',
  `col_query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `col_html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `col_dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
  `col_sort` int NULL DEFAULT NULL COMMENT '排序',
  PRIMARY KEY (`col_id`) USING BTREE,
  INDEX `index_tab_id`(`tab_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_column
-- ----------------------------
INSERT INTO `sys_gen_column` VALUES (1, 1, 'col_id', '主键', 'INT', 0, 10, 'Integer', 'colId', NULL, '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (2, 1, 'tab_id', '表ID', 'INT', 0, 10, 'Integer', 'tabId', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (3, 1, 'col_column_name', '列名称', 'VARCHAR', 0, 250, 'String', 'colColumnName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (4, 1, 'col_column_comment', '列描述', 'VARCHAR', 0, 260, 'String', 'colColumnComment', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (5, 1, 'col_column_type', '列类型', 'VARCHAR', 0, 100, 'String', 'colColumnType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (6, 1, 'col_java_type', 'JAVA类型', 'VARCHAR', 0, 500, 'String', 'colJavaType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (7, 1, 'col_java_field', 'JAVA字段名', 'VARCHAR', 0, 200, 'String', 'colJavaField', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (8, 1, 'col_is_pk', '是否主键（1是）', 'CHAR', 0, 1, 'String', 'colIsPk', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (9, 1, 'col_is_increment', '是否自增（1是）', 'CHAR', 0, 1, 'String', 'colIsIncrement', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (10, 1, 'col_is_required', '是否必填（1是）', 'CHAR', 0, 1, 'String', 'colIsRequired', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (11, 1, 'col_is_insert', '是否为插入字段（1是）', 'CHAR', 0, 1, 'String', 'colIsInsert', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (12, 1, 'col_is_edit', '是否编辑字段（1是）', 'CHAR', 0, 1, 'String', 'colIsEdit', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (13, 1, 'col_is_list', '是否列表字段（1是）', 'CHAR', 0, 1, 'String', 'colIsList', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (14, 1, 'col_is_query', '是否查询字段（1是）', 'CHAR', 0, 1, 'String', 'colIsQuery', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (15, 1, 'col_query_type', '查询方式（等于、不等于、大于、小于、范围）', 'VARCHAR', 0, 200, 'String', 'colQueryType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (16, 1, 'col_html_type', '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）', 'VARCHAR', 0, 200, 'String', 'colHtmlType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (17, 1, 'col_dict_type', '字典类型', 'VARCHAR', 0, 200, 'String', 'colDictType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (18, 1, 'col_sort', '排序', 'INT', 0, 10, 'Integer', 'colSort', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (19, 1, 'test', '测试', 'VARCHAR', 0, 255, 'String', 'test', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);

-- ----------------------------
-- Table structure for sys_gen_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_config`;
CREATE TABLE `sys_gen_config`  (
  `dbc_id` int NOT NULL AUTO_INCREMENT,
  `dbc_log` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '是否有日志',
  `dbc_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'JDBC,REDIS',
  `dbc_database` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'FILE,NONE',
  `dbc_driver_link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '驱动下载地址',
  `dbc_driver` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '驱动类型,多个逗号分割',
  `dbc_driver_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '驱动文件地址,服务器生成',
  `dbc_console_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '控制台地址',
  `dbc_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '数据库名称',
  `dbc_status` smallint NULL DEFAULT 0 COMMENT '是否开启, 1: 开启',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dbc_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_config
-- ----------------------------
INSERT INTO `sys_gen_config` VALUES (1, 'false', 'JDBC', 'NONE', 'https://archiva-maven-storage-prod.oss-cn-beijing.aliyuncs.com/repository/central/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar?Expires=1695912654&OSSAccessKeyId=LTAIfU51SusnnfCC&Signature=rZ7nYH3oWgUa6phzVnKdB%2FiKxuU%3D', 'com.mysql.cj.jdbc.Driver', '', '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"},{\"name\":\"日志\",\"url\":\"/ext/jdbc/log\"},{\"name\":\"UI\",\"url\":\"/ext/jdbc/board\"},{\"name\":\"文档\",\"url\":\"/ext/jdbc/doc\"}]', 'MYSQL', 1, '2023-09-28 21:56:40', '2023-10-09 13:11:17');
INSERT INTO `sys_gen_config` VALUES (2, 'false', 'JDBC', 'FILE', 'https://archiva-maven-storage-prod.oss-cn-beijing.aliyuncs.com/repository/gradle-plugin/org/xerial/sqlite-jdbc/3.8.9.1/sqlite-jdbc-3.8.9.1.jar?Expires=1695966895&OSSAccessKeyId=LTAIfU51SusnnfCC&Signature=TO%2BwBB7FbGbhS7uJ%2Fkj8Xjn9zPQ%3D', 'org.sqlite.JDBC', 'Z:\\works\\utils-support-parent-starter\\gen\\3\\driver\\sqlite-jdbc-3.8.9.1.jar', '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"},{\"name\":\"文档\",\"url\":\"/ext/jdbc/doc\"}]', 'SQLITE', 1, '2023-09-29 12:52:55', '2023-10-09 13:11:18');
INSERT INTO `sys_gen_config` VALUES (3, 'false', 'CALCITE', 'NONE', NULL, NULL, NULL, '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"}]', 'CALCITE', 1, '2023-09-29 18:39:50', '2023-10-09 13:11:18');
INSERT INTO `sys_gen_config` VALUES (4, 'false', 'REDIS', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/redis/console\"}]', 'REDIS', 1, '2023-09-30 18:45:34', '2023-10-09 13:11:18');
INSERT INTO `sys_gen_config` VALUES (5, 'false', 'FTP', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/ftp/console\"}]', 'FTP', 1, '2023-10-02 15:18:19', '2023-10-09 13:11:19');
INSERT INTO `sys_gen_config` VALUES (6, 'false', 'SFTP', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/ftp/console\"}]', 'SFTP', 1, '2023-10-03 10:07:05', '2023-10-09 13:11:19');
INSERT INTO `sys_gen_config` VALUES (7, 'false', 'SSH', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/ssh/console\"}]', 'SSH', 1, '2023-10-09 15:04:33', '2023-10-09 15:05:12');
INSERT INTO `sys_gen_config` VALUES (8, 'false', 'ZOOKEEPER', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/zk/console\"}]', 'ZOOKEEPER', 1, '2023-10-10 08:58:54', '2023-10-10 09:03:08');
INSERT INTO `sys_gen_config` VALUES (9, NULL, 'NGINX', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/nginx/console\"}]', 'NGINX', 1, '2023-10-10 20:33:22', '2023-10-10 20:33:22');

-- ----------------------------
-- Table structure for sys_gen_table
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_table`;
CREATE TABLE `sys_gen_table`  (
  `tab_id` int NOT NULL AUTO_INCREMENT COMMENT '表ID',
  `tab_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '表名称',
  `tab_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `gen_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属数据源名称, 用于处理内部数据源',
  `gen_id` int NULL DEFAULT NULL COMMENT '所属数据源',
  `tab_class_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实体类名称',
  `tab_package_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成包路径',
  `tab_module_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成模块名',
  `tab_business_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成业务名',
  `tab_function_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成功能名',
  `tab_function_author` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成功能作者',
  `tab_gen_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `tab_gen_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '生成路径（不填默认项目路径）',
  `tab_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `tab_tpl_category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '使用的模板（crud单表操作 tree树表操作 sub主子表操作）',
  PRIMARY KEY (`tab_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_table
-- ----------------------------
INSERT INTO `sys_gen_table` VALUES (1, 'sys_gen_column', '字段信息表', '本地数据库', 1, 'SysGenColumn', NULL, 'com', 'genColumn', NULL, NULL, NULL, NULL, NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;
