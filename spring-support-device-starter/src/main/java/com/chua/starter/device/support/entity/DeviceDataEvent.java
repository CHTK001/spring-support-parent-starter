package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */

/**
 * 门禁设备数据
 */
@Schema(description = "门禁设备数据")
@Data
@TableName(value = "device_data_event")
public class DeviceDataEvent implements Serializable {
    @TableId(value = "device_data_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer deviceDataId;

    /**
     * 数据ID
     */
    @TableField(value = "device_data_data_id")
    @Schema(description = "数据ID")
    @Size(max = 255, message = "数据ID最大长度要小于 255")
    private String deviceDataDataId;

    /**
     * 设备时间
     */
    @TableField(value = "device_data_event_time")
    @Schema(description = "设备时间")
    private Date deviceDataEventTime;

    /**
     * 事件编号
     */
    @TableField(value = "device_data_event_type")
    @Schema(description = "事件编号")
    @Size(max = 255, message = "事件编号最大长度要小于 255")
    private String deviceDataEventType;

    /**
     * 事件码
     */
    @TableField(value = "device_data_event_code")
    @Schema(description = "事件码")
    @Size(max = 255, message = "事件码最大长度要小于 255")
    private String deviceDataEventCode;

    /**
     * 事件码名称
     */
    @TableField(value = "device_data_event_code_label")
    @Schema(description = "事件码名称")
    @Size(max = 255, message = "事件码名称最大长度要小于 255")
    private String deviceDataEventCodeLabel;

    /**
     * 设备ID
     */
    @TableField(value = "device_imsi")
    @Schema(description = "设备ID")
    @Size(max = 255, message = "设备ID最大长度要小于 255")
    private String deviceImsi;

    /**
     * 设备名称
     */
    @TableField(value = "device_name")
    @Schema(description = "设备名称")
    @Size(max = 255, message = "设备名称最大长度要小于 255")
    private String deviceName;

    /**
     * 坐标
     */
    @TableField(value = "device_data_coordinate")
    @Schema(description = "坐标")
    @Size(max = 255, message = "坐标最大长度要小于 255")
    private String deviceDataCoordinate;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @Schema(description = "更新时间")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}