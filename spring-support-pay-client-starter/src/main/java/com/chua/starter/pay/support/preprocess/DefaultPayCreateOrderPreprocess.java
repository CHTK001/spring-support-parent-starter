package com.chua.starter.pay.support.preprocess;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;

/**
 * 订单预处理
 * @author CH
 * @since 2025/10/14 14:19
 */
@SpiDefault
@Spi("default")
public class DefaultPayCreateOrderPreprocess implements PayCreateOrderPreprocess{
    @Override
    public ReturnResult<CreateOrderV2Request> preprocess(CreateOrderV2Request request, String userId, String openId) {
        return ReturnResult.ok(request);
    }
}
