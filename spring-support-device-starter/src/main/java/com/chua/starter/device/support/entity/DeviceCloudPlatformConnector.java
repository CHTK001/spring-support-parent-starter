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
 * 设备厂家配置信息表
 */
@Schema(description = "设备厂家配置信息表")
@Data
@TableName(value = "device_cloud_platform_connector")
public class DeviceCloudPlatformConnector implements Serializable {
    @TableId(value = "device_connector_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer deviceConnectorId;

    /**
     * 厂家appKey
     */
    @TableField(value = "device_connector_name")
    @Schema(description = "服务名称")
    private String deviceConnectorName;
    /**
     * 厂家appKey
     */
    @TableField(value = "device_connector_app_key")
    @Schema(description = "厂家appKey")
    @Size(max = 255, message = "厂家appKey最大长度要小于 255")
    private String deviceConnectorAppKey;

    /**
     * 厂家appSecret
     */
    @TableField(value = "device_connector_app_secret")
    @Schema(description = "厂家appSecret")
    @Size(max = 255, message = "厂家appSecret最大长度要小于 255")
    private String deviceConnectorAppSecret;

    /**
     * 厂家项目ID
     */
    @TableField(value = "device_connector_project_id")
    @Schema(description = "厂家项目ID")
    @Size(max = 255, message = "厂家项目ID最大长度要小于 255")
    private String deviceConnectorProjectId;

    /**
     * 超时时间
     */
    @TableField(value = "device_connector_timeout")
    @Schema(description = "超时时间")
    private Integer deviceConnectorTimeout;

    /**
     * 厂家项目编码
     */
    @TableField(value = "device_connector_project_code")
    @Schema(description = "厂家项目编码")
    @Size(max = 255, message = "厂家项目编码最大长度要小于 255")
    private String deviceConnectorProjectCode;

    /**
     * 对应的平台ID
     */
    @TableField(value = "device_platform_id")
    @Schema(description = "对应的平台ID")
    @Size(max = 255, message = "对应的平台ID最大长度要小于 255")
    private String devicePlatformId;

    private static final long serialVersionUID = 1L;
    /**
     * 厂家协议配置地址
     */
    @TableField(exist = false)
    private String deviceConnectorAddress;
}