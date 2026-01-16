package com.chua.starter.pay.support.order;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.pojo.PayMerchantWrapper;
import com.chua.starter.pay.support.pojo.PaySignResponse;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantService;
import com.chua.starter.pay.support.wechat.WechatPaySignatureCertificate;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * 微信签名
 * @author CH
 * @since 2025/10/14 14:57
 */
@Spi({"pay_wechat_js_api", "pay_wechat_native", "pay_WECHAT_H5"})
public class WechatJsApiCreateSignAdaptor implements CreateSignAdaptor{


    @AutoInject
    private PayMerchantService payMerchantService;

    @AutoInject
    private PayMerchantConfigWechatService payMerchantConfigWechatService;

    @Override
    public ReturnResult<PaySignResponse> createSign(PayMerchantOrder merchantOrder, @NotNull String prepayId) {
        PayMerchantWrapper payMerchantWrapper = payMerchantService.getByCodeForPayMerchant(merchantOrder.getPayMerchantId());
        if(!payMerchantWrapper.hasMerchant()) {
            return ReturnResult.illegal("商户不存在");
        }
        PayMerchant payMerchant = payMerchantWrapper.getPayMerchant();
        PayMerchantConfigWechatWrapper payMerchantConfigWechatWrapper = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat
                (payMerchant.getPayMerchantId(), merchantOrder.getPayMerchantTradeType().getName());
        if(!payMerchantConfigWechatWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启微信支付");
        }

        WechatPaySignatureCertificate certificate = new WechatPaySignatureCertificate(payMerchantConfigWechatWrapper.getPayMerchantConfigWechat());
        //生成签名
        Long timestamp = System.currentTimeMillis() / 1000;
        //随机字符串
        String nonceStr = RandomStringUtils.randomAlphanumeric(32);
        String signCode = null;
        try {
            signCode = certificate.jsApiPaySign(String.valueOf(timestamp), nonceStr, prepayId);
        } catch (Exception e) {
            return ReturnResult.error("生成签名失败");
        }
        PaySignResponse paySignResponse = new PaySignResponse();
        paySignResponse.setNonceStr(nonceStr);
        paySignResponse.setPaySign(signCode);
        paySignResponse.setTimeStamp(timestamp);
        return ReturnResult.ok(paySignResponse);
    }
}
