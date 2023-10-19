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
 * 设备通道
 */
@Schema(description = "设备通道")
@Data
@TableName(value = "device_channel")
public class DeviceChannel implements Serializable {
    @TableId(value = "channel_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer channelId;

    /**
     * 设备ID
     */
    @TableField(value = "device_id")
    @Schema(description = "设备ID")
    private Integer deviceId;

    /**
     * 通道号
     */
    @TableField(value = "channel_no")
    @Schema(description = "通道号")
    @Size(max = 10, message = "通道号最大长度要小于 10")
    private String channelNo;

    /**
     * 通道名称
     */
    @TableField(value = "channel_name")
    @Schema(description = "通道名称")
    @Size(max = 255, message = "通道名称最大长度要小于 255")
    private String channelName;

    /**
     * 标签
     */
    @TableField(value = "channe_tag")
    @Schema(description = "标签")
    @Size(max = 255, message = "标签最大长度要小于 255")
    private String channeTag;

    /**
     * 更新时间
     */
    @TableField(value = "create_time")
    @Schema(description = "更新时间")
    private Date createTime;

    /**
     * 创建时间
     */
    @TableField(value = "update_time")
    @Schema(description = "创建时间")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}