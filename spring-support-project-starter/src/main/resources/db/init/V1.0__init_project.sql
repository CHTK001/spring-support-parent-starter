/*
 * 项目管理模块 - 数据库初始化脚本
 * 
 * 包含表:
 *   - sys_project: 项目管理表
 *   - sys_project_default: 项目默认配置表
 *   - sys_secret: 密钥管理表
 *   - sys_device: 设备管理表
 *   - sys_device_channel: 设备通道表
 *   - sys_device_org: 设备组织表
 *   - sys_device_camera_record: 设备记录表
 *   - sys_device_card_event: 门禁事件表
 *   - sys_device_message_event: 设备消息事件表
 *   - sys_sms_template: 短信模板表
 *   - sys_sms_log: 短信日志表
 *   - sys_email_template: 邮件模板表
 *   - sys_email_log: 邮件日志表
 *
 * 用于项目配置管理、设备接入、消息发送等功能
 *
 * @author System
 * @since 2025-01-01
 * @version 1.0
 */

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 项目管理表 (sys_project)
-- 用途: 管理各类第三方项目/服务的配置信息
-- =====================================================
DROP TABLE IF EXISTS `sys_project`;
CREATE TABLE `sys_project` (
    `sys_project_id` int NOT NULL AUTO_INCREMENT COMMENT '项目ID - 主键自增',
    `sys_project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '项目名称 - 项目的显示名称',
    `sys_project_icon` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目图标 - 项目图标URL地址',
    `sys_project_group` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '项目分组 - 项目所属分组，用于分类管理',
    `sys_project_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '项目编码 - 项目唯一标识码',
    `sys_project_app_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'AppID - 应用标识ID，用于API认证',
    `sys_project_app_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'AppSecret - 应用密钥，用于API认证',
    `sys_project_app_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'AppKey - 应用访问密钥',
    `sys_project_sign` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签名 - 短信/接口签名',
    `sys_project_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `sys_project_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
    `sys_project_sign_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '项目标识编码 - 海康等平台的项目标识',
    `sys_project_sort` int NULL DEFAULT 0 COMMENT '排序号 - 项目在列表中的显示顺序',
    `sys_project_cdn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'CDN地址 - 内容分发网络地址',
    `sys_project_endpoint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '端点地址 - API服务端点URL',
    `sys_project_local_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本地路径 - 本地文件存储路径',
    `sys_project_api_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API地址 - 接口服务基础地址',
    `sys_project_vender` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '厂家编码 - 前端固定值',
    `sys_project_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '项目描述 - 项目用途说明',
    `sys_project_function` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '功能类型 - 前端固定值，多个用逗号分隔',
    `sys_project_smtp_host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SMTP主机 - 邮件服务器主机地址',
    `sys_project_smtp_port` int NULL DEFAULT NULL COMMENT 'SMTP端口 - 邮件服务器端口号',
    `sys_project_smtp_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SMTP密码 - 邮件服务器授权码',
    `sys_project_smtp_from` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SMTP发送账号 - 发件人邮箱地址',
    `sys_project_location_temp_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '临时文件路径 - 临时文件存储目录',
    `sys_project_location_oss_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'OSS地址 - 对象存储服务地址',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_project_id`) USING BTREE,
    UNIQUE INDEX `uk_project_code` (`sys_project_code`) USING BTREE COMMENT '项目编码唯一索引',
    INDEX `idx_project_status` (`sys_project_status`) USING BTREE COMMENT '状态索引',
    INDEX `idx_project_vender` (`sys_project_vender`) USING BTREE COMMENT '厂家索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '项目管理表 - 管理各类第三方项目/服务的配置信息' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 2. 项目默认配置表 (sys_project_default)
