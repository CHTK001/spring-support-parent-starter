package com.chua.starter.pay.support.handler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PaySignCreateRequest;
import com.chua.starter.pay.support.result.PaySignResponse;

/**
 * 签名创建
 * @author CH
 * @since 2024/12/31
 */
public interface PaySignCreator {
    /**
     * 签名创建
     * @param request 请求
     * @return 签名
     */
    ReturnResult<PaySignResponse> handle(PaySignCreateRequest request);
}
