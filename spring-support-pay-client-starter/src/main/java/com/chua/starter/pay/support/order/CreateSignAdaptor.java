package com.chua.starter.pay.support.order;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PaySignResponse;
import jakarta.validation.constraints.NotNull;

/**
 * 创建签名适配器接口
 *
 * @author CH
 * @since 2025/10/14 14:47
 */
public interface CreateSignAdaptor {

    /**
     * 创建支付签名
     *
     * @param merchantOrder 商户订单信息，包含商户ID、订单号、金额等支付相关参数
     * @param prepayId      预支付交易会话ID，用于生成支付签名的必要参数
     * @return 签名结果，包含时间戳、随机字符串、签名等信息
     */
    ReturnResult<PaySignResponse> createSign(@NotNull PayMerchantOrder merchantOrder, @NotNull String prepayId);
}
