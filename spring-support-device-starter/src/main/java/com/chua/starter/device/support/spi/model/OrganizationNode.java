package com.chua.starter.device.spi.model;

/**
 * 组织节点模型（厂商平台拉取的组织/部门）
 *
 * @param orgId    厂商侧组织标识
 * @param orgName  组织名称
 * @param parentId 父组织标识（顶层节点为空）
 * @since 4.0.0.42
 * @author CH
 */
public record OrganizationNode(
        String orgId,
        String orgName,
        String parentId) {
}
