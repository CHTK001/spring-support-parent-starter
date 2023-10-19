package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */

/**
 * 厂家表
 */
@Schema(description = "厂家表")
@Data
@TableName(value = "device_manufacturer")
public class DeviceManufacturer implements Serializable {
    @TableId(value = "manufacturer_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer manufacturerId;

    /**
     * 厂家名称
     */
    @TableField(value = "manufacturer_name")
    @Schema(description = "厂家名称")
    @Size(max = 255, message = "厂家名称最大长度要小于 255")
    @NotBlank(message = "厂家名称不能为空")
    private String manufacturerName;

    /**
     * 厂家类型
     */
    @TableField(value = "manufacturer_type")
    @Schema(description = "厂家类型")
    @Size(max = 255, message = "厂家类型最大长度要小于 255")
    @NotBlank(message = "厂家类型不能为空")
    private String manufacturerType;

    /**
     * 厂家地址
     */
    @TableField(value = "manufacturer_address")
    @Schema(description = "厂家地址")
    @Size(max = 255, message = "厂家地址最大长度要小于 255")
    private String manufacturerAddress;

    /**
     * 厂家联系人
     */
    @TableField(value = "manufacturer_contact")
    @Schema(description = "厂家联系人")
    @Size(max = 255, message = "厂家联系人最大长度要小于 255")
    private String manufacturerContact;

    /**
     * 厂家电话
     */
    @TableField(value = "manufacturer_phone")
    @Schema(description = "厂家电话")
    @Size(max = 255, message = "厂家电话最大长度要小于 255")
    private String manufacturerPhone;

    /**
     * 厂家邮箱
     */
    @TableField(value = "manufacturer_email")
    @Schema(description = "厂家邮箱")
    @Size(max = 255, message = "厂家邮箱最大长度要小于 255")
    private String manufacturerEmail;

    /**
     * 厂家编码
     */
    @TableField(value = "manufacturer_code")
    @Schema(description = "厂家编码")
    @Size(max = 255, message = "厂家编码最大长度要小于 255")
    private String manufacturerCode;

    /**
     * 备注
     */
    @TableField(value = "manufacturer_remark")
    @Schema(description = "备注")
    @Size(max = 255, message = "备注最大长度要小于 255")
    private String manufacturerRemark;

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