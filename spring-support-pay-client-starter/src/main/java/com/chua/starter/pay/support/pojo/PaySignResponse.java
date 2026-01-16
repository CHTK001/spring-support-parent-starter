package com.chua.starter.pay.support.pojo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 签名
 * @author CH
 * @since 2024/12/30
 */
@Data
@ApiModel(description = "签名响应数据")
public class PaySignResponse {
    /**
     * 支付签名，用于验证支付请求的合法性
     */
    @ApiModelProperty(value = "支付签名")
    private String paySign;

    /**
     * 随机字符串，用于生成签名，以防止重放攻击
     */
    @ApiModelProperty(value = "随机字符串")
    private String nonceStr;

    /**
     * 时间戳，用于生成签名，表示支付请求的生成时间
     */
    @ApiModelProperty(value = "时间戳")
    private Long timeStamp;
}
