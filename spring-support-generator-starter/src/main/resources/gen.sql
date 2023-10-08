/*
 Navicat Premium Data Transfer

 Source Server         : 127.0.0.1
 Source Server Type    : MySQL
 Source Server Version : 80034 (8.0.34)
 Source Host           : localhost:3306
 Source Schema         : gen

 Target Server Type    : MySQL
 Target Server Version : 80034 (8.0.34)
 File Encoding         : 65001

 Date: 08/10/2023 19:53:24
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
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`gen_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen
-- ----------------------------
INSERT INTO `sys_gen` VALUES (8, 'mysql数据库', 'jdbc:mysql://127.0.0.1:3306/config?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true', 'root', 'root', 'com.mysql.cj.jdbc.Driver', 'config', 2, NULL, '2023-09-28 21:56:40', '2023-09-28 22:06:21');
INSERT INTO `sys_gen` VALUES (10, '1', 'jdbc:sqlite:Z:/works/utils-support-parent-starter/gen/10/database/wc.db', 'root', 'root', 'org.sqlite.JDBC', NULL, 3, 'Z:\\works\\utils-support-parent-starter\\gen\\10\\database\\wc.db', '2023-09-29 12:52:55', '2023-09-29 12:53:08');
INSERT INTO `sys_gen` VALUES (11, 'ex', NULL, 'root', 'root', 'nothing', NULL, 4, NULL, '2023-09-29 18:32:01', NULL);
INSERT INTO `sys_gen` VALUES (13, 'ssa', NULL, 'root', 'root', NULL, NULL, 5, 'Z:\\works\\utils-support-parent-starter\\gen\\13\\database\\1.xls', '2023-09-29 18:39:50', '2023-09-29 18:40:07');
INSERT INTO `sys_gen` VALUES (14, 'redis', '127.0.0.1:6379', '', '', NULL, NULL, 6, NULL, '2023-09-30 18:45:34', '2023-09-30 18:45:55');
INSERT INTO `sys_gen` VALUES (15, 'ftp', '127.0.0.1:21', 'guest', 'guest', NULL, NULL, 7, NULL, '2023-10-02 15:19:13', NULL);
INSERT INTO `sys_gen` VALUES (16, 'sftp服务', '112.124.44.21:22', 'root', 'Yaq.KqiRenWen>a6XsJlbEN', NULL, NULL, 8, NULL, '2023-10-03 10:08:17', NULL);

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
) ENGINE = InnoDB AUTO_INCREMENT = 835 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_column
-- ----------------------------
INSERT INTO `sys_gen_column` VALUES (731, 36, 'wc_id', NULL, 'INTEGER', 0, 2000000000, 'Long', 'wcId', NULL, '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (732, 36, 'local_relpath', NULL, 'TEXT', 0, 2000000000, 'String', 'localRelpath', NULL, '0', '0', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (733, 36, 'op_depth', NULL, 'INTEGER', 0, 2000000000, 'Long', 'opDepth', NULL, '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (734, 36, 'parent_relpath', NULL, 'TEXT', 0, 2000000000, 'String', 'parentRelpath', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (735, 36, 'repos_id', NULL, 'INTEGER', 0, 2000000000, 'Long', 'reposId', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (736, 36, 'repos_path', NULL, 'TEXT', 0, 2000000000, 'String', 'reposPath', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (737, 36, 'revision', NULL, 'INTEGER', 0, 2000000000, 'Long', 'revision', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (738, 36, 'presence', NULL, 'TEXT', 0, 2000000000, 'String', 'presence', NULL, '0', '0', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (739, 36, 'moved_here', NULL, 'INTEGER', 0, 2000000000, 'Long', 'movedHere', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (740, 36, 'moved_to', NULL, 'TEXT', 0, 2000000000, 'String', 'movedTo', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (741, 36, 'kind', NULL, 'TEXT', 0, 2000000000, 'String', 'kind', NULL, '0', '0', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (742, 36, 'properties', NULL, 'BLOB', 0, 2000000000, 'String', 'properties', NULL, '0', '1', '1', '1', '1', '1', 'EQ', NULL, '', NULL);
INSERT INTO `sys_gen_column` VALUES (743, 36, 'depth', NULL, 'TEXT', 0, 2000000000, 'String', 'depth', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (744, 36, 'checksum', NULL, 'TEXT', 0, 2000000000, 'String', 'checksum', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (745, 36, 'symlink_target', NULL, 'TEXT', 0, 2000000000, 'String', 'symlinkTarget', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (746, 36, 'changed_revision', NULL, 'INTEGER', 0, 2000000000, 'Long', 'changedRevision', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (747, 36, 'changed_date', NULL, 'INTEGER', 0, 2000000000, 'Long', 'changedDate', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (748, 36, 'changed_author', NULL, 'TEXT', 0, 2000000000, 'String', 'changedAuthor', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (749, 36, 'translated_size', NULL, 'INTEGER', 0, 2000000000, 'Long', 'translatedSize', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (750, 36, 'last_mod_time', NULL, 'INTEGER', 0, 2000000000, 'Long', 'lastModTime', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (751, 36, 'dav_cache', NULL, 'BLOB', 0, 2000000000, 'String', 'davCache', NULL, '0', '1', '1', '1', '1', '1', 'EQ', NULL, '', NULL);
INSERT INTO `sys_gen_column` VALUES (752, 36, 'file_external', NULL, 'INTEGER', 0, 2000000000, 'Long', 'fileExternal', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (753, 36, 'inherited_props', NULL, 'BLOB', 0, 2000000000, 'String', 'inheritedProps', NULL, '0', '1', '1', '1', '1', '1', 'EQ', NULL, '', NULL);
INSERT INTO `sys_gen_column` VALUES (754, 37, 'col_id', '主键', 'INT', 0, 10, 'Integer', 'colId', '1', '1', '0', NULL, '1', '1', NULL, 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (755, 37, 'tab_id', '表ID', 'INT', 0, 10, 'Integer', 'tabId', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (756, 37, 'col_column_name', '列名称', 'VARCHAR', 0, 200, 'String', 'colColumnName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (757, 37, 'col_column_comment', '列描述', 'VARCHAR', 0, 500, 'String', 'colColumnComment', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (758, 37, 'col_column_type', '列类型', 'VARCHAR', 0, 100, 'String', 'colColumnType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (759, 37, 'col_java_type', 'JAVA类型', 'VARCHAR', 0, 500, 'String', 'colJavaType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (760, 37, 'col_java_field', 'JAVA字段名', 'VARCHAR', 0, 200, 'String', 'colJavaField', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (761, 37, 'col_is_pk', '是否主键（1是）', 'CHAR', 0, 1, 'String', 'colIsPk', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (762, 37, 'col_is_increment', '是否自增（1是）', 'CHAR', 0, 1, 'String', 'colIsIncrement', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (763, 37, 'col_is_required', '是否必填（1是）', 'CHAR', 0, 1, 'String', 'colIsRequired', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (764, 37, 'col_is_insert', '是否为插入字段（1是）', 'CHAR', 0, 1, 'String', 'colIsInsert', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (765, 37, 'col_is_edit', '是否编辑字段（1是）', 'CHAR', 0, 1, 'String', 'colIsEdit', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (766, 37, 'col_is_list', '是否列表字段（1是）', 'CHAR', 0, 1, 'String', 'colIsList', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (767, 37, 'col_is_query', '是否查询字段（1是）', 'CHAR', 0, 1, 'String', 'colIsQuery', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (768, 37, 'col_query_type', '查询方式（等于、不等于、大于、小于、范围）', 'VARCHAR', 0, 200, 'String', 'colQueryType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (769, 37, 'col_html_type', '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）', 'VARCHAR', 0, 200, 'String', 'colHtmlType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (770, 37, 'col_dict_type', '字典类型', 'VARCHAR', 0, 200, 'String', 'colDictType', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'select', '', NULL);
INSERT INTO `sys_gen_column` VALUES (771, 37, 'col_sort', '排序', 'INT', 0, 10, 'Integer', 'colSort', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (818, 41, 'config_id', '主键', 'INT', 0, 10, 'Integer', 'configId', '1', '1', '0', NULL, '1', '1', NULL, 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (819, 41, 'config_application_name', '所属配置模块', 'VARCHAR', 0, 255, 'String', 'configApplicationName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (820, 41, 'config_condition', '处理条件', 'VARCHAR', 0, 255, 'String', 'configCondition', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (821, 41, 'config_desc', '配置项描述', 'VARCHAR', 0, 255, 'String', 'configDesc', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (822, 41, 'config_mapping_name', '配置所在配置名称', 'VARCHAR', 0, 255, 'String', 'configMappingName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (823, 41, 'config_name', '配置项名称', 'VARCHAR', 0, 255, 'String', 'configName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (824, 41, 'config_profile', '配置环境', 'VARCHAR', 0, 255, 'String', 'configProfile', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'fileUpload', '', NULL);
INSERT INTO `sys_gen_column` VALUES (825, 41, 'config_value', '配置项值', 'TEXT', 0, 65535, 'String', 'configValue', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'textarea', '', NULL);
INSERT INTO `sys_gen_column` VALUES (826, 41, 'disable', '是否禁用, 0: 开启', 'INT', 0, 10, 'Integer', 'disable', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (827, 42, 'app_id', '', 'INT', 0, 10, 'Integer', 'appId', '1', '1', '0', NULL, '1', '1', NULL, 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (828, 42, 'app_name', '注册的程序名称', 'VARCHAR', 0, 255, 'String', 'appName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (829, 42, 'app_host', '注册程序的主机', 'VARCHAR', 0, 255, 'String', 'appHost', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (830, 42, 'app_port', '注册程序的端口', 'INT', 0, 10, 'Integer', 'appPort', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (831, 42, 'app_profile', '注册程序的环境', 'VARCHAR', 0, 255, 'String', 'appProfile', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'fileUpload', '', NULL);
INSERT INTO `sys_gen_column` VALUES (832, 42, 'app_spring_port', 'spring端口', 'INT', 0, 10, 'Integer', 'appSpringPort', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (833, 42, 'app_context_path', '路径', 'VARCHAR', 0, 255, 'String', 'appContextPath', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (834, 42, 'app_actuator', 'actuator路径', 'VARCHAR', 0, 255, 'String', 'appActuator', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);

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
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_config
-- ----------------------------
INSERT INTO `sys_gen_config` VALUES (2, 'false', 'JDBC', 'NONE', 'https://archiva-maven-storage-prod.oss-cn-beijing.aliyuncs.com/repository/central/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar?Expires=1695912654&OSSAccessKeyId=LTAIfU51SusnnfCC&Signature=rZ7nYH3oWgUa6phzVnKdB%2FiKxuU%3D', 'com.mysql.cj.jdbc.Driver', '', '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"},{\"name\":\"日志\",\"url\":\"/ext/jdbc/log\"},{\"name\":\"web\",\"url\":\"/ext/jdbc/board\"},{\"name\":\"文档\",\"url\":\"/ext/jdbc/doc\"}]', 'MYSQL', 1, '2023-09-28 21:56:40', '2023-09-30 19:26:29');
INSERT INTO `sys_gen_config` VALUES (3, 'false', 'JDBC', 'FILE', 'https://archiva-maven-storage-prod.oss-cn-beijing.aliyuncs.com/repository/gradle-plugin/org/xerial/sqlite-jdbc/3.8.9.1/sqlite-jdbc-3.8.9.1.jar?Expires=1695966895&OSSAccessKeyId=LTAIfU51SusnnfCC&Signature=TO%2BwBB7FbGbhS7uJ%2Fkj8Xjn9zPQ%3D', 'org.sqlite.JDBC', 'Z:\\works\\utils-support-parent-starter\\gen\\3\\driver\\sqlite-jdbc-3.8.9.1.jar', '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"},{\"name\":\"文档\",\"url\":\"/ext/jdbc/doc\"}]', 'SQLITE', 1, '2023-09-29 12:52:55', '2023-09-30 19:26:31');
INSERT INTO `sys_gen_config` VALUES (5, 'false', 'CALCITE', 'NONE', NULL, NULL, NULL, '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"}]', 'CALCITE', 1, '2023-09-29 18:39:50', '2023-09-30 19:26:33');
INSERT INTO `sys_gen_config` VALUES (6, NULL, 'REDIS', 'NONE', NULL, NULL, NULL, '[{\"name\":\"web\",\"url\":\"/ext/redis/console\"}]', 'REDIS', 1, '2023-09-30 18:45:34', '2023-09-30 18:45:55');
INSERT INTO `sys_gen_config` VALUES (7, NULL, 'FTP', 'NONE', NULL, NULL, NULL, '[{\"name\":\"web\",\"url\":\"/ext/ftp/console\"}]', 'FTP', 1, '2023-10-02 15:18:19', '2023-10-02 15:18:51');
INSERT INTO `sys_gen_config` VALUES (8, 'false', 'SFTP', 'NONE', NULL, NULL, NULL, '[{\"name\":\"web\",\"url\":\"/ext/ftp/console\"}]', 'SFTP', 1, '2023-10-03 10:07:05', '2023-10-03 10:07:08');

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
) ENGINE = InnoDB AUTO_INCREMENT = 44 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_table
-- ----------------------------
INSERT INTO `sys_gen_table` VALUES (36, 'NODES', NULL, 'sqlite', 6, 'NODES', NULL, 'com', 'NODES', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `sys_gen_table` VALUES (37, 'sys_gen_column', '', 'mysql', 8, 'SysGenColumn', NULL, 'com', 'genColumn', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `sys_gen_table` VALUES (41, 'configuration_center_info', '', 'mysql数据库', 8, 'ConfigurationCenterInfo', NULL, 'com', 'centerInfo', NULL, NULL, NULL, NULL, '测试', NULL);
INSERT INTO `sys_gen_table` VALUES (42, 'configuration_application_info', '应用', 'mysql数据库', 8, 'ConfigurationApplicationInfo', NULL, 'com', 'applicationInfo', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `sys_gen_table` VALUES (43, '1', NULL, 'ssa', 13, '1', NULL, 'com', '1', NULL, NULL, NULL, NULL, NULL, NULL);

SET FOREIGN_KEY_CHECKS = 1;
