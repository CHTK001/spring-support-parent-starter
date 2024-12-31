package com.chua.starter.pay.support.handler.wechat;

import com.chua.common.support.json.Json;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.CallbackNotificationParser;
import com.chua.starter.pay.support.mapper.PayMerchantConfigWechatMapper;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.OrderCallbackRequest;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackRequest;
import com.chua.starter.pay.support.service.PayMerchantService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信解析
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class WebchatCallbackNotificationParser implements CallbackNotificationParser {

    private final PayMerchantConfigWechatMapper payMerchantConfigWechatMapper;
    private final String requestBody;
    private final  String wechatSignature;
    private final String wechatpayNonce;
    private final String wechatPaySerial;
    private final String wechatTimestamp;
    private final String wechatpaySignatureType;
    private final OrderCallbackRequest request = new OrderCallbackRequest();
    private final WechatOrderCallbackRequest wechatOrderCallbackRequest;
    private final String payMerchantOrderId;
    private final String payMerchantOrderCode;
    private PayMerchantOrder payMerchantOrder;

    public WebchatCallbackNotificationParser(PayMerchantConfigWechatMapper payMerchantConfigWechatMapper,
                                             String requestBody,
                                             String wechatSignature,
                                             String wechatpayNonce,
                                             String wechatPaySerial,
                                             String wechatTimestamp,
                                             String wechatpaySignatureType,
                                             String payMerchantOrderId,
                                             String payMerchantOrderCode) {
        this.payMerchantConfigWechatMapper = payMerchantConfigWechatMapper;
        this.requestBody = requestBody;
        this.wechatSignature = wechatSignature;
        this.wechatpayNonce = wechatpayNonce;
        this.wechatPaySerial = wechatPaySerial;
        this.wechatTimestamp = wechatTimestamp;
        this.wechatpaySignatureType = wechatpaySignatureType;
        this.wechatOrderCallbackRequest = Json.fromJson(requestBody, WechatOrderCallbackRequest.class);
        this.payMerchantOrderId = payMerchantOrderId;
        this.payMerchantOrderCode = payMerchantOrderCode;
        request.setStatus("TRANSACTION.SUCCESS".equals(wechatOrderCallbackRequest.getEventType()) ? OrderCallbackRequest.Status.SUCCESS : OrderCallbackRequest.Status.FAILURE);
        request.setDataId(payMerchantOrderCode);
        request.setOutTradeId(payMerchantOrderCode);
    }

    @Override
    public OrderCallbackRequest getRequest() {
        return request;
    }

    @Override
    public PayMerchantOrder getOrder() {
        return payMerchantOrder;
    }

    @Override
    public String id() {
        return request.getDataId();
    }

    @Override
    public boolean parser(PayMerchantService payMerchantService, PayMerchantOrderMapper payMerchantOrderMapper) {
        this.payMerchantOrder = CallbackNotificationParser.getPayMerchantOrder(payMerchantOrderMapper, getRequest());
        if(null == payMerchantOrder) {
            return false;
        }
        PayMerchantConfigWechat payMerchantConfigWechat = payMerchantConfigWechatMapper.getConfig(payMerchantOrder.getPayMerchantCode(), payMerchantOrder.getPayMerchantOrderTradeType().replace("wechat_", ""));
        if(null == payMerchantConfigWechat) {
            return false;
        }

        // 构造 RequestParam
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(wechatPaySerial)
                .nonce(wechatpayNonce)
                .signature(wechatSignature)
                .timestamp(wechatTimestamp)
                .body(requestBody)
                .build();
        NotificationConfig config = new RSAAutoCertificateConfig.Builder()
                .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                .build();

        // 初始化 NotificationParser
        NotificationParser parser = new NotificationParser(config);

        try {
            // 以支付通知回调为例，验签、解密并转换成 Transaction
            Transaction transaction = parser.parse(requestParam, Transaction.class);
            request.setTransactionId(transaction.getTransactionId());
            request.setStatus(OrderCallbackRequest.Status.SUCCESS);
            return true;
        } catch (Exception e) {
            // 签名验证失败，返回 401 UNAUTHORIZED 状态码
            log.error("sign verification failed", e);
        }
        request.setStatus(OrderCallbackRequest.Status.FAILURE);
        return false;
    }
}
