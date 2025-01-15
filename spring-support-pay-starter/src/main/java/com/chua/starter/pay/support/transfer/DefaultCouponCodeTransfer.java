package com.chua.starter.pay.support.transfer;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.pojo.PayOrderRequest;

import java.math.BigDecimal;

/**
 * 优惠券转化
 *
 * @author CH
 */
@SpiDefault
public class DefaultCouponCodeTransfer implements CouponCodeTransfer{

    @Override
    public BigDecimal transferToPrice(String code) {
        return BigDecimal.ZERO;
    }

    @Override
    public ReturnResult<String> check(PayOrderRequest request) {
        return ReturnResult.SUCCESS;
    }
}
