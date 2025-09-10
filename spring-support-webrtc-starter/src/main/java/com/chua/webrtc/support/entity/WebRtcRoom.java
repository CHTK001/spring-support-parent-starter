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
 * WebRTC房间实体类
 *
 * @author CH
 * @since 4.1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("webrtc_room")
@Schema(description = "WebRTC房间信息")
public class WebRtcRoom {

    /**
     * 房间ID
     */
    @TableId(value = "webrtc_room_id", type = IdType.ASSIGN_ID)
    @Schema(description = "房间ID", example = "1234567890")
    private String webrtcRoomId;

    /**
     * 房间号（雪花算法生成的唯一数字）
     */
    @TableField("webrtc_room_number")
    @Schema(description = "房间号", example = "1234567890123456789")
    private Long webrtcRoomNumber;

    /**
     * 创建人ID
     */
    @TableField("webrtc_room_creator_id")
    @Schema(description = "创建人ID", example = "user123")
    private String webrtcRoomCreatorId;

    /**
     * 房间名称
     */
    @TableField("webrtc_room_name")
    @Schema(description = "房间名称", example = "会议室A")
    private String webrtcRoomName;

    /**
     * 房间描述
     */
    @TableField("webrtc_room_description")
    @Schema(description = "房间描述", example = "用于团队会议的房间")
    private String webrtcRoomDescription;

    /**
     * 房间状态
     */
    @TableField("webrtc_room_status")
    @Schema(description = "房间状态", example = "ACTIVE", allowableValues = {"ACTIVE", "CLOSED"})
    private String webrtcRoomStatus;

    /**
     * 房间最大用户数
     */
    @TableField("webrtc_room_max_users")
    @Schema(description = "房间最大用户数", example = "10")
    private Integer webrtcRoomMaxUsers;

    /**
     * 房间当前用户数
     */
    @TableField("webrtc_room_current_users")
    @Schema(description = "房间当前用户数", example = "3")
    private Integer webrtcRoomCurrentUsers;

    /**
     * 创建时间
     */
    @TableField("webrtc_room_create_time")
    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime webrtcRoomCreateTime;

    /**
     * 最后活跃时间
     */
    @TableField("webrtc_room_last_active_time")
    @Schema(description = "最后活跃时间", example = "2024-01-01T10:30:00")
    private LocalDateTime webrtcRoomLastActiveTime;

    /**
     * 关闭时间
     */
    @TableField("webrtc_room_close_time")
    @Schema(description = "关闭时间", example = "2024-01-01T11:00:00")
    private LocalDateTime webrtcRoomCloseTime;

    /**
     * 是否删除
     */
    @TableField("webrtc_room_deleted")
    @Schema(description = "是否删除", example = "false")
    private Boolean webrtcRoomDeleted;

    /**
     * 备注
     */
    @TableField("webrtc_room_remark")
    @Schema(description = "备注信息", example = "重要会议房间")
    private String webrtcRoomRemark;
}