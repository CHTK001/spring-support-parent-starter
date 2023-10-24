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

/**
 *    
 * @author CH
 */

/**
 * 云平台信息表
 */
@Schema(description = "云平台信息表")
@Data
@TableName(value = "device_cloud_platform")
public class DeviceCloudPlatform implements Serializable {
    @TableId(value = "device_platform_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer devicePlatformId;

    /**
     * 云平台名称
     */
    @TableField(value = "device_platform_name")
    @Schema(description = "云平台名称")
    @Size(max = 255, message = "云平台名称最大长度要小于 255")
    private String devicePlatformName;

    /**
     * 云平台编码(开发定义)
     */
    @TableField(value = "device_platform_code")
    @Schema(description = "云平台编码(开发定义)")
    @Size(max = 255, message = "云平台编码(开发定义)最大长度要小于 255")
    private String devicePlatformCode;

    /**
     * 云平台地址
     */
    @TableField(value = "device_platform_address")
    @Schema(description = "云平台地址")
    @Size(max = 255, message = "云平台地址最大长度要小于 255")
    private String devicePlatformAddress;

    /**
     * 厂家ID
     */
    @TableField(value = "manufacturer_id")
    @Schema(description = "厂家ID")
    private Integer manufacturerId;
    /**
     * 厂家名称
     */
    @TableField(exist = false)
    @Schema(description = "厂家名称")
    private String manufacturerName;
    /**
     * 存在实现接口
     */
    @TableField(exist = false)
    @Schema(description = "存在实现接口")
    private Boolean existImplInterface;

    private static final long serialVersionUID = 1L;
}