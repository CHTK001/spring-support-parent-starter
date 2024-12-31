package com.chua.starter.pay.support.handler.wechat;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayOrderCreator;
import com.chua.starter.pay.support.handler.PaySignCreator;
import com.chua.starter.pay.support.pojo.PaySignCreateRequest;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PaySignResponse;
import com.chua.starter.pay.support.sign.KeyGenerator;
import com.chua.starter.pay.support.sign.Md5Encrypt;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.service.payments.jsapi.JsapiService;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
        long startTime = new Date().getTime();
        StringBuilder paramStr = new StringBuilder();
        String nonceStr = KeyGenerator.generateRandomKey();
        long timeStamp = System.currentTimeMillis();
        paramStr.append("appId=").append(payMerchantConfigWechat.getPayMerchantConfigWechatAppId());
        paramStr.append("&nonceStr=").append(nonceStr);
        paramStr.append("&package=").append(request.getPackageStr());
        paramStr.append("&signType=").append("MD5");
        paramStr.append("&timeStamp=").append(timeStamp);
        paramStr.append("&key=").append(payMerchantConfigWechat.getPayMerchantConfigWechatAppSecret());
        String s = Md5Encrypt.md5(paramStr.toString()).toUpperCase();
        PaySignResponse paySignResponse = new PaySignResponse();
        paySignResponse.setNonceStr(nonceStr);
        paySignResponse.setPaySign(s);
        paySignResponse.setTimeStamp(timeStamp);
        long startTime1 = new Date().getTime();
        System.out.println("支付服务生成签名接口耗时：" + (startTime1 - startTime) / 1000 + "秒");
        return ReturnResult.ok(paySignResponse);
    }
}
