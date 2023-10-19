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
 * 厂家组织编码
 */
@Schema(description = "厂家组织编码")
@Data
@TableName(value = "device_org")
public class DeviceOrg implements Serializable {
    @TableId(value = "device_org_id", type = IdType.AUTO)
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer deviceOrgId;

    /**
     * 组织编码
     */
    @TableField(value = "device_org_code")
    @Schema(description = "组织编码")
    @Size(max = 255, message = "组织编码最大长度要小于 255")
    private String deviceOrgCode;

    /**
     * 组织名称
     */
    @TableField(value = "device_org_name")
    @Schema(description = "组织名称")
    @Size(max = 255, message = "组织名称最大长度要小于 255")
    private String deviceOrgName;

    /**
     * 路径
     */
    @TableField(value = "device_org_path")
    @Schema(description = "路径")
    @Size(max = 255, message = "路径最大长度要小于 255")
    private String deviceOrgPath;

    /**
     * 父组织编码
     */
    @TableField(value = "device_org_parent_code")
    @Schema(description = "父组织编码")
    @Size(max = 255, message = "父组织编码最大长度要小于 255")
    private String deviceOrgParentCode;

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