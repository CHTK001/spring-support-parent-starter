-- WebRTC模块数据库初始化脚本
-- 版本: V1.0
-- 描述: 创建WebRTC相关的数据表

-- 创建WebRTC房间表
CREATE TABLE IF NOT EXISTS `webrtc_room`
(
    `webrtc_room_id`                 VARCHAR(32) NOT NULL COMMENT '房间ID',
    `webrtc_room_number`             BIGINT      NOT NULL COMMENT '房间号',
    `webrtc_room_name`               VARCHAR(100)         DEFAULT NULL COMMENT '房间名称',
    `webrtc_room_description`        VARCHAR(500)         DEFAULT NULL COMMENT '房间描述',
    `webrtc_room_creator_id`         VARCHAR(32) NOT NULL COMMENT '创建人ID',
    `webrtc_room_status`             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '房间状态：ACTIVE-活跃，INACTIVE-非活跃，CLOSED-已关闭',
    `webrtc_room_type`               VARCHAR(20)          DEFAULT 'PUBLIC' COMMENT '房间类型：PUBLIC-公开，PRIVATE-私有',
    `webrtc_room_password`           VARCHAR(100)         DEFAULT NULL COMMENT '房间密码',
    `webrtc_room_max_users`          INT         NOT NULL DEFAULT 10 COMMENT '最大用户数',
    `webrtc_room_current_users`      INT         NOT NULL DEFAULT 0 COMMENT '当前用户数',
    `webrtc_room_allow_recording`    BOOLEAN              DEFAULT FALSE COMMENT '是否允许录制',
    `webrtc_room_allow_screen_share` BOOLEAN              DEFAULT TRUE COMMENT '是否允许屏幕共享',
    `webrtc_room_quality`            VARCHAR(20)          DEFAULT 'HD' COMMENT '视频质量：SD-标清，HD-高清，FHD-全高清',
    `webrtc_room_create_time`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `webrtc_room_last_active_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    `webrtc_room_close_time`         DATETIME             DEFAULT NULL COMMENT '关闭时间',
    `webrtc_room_update_time`        DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `webrtc_room_deleted`            BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '是否已删除',
    `webrtc_room_remark`             VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`webrtc_room_id`),
    UNIQUE KEY `uk_webrtc_room_number` (`webrtc_room_number`),
    KEY `idx_webrtc_room_creator_id` (`webrtc_room_creator_id`),
    KEY `idx_webrtc_room_status` (`webrtc_room_status`),
    KEY `idx_webrtc_room_create_time` (`webrtc_room_create_time`),
    KEY `idx_webrtc_room_last_active_time` (`webrtc_room_last_active_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WebRTC房间表';

-- 创建WebRTC用户表
CREATE TABLE IF NOT EXISTS `webrtc_user`
(
    `webrtc_user_id`                    VARCHAR(32) NOT NULL COMMENT '用户ID',
    `webrtc_user_name`                  VARCHAR(50) NOT NULL COMMENT '用户名',
    `webrtc_user_nickname`              VARCHAR(50)          DEFAULT NULL COMMENT '用户昵称',
    `webrtc_user_avatar`                VARCHAR(500)         DEFAULT NULL COMMENT '用户头像URL',
    `webrtc_user_email`                 VARCHAR(100)         DEFAULT NULL COMMENT '用户邮箱',
    `webrtc_user_phone`                 VARCHAR(20)          DEFAULT NULL COMMENT '用户手机号',
    `webrtc_user_status`                VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' COMMENT '用户状态：ONLINE-在线，OFFLINE-离线，BUSY-忙碌，AWAY-离开',
    `webrtc_user_current_room_id`       VARCHAR(32)          DEFAULT NULL COMMENT '当前所在房间ID',
    `webrtc_user_audio_enabled`         BOOLEAN              DEFAULT TRUE COMMENT '音频是否启用',
    `webrtc_user_video_enabled`         BOOLEAN              DEFAULT TRUE COMMENT '视频是否启用',
    `webrtc_user_screen_sharing`        BOOLEAN              DEFAULT FALSE COMMENT '是否正在屏幕共享',
    `webrtc_user_device_type`           VARCHAR(20)          DEFAULT 'WEB' COMMENT '设备类型：WEB-网页，MOBILE-移动端，DESKTOP-桌面端',
    `webrtc_user_browser`               VARCHAR(50)          DEFAULT NULL COMMENT '浏览器信息',
    `webrtc_user_ip_address`            VARCHAR(45)          DEFAULT NULL COMMENT 'IP地址',
    `webrtc_user_last_online_time`      DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '最后在线时间',
    `webrtc_user_join_room_time`        DATETIME             DEFAULT NULL COMMENT '加入房间时间',
    `webrtc_user_leave_room_time`       DATETIME             DEFAULT NULL COMMENT '离开房间时间',
    `webrtc_user_total_online_duration` BIGINT               DEFAULT 0 COMMENT '总在线时长（秒）',
    `webrtc_user_create_time`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `webrtc_user_update_time`           DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `webrtc_user_deleted`               BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '是否已删除',
    `webrtc_user_remark`                VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`webrtc_user_id`),
    UNIQUE KEY `uk_webrtc_user_name` (`webrtc_user_name`),
    KEY `idx_webrtc_user_status` (`webrtc_user_status`),
    KEY `idx_webrtc_user_current_room_id` (`webrtc_user_current_room_id`),
    KEY `idx_webrtc_user_last_online_time` (`webrtc_user_last_online_time`),
    KEY `idx_webrtc_user_create_time` (`webrtc_user_create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WebRTC用户表';

-- 创建WebRTC房间用户关联表
CREATE TABLE IF NOT EXISTS `webrtc_room_user`
(
    `webrtc_room_user_id`             VARCHAR(32) NOT NULL COMMENT '关联ID',
    `webrtc_room_user_room_id`        VARCHAR(32) NOT NULL COMMENT '房间ID',
    `webrtc_room_user_user_id`        VARCHAR(32) NOT NULL COMMENT '用户ID',
    `webrtc_room_user_role`           VARCHAR(20)          DEFAULT 'MEMBER' COMMENT '用户角色：CREATOR-创建者，ADMIN-管理员，MEMBER-成员',
    `webrtc_room_user_join_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `webrtc_room_user_leave_time`     DATETIME             DEFAULT NULL COMMENT '离开时间',
    `webrtc_room_user_duration`       BIGINT               DEFAULT 0 COMMENT '在房间时长（秒）',
    `webrtc_room_user_audio_enabled`  BOOLEAN              DEFAULT TRUE COMMENT '音频是否启用',
    `webrtc_room_user_video_enabled`  BOOLEAN              DEFAULT TRUE COMMENT '视频是否启用',
    `webrtc_room_user_screen_sharing` BOOLEAN              DEFAULT FALSE COMMENT '是否正在屏幕共享',
    `webrtc_room_user_status`         VARCHAR(20)          DEFAULT 'ACTIVE' COMMENT '在房间状态：ACTIVE-活跃，INACTIVE-非活跃，LEFT-已离开',
    `webrtc_room_user_create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `webrtc_room_user_update_time`    DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `webrtc_room_user_deleted`        BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '是否已删除',
    `webrtc_room_user_remark`         VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`webrtc_room_user_id`),
    UNIQUE KEY `uk_webrtc_room_user` (`webrtc_room_user_room_id`, `webrtc_room_user_user_id`),
    KEY `idx_webrtc_room_user_room_id` (`webrtc_room_user_room_id`),
    KEY `idx_webrtc_room_user_user_id` (`webrtc_room_user_user_id`),
    KEY `idx_webrtc_room_user_join_time` (`webrtc_room_user_join_time`),
    KEY `idx_webrtc_room_user_status` (`webrtc_room_user_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WebRTC房间用户关联表';

-- 创建WebRTC消息记录表
CREATE TABLE IF NOT EXISTS `webrtc_message`
(
    `webrtc_message_id`          VARCHAR(32) NOT NULL COMMENT '消息ID',
    `webrtc_message_room_id`     VARCHAR(32) NOT NULL COMMENT '房间ID',
    `webrtc_message_sender_id`   VARCHAR(32) NOT NULL COMMENT '发送者ID',
    `webrtc_message_receiver_id` VARCHAR(32)          DEFAULT NULL COMMENT '接收者ID（私聊消息）',
    `webrtc_message_type`        VARCHAR(20) NOT NULL COMMENT '消息类型：TEXT-文本，IMAGE-图片，FILE-文件，SYSTEM-系统消息',
    `webrtc_message_content`     TEXT        NOT NULL COMMENT '消息内容',
    `webrtc_message_file_url`    VARCHAR(500)         DEFAULT NULL COMMENT '文件URL',
    `webrtc_message_file_name`   VARCHAR(200)         DEFAULT NULL COMMENT '文件名',
    `webrtc_message_file_size`   BIGINT               DEFAULT NULL COMMENT '文件大小（字节）',
    `webrtc_message_send_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `webrtc_message_read_status` BOOLEAN              DEFAULT FALSE COMMENT '是否已读',
    `webrtc_message_create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `webrtc_message_update_time` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `webrtc_message_deleted`     BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '是否已删除',
    `webrtc_message_remark`      VARCHAR(500)         DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`webrtc_message_id`),
    KEY `idx_webrtc_message_room_id` (`webrtc_message_room_id`),
    KEY `idx_webrtc_message_sender_id` (`webrtc_message_sender_id`),
    KEY `idx_webrtc_message_receiver_id` (`webrtc_message_receiver_id`),
    KEY `idx_webrtc_message_send_time` (`webrtc_message_send_time`),
    KEY `idx_webrtc_message_type` (`webrtc_message_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='WebRTC消息记录表';

-- 添加外键约束
ALTER TABLE `webrtc_user`
    ADD CONSTRAINT `fk_webrtc_user_room` FOREIGN KEY (`webrtc_user_current_room_id`) REFERENCES `webrtc_room` (`webrtc_room_id`) ON DELETE SET NULL;
ALTER TABLE `webrtc_room_user`
    ADD CONSTRAINT `fk_webrtc_room_user_room` FOREIGN KEY (`webrtc_room_user_room_id`) REFERENCES `webrtc_room` (`webrtc_room_id`) ON DELETE CASCADE;
ALTER TABLE `webrtc_room_user`
    ADD CONSTRAINT `fk_webrtc_room_user_user` FOREIGN KEY (`webrtc_room_user_user_id`) REFERENCES `webrtc_user` (`webrtc_user_id`) ON DELETE CASCADE;
ALTER TABLE `webrtc_message`
    ADD CONSTRAINT `fk_webrtc_message_room` FOREIGN KEY (`webrtc_message_room_id`) REFERENCES `webrtc_room` (`webrtc_room_id`) ON DELETE CASCADE;
ALTER TABLE `webrtc_message`
    ADD CONSTRAINT `fk_webrtc_message_sender` FOREIGN KEY (`webrtc_message_sender_id`) REFERENCES `webrtc_user` (`webrtc_user_id`) ON DELETE CASCADE;
ALTER TABLE `webrtc_message`
    ADD CONSTRAINT `fk_webrtc_message_receiver` FOREIGN KEY (`webrtc_message_receiver_id`) REFERENCES `webrtc_user` (`webrtc_user_id`) ON DELETE CASCADE;

-- 插入初始数据（可选）
-- INSERT INTO `webrtc_room` (`webrtc_room_id`, `webrtc_room_number`, `webrtc_room_name`, `webrtc_room_creator_id`, `webrtc_room_status`) 
-- VALUES ('demo_room_001', 1001, '演示房间', 'system', 'ACTIVE');

-- 创建视图：活跃房间统计
CREATE OR REPLACE VIEW `v_webrtc_active_room_stats` AS
SELECT r.webrtc_room_id,
       r.webrtc_room_number,
       r.webrtc_room_name,
       r.webrtc_room_creator_id,
       r.webrtc_room_current_users,
       r.webrtc_room_max_users,
       r.webrtc_room_create_time,
       r.webrtc_room_last_active_time,
       COUNT(ru.webrtc_room_user_id) as actual_user_count
FROM webrtc_room r
         LEFT JOIN webrtc_room_user ru ON r.webrtc_room_id = ru.webrtc_room_user_room_id
    AND ru.webrtc_room_user_status = 'ACTIVE'
    AND ru.webrtc_room_user_deleted = FALSE
WHERE r.webrtc_room_status = 'ACTIVE'
  AND r.webrtc_room_deleted = FALSE
GROUP BY r.webrtc_room_id;

-- 创建视图：用户在线统计
CREATE OR REPLACE VIEW `v_webrtc_user_online_stats` AS
SELECT u.webrtc_user_id,
       u.webrtc_user_name,
       u.webrtc_user_nickname,
       u.webrtc_user_status,
       u.webrtc_user_current_room_id,
       r.webrtc_room_number,
       r.webrtc_room_name,
       u.webrtc_user_last_online_time,
       u.webrtc_user_join_room_time
FROM webrtc_user u
         LEFT JOIN webrtc_room r ON u.webrtc_user_current_room_id = r.webrtc_room_id
WHERE u.webrtc_user_status IN ('ONLINE', 'BUSY', 'AWAY')
  AND u.webrtc_user_deleted = FALSE;