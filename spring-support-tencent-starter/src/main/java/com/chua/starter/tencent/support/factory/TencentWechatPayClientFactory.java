package com.chua.starter.tencent.support.factory;

import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.app.AppService;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;

/**
 * 微信支付客户端工厂
 *
 * @author CH
 * @since 2026-03-19
 */
public class TencentWechatPayClientFactory {

    public Config createAutoCertificateConfig(TencentWechatPayProperties properties) {
        return createAutoCertificateConfig(
                properties.getMerchantId(),
                properties.getPrivateKey(),
                properties.getMerchantSerialNumber(),
                properties.getApiV3Key());
    }

    public Config createAutoCertificateConfig(String merchantId,
                                              String privateKey,
                                              String merchantSerialNumber,
                                              String apiV3Key) {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKey(privateKey)
                .merchantSerialNumber(merchantSerialNumber)
                .apiV3Key(apiV3Key)
                .build();
    }

    public JsapiServiceExtension createJsapiService(Config config) {
        return new JsapiServiceExtension.Builder()
                .config(config)
                .build();
    }

    public H5Service createH5Service(Config config) {
        return new H5Service.Builder()
                .config(config)
                .build();
    }

    public RefundService createRefundService(Config config) {
        return new RefundService.Builder()
                .config(config)
                .build();
    }

    public NativePayService createNativePayService(Config config) {
        return new NativePayService.Builder()
                .config(config)
                .build();
    }

    public AppService createAppService(Config config) {
        return new AppService.Builder()
                .config(config)
                .build();
    }

    public AppServiceExtension createAppServiceExtension(Config config) {
        return new AppServiceExtension.Builder()
                .config(config)
                .build();
    }

    public NotificationParser createNotificationParser(Config config) {
        if (config instanceof RSAAutoCertificateConfig autoCertificateConfig) {
            return new NotificationParser(autoCertificateConfig);
        }
        throw new IllegalArgumentException("当前配置不支持自动证书回调解析: " + config.getClass().getName());
    }
}
