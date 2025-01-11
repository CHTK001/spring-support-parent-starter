package com.chua.starter.pay.support.checker;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PayOrderRequest;

/**
 * 检测器
 * @author CH
 */
public interface CreateOrderChecker {


    /**
     * 检测
     * @param request 请求
     * @return 结果
     */
    ReturnResult<String> check(PayOrderRequest request);
}
