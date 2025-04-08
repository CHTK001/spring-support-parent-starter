package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.pojo.WaterQueryV1Request;

import java.util.List;

/**
 * 支付服务
 *
 * @author CH
 * @since 2025/1/3
 */
public interface RpcPayOrderWaterService {

    /**
     * 订单流水
     *
     * @param payMerchantOrderCode 订单编号
     * @return 订单
     */
    ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode);


    /**
     * 订单流水
     *
     * @param request 订单编号
     * @return 订单
     */
    ReturnPageResult<PayMerchantOrderWater> water(WaterQueryV1Request request);

}
