package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.RefundOrderV2Request;

/**
 * 订单退款预处理
 *
 * @author CH
 * @since 2025/10/14 14:14
 */
public class DefaultPayRefundOrderPreprocess implements PayRefundOrderPreprocess{

    @Override
    public ReturnResult<RefundOrderV2Request> preprocess(RefundOrderV2Request request, PayMerchantOrder payMerchantOrder) {
        return ReturnResult.ok(request);
    }
}
