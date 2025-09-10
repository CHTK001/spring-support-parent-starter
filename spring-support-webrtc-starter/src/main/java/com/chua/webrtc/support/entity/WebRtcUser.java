package com.chua.webrtc.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * WebRTC用户实体类
 *
 * @author CH
 * @since 4.1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("webrtc_user")
@Schema(description = "WebRTC用户信息")
public class WebRtcUser {

    /**
     * 用户ID
     */
    @TableId(value = "webrtc_user_id", type = IdType.ASSIGN_ID)
    @Schema(description = "用户ID", example = "1234567890")
    private String webrtcUserId;

    /**
     * 用户名
     */
    @TableField("webrtc_user_name")
    @Schema(description = "用户名", example = "john_doe")
    private String webrtcUserName;

    /**
     * 用户昵称
     */
    @TableField("webrtc_user_nickname")
    @Schema(description = "用户昵称", example = "约翰")
    private String webrtcUserNickname;

    /**
     * 用户头像
     */
    @TableField("webrtc_user_avatar")
    @Schema(description = "用户头像URL", example = "https://example.com/avatar.jpg")
    private String webrtcUserAvatar;

    /**
     * 用户邮箱
     */
    @TableField("webrtc_user_email")
    @Schema(description = "用户邮箱", example = "john@example.com")
    private String webrtcUserEmail;

    /**
     * 用户手机号
     */
    @TableField("webrtc_user_phone")
    @Schema(description = "用户手机号", example = "13800138000")
    private String webrtcUserPhone;

    /**
     * 用户状态
     */
    @TableField("webrtc_user_status")
    @Schema(description = "用户状态", example = "ONLINE", allowableValues = {"ONLINE", "OFFLINE", "BUSY"})
    private String webrtcUserStatus;

    /**
     * 当前所在房间ID
     */
    @TableField("webrtc_user_current_room_id")
    @Schema(description = "当前所在房间ID", example = "room123")
    private String webrtcUserCurrentRoomId;

    /**
     * 音频是否启用
     */
    @TableField("webrtc_user_audio_enabled")
    @Schema(description = "音频是否启用", example = "true")
    private Boolean webrtcUserAudioEnabled;

    /**
     * 视频是否启用
     */
    @TableField("webrtc_user_video_enabled")
    @Schema(description = "视频是否启用", example = "true")
    private Boolean webrtcUserVideoEnabled;

    /**
     * 是否屏幕共享
     */
    @TableField("webrtc_user_screen_sharing")
    @Schema(description = "是否屏幕共享", example = "false")
    private Boolean webrtcUserScreenSharing;

    /**
     * 最后在线时间
     */
    @TableField("webrtc_user_last_online_time")
    @Schema(description = "最后在线时间", example = "2024-01-01T10:00:00")
    private LocalDateTime webrtcUserLastOnlineTime;

    /**
     * 加入房间时间
     */
    @TableField("webrtc_user_join_room_time")
    @Schema(description = "加入房间时间", example = "2024-01-01T10:15:00")
    private LocalDateTime webrtcUserJoinRoomTime;

    /**
     * 离开房间时间
     */
    @TableField("webrtc_user_leave_room_time")
    @Schema(description = "离开房间时间", example = "2024-01-01T11:00:00")
    private LocalDateTime webrtcUserLeaveRoomTime;

    /**
     * 创建时间
     */
    @TableField("webrtc_user_create_time")
    @Schema(description = "创建时间", example = "2024-01-01T09:00:00")
    private LocalDateTime webrtcUserCreateTime;

    /**
     * 更新时间
     */
    @TableField("webrtc_user_update_time")
    @Schema(description = "更新时间", example = "2024-01-01T10:30:00")
    private LocalDateTime webrtcUserUpdateTime;

    /**
     * 是否删除
     */
    @TableField("webrtc_user_deleted")
    @Schema(description = "是否删除", example = "false")
    private Boolean webrtcUserDeleted;

    /**
     * 备注
     */
    @TableField("webrtc_user_remark")
    @Schema(description = "备注信息", example = "VIP用户")
    private String webrtcUserRemark;
}