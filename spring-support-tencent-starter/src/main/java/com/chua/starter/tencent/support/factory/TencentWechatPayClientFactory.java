package com.chua.starter.tencent.support.factory;

import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.service.payments.app.AppService;
import com.wechat.pay.java.service.payments.app.AppServiceExtension;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;

/**
 * 微信支付客户端工厂
 *
 * @since 2026-03-19
 * @author CH
 */
public class TencentWechatPayClientFactory {

    /**
     * 创建auto证书配置
     *
     * @param properties 属性
     * @return 创建auto证书配置的结果
     */
    public Config createAutoCertificateConfig(TencentWechatPayProperties properties) {
        return createAutoCertificateConfig(
                properties.getMerchantId(),
                properties.getPrivateKey(),
                properties.getMerchantSerialNumber(),
                properties.getApiV3Key());
    }

    /**
      * 创建auto证书配置
     * @param merchantId merchantid
     * @param privateKey 私募钥匙
     * @param merchantSerialNumber merchant串行数字
     * @param apiV3Key apiv3钥匙
     */
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

    /**
     * 创建jsapi服务
     *
     * @param config 配置
     * @return 创建jsapi服务的结果
     */
    public JsapiServiceExtension createJsapiService(Config config) {
        return new JsapiServiceExtension.Builder()
                .config(config)
                .build();
    }

    /**
     * 创建服务
     *
     * @param config 配置
     * @return 创建h5服务的结果
     */
    public H5Service createH5Service(Config config) {
        return new H5Service.Builder()
                .config(config)
                .build();
    }

    /**
     * 创建退税服务
     *
     * @param config 配置
     * @return 创建退税服务的结果
     */
    public RefundService createRefundService(Config config) {
        return new RefundService.Builder()
                .config(config)
                .build();
    }

    /**
     * 创建NAT薪酬服务
     *
     * @param config 配置
     * @return 创建NAT薪酬服务的结果
     */
    public NativePayService createNativePayService(Config config) {
        return new NativePayService.Builder()
                .config(config)
                .build();
    }

    /**
     * 创建app服务
     *
     * @param config 配置
     * @return 创建app服务的结果
     */
    public AppService createAppService(Config config) {
        return new AppService.Builder()
                .config(config)
                .build();
    }

    /**
     * 创建app服务延伸
     *
     * @param config 配置
     * @return 创建app服务延伸的结果
     */
    public AppServiceExtension createAppServiceExtension(Config config) {
        return new AppServiceExtension.Builder()
                .config(config)
                .build();
    }

    /**
     * 创建notificationparser
     *
     * @param config 配置
     * @return 创建notificationparser的结果
     */
    public NotificationParser createNotificationParser(Config config) {
        if (config instanceof RSAAutoCertificateConfig autoCertificateConfig) {
            /**
              * notificationparser。
             * @param autoCertificateConfig auto证书配置
             * @return NotificationParser的结果
             */
            return new NotificationParser(autoCertificateConfig);
        }
        /**
         * illegal参数异常。
         * @param config.getClass( 配置.获取类(
         * @return illegal参数异常的结果
         */
        throw new IllegalArgumentException("当前配置不支持自动证书回调解析: " + config.getClass().getName());
    }

    /**
     * 创建http客户端
     *
     * @param config 配置
     * @return 创建http客户端的结果
     */
    public HttpClient createHttpClient(Config config) {
        /**
         * 默认http客户端构建器。
         * @return 默认http客户端构建器的结果
         */
        return new DefaultHttpClientBuilder()
                .config(config)
                .build();
    }
}
