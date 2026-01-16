package com.chua.starter.pay.support.refund;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.RefundOrderV2Request;
import com.chua.starter.pay.support.pojo.RefundOrderV2Response;

/**
 * 订单退款适配器
 *
 * @author CH
 * @since 2025/10/14 16:29
 */
public interface RefundOrderAdaptor {

    /**
     * 执行订单退款操作
     *
     * @param merchantOrder 商户订单信息
     * @param request       退款请求参数
     * @return 退款结果响应
     */
    ReturnResult<RefundOrderV2Response> refundOrder(PayMerchantOrder merchantOrder, RefundOrderV2Request request);
}
