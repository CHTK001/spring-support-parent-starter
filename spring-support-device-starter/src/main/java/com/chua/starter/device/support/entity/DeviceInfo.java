package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.*;
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
 * 设备信息表
 */
@Schema(description = "设备信息表")
@Data
@TableName(value = "device_info")
public class DeviceInfo implements Serializable {
    @TableId(value = "device_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer deviceId;

    /**
     * 识别码(每台设备唯一)
     */
    @TableField(value = "device_imsi")
    @Schema(description = "识别码(每台设备唯一)")
    @Size(max = 255, message = "识别码(每台设备唯一)最大长度要小于 255")
    private String deviceImsi;

    /**
     * 设备类型
     */
    @TableField(value = "device_type_id")
    @Schema(description = "设备类型")
    private String deviceTypeId;

    /**
     * 设备云服务
     */
    @TableField(value = "device_connector_id")
    @Schema(description = "设备云服务")
    private String deviceConnectorId;
    /**
     * 设备云服务名称
     */
    @TableField(exist = false)
    @Schema(description = "设备云服务名称")
    private String deviceServiceName;

    /**
     * 设备类型名称
     */
    @TableField(exist = false)
    @Schema(description = "设备类型名称")
    private String deviceTypeName;
    /**
     * 设备类型编号
     */
    @TableField(exist = false)
    @Schema(description = "设备类型编号")
    private String deviceTypeCode;
    /**
     * 设备名称
     */
    @TableField(value = "device_name")
    @Schema(description = "设备名称")
    @Size(max = 255, message = "设备名称最大长度要小于 255")
    private String deviceName;

    /**
     * 设备状态; offline: 离线, online: 在线
     */
    @TableField(value = "device_status")
    @Schema(description = "设备状态; offline: 离线, online: 在线")
    private String deviceStatus;

    /**
     * 经度
     */
    @TableField(value = "device_longitude")
    @Schema(description = "经度")
    @Size(max = 255, message = "经度最大长度要小于 255")
    private String deviceLongitude;

    /**
     * 维度
     */
    @TableField(value = "device_latitude")
    @Schema(description = "维度")
    @Size(max = 255, message = "维度最大长度要小于 255")
    private String deviceLatitude;

    /**
     * 设备所在的详细地址
     */
    @TableField(value = "device_address")
    @Schema(description = "设备所在的详细地址")
    @Size(max = 255, message = "设备所在的详细地址最大长度要小于 255")
    private String deviceAddress;

    /**
     * 设备类型; 门禁: ACCESS_CONTROL 视频: VIDEO 其它: OTHER
     */
    @TableField(value = "device_category")
    @Schema(description = "设备类型; 门禁: ACCESS_CONTROL 视频: VIDEO 其它: OTHER")
    @Size(max = 255, message = "设备类型; 门禁: ACCESS_CONTROL 视频: VIDEO 其它: OTHER最大长度要小于 255")
    private String deviceCategory;

    /**
     * 设备版本
     */
    @TableField(value = "device_version")
    @Schema(description = "设备版本")
    @Size(max = 255, message = "设备版本最大长度要小于 255")
    private String deviceVersion;

    /**
     * 设备型号
     */
    @TableField(value = "device_model")
    @Schema(description = "设备型号")
    @Size(max = 255, message = "设备型号最大长度要小于 255")
    private String deviceModel;

    /**
     * 设备序列号
     */
    @TableField(value = "device_serial")
    @Schema(description = "设备序列号")
    @Size(max = 255, message = "设备序列号最大长度要小于 255")
    private String deviceSerial;

    /**
     * 设备协议名称
     */
    @TableField(value = "device_treaty_type_label")
    @Schema(description = "设备协议名称")
    @Size(max = 255, message = "设备协议名称最大长度要小于 255")
    private String deviceTreatyTypeLabel;

    /**
     * 设备协议
     */
    @TableField(value = "device_treaty_type")
    @Schema(description = "设备协议")
    @Size(max = 255, message = "设备协议最大长度要小于 255")
    private String deviceTreatyType;

    /**
     * 标签
     */
    @TableField(value = "device_tag")
    @Schema(description = "标签")
    @Size(max = 255, message = "标签最大长度要小于 255")
    private String deviceTag;

    /**
     * 备注
     */
    @TableField(value = "device_remark")
    @Schema(description = "备注")
    private String deviceRemark;

    /**
     * 所属组织
     */
    @TableField(value = "device_org_code")
    @Schema(description = "所属组织")
    @Size(max = 255, message = "所属组织最大长度要小于 255")
    private String deviceOrgCode;

    /**
     * 采集方式
     */
    @TableField(value = "device_connector")
    @Schema(description = "采集方式")
    @Size(max = 255, message = "采集方式最大长度要小于 255")
    private String deviceConnector;

    /**
     * 删除状态;0:删除
     */
    @TableLogic(value = "0", delval = "1")
    @TableField(value = "device_delete")
    @Schema(description = "删除状态;1:删除")
    private Integer deviceDelete;

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