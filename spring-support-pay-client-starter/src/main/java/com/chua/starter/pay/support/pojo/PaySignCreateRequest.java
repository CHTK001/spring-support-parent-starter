package com.chua.starter.pay.support.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 签名创建
 * @author CH
 * @since 2024/12/31
 */
@Data
@Schema(title = "签名创建")
public class PaySignCreateRequest implements Serializable {

    /**
     * 预处理ID
     */
    @Schema(title = "prepay_id")
    private String prepayId;

    /**
     * 订单编号
     */
    @Schema(description = "订单编号")
    private String payMerchantCode;
}
