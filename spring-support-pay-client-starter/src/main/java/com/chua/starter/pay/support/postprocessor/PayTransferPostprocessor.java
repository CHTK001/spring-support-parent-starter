package com.chua.starter.pay.support.postprocessor;

import com.chua.common.support.constant.Action;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;

/**
 * 转账预处理
 *
 * @author CH
 * @since 2025/10/14 14:14
 */
public interface PayTransferPostprocessor {

    /**
     * 创建处理器
     *
     * @return 处理器
     */
    static PayTransferPostprocessor createProcessor() {
        return ServiceProvider.of(PayTransferPostprocessor.class).getNewExtension("default");
    }

    /**
     * 发布订单创建结果
     *
     * @param data   订单创建结果对象
     *               示例: {
     *               "code": 0,
     *               "msg": "success",
     *               "data": "PRE_ORDER_20251014001"
     *               }
     * @param action
     */
    void publish(PayMerchantTransferRecord data, Action action);
}
