package com.chua.starter.pay.support.transfer;

import com.chua.common.support.lang.code.ReturnResult;

import java.math.BigDecimal;

/**
 * 优惠券转化
 *
 * @author CH
 */
public interface CouponCodeTransfer {


    /**
     * 转换
     *
     * @param code 券码
     * @return 转换后的用户id
     */
    BigDecimal transferToPrice(String code);


    /**
     * 检查
     *
     * @param code 券码
     * @return 是否可用
     */
    ReturnResult<String> check(String code);
}
