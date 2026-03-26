package com.chua.starter.proxy.support.pojo;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务器配置排序请求参数
 *
 * @author CH
 * @since 2025/8/8 20:50
 */
@NoArgsConstructor
@Data
@Schema(description = "服务器配置排序请求参数")
public class ServerSettingOrderRequest {

    /**
     * 配置ID
     */
    @Schema(description = "配置ID")
    @SerializedName("id")
    private Integer id;

    /**
     * 排序值
     */
    @Schema(description = "排序")
    @SerializedName("sortOrder")
    private Integer sortOrder;
}




