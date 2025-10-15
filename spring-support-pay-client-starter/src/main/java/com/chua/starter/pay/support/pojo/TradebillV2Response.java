package com.chua.starter.pay.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交易账单响应数据
 *
 * @author CH
 * @since 2025/10/15 16:13
 */
@NoArgsConstructor
@Data
@Schema(description = "交易账单响应数据")
public class TradebillV2Response {

    /**
     * 哈希类型
     */
    @Schema(description = "哈希类型")
    private String hashType;

    /**
     * 哈希值
     */
    @Schema(description = "哈希值")
    private String hashValue;

    /**
     * 下载链接
     */
    @Schema(description = "下载链接")
    private String downloadUrl;
}
