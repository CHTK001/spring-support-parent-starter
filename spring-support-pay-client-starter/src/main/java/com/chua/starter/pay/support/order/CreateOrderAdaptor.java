package com.chua.starter.pay.support.order;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PaySignResponse;

/**
 *
 * 创建订单适配器
 * @author CH
 * @since 2025/10/14 13:49
 */
public interface CreateOrderAdaptor {
    /**
     * 创建订单
     * @param request 创建订单请求参数
     * @param userId 用户id
     * @param openId openId
     * @return 创建订单结果
     */
    ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request, String userId, String openId);

}
