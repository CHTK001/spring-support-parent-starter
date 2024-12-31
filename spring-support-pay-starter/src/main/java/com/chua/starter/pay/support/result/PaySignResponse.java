package com.chua.starter.pay.support.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 签名
 * @author CH
 * @since 2024/12/30
 */
@Data
@Schema(description = "签名响应数据")
public class PaySignResponse {
    /**
     * 支付签名，用于验证支付请求的合法性
     */
    private String paySign;

    /**
     * 随机字符串，用于生成签名，以防止重放攻击
     */
    private String nonceStr;

    /**
     * 时间戳，用于生成签名，表示支付请求的生成时间
     */
    private Long timeStamp;
}
