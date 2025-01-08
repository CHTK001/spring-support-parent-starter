package com.chua.starter.pay.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PayMerchantOrderWaterService extends IService<PayMerchantOrderWater>{



    /**
     * 创建订单流水
     * @param payMerchantOrder 订单
     * @return 是否成功
     */
    ReturnResult<Boolean> createOrderWater(PayMerchantOrder payMerchantOrder);

    /**
     * 订单流水
     * @param payMerchantOrderCode 订单号
     * @return 订单流水
     */
    ReturnResult<List<PayMerchantOrderWater>> water(String payMerchantOrderCode);

    /**
     * 创建新表
     * @return 是否成功
     */
    ReturnResult<Boolean> createNewTable();
}
