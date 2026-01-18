package com.chua.starter.pay.support.order;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantConfigAlipayWrapper;
import com.chua.starter.pay.support.pojo.PayMerchantWrapper;
import com.chua.starter.pay.support.pojo.PaySignResponse;
import com.chua.starter.pay.support.service.PayMerchantConfigAlipayService;
import com.chua.starter.pay.support.service.PayMerchantService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付宝签名适配器
 *
 * @author CH
 * @since 2025/10/15 18:30
 */
@Slf4j
@Spi({"pay_alipay_app", "pay_alipay_qr_code", "pay_alipay_wap", "pay_alipay_mini"})
public class AlipayCreateSignAdaptor implements CreateSignAdaptor {

    @AutoInject
    private PayMerchantService payMerchantService;

    @AutoInject
    private PayMerchantConfigAlipayService payMerchantConfigAlipayService;

    @Override
    public ReturnResult<PaySignResponse> createSign(@NotNull PayMerchantOrder merchantOrder, @NotNull String prepayId) {
        PayMerchantWrapper payMerchantWrapper = payMerchantService.getByCodeForPayMerchant(merchantOrder.getPayMerchantId());
        if (!payMerchantWrapper.hasMerchant()) {
            return ReturnResult.illegal("商户不存在");
        }

        PayMerchant payMerchant = payMerchantWrapper.getPayMerchant();
        PayMerchantConfigAlipayWrapper configWrapper = payMerchantConfigAlipayService.getByCodeForPayMerchantConfigAlipay(
                payMerchant.getPayMerchantId(),
                merchantOrder.getPayMerchantTradeType().getName()
        );
        if (!configWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启支付宝支付");
        }

        // 支付宝APP支付返回的prepayId就是订单字符串，直接返回
        PaySignResponse paySignResponse = new PaySignResponse();
        paySignResponse.setPaySign(prepayId);
        return ReturnResult.ok(paySignResponse);
    }
}

