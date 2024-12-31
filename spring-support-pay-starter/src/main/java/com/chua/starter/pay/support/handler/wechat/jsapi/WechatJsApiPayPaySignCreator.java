package com.chua.starter.pay.support.handler.wechat.jsapi;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.handler.PaySignCreator;
import com.chua.starter.pay.support.handler.wechat.WechatPaySignatureCertificate;
import com.chua.starter.pay.support.pojo.PaySignCreateRequest;
import com.chua.starter.pay.support.result.PaySignResponse;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * 支付订单处理器
 * @author CH
 * @since 2024/12/30
 */
@Spi("wechat_js_api")
public class WechatJsApiPayPaySignCreator implements PaySignCreator {
    final PayMerchantConfigWechat payMerchantConfigWechat;

    public WechatJsApiPayPaySignCreator(PayMerchantConfigWechat payMerchantConfigWechat) {
        this.payMerchantConfigWechat = payMerchantConfigWechat;
    }
    @Override
    public ReturnResult<PaySignResponse> handle(PaySignCreateRequest request) {
        WechatPaySignatureCertificate certificate = new WechatPaySignatureCertificate(payMerchantConfigWechat);
        //生成签名
        Long timestamp = System.currentTimeMillis() / 1000;
        //随机字符串
        String nonceStr = RandomStringUtils.randomAlphanumeric(32);
        String signCode = null;
        try {
            signCode = certificate.jsApiPaySign(String.valueOf(timestamp), nonceStr, request.getPrepayId());
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
