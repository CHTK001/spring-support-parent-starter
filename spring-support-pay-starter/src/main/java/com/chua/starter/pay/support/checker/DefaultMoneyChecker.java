package com.chua.starter.pay.support.checker;

import com.chua.common.support.annotations.SpiDefault;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.transfer.CouponCodeTransfer;

import java.math.BigDecimal;

/**
 * 金额校验
 *
 * @author CH
 */
@SpiDefault
public class DefaultMoneyChecker implements MoneyChecker{


    final static BigDecimal MIN = new BigDecimal("0.01");
    @Override
    public ReturnResult<String> check(PayOrderRequest request) {
        //原始金额
        BigDecimal price = request.getPrice();
        //支付金额
        BigDecimal totalPrice = request.getTotalPrice();

        if (null == price || null == totalPrice) {
            return ReturnResult.error("支付金额与订单金额不能为空");
        }

        String couponCode = request.getCouponCode();
        CouponCodeTransfer couponCodeTransfer = ServiceProvider.of(CouponCodeTransfer.class).getNewExtension(request.getTradeType());
        //优惠金额
        BigDecimal couponPrice = couponCodeTransfer.transferToPrice(couponCode);

        //交易方式
        TradeType tradeType = request.getTradeType();
        //优惠后金额
        BigDecimal totalPriceAfter = price.subtract(couponPrice);


        if (price.compareTo(BigDecimal.ZERO) < 0 || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
            return ReturnResult.error("支付金额与订单金额不能小于0");
        }

        if (price.compareTo(totalPrice) != 0) {
            return ReturnResult.error("支付金额与订单金额不一致");
        }


        if(tradeType == TradeType.WALLET) {
            if(totalPriceAfter.compareTo(BigDecimal.ZERO) < 0) {
                totalPriceAfter = BigDecimal.ZERO;
            }
            if(totalPriceAfter.compareTo(totalPrice) != 0) {
                return ReturnResult.error("支付金额与订单金额不一致");
            }

            return ReturnResult.SUCCESS;
        }

        if(totalPriceAfter.compareTo(BigDecimal.ZERO) < 0) {
            totalPriceAfter = MIN;
        }

        if(totalPriceAfter.compareTo(totalPrice) != 0) {
            return ReturnResult.error("支付金额与订单金额不一致");
        }

        return ReturnResult.SUCCESS;
    }
}
