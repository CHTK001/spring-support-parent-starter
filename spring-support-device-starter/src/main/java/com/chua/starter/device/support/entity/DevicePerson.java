package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 厂商人员实体
 * <p>对应数据库表 device_person。</p>
 *
 * <p>由厂商平台 {@code fetchPersons()} 拉取并落地，按 {@code vendor + 厂商侧人员标识}
 * 幂等入库；行政归属挂接厂商侧组织标识（与 {@link DeviceOrganization#deviceOrganizationExternalId}
 * 对齐），不与人力的组织关系表耦合。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("device_person")
public class DevicePerson {

    /**
     * 主键 标识
     */
    @TableId(value = "device_person_id", type = IdType.ASSIGN_UUID)
    /**
     * Devicepersonid
     */
    private String devicePersonId;

    /**
     * 人员姓名
     */
    @TableField("device_person_name")
    /**
     * Deviceperson姓名
     */
    private String devicePersonName;

    /**
     * 职称（平台未提供时为空）
     */
    @TableField("device_person_title")
    /**
     * Devicepersontitle
     */
    private String devicePersonTitle;

    /**
     * 手机（平台未提供时为空）
     */
    @TableField("device_person_phone")
    /**
     * Devicepersonphone
     */
    private String devicePersonPhone;

    /**
     * 邮箱（平台未提供时为空）
     */
    @TableField("device_person_email")
    /**
     * Devicepersoneemail
     */
    private String devicePersonEmail;

    /**
     * 厂商标识：hikvision / dahua
     */
    @TableField("device_person_vendor")
    /**
     * Devicepersonvendor
     */
    private String devicePersonVendor;

    /**
     * 厂商侧人员唯一标识（同步幂等键）
     */
    @TableField("device_person_external_id")
    /**
     * Devicepersonexternalid
     */
    private String devicePersonExternalId;

    /**
     * 厂商侧组织标识（部门 ID，挂接 device_organization 的厂商侧外标）
     */
    @TableField("device_person_org_id")
    /**
     * Devicepersonorgid
     */
    private String devicePersonOrgId;

    /**
     * 厂商已删除标记：1 已删除、0 正常
     */
    @TableField("device_person_deleted")
    /**
     * Devicepersondeleted
     */
    private Boolean devicePersonDeleted;

    /**
     * 创建时间
     */
    @TableField("device_person_created_at")
    /**
     * Deviceperscreatedat
     */
    private LocalDateTime devicePersonCreatedAt;

    /**
     * 更新时间
     */
    @TableField("device_person_updated_at")
    /**
     * Devicepersonupdatedat
     */
    private LocalDateTime devicePersonUpdatedAt;
}
