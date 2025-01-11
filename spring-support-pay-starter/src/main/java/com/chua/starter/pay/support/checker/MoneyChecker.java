package com.chua.starter.pay.support.checker;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PayOrderRequest;

/**
 * 金额校验
 *
 * @author CH
 */
public interface MoneyChecker {
    /**
     * 检测
     * @param request 请求
     * @return 结果
     */
    ReturnResult<String> check(PayOrderRequest request);
}
