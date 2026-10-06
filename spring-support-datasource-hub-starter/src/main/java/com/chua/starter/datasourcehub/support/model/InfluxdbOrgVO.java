package com.chua.starter.datasourcehub.support.model;

/**
 * InfluxDB 组织（Org）信息。
 *
 * @param orgId   组织 ID
 * @param orgName 组织名
 * @author CH
 * @since 4.0.0.42
 */
public record InfluxdbOrgVO(
        String orgId,
        String orgName
) {
}
