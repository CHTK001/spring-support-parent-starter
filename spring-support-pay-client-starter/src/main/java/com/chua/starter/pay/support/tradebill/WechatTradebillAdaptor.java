package com.chua.starter.pay.support.tradebill;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.dto.SearchComplaintsResponse;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.pojo.SearchComplaintsV2Response;
import com.chua.starter.pay.support.pojo.TradebillV2Request;
import com.chua.starter.pay.support.pojo.TradebillV2Response;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantTransferRecordService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.PrivacyEncryptor;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.*;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import static com.chua.starter.pay.support.enums.PayTradeType.PAY_WECHAT_JS_API;

/**
 * 微信交易账单适配器接口
 * @author CH
 * @since 2025/10/15 16:11
 */
@Spi({"pay_wechat_js_api", "pay_wechat_native", "pay_WECHAT_H5"})
public class WechatTradebillAdaptor implements TradebillAdaptor{
    @AutoInject
    private PayUserWalletService payUserWalletService;

    @AutoInject
    private PayMerchantConfigWechatService payMerchantConfigWechatService;

    @AutoInject
    private PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    private RedissonClient redissonClient;

    @AutoInject
    private TransactionTemplate transactionTemplate;

    @AutoInject
    private PayMerchantTransferRecordService payMerchantTransferRecordService;

    @Override
    public ReturnResult<TradebillV2Response> download(TradebillV2Request request) {
        PayMerchantConfigWechatWrapper payMerchantConfigWechatWrapper = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat
                (request.getMerchantId(), PAY_WECHAT_JS_API.getName());
        if (!payMerchantConfigWechatWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启微信支付");
        }
        PayMerchantConfigWechat payMerchantConfigWechat = payMerchantConfigWechatWrapper.getPayMerchantConfigWechat();
        // 参数配置
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();

        PrivacyEncryptor encryptor = config.createEncryptor();
        String requestPath = "https://api.mch.weixin.qq.com/v3/bill/tradebill";
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader(Constant.ACCEPT, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.CONTENT_TYPE, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.WECHAT_PAY_SERIAL, encryptor.getWechatpaySerial());
        HttpRequest httpRequest =
                new HttpRequest.Builder()
                        .httpMethod(HttpMethod.GET)
                        .url(requestPath)
                        .headers(headers)
                        .build();
        HttpClient httpClient = new DefaultHttpClientBuilder().config(config).build();
        try {
            HttpResponse<TradebillV2Response> httpResponse =
                    httpClient.execute(httpRequest, TradebillV2Response.class);
            return ReturnResult.ok(httpResponse.getServiceResponse());
        } catch (ServiceException e) {
            // 获取错误码和错误信息
            String errorMessage = e.getErrorMessage().trim();
            return ReturnResult.illegal("查询投诉单订单失败：" + errorMessage);
        }
    }
}
