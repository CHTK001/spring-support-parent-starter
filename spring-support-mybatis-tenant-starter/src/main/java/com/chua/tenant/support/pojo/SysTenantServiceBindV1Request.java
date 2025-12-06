package com.chua.tenant.support.pojo;

import com.chua.tenant.support.entity.SysTenantService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 租户服务绑定请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Data
@Schema(name = "租户服务绑定请求")
@AllArgsConstructor
@NoArgsConstructor
public class SysTenantServiceBindV1Request {

    /**
     * 租户ID
     */
    @Schema(description = "租户ID")
    private Integer sysTenantId;

    /**
     * 服务列表
     */
    @Schema(description = "服务列表")
    private List<SysTenantService> sysServiceIds;
}
