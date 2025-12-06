package com.chua.tenant.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户同步请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Data
@Schema(name = "租户同步请求")
@AllArgsConstructor
@NoArgsConstructor
public class SysTenantSyncV1Request {

    /**
     * 租户ID
     */
    @Schema(description = "租户ID")
    private Integer sysTenantId;
}
