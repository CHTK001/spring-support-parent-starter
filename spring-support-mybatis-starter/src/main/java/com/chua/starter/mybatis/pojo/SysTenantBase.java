package com.chua.starter.mybatis.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统租户基础
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SysTenantBase extends SysBase{

    /**
     * 多租户编号
     */
    private Long tenantId;
}
