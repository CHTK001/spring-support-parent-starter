package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.annotation.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.CreatePaymentPointsOrderV2Request;

/**
 * 信用分预处理
 * @author CH
 * @since 2025/10/15 11:13
 */
@SpiDefault
@Spi("default")
public class DefaultPayPaymentPointsCreateOrderPreprocess implements PayPaymentPointsCreateOrderPreprocess{
    @Override
    public ReturnResult<CreatePaymentPointsOrderV2Request> preprocess(CreatePaymentPointsOrderV2Request request, String userId, String openId) {
        return ReturnResult.ok(request);
    }
}
