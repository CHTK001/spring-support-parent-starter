/*
 Navicat Premium Data Transfer

 Source Server         : 172.16.2.226
 Source Server Type    : MySQL
 Source Server Version : 80033 (8.0.33)
 Source Host           : 172.16.2.226:3306
 Source Schema         : gen

 Target Server Type    : MySQL
 Target Server Version : 80033 (8.0.33)
 File Encoding         : 65001

 Date: 16/10/2023 20:33:26
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
  `gen_backup_status` int NULL DEFAULT NULL COMMENT '0:未启动',
  `gen_database_file` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '数据库文件目录',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`gen_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen
-- ----------------------------
INSERT INTO `sys_gen` VALUES (1, 'MYSQL数据库', 'jdbc:mysql://172.16.2.226:3306/config?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true', 'root', 'root', 'com.mysql.cj.jdbc.Driver', 'config', 1, 0, NULL, 'guest', '2023-09-28 21:56:40', '2023-10-09 13:11:17');
INSERT INTO `sys_gen` VALUES (2, 'redis', '172.16.2.226:6379', NULL, NULL, NULL, NULL, 4, NULL, NULL, 'guest', '2023-09-30 18:45:34', '2023-10-15 10:07:25');
INSERT INTO `sys_gen` VALUES (3, 'zookeeper', '127.0.0.1:2181', NULL, NULL, NULL, NULL, 8, NULL, NULL, 'guest', '2023-10-10 08:58:54', '2023-10-15 10:06:25');
INSERT INTO `sys_gen` VALUES (4, 'nginx', '192.168.110.100:22', 'root', 'boren1818', NULL, NULL, 9, NULL, NULL, 'guest', '2023-10-10 20:33:22', '2023-10-15 10:06:25');
INSERT INTO `sys_gen` VALUES (5, 'elasticsearch', 'http://172.16.1.112:9200', NULL, NULL, NULL, NULL, 10, NULL, NULL, 'guest', '2023-10-11 13:17:19', '2023-10-15 10:06:25');
INSERT INTO `sys_gen` VALUES (7, '服务器本地数据包', NULL, 'root', '', NULL, NULL, 11, 1, NULL, 'guest', '2023-10-15 13:49:51', '2023-10-15 13:49:54');

-- ----------------------------
-- Table structure for sys_gen_backup
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_backup`;
CREATE TABLE `sys_gen_backup`  (
  `backup_id` int NOT NULL AUTO_INCREMENT,
  `gen_id` int NULL DEFAULT NULL COMMENT '工具ID',
  `backup_status` int NULL DEFAULT 1 COMMENT '是否开启; 1:开启; 0:暂停',
  `backup_period` int NULL DEFAULT NULL COMMENT '保存周期, 天',
  `backup_strategy` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '策略',
  `backup_action` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '动作多个,分隔; CREATE, UPDATE,DELETE',
  `backup_ignore` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '忽略',
  `backup_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备份目录',
  `backup_driver` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备份驱动名称',
  `backup_filter` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '过滤条件',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`backup_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_backup
-- ----------------------------
INSERT INTO `sys_gen_backup` VALUES (1, 1, 1, 7, 'day', NULL, '', './', NULL, NULL, '2023-10-14 19:31:10', '2023-10-15 14:16:16');
INSERT INTO `sys_gen_backup` VALUES (2, 7, 1, 3, 'day', NULL, '', './', '\\Device\\NPF_{C2FCFFBF-F55B-442E-AFCE-434D23C48DE5}', '', '2023-10-15 14:36:59', '2023-10-15 14:44:37');

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
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_column
-- ----------------------------
INSERT INTO `sys_gen_column` VALUES (20, 2, 'backup_id', '主键', 'INT', 0, 10, 'Integer', 'backupId', '1', '1', '0', NULL, '1', '1', NULL, 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (21, 2, 'gen_id', '工具ID', 'INT', 0, 10, 'Integer', 'genId', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (22, 2, 'backup_status', '是否开启; 1:开启; 0:暂停', 'INT', 0, 10, 'Integer', 'backupStatus', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'radio', '', NULL);
INSERT INTO `sys_gen_column` VALUES (23, 2, 'backup_period', '保存周期, 天', 'INT', 0, 10, 'Integer', 'backupPeriod', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (24, 2, 'backup_strategy', '策略', 'VARCHAR', 0, 255, 'String', 'backupStrategy', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (25, 2, 'backup_ignore', '忽略', 'VARCHAR', 0, 255, 'String', 'backupIgnore', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (26, 2, 'backup_path', '备份目录', 'VARCHAR', 0, 255, 'String', 'backupPath', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (27, 2, 'create_time', '创建时间', 'DATETIME', 0, 19, 'LocalDateTime', 'createTime', NULL, '0', '1', NULL, NULL, NULL, NULL, 'EQ', 'datetime', '', NULL);
INSERT INTO `sys_gen_column` VALUES (28, 2, 'update_time', '更新时间', 'DATETIME', 0, 19, 'LocalDateTime', 'updateTime', NULL, '0', '1', NULL, NULL, NULL, NULL, 'EQ', 'datetime', '', NULL);
INSERT INTO `sys_gen_column` VALUES (29, 3, 'gen_id', '主键1', 'INT', 0, 10, 'Integer', 'genId', '1', '1', '0', NULL, '1', '1', NULL, 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (30, 3, 'gen_name', '名称', 'VARCHAR', 0, 255, 'String', 'genName', NULL, '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (31, 3, 'gen_url', 'url', 'VARCHAR', 0, 255, 'String', 'genUrl', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (32, 3, 'gen_user', '用户名', 'VARCHAR', 0, 255, 'String', 'genUser', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (33, 3, 'gen_password', '密码', 'VARCHAR', 0, 255, 'String', 'genPassword', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (34, 3, 'gen_driver', '驱动包', 'VARCHAR', 0, 255, 'String', 'genDriver', NULL, '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', NULL);
INSERT INTO `sys_gen_column` VALUES (37, 3, 'jjjjjjj', '', 'VARCHAR', NULL, 255, 'String', '', NULL, NULL, '0', '1', '1', '1', '1', 'EQ', 'input', '', NULL);

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
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_config
-- ----------------------------
INSERT INTO `sys_gen_config` VALUES (1, 'false', 'JDBC', 'NONE', 'https://archiva-maven-storage-prod.oss-cn-beijing.aliyuncs.com/repository/central/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar?Expires=1695912654&OSSAccessKeyId=LTAIfU51SusnnfCC&Signature=rZ7nYH3oWgUa6phzVnKdB%2FiKxuU%3D', 'com.mysql.cj.jdbc.Driver', '', '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"},{\"name\":\"日志\",\"url\":\"/ext/jdbc/log\"},{\"name\":\"UI\",\"url\":\"/ext/jdbc/board\"},{\"name\":\"文档\",\"url\":\"/ext/jdbc/doc\"},{\"name\":\"模板\",\"url\":\"/ext/jdbc/template\"}]', 'MYSQL', 1, '2023-09-28 21:56:40', '2023-10-09 13:11:17');
INSERT INTO `sys_gen_config` VALUES (2, 'false', 'JDBC', 'FILE', 'https://archiva-maven-storage-prod.oss-cn-beijing.aliyuncs.com/repository/gradle-plugin/org/xerial/sqlite-jdbc/3.8.9.1/sqlite-jdbc-3.8.9.1.jar?Expires=1695966895&OSSAccessKeyId=LTAIfU51SusnnfCC&Signature=TO%2BwBB7FbGbhS7uJ%2Fkj8Xjn9zPQ%3D', 'org.sqlite.JDBC', 'Z:\\works\\utils-support-parent-starter\\gen\\3\\driver\\sqlite-jdbc-3.8.9.1.jar', '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"},{\"name\":\"文档\",\"url\":\"/ext/jdbc/doc\"},{\"name\":\"模板\",\"url\":\"/ext/jdbc/template\"}]', 'SQLITE', 1, '2023-09-29 12:52:55', '2023-10-09 13:11:18');
INSERT INTO `sys_gen_config` VALUES (3, 'false', 'CALCITE', 'NONE', NULL, NULL, NULL, '[{\"name\":\"控制台\",\"url\":\"/ext/jdbc/console\"}]', 'CALCITE', 1, '2023-09-29 18:39:50', '2023-10-09 13:11:18');
INSERT INTO `sys_gen_config` VALUES (4, 'false', 'REDIS', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/redis/console\"}]', 'REDIS', 1, '2023-09-30 18:45:34', '2023-10-09 13:11:18');
INSERT INTO `sys_gen_config` VALUES (5, 'false', 'FTP', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/ftp/console\"}]', 'FTP', 1, '2023-10-02 15:18:19', '2023-10-09 13:11:19');
INSERT INTO `sys_gen_config` VALUES (6, 'false', 'SFTP', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/ftp/console\"}]', 'SFTP', 1, '2023-10-03 10:07:05', '2023-10-09 13:11:19');
INSERT INTO `sys_gen_config` VALUES (7, 'false', 'SSH', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/ssh/console\"}]', 'SSH', 1, '2023-10-09 15:04:33', '2023-10-09 15:05:12');
INSERT INTO `sys_gen_config` VALUES (8, 'false', 'ZOOKEEPER', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/zk/console\"}]', 'ZOOKEEPER', 1, '2023-10-10 08:58:54', '2023-10-10 09:03:08');
INSERT INTO `sys_gen_config` VALUES (9, NULL, 'NGINX', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/nginx/console\"}]', 'NGINX', 1, '2023-10-10 20:33:22', '2023-10-10 20:33:22');
INSERT INTO `sys_gen_config` VALUES (10, NULL, 'ELASTICSEARCH', 'REMOTE', NULL, NULL, NULL, '[{\"name\":\"UI\",\"url\":\"/ext/es/console\"}]', 'ELASTICSEARCH', 1, '2023-10-11 13:17:19', '2023-10-11 13:17:21');
INSERT INTO `sys_gen_config` VALUES (11, NULL, 'PCAP', 'NONE', NULL, NULL, NULL, '[{\"name\":\"控制台\",\"url\":\"/ext/pcap/console\"}]', 'PCAP', 1, '2023-10-15 13:49:51', '2023-10-15 13:49:54');

-- ----------------------------
-- Table structure for sys_gen_nginx_http_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_nginx_http_config`;
CREATE TABLE `sys_gen_nginx_http_config`  (
  `http_config_id` int NOT NULL AUTO_INCREMENT,
  `http_config_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置名称',
  `http_config_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置值',
  `http_config_status` int NULL DEFAULT 1 COMMENT '状态; 0禁用',
  `gen_id` int NULL DEFAULT NULL,
  PRIMARY KEY (`http_config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_nginx_http_config
-- ----------------------------

-- ----------------------------
-- Table structure for sys_gen_nginx_server
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_nginx_server`;
CREATE TABLE `sys_gen_nginx_server`  (
  `server_id` int NOT NULL AUTO_INCREMENT,
  `server_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转发类型; http/https, tcp, udp',
  `server_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '监听ip端口	',
  `server_domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '监听域名',
  `server_ssl` int NULL DEFAULT NULL COMMENT '是否开启ssl; 0：不开启',
  `server_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '额外参数',
  `server_status` int NULL DEFAULT NULL COMMENT '是否启用; 0:停用',
  `upstream_id` int NULL DEFAULT NULL COMMENT '负载均衡方式； tcp使用',
  `gen_id` int NULL DEFAULT NULL,
  PRIMARY KEY (`server_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_nginx_server
-- ----------------------------

-- ----------------------------
-- Table structure for sys_gen_nginx_server_item
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_nginx_server_item`;
CREATE TABLE `sys_gen_nginx_server_item`  (
  `server_item_id` int NOT NULL AUTO_INCREMENT,
  `server_id` int NULL DEFAULT NULL COMMENT '反向代理(server)id',
  `server_item_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '监控路径',
  `server_item_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '代理类型; dymaic: 动态http, static: 静态html,upstream: 负载均衡',
  `server_item_websocket` int NULL DEFAULT NULL COMMENT '是否开启ws; 0:不开启',
  `server_item_cors` int NULL DEFAULT NULL COMMENT '是否开启跨域; 0:不开启',
  `server_item_host` int NULL DEFAULT NULL COMMENT '是否设置$host;0:不开启',
  `server_item_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '额外参数',
  PRIMARY KEY (`server_item_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_nginx_server_item
-- ----------------------------

-- ----------------------------
-- Table structure for sys_gen_nginx_upstream
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_nginx_upstream`;
CREATE TABLE `sys_gen_nginx_upstream`  (
  `upstream_id` int NOT NULL AUTO_INCREMENT,
  `upstream_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '名称',
  `upstream_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `upstrean_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转发类型; http/https; tcp/udp',
  `upstream_strategy` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '策略; least_conn',
  `gen_id` int NULL DEFAULT NULL,
  PRIMARY KEY (`upstream_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_nginx_upstream
-- ----------------------------

-- ----------------------------
-- Table structure for sys_gen_nginx_upstream_item
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_nginx_upstream_item`;
CREATE TABLE `sys_gen_nginx_upstream_item`  (
  `upstream_item_id` int NOT NULL AUTO_INCREMENT,
  `upstream_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '配置名称',
  `upstream_item_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IP',
  `upstream_item_port` int NULL DEFAULT NULL COMMENT '端口',
  `upstream_item_weight` int NULL DEFAULT NULL COMMENT '权重',
  `upstream_item_failure` int NULL DEFAULT NULL COMMENT '最大失败次数',
  `upstream_item_conn` int NULL DEFAULT NULL COMMENT '最大连接数',
  `upstream_item_st` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '策略; down: 停用；backup: 备用;',
  PRIMARY KEY (`upstream_item_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_nginx_upstream_item
-- ----------------------------

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
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_gen_table
-- ----------------------------
INSERT INTO `sys_gen_table` VALUES (2, 'sys_gen_backup', '', '本地数据库', 1, 'SysGenBackup', NULL, 'com', 'genBackup', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `sys_gen_table` VALUES (3, 'sys_gen', '工具表', 'MYSQL数据库', 1, 'SysGen', NULL, 'com', 'gen', NULL, NULL, NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_gen_template
-- ----------------------------
DROP TABLE IF EXISTS `sys_gen_template`;
CREATE TABLE `sys_gen_template`  (
  `template_id` int NOT NULL AUTO_INCREMENT,
  `template_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板名称',
  `gen_id` int NULL DEFAULT NULL COMMENT '工具ID',
  `template_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板',
  `template_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '目录',
  `template_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板类型',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`template_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_gen_template
-- ----------------------------
INSERT INTO `sys_gen_template` VALUES (1, 'controller', NULL, 'package ${packageName}.controller;\n\nimport java.util.List;\n\nimport com.baomidou.mybatisplus.extension.plugins.pagination.Page;\nimport com.baomidou.mybatisplus.core.toolkit.Wrappers;\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.web.bind.annotation.GetMapping;\nimport org.springframework.validation.annotation.Validated;\nimport org.springframework.web.bind.annotation.PostMapping;\nimport org.springframework.web.bind.annotation.PutMapping;\nimport org.springframework.web.bind.annotation.DeleteMapping;\nimport org.springframework.web.bind.annotation.PathVariable;\nimport org.springframework.web.bind.annotation.RequestBody;\nimport org.springframework.web.bind.annotation.RequestMapping;\nimport org.springframework.web.bind.annotation.RestController;\nimport ${packageName}.entity.${ClassName};\nimport ${packageName}.query.PageQuery;\nimport ${packageName}.service.${ClassName}Service;\nimport com.chua.starter.common.support.result.ReturnResult;\nimport com.chua.starter.common.support.result.ReturnPageResult;\nimport com.chua.starter.mybatis.utils.PageResultUtils;\nimport com.chua.starter.common.support.result.Result;\nimport com.chua.starter.gen.support.validator.group.AddGroup;\n\nimport javax.annotation.Resource;\nimport java.util.Arrays;\n/**\n * ${functionName}\n *\n * @author ${author}\n * @since  ${datetime}\n */\n@RestController\n#if($version)\n@RequestMapping(\"/${version}/${businessName}\")\n#else\n@RequestMapping(\"/${moduleName}/${businessName}\")\n#end\npublic class ${ClassName}Controller {\n\n    @Resource\n    private ${ClassName}Service ${className}Service;\n\n    /**\n     * 分页查询${functionName}列表\n     * @param query 查询条件\n     */\n    @GetMapping(\"/page\")\n    public ReturnPageResult<${Entity}> queryPage(@RequestParam(value = \"page\", defaultValue = \"1\") Integer pageNum,\n                                         @RequestParam(value = \"pageSize\", defaultValue = \"10\") Integer pageSize) {\n        return PageResultUtils.ok(${className}Service.page(new Page<${Entity}>(pageNum, pageSize)));\n    }\n\n    /**\n     * 获取${functionName}详细信息\n     *\n     * @param ${pkColumn.colJavaField} 主键\n     */\n    @GetMapping(value = \"/info\")\n    public ReturnResult<${ClassName}> getInfo(${pkColumn.colJavaType} ${pkColumn.colJavaField}) {\n        return Result.success(${className}Service.getById(${pkColumn.colJavaField}));\n    }\n\n    /**\n     * 新增${functionName}\n     */\n    @PostMapping(\"/save\")\n    public Result<${ClassName}> save(@Validated(AddGroup.class) @RequestBody ${ClassName} ${className}) {\n        ${className}Service.save(${className});\n        return Result.success(${className});\n    }\n\n    /**\n     * 修改${functionName}\n     */\n    @PutMapping(\"/update\")\n    public Result<Boolean> update(@RequestBody ${ClassName} ${className}) {\n        return Result.success(${className}Service.updateById(${className}));\n    }\n\n    /**\n     * 删除${functionName}\n     *\n     * @param ${pkColumn.colJavaField}s 主键串\n     */\n    @DeleteMapping(\"/delete\")\n    public ReturnResult<Boolean> delete(String ${pkColumn.colJavaField}s) {\n        if(null == ${pkColumn.colJavaField}s) {\n            return Result.illegal(false, \"数据不存在\");\n        }\n        return Result.success(${className}Service.removeByIds(Arrays.asList(${pkColumn.colJavaField}s.split(\",\"))));\n    }\n}\n', '{}/controller/{}Controller.java', 'java', '2023-10-15 00:50:55', 'guest', '2023-10-15 00:59:12');
INSERT INTO `sys_gen_template` VALUES (2, 'entity', NULL, 'package ${packageName}.entity;\r\n\r\nimport com.baomidou.mybatisplus.annotation.*;\r\nimport lombok.Data;\r\nimport lombok.EqualsAndHashCode;\r\nimport java.time.LocalDateTime;\r\nimport java.io.Serializable;\r\nimport java.math.BigDecimal;\r\n\r\n#foreach ($import in $importList)\r\nimport ${import};\r\n#end\r\n#if($table.crud || $table.sub)\r\nimport com.tduck.cloud.common.entity.BaseEntity;\r\n#elseif($table.tree)\r\nimport com.tduck.cloud.common.entity.TreeEntity;\r\n#end\r\n\r\n/**\r\n * ${functionName}对象 ${tableName}\r\n *\r\n * @author ${author}\r\n * @since ${datetime}\r\n */\r\n#if($table.crud || $table.sub)\r\n    #set($Entity=\"BaseEntity\")\r\n#elseif($table.tree)\r\n    #set($Entity=\"TreeEntity<${ClassName}>\")\r\n#end\r\n@Data\r\n@EqualsAndHashCode(callSuper = false)\r\n@TableName(\"${tableName}\")\r\npublic class ${Entity} {\r\n\r\n    private static final long serialVersionUID = 1L;\r\n\r\n#foreach ($column in $columns)\r\n    #if(!$table.isSuperColumn($column.javaField))\r\n    /**\r\n    * $column.colColumnComment\r\n    */\r\n    #if($column.colJavaType==\'delFlag\')\r\n    @TableLogic\r\n    #end\r\n    #if($column.colJavaType==\'version\')\r\n    @Version\r\n    #end\r\n    #if($column.colIsPk)\r\n    @TableId(value = \"$column.colColumnName\")\r\n    #else\r\n    @TableField(value = \"$column.colColumnName\") \r\n    #end\r\n    #if($openSwagger)\r\n    @ApiModelProperty(\"$column.colColumnComment\")\r\n    #end\r\n    private $column.colJavaType $column.colJavaField;\r\n    #end\r\n#end\r\n\r\n}\r\n', '{}/entity/{}.java', 'java', NULL, NULL, NULL);
INSERT INTO `sys_gen_template` VALUES (3, 'mapper', NULL, 'package ${packageName}.mapper;\r\n\r\nimport com.baomidou.mybatisplus.core.mapper.BaseMapper;\r\nimport ${packageName}.entity.${ClassName};\r\n\r\n/**\r\n * ${functionName}Mapper接口\r\n *\r\n * @author ${author}\r\n * @since ${datetime}\r\n */\r\npublic interface ${ClassName}Mapper extends BaseMapper<${ClassName}> {\r\n\r\n}\r\n', '{}/mapper/{}Mapper.java', 'java', NULL, NULL, NULL);
INSERT INTO `sys_gen_template` VALUES (4, 'query', NULL, 'package ${packageName}.query;\n\nimport com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n#if($openSwagger)\nimport io.swagger.v3.oas.annotations.media.Schema;\n#end\nimport lombok.Data;\n\n/**\n* 基础分页请求对象\n*\n* @author ${author}\n* @since ${datetime}\n*/\n@Data\n#if($openSwagger)\n@Schema\n#end\npublic class PageQuery<T> {\n    /**\n     * 页码\n     */\n    #if($openSwagger)\n    @Schema(description = \"页码\", example = \"1\")\n    #end\n    private int pageNum = 1;\n    /**\n     * 每页记录数\n    */\n    #if($openSwagger)\n    @Schema(description = \"每页记录数\", example = \"10\")\n    #end\n    private int pageSize = 10;\n	/**\n	 * mybatis分页\n	 */\n    public Page<T> page() {\n        return new Page<>(pageNum, pageSize);\n    }\n}\n', '{}/query/PageQuery.java', 'java', NULL, 'guest', '2023-10-15 10:15:11');
INSERT INTO `sys_gen_template` VALUES (5, 'service', NULL, 'package ${packageName}.service;\r\n\r\nimport java.util.List;\r\n\r\nimport ${packageName}.entity.${ClassName};\r\nimport com.baomidou.mybatisplus.extension.service.IService;\r\n\r\n/**\r\n * ${functionName}Service接口\r\n *\r\n * @author ${author}\r\n * @since ${datetime}\r\n */\r\npublic interface ${ClassName}Service extends IService<${ClassName}> {\r\n\r\n}\r\n', '{}/service/{}Service.java', 'java', NULL, NULL, NULL);
INSERT INTO `sys_gen_template` VALUES (6, 'serviceImpl', NULL, 'package ${packageName}.service.impl;\n\nimport lombok.RequiredArgsConstructor;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.stereotype.Service;\n#if($table.sub)\nimport java.util.ArrayList;\n\nimport org.springframework.transaction.annotation.Transactional;\nimport ${packageName}.entity.${subClassName};\n#end\nimport com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;\nimport ${packageName}.mapper.${ClassName}Mapper;\nimport ${packageName}.entity.${ClassName};\nimport ${packageName}.service.${ClassName}Service;\n\n/**\n * ${functionName}Service业务层处理\n *\n * @author ${author}\n * @since ${datetime}\n */\n@Service\npublic class ${ClassName}ServiceImpl extends ServiceImpl< ${ClassName}Mapper, ${ClassName}> implements ${ClassName}Service {\n\n}\n', '{}/service/impl/{}ServiceImpl.java', 'java', NULL, 'guest', '2023-10-15 09:47:43');
INSERT INTO `sys_gen_template` VALUES (7, 'mapper', NULL, '<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<!DOCTYPE mapper\n        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n<mapper namespace=\"${packageName}.mapper.${ClassName}Mapper\">\n\n</mapper>\n', '{}/{}Mapper.xml', 'xml', NULL, 'guest', '2023-10-15 09:47:25');

SET FOREIGN_KEY_CHECKS = 1;
