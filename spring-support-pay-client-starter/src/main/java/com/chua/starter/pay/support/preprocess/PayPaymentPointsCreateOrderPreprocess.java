package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreatePaymentPointsOrderV2Request;

/**
 * 信用分预处理
 *
 * @author CH
 * @since 2025/10/14 14:14
 */
public interface PayPaymentPointsCreateOrderPreprocess {

    /**
     * 创建处理器
     *
     * @return 创建处理器
     */
    static PayPaymentPointsCreateOrderPreprocess createProcessor() {
        return ServiceProvider.of(PayPaymentPointsCreateOrderPreprocess.class).getNewExtension("default");
    }

    /**
     * 预处理订单
     *
     * @param request 创建订单请求参数
     * @param userId  用户ID
     * @param openId  微信开放ID（可为空）
     * @return 预处理结果，包含预处理后的订单信息或错误信息
     */
    ReturnResult<CreatePaymentPointsOrderV2Request> preprocess(CreatePaymentPointsOrderV2Request request, String userId, String openId);
}
