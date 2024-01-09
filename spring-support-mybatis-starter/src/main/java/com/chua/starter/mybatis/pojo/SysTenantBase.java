package com.chua.starter.mybatis.pojo;

import com.chua.common.support.datasource.annotation.Column;
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
    @Column(defaultValue = "0", comment = "租户ID", refresh = true)
    private Long tenantId;
}
