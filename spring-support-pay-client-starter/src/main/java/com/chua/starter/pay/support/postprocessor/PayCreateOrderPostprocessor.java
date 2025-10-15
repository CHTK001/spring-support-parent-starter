package com.chua.starter.pay.support.postprocessor;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import lombok.Data;

/**
 * 订单预处理
 *
 * @author CH
 * @since 2025/10/14 14:14
 */
public interface PayCreateOrderPostprocessor {

    /**
     * 创建处理器
     *
     * @return 处理器
     */
    static PayCreateOrderPostprocessor createProcessor() {
        return ServiceProvider.of(PayCreateOrderPostprocessor.class).getNewExtension("default");
    }

    /**
     * 发布订单创建结果
     *
     * @param data 订单创建结果对象
     *             示例: {
     *             "code": 0,
     *             "msg": "success",
     *             "data": "PRE_ORDER_20251014001"
     *             }
     */
    void publish(PayMerchantOrder data);
}
