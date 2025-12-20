/*
 定时任务日志详情和备份表

 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27)
 File Encoding         : 65001

 Date: 19/12/2024
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for monitor_job_log_detail
-- 存储任务执行过程中的详细日志记录
-- ----------------------------
DROP TABLE IF EXISTS `monitor_job_log_detail`;
CREATE TABLE `monitor_job_log_detail` (
    `job_log_detail_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志详情ID',
    `job_log_id` int(11) NULL DEFAULT NULL COMMENT '关联的任务日志ID',
    `job_id` int(11) NULL DEFAULT NULL COMMENT '任务ID',
    `job_log_detail_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'INFO' COMMENT '日志级别: DEBUG/INFO/WARN/ERROR',
    `job_log_detail_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '日志内容',
    `job_log_detail_time` datetime NULL DEFAULT NULL COMMENT '日志时间戳',
    `job_log_detail_phase` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '执行阶段: START/RUNNING/END',
    `job_log_detail_progress` int(11) NULL DEFAULT NULL COMMENT '执行进度(0-100)',
    `job_log_detail_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日志文件路径',
    `job_log_detail_handler` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '执行的方法/Handler名',
    `job_log_detail_profile` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '执行环境/Profile',
    `job_log_detail_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '执行地址',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`job_log_detail_id`) USING BTREE,
    KEY `idx_job_log_id` (`job_log_id`) USING BTREE,
    KEY `idx_job_id` (`job_id`) USING BTREE,
    KEY `idx_detail_time` (`job_log_detail_time`) USING BTREE,
    KEY `idx_level` (`job_log_detail_level`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务日志详情表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for monitor_job_log_backup
-- 记录日志压缩备份的历史记录
-- ----------------------------
DROP TABLE IF EXISTS `monitor_job_log_backup`;
CREATE TABLE `monitor_job_log_backup` (
    `job_log_backup_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '备份记录ID',
    `job_log_backup_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备份文件名',
    `job_log_backup_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备份文件路径',
    `job_log_backup_file_size` bigint(20) NULL DEFAULT NULL COMMENT '备份文件大小(字节)',
    `job_log_backup_count` bigint(20) NULL DEFAULT NULL COMMENT '备份日志条数',
    `job_log_backup_start_date` date NULL DEFAULT NULL COMMENT '备份日志开始日期',
    `job_log_backup_end_date` date NULL DEFAULT NULL COMMENT '备份日志结束日期',
    `job_log_backup_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'RUNNING' COMMENT '备份状态: RUNNING/SUCCESS/FAILED',
    `job_log_backup_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT '备份类型: AUTO/MANUAL',
    `job_log_backup_start_time` datetime NULL DEFAULT NULL COMMENT '备份开始时间',
    `job_log_backup_end_time` datetime NULL DEFAULT NULL COMMENT '备份完成时间',
    `job_log_backup_cost` bigint(20) NULL DEFAULT NULL COMMENT '备份耗时(毫秒)',
    `job_log_backup_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备份消息/错误信息',
    `job_log_backup_compress_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ZIP' COMMENT '压缩格式: ZIP/GZIP/TAR',
    `job_log_backup_cleaned` tinyint(1) NULL DEFAULT 0 COMMENT '是否已删除原始日志: 0否 1是',
    `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`job_log_backup_id`) USING BTREE,
    KEY `idx_backup_status` (`job_log_backup_status`) USING BTREE,
    KEY `idx_backup_type` (`job_log_backup_type`) USING BTREE,
    KEY `idx_backup_start_time` (`job_log_backup_start_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务日志备份记录表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
