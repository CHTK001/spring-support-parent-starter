SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `sys_message` (
    `sys_message_id` int NOT NULL AUTO_INCREMENT COMMENT '消息ID - 主键自增',
    `sys_message_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息标题 - 消息的简短标题',
    `sys_message_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '消息内容 - 消息的详细内容，支持富文本',
    `sys_message_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'system' COMMENT '消息类型 - system:系统通知, notice:公告, message:私信, warning:警告, info:信息',
    `sys_message_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'normal' COMMENT '消息级别 - low:低, normal:普通, high:高, urgent:紧急',
    `sys_message_sender_id` int NULL DEFAULT 0 COMMENT '发送者ID - 0表示系统自动发送',
    `sys_message_sender_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送者名称',
    `sys_message_receiver_id` int NULL DEFAULT 0 COMMENT '接收者ID - 0表示广播给所有人',
    `sys_message_receiver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '接收者名称',
    `sys_message_read` tinyint NULL DEFAULT 0 COMMENT '是否已读 - 0:未读, 1:已读',
    `sys_message_read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间 - 消息被阅读的时间',
    `sys_message_send_time` datetime NULL DEFAULT NULL COMMENT '发送时间 - 消息发送的时间',
    `sys_message_expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间 - 消息过期时间，NULL表示永不过期',
    `sys_message_biz_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '业务类型 - 关联的业务类型标识',
    `sys_message_biz_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '业务ID - 关联的业务数据ID',
    `sys_message_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '跳转链接 - 点击消息后跳转的URL',
    `sys_message_extra` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '扩展数据 - 其他扩展数据(JSON格式)',
    `sys_message_status` tinyint NULL DEFAULT 1 COMMENT '消息状态 - 0:草稿, 1:已发送, 2:已撤回',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`sys_message_id`) USING BTREE,
    INDEX `idx_receiver_id` (`sys_message_receiver_id`) USING BTREE COMMENT '接收者索引',
    INDEX `idx_sender_id` (`sys_message_sender_id`) USING BTREE COMMENT '发送者索引',
    INDEX `idx_type` (`sys_message_type`) USING BTREE COMMENT '消息类型索引',
    INDEX `idx_status` (`sys_message_status`) USING BTREE COMMENT '状态索引',
    INDEX `idx_read` (`sys_message_read`) USING BTREE COMMENT '已读状态索引',
    INDEX `idx_send_time` (`sys_message_send_time`) USING BTREE COMMENT '发送时间索引',
    INDEX `idx_expire_time` (`sys_message_expire_time`) USING BTREE COMMENT '过期时间索引',
    INDEX `idx_biz` (`sys_message_biz_type`, `sys_message_biz_id`) USING BTREE COMMENT '业务关联索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '系统消息表 - 存储系统消息、通知、公告' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_message_history` (
    `sys_message_history_id` int NOT NULL AUTO_INCREMENT COMMENT '历史记录ID - 主键自增',
    `sys_message_history_message_id` int NULL DEFAULT NULL COMMENT '原消息ID - 关联原sys_message表的消息ID',
    `sys_message_history_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息标题',
    `sys_message_history_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '消息内容',
    `sys_message_history_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息类型',
    `sys_message_history_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息级别',
    `sys_message_history_sender_id` int NULL DEFAULT NULL COMMENT '发送者ID',
    `sys_message_history_sender_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发送者名称',
    `sys_message_history_receiver_id` int NULL DEFAULT NULL COMMENT '接收者ID',
    `sys_message_history_receiver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '接收者名称',
    `sys_message_history_send_time` datetime NULL DEFAULT NULL COMMENT '发送时间',
    `sys_message_history_read_time` datetime NULL DEFAULT NULL COMMENT '阅读时间',
    `sys_message_history_biz_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '业务类型',
    `sys_message_history_biz_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '业务ID',
    `sys_message_history_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '跳转链接',
    `sys_message_history_extra` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '扩展数据',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`sys_message_history_id`) USING BTREE,
    INDEX `idx_receiver_id` (`sys_message_history_receiver_id`) USING BTREE COMMENT '接收者索引',
    INDEX `idx_read_time` (`sys_message_history_read_time`) USING BTREE COMMENT '阅读时间索引',
    INDEX `idx_message_id` (`sys_message_history_message_id`) USING BTREE COMMENT '原消息ID索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '系统消息历史表 - 归档已读的历史消息' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
