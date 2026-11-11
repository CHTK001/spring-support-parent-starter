package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 过车拉取游标实体。
 *
 * <p>按设备保存平台侧最后处理的消息 ID，避免定时任务每次都从头翻页。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("device_vehicle_pass_cursor")
public class DeviceVehiclePassCursor {

    /**
     * 设备标识
     */
    @TableId(value = "device_vehicle_pass_cursor_device_id", type = IdType.INPUT)
    private String deviceVehiclePassCursorDeviceId;

    /**
     * 最后处理的平台消息 ID
     */
    @TableField("device_vehicle_pass_cursor_last_msg_id")
    private Long deviceVehiclePassCursorLastMsgId;

    /**
     * 更新时间
     */
    @TableField("device_vehicle_pass_cursor_updated_at")
    private LocalDateTime deviceVehiclePassCursorUpdatedAt;
}
