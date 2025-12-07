package com.chua.tenant.support.server.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 服务绑定请求对象
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@Data
@Schema(name = "服务绑定请求")
public class SysServiceBindV1Request {

    /**
     * 服务ID
     */
    @Schema(description = "服务ID")
    private Integer sysServiceId;

    /**
     * 服务模块ID列表
     */
    @Schema(description = "服务模块ID列表")
    private List<Integer> sysServiceModuleIds;
}