-- 用途: 配置各功能类型的默认项目
-- =====================================================
DROP TABLE IF EXISTS `sys_project_default`;
CREATE TABLE `sys_project_default` (
    `sys_project_default_id` int NOT NULL AUTO_INCREMENT COMMENT '配置ID - 主键自增',
    `sys_project_default_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '功能类型 - 前端固定值',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表，该功能类型的默认项目',
    PRIMARY KEY (`sys_project_default_id`) USING BTREE,
    UNIQUE INDEX `uk_default_type` (`sys_project_default_type`) USING BTREE COMMENT '功能类型唯一索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '项目默认配置表 - 配置各功能类型的默认项目' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 3. 密钥管理表 (sys_secret)
-- 用途: 独立管理各类服务的密钥信息
-- =====================================================
DROP TABLE IF EXISTS `sys_secret`;
CREATE TABLE `sys_secret` (
    `sys_secret_id` int NOT NULL AUTO_INCREMENT COMMENT '密钥ID - 主键自增',
    `sys_secret_group` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密钥分组 - 密钥所属分组',
    `sys_secret_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '密钥编码 - 密钥唯一标识码',
    `sys_secret_app_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'AppID',
    `sys_secret_app_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'AppSecret',
    `sys_secret_sign` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签名',
    `sys_secret_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `sys_secret_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
    `sys_secret_cdn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'CDN地址',
    `sys_secret_endpoint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '端点地址',
    `sys_secret_api_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API地址',
    `sys_secret_remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
    `sys_secret_function` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '功能类型 - SMS/EMAIL等',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_secret_id`) USING BTREE,
    INDEX `idx_secret_code` (`sys_secret_code`) USING BTREE COMMENT '密钥编码索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '密钥管理表 - 独立管理各类服务的密钥信息' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 4. 设备管理表 (sys_device)
-- 用途: 管理摄像头、门禁等IoT设备
-- =====================================================
DROP TABLE IF EXISTS `sys_device`;
CREATE TABLE `sys_device` (
    `sys_device_id` int NOT NULL AUTO_INCREMENT COMMENT '设备ID - 主键自增',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表',
    `sys_device_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备名称',
    `sys_device_serial_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备序列号 - 设备唯一标识',
    `sys_device_org_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组织编码 - 所属组织的编码',
    `sys_device_org_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组织名称',
    `sys_device_channel_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '通道名称',
    `sys_device_channel_number` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '通道号',
    `sys_device_channel_count` int NULL DEFAULT NULL COMMENT '通道数量',
    `sys_device_status` int NULL DEFAULT 1 COMMENT '设备状态 - 0:禁用, 1:启用',
    `sys_device_position` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '安装位置 - 设备物理安装位置描述',
    `sys_device_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '固件版本',
    `sys_device_resource_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '资源类型',
    `sys_device_account` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备账号 - 设备登录账号',
    `sys_device_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备密码 - 设备登录密码(加密存储)',
    `sys_device_rtsp` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'RTSP地址 - 视频流推流地址',
    `sys_device_rtsp_webrtc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'WebRTC地址 - RTSP转WebRTC的地址',
    `sys_device_channels` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '通道列表 - JSON格式的通道信息',
    `sys_device_owner` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属者编码 - 设备所有者唯一标识',
    `sys_device_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备描述',
    `sys_device_online` int NULL DEFAULT 0 COMMENT '在线状态 - 0:离线, 1:在线',
    `sys_device_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备类型 - 前端固定值',
    `sys_device_latitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '纬度 - GPS纬度坐标',
    `sys_device_longitude` decimal(10, 6) NULL DEFAULT NULL COMMENT '经度 - GPS经度坐标',
    `sys_device_net_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '网络地址 - 设备IP地址',
    `sys_device_net_physical_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'MAC地址 - 设备物理地址',
    `sys_device_validate_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '验证码 - 设备绑定验证码',
    `sys_device_main_subtype` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主码流编码 - 高清主码流标识',
    `sys_device_sub_subtype` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '子码流编码 - 标清子码流标识',
    `sys_dept_id` int NULL DEFAULT NULL COMMENT '机构ID - 关联组织机构表',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_device_id`) USING BTREE,
    UNIQUE INDEX `uk_serial_number` (`sys_device_serial_number`) USING BTREE COMMENT '序列号唯一索引',
    INDEX `idx_project_id` (`sys_project_id`) USING BTREE COMMENT '项目ID索引',
    INDEX `idx_device_type` (`sys_device_type`) USING BTREE COMMENT '设备类型索引',
    INDEX `idx_device_online` (`sys_device_online`) USING BTREE COMMENT '在线状态索引',
    INDEX `idx_dept_id` (`sys_dept_id`) USING BTREE COMMENT '机构ID索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '设备管理表 - 管理摄像头、门禁等IoT设备' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 5. 设备通道表 (sys_device_channel)
-- 用途: 管理设备的视频/报警通道
-- =====================================================
DROP TABLE IF EXISTS `sys_device_channel`;
CREATE TABLE `sys_device_channel` (
    `sys_device_channel_id` int NOT NULL AUTO_INCREMENT COMMENT '通道ID - 主键自增',
    `sys_device_channel_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '通道名称',
    `sys_device_channel_no` int NULL DEFAULT NULL COMMENT '通道号',
    `sys_device_serial_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备序列号 - 关联设备表',
    `sys_device_channel_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '通道类型 - 10300:视频通道, 10302:报警输入',
    `sys_device_channel_status` int NULL DEFAULT 1 COMMENT '通道状态 - -1:未上报, 0:离线, 1:在线',
    `sys_device_channel_use` int NULL DEFAULT 1 COMMENT '通道启用状态 - 0:禁用, 1:启用',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_device_channel_id`) USING BTREE,
    INDEX `idx_serial_number` (`sys_device_serial_number`) USING BTREE COMMENT '设备序列号索引',
    INDEX `idx_channel_status` (`sys_device_channel_status`) USING BTREE COMMENT '通道状态索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '设备通道表 - 管理设备的视频/报警通道' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 6. 设备组织表 (sys_device_org)
-- 用途: 管理设备的组织架构
-- =====================================================
DROP TABLE IF EXISTS `sys_device_org`;
CREATE TABLE `sys_device_org` (
    `sys_device_org_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组织ID - 主键',
    `sys_device_org_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组织名称',
    `sys_device_org_pid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '上级组织ID',
    `sys_device_org_tree_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组织树路径',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_device_org_id`) USING BTREE,
    INDEX `idx_org_pid` (`sys_device_org_pid`) USING BTREE COMMENT '上级组织索引',
    INDEX `idx_project_id` (`sys_project_id`) USING BTREE COMMENT '项目ID索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '设备组织表 - 管理设备的组织架构' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 7. 设备记录表 (sys_device_camera_record)
