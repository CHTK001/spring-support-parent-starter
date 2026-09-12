package com.chua.starter.device.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* 组织节点模型（厂商平台拉取的组织/部门）
*
* @since 4.0.0.42
* @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationNode {

    /**
    * 厂商侧组织 标识
     */
    private String orgId;

    /**
    * 组织名称
     */
    private String orgName;

    /**
    * 父组织 标识（顶层节点为空）
     */
    private String parentId;
}
