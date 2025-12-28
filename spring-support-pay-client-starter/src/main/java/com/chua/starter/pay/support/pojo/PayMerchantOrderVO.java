package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单列表展示对象（含商户名等关联信息）
 * 作者: CH
 * 创建时间: 2025-10-15
 * 版本: 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单列表展示对象（含商户名等关联信息）")
public class PayMerchantOrderVO extends PayMerchantOrder {

    /** 商户名称 */
    @Schema(description = "商户名称")
    private String payMerchantName;
}
