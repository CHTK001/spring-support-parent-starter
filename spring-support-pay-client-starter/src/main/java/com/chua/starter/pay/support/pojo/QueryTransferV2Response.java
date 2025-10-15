package com.chua.starter.pay.support.pojo;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 查询转账订单响应实体类
 *
 * @author CH
 * @since 2025/10/15 10:49
 */
@Data
@Schema(description = "查询转账订单")
public class QueryTransferV2Response {
    /**
     * 转账状态
     */
    @SerializedName("state")
    @Schema(description = "转账状态")
    private String state;
}
