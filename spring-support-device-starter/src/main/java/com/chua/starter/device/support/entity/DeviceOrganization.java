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
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_organization")
public class DeviceOrganization {

    /**
     * 主键 标识
     */
    @TableId(value = "device_organization_id", type = IdType.ASSIGN_UUID)
    /**
     * deviceorganizationid
    */
    private String deviceOrganizationId;

    /**
     * 父级组织 标识
     */
    @TableField("device_organization_parent_id")
    /**
     * Deviceorganization父级标识
    */
    private String deviceOrganizationParentId;

    /**
     * 组织名称
     */
    @TableField("device_organization_name")
    /**
     * Deviceorganization名称
    */
    private String deviceOrganizationName;

    /**
     * 路径，例 1/2/3
     */
    @TableField("device_organization_path")
    /**
     * Deviceorganization路径
    */
    private String deviceOrganizationPath;

    /**
     * 所属厂商，例如 hikvision、dahua
     */
    @TableField("device_organization_vendor")
    /**
     * Deviceorganizationvendor
    */
    private String deviceOrganizationVendor;
}
