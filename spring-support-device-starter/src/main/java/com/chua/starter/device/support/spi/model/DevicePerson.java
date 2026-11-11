package com.chua.starter.device.spi.model;

/**
 * 人员条目（由厂商平台 {@code fetchPersons()} 拉取并落库到 {@code device_person}）
 *
 * <p>厂商侧的操作员/成员，按 {@code vendor + externalId} 在 {@code device_person}
 * 中幂等入库；组织归属取 {@code OrganizationNode} 中的组织标识。</p>
 *
 * @param orgId      厂商侧组织标识（部门 ID，与设备台账 device_organization 对齐）
 * @param externalId 厂商侧人员唯一标识
 * @param name       人员姓名
 * @param title      职称（若平台未提供）
 * @param phone      手机（若平台未提供）
 * @param email      邮箱（若平台未提供）
 * @param paperType  证件类型（ICC 等平台写入时必填，读接口可能为空）
 * @param paperNumber 证件号码（ICC 等平台写入时必填，读接口可能为空）
 * @since 4.0.0.42
 * @author CH
 */
public record DevicePerson(
        String orgId,
        String externalId,
        String name,
        String title,
        String phone,
        String email,
        Integer paperType,
        String paperNumber) {

    /**
     * 兼容旧调用的六参构造：证件字段留空。
     *
     * @param orgId      组织标识
     * @param externalId 厂商侧人员标识
     * @param name       姓名
     * @param title      职称
     * @param phone      手机
     * @param email      邮箱
     */
    public DevicePerson(String orgId, String externalId, String name, String title,
                        String phone, String email) {
        this(orgId, externalId, name, title, phone, email, null, null);
    }
}
