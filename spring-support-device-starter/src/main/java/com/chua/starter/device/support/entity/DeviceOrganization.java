package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 设备组织实体（树形结构）
 * <p>对应数据库表 device_organization。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("device_organization")
public class DeviceOrganization {

    /**
     * 主键 ID
     */
    @TableId(value = "device_organization_id", type = IdType.ASSIGN_UUID)
    private String deviceOrganizationId;

    /**
     * 父级组织 ID
     */
    @TableField("device_organization_parent_id")
    private String deviceOrganizationParentId;

    /**
     * 组织名称
     */
    @TableField("device_organization_name")
    private String deviceOrganizationName;

    /**
     * 路径，例 1/2/3
     */
    @TableField("device_organization_path")
    private String deviceOrganizationPath;

    /**
     * 所属厂商，例如 hikvision、dahua
     */
    @TableField("device_organization_vendor")
    private String deviceOrganizationVendor;
}