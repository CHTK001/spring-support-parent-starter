package com.chua.starter.mybatis.pojo;

import com.chua.common.support.datasource.annotation.Column;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "租户ID")
    @ApiModelProperty("租户ID")
    private Long sysTenantId;
}