-- 用途: 记录设备操作历史
-- =====================================================
DROP TABLE IF EXISTS `sys_device_camera_record`;
CREATE TABLE `sys_device_camera_record` (
    `sys_device_camera_record_id` int NOT NULL AUTO_INCREMENT COMMENT '记录ID - 主键自增',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_device_camera_record_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '设备记录表 - 记录摄像头操作历史' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 8. 门禁事件表 (sys_device_card_event)
-- 用途: 记录门禁刷卡事件
-- =====================================================
DROP TABLE IF EXISTS `sys_device_card_event`;
CREATE TABLE `sys_device_card_event` (
    `sys_device_card_event_id` int NOT NULL AUTO_INCREMENT COMMENT '事件ID - 主键自增',
    `sys_device_card_event_time` datetime NULL DEFAULT NULL COMMENT '事件时间',
    `sys_device_serial_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备序列号',
    `sys_device_card_event_person_involved` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '涉事人ID',
    `sys_device_card_event_person_involved_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '涉事人姓名',
    `sys_device_card_event_major_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主事件类型',
    `sys_device_card_event_minor_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '次事件类型',
    `sys_device_card_event_message_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息ID',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_device_card_event_id`) USING BTREE,
    INDEX `idx_event_time` (`sys_device_card_event_time`) USING BTREE COMMENT '事件时间索引',
    INDEX `idx_serial_number` (`sys_device_serial_number`) USING BTREE COMMENT '设备序列号索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '门禁事件表 - 记录门禁刷卡事件' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 9. 设备消息事件表 (sys_device_message_event)
-- 用途: 记录设备上报的消息事件
-- =====================================================
DROP TABLE IF EXISTS `sys_device_message_event`;
CREATE TABLE `sys_device_message_event` (
    `sys_device_message_event_id` int NOT NULL AUTO_INCREMENT COMMENT '事件ID - 主键自增',
    `sys_device_message_event_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件类型',
    `sys_device_message_event_event_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件唯一ID',
    `sys_device_message_event_data_time` datetime NULL DEFAULT NULL COMMENT '事件数据时间',
    `sys_device_message_event_employee_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '人员工号',
    `sys_device_message_event_employee_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '人员姓名',
    `sys_device_message_event_card_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '卡号',
    `sys_device_serial_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备序列号',
    `sys_device_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设备名称',
    `sys_device_message_event_channel_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '触发通道号',
    `sys_device_message_event_channel_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '触发通道名称',
    `sys_device_message_event_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '触发位置',
    `sys_device_message_event_card_reader_kind` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '读卡器类型 - 1:IC, 2:身份证, 3:二维码',
    `sys_device_message_event_card_reader_no` int NULL DEFAULT NULL COMMENT '读卡器编号',
    `sys_device_message_event_door_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '门编号',
    `sys_device_message_event_event_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息事件类型',
    `sys_device_message_event_event_type_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息事件类型名称',
    `sys_device_message_event_event_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件码',
    `sys_device_message_event_event_description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件描述',
    `sys_device_message_event_event_source` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件来源',
    `sys_device_message_event_event_picture_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '事件图片URL',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_device_message_event_id`) USING BTREE,
    INDEX `idx_data_time` (`sys_device_message_event_data_time`) USING BTREE COMMENT '事件时间索引',
    INDEX `idx_serial_number` (`sys_device_serial_number`) USING BTREE COMMENT '设备序列号索引',
    INDEX `idx_event_type` (`sys_device_message_event_type`) USING BTREE COMMENT '事件类型索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
COMMENT = '设备消息事件表 - 记录设备上报的消息事件' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 10. 短信模板表 (sys_sms_template)
-- 用途: 管理短信发送模板
-- =====================================================
DROP TABLE IF EXISTS `sys_sms_template`;
CREATE TABLE `sys_sms_template` (
    `sys_sms_template_id` int NOT NULL AUTO_INCREMENT COMMENT '模板ID - 主键自增',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表',
    `sys_project_type` int NULL DEFAULT NULL COMMENT '类别 - 前端固定值',
    `sys_sms_template_sort` int NULL DEFAULT 0 COMMENT '模板优先级',
    `sys_sms_template_category` int NULL DEFAULT NULL COMMENT '子类型 - 前端固定值(短信通知/验证码等)',
    `sys_sms_template_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板名称',
    `sys_sms_template_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板编码 - 短信平台的模板ID',
    `sys_sms_template_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板内容 - 短信模板内容，变量用${var}表示',
    `sys_sms_template_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `sys_sms_template_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
    `sys_sms_template_type` int NULL DEFAULT 0 COMMENT '模板类型 - 0:系统, 1:用户, 2:其它',
    `sys_sms_template_default` int NULL DEFAULT 0 COMMENT '是否默认 - 0:否, 1:是',
    `sys_sms_template_approve` int NULL DEFAULT 0 COMMENT '审批状态 - 0:未审批, 1:审批中, 2:已通过, 3:未通过',
    `sys_sms_template_approve_reject` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '拒绝原因',
    `sys_sms_template_approve_create_by` int NULL DEFAULT NULL COMMENT '审批人ID',
    `sys_sms_template_disabled` int NULL DEFAULT 0 COMMENT '编辑状态 - 0:可编辑, 1:不可编辑',
    `sys_sms_template_origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源',
    `sys_sms_template_origin_uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源UUID',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_sms_template_id`) USING BTREE,
    INDEX `idx_project_id` (`sys_project_id`) USING BTREE COMMENT '项目ID索引',
    INDEX `idx_template_code` (`sys_sms_template_code`) USING BTREE COMMENT '模板编码索引',
    INDEX `idx_template_status` (`sys_sms_template_status`) USING BTREE COMMENT '状态索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '短信模板表 - 管理短信发送模板' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 11. 短信日志表 (sys_sms_log)
-- 用途: 记录短信发送历史
-- =====================================================
DROP TABLE IF EXISTS `sys_sms_log`;
CREATE TABLE `sys_sms_log` (
    `sys_sms_log_id` int NOT NULL AUTO_INCREMENT COMMENT '日志ID - 主键自增',
    `sys_sms_log_target` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '接收人 - 手机号码',
    `sys_sms_log_template_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板ID - 使用的模板编码',
    `sys_sms_log_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板参数 - JSON格式的模板变量',
    `sys_sms_log_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '发送内容 - 最终发送的短信内容',
    `sys_sms_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '耗时 - 发送耗时(毫秒)',
    `sys_sms_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '返回码 - 短信平台返回的状态码',
    `sys_sms_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '返回消息 - 短信平台返回的消息',
    `sys_sms_app_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '短信厂家 - 使用的短信服务商',
    `sys_sms_app_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '短信AppID',
    `sys_sms_app_sign` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '短信签名',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_sms_log_id`) USING BTREE,
    INDEX `idx_target` (`sys_sms_log_target`) USING BTREE COMMENT '接收人索引',
    INDEX `idx_project_id` (`sys_project_id`) USING BTREE COMMENT '项目ID索引',
    INDEX `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '短信日志表 - 记录短信发送历史' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 12. 邮件模板表 (sys_email_template)
-- 用途: 管理邮件发送模板
-- =====================================================
DROP TABLE IF EXISTS `sys_email_template`;
CREATE TABLE `sys_email_template` (
    `sys_email_template_id` int NOT NULL AUTO_INCREMENT COMMENT '模板ID - 主键自增',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表',
    `sys_project_type` int NULL DEFAULT NULL COMMENT '类别 - 前端固定值',
    `sys_email_template_sort` int NULL DEFAULT 0 COMMENT '模板优先级',
    `sys_email_template_category` int NULL DEFAULT NULL COMMENT '子类型 - 前端固定值',
    `sys_email_template_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板名称',
    `sys_email_template_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板编码 - 模板唯一标识',
    `sys_email_template_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板内容 - HTML格式的邮件模板',
    `sys_email_template_delete` int NULL DEFAULT 0 COMMENT '是否删除 - 0:未删除, 1:已删除',
    `sys_email_template_status` int NULL DEFAULT 1 COMMENT '是否启用 - 0:禁用, 1:启用',
    `sys_email_template_type` int NULL DEFAULT 0 COMMENT '模板类型 - 0:系统, 1:用户, 2:其它',
    `sys_email_template_default` int NULL DEFAULT 0 COMMENT '是否默认 - 0:否, 1:是',
    `sys_email_template_approve` int NULL DEFAULT 0 COMMENT '审批状态 - 0:未审批, 1:审批中, 2:已通过, 3:未通过',
    `sys_email_template_approve_reject` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '拒绝原因',
    `sys_email_template_approve_create_by` int NULL DEFAULT NULL COMMENT '审批人ID',
    `sys_email_template_disabled` int NULL DEFAULT 0 COMMENT '编辑状态 - 0:可编辑, 1:不可编辑',
    `sys_email_template_origin` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源',
    `sys_email_template_origin_uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源UUID',
    `sys_email_template_callback_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '回调地址 - 邮件状态回调URL',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_email_template_id`) USING BTREE,
    INDEX `idx_project_id` (`sys_project_id`) USING BTREE COMMENT '项目ID索引',
    INDEX `idx_template_code` (`sys_email_template_code`) USING BTREE COMMENT '模板编码索引',
    INDEX `idx_template_status` (`sys_email_template_status`) USING BTREE COMMENT '状态索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '邮件模板表 - 管理邮件发送模板' ROW_FORMAT = DYNAMIC;

-- =====================================================
-- 13. 邮件日志表 (sys_email_log)
-- 用途: 记录邮件发送历史
-- =====================================================
DROP TABLE IF EXISTS `sys_email_log`;
CREATE TABLE `sys_email_log` (
    `sys_email_log_id` int NOT NULL AUTO_INCREMENT COMMENT '日志ID - 主键自增',
    `sys_email_log_target` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '接收人 - 收件人邮箱地址',
    `sys_email_log_template_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板ID',
    `sys_email_log_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模板参数 - JSON格式的模板变量',
    `sys_email_log_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '发送内容 - 最终发送的邮件内容',
    `sys_email_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '耗时 - 发送耗时(毫秒)',
    `sys_email_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '返回码 - SUCCESS/FAILURE',
    `sys_email_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '返回消息 - 错误信息',
    `sys_email_app_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮件厂家',
    `sys_email_app_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮件密钥',
    `sys_email_app_sign` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '签名',
    `sys_email_read` int NULL DEFAULT 0 COMMENT '是否已读 - 0:未读, 1:已读',
    `sys_project_id` int NULL DEFAULT NULL COMMENT '项目ID - 关联sys_project表',
    `create_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人姓名',
    `create_by` int NULL DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
    `update_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人姓名',
    `update_by` int NULL DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`sys_email_log_id`) USING BTREE,
    INDEX `idx_target` (`sys_email_log_target`) USING BTREE COMMENT '接收人索引',
    INDEX `idx_project_id` (`sys_project_id`) USING BTREE COMMENT '项目ID索引',
    INDEX `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci 
COMMENT = '邮件日志表 - 记录邮件发送历史' ROW_FORMAT = DYNAMIC;


SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- Seed Data
-- =====================================================
-- =====================================================
-- Project 模块初始化数据脚本
-- 版本: V1.0
-- 说明: 包含邮件日志/模板、项目默认映射、密钥等基础数据
-- 执行方式: 异步执行（虚拟线程）
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 初始数据 - 邮件日志表 (sys_email_log)
-- =====================================================
INSERT INTO `sys_email_log` VALUES (6, 'yemengzhidiandi@163.com', '1', '{}', '<p><strong>测试</strong></p>', 2392.00, '00000', NULL, 'NEI_BU_XI_TONG', NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `sys_email_log` VALUES (7, 'yemengzhidiandi@163.com', '1', '{}', '<p><strong>测试</strong></p>', 1348.00, 'SUCCESS', NULL, 'NEI_BU_XI_TONG', NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, 2);
INSERT INTO `sys_email_log` VALUES (8, 'yemengzhidiandi@163.com', '1', NULL, '<p><strong>测试</strong></p>', 893.00, 'SUCCESS', NULL, 'NEI_BU_XI_TONG', NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, 2);
INSERT INTO `sys_email_log` VALUES (9, 'request.getParams()', '1', NULL, '<p><strong>测试</strong></p>', 40.00, 'FAILURE', NULL, 'NEI_BU_XI_TONG', NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, 2);
INSERT INTO `sys_email_log` VALUES (10, 'yemengzhidiandi@163.com', '1', NULL, '<p><strong>测试</strong></p>', 917.00, 'SUCCESS', NULL, 'NEI_BU_XI_TONG', NULL, NULL, 1, NULL, NULL, NULL, NULL, NULL, NULL, 2);

-- =====================================================
-- 初始数据 - 邮件模板 (sys_email_template)
-- =====================================================
INSERT INTO `sys_email_template` VALUES (1, 2, NULL, 1, 4, '测试', '111', '<p><strong>测试</strong></p>', NULL, 1, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 'http://127.0.0.1:18170/system/api/v2/project/template/email/callback', 'sa', 1, '2025-01-27 20:00:31', '2025-01-27 20:04:46', 'sa', 1);

-- =====================================================
-- 初始数据 - 默认功能映射 (sys_project_default)
-- =====================================================
INSERT INTO `sys_project_default` VALUES (30, '6', 3);
INSERT INTO `sys_project_default` VALUES (33, '16', 2);
INSERT INTO `sys_project_default` VALUES (37, '14', 4);
INSERT INTO `sys_project_default` VALUES (46, '13', 1);
INSERT INTO `sys_project_default` VALUES (47, '20', 16);


-- 初始数据 - 密钥 (sys_secret)
-- =====================================================
INSERT INTO `sys_secret` VALUES (1, '阿里系', 'ALI', 'LTAI5tKQQnvCS3yEjGxjHmCd', 'Pm45AutVaPLqWCOUNeZ7gEQBTYJQAh', '犀鸟校园', 0, 1, NULL, NULL, NULL, NULL, 'SMS', 'sa', 1, '2024-09-06 13:32:21', '2024-09-06 13:36:12', 'sa', 1);

SET FOREIGN_KEY_CHECKS = 1;
