package com.chua.starter.pay.support.callback.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chua.starter.pay.support.callback.AesUtil;
import com.chua.starter.pay.support.callback.OrderCallbackRequest;
import com.chua.starter.pay.support.callback.WechatOrderCallbackRequest;
import com.chua.starter.pay.support.callback.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantFailureRecord;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 微信支付解析
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class WebchatCallbackNotificationParser implements CallbackNotificationParser {

    private final PayMerchantOrder merchantOrder;
    private final String requestBody;
    private final String wechatSignature;
    private final String wechatpayNonce;
    private final String wechatPaySerial;
    private final String wechatTimestamp;
    private final String wechatpaySignatureType;
    private final OrderCallbackRequest request = new OrderCallbackRequest();
    private final WechatOrderCallbackRequest wechatOrderCallbackRequest;
    private final PayMerchantOrderService payMerchantOrderService;
    private final PayMerchantFailureRecordService payMerchantFailureRecordService;
    private final PayMerchantConfigWechat payMerchantConfigWechat;

    public WebchatCallbackNotificationParser(
            PayMerchantOrder merchantOrder,
            PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat,
            String requestBody,
            String wechatSignature,
            String wechatpayNonce,
            String wechatPaySerial,
            String wechatTimestamp,
            String wechatpaySignatureType,
            PayMerchantOrderService payMerchantOrderService,
            PayMerchantFailureRecordService payMerchantFailureRecordService) {
        this.merchantOrder = merchantOrder;
        this.requestBody = requestBody;
        this.payMerchantConfigWechat = byCodeForPayMerchantConfigWechat.getPayMerchantConfigWechat();
        this.wechatSignature = wechatSignature;
        this.wechatpayNonce = wechatpayNonce;
        this.wechatPaySerial = wechatPaySerial;
        this.wechatTimestamp = wechatTimestamp;
        this.wechatpaySignatureType = wechatpaySignatureType;
        this.wechatOrderCallbackRequest = JSON.parseObject(requestBody, WechatOrderCallbackRequest.class);
        this.payMerchantOrderService = payMerchantOrderService;
        this.payMerchantFailureRecordService = payMerchantFailureRecordService;
        request.setStatus("TRANSACTION.SUCCESS".equals(wechatOrderCallbackRequest.getEventType()) ? OrderCallbackRequest.Status.SUCCESS : OrderCallbackRequest.Status.FAILURE);
        request.setBusinessStatus("TRANSACTION.SUCCESS".equals(wechatOrderCallbackRequest.getEventType()) ? OrderCallbackRequest.Status.SUCCESS : OrderCallbackRequest.Status.FAILURE);
    }


    /**
     * 获取请求参数
     *
     * @return 请求参数
     */
    public RequestParam getRequestParam() {
        return new RequestParam.Builder()
                .serialNumber(wechatPaySerial)
                .nonce(wechatpayNonce)
                .signature(wechatSignature)
                .timestamp(wechatTimestamp)
                .body(requestBody)
                .build();
    }

    /**
     * 获取通知配置
     *
     * @return 配置
     */
    public NotificationConfig getNotificationConfig() {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                .build();
    }

    @Override
    public WechatOrderCallbackResponse parse() {
        // 构造 RequestParam
        RequestParam requestParam = getRequestParam();
        // 构造 NotificationConfig
        NotificationConfig config = getNotificationConfig();
        // 初始化 NotificationParser
        NotificationParser notificationParser = new NotificationParser(config);
        try {
            // 以支付通知回调为例，验签、解密并转换成 Transaction
            Transaction transaction = null;
            try {
                transaction = notificationParser.parse(requestParam, Transaction.class);
            } catch (Exception e) {
                JSONObject jsonObject = JSON.parseObject(requestBody);
                JSONObject resource = jsonObject.getJSONObject("resource");
                AesUtil aesUtil = new AesUtil(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3().getBytes(StandardCharsets.UTF_8));
                String decryptedData = aesUtil.decryptToString(
                        resource.getString("associated_data").getBytes(StandardCharsets.UTF_8),
                        resource.getString("nonce").getBytes(StandardCharsets.UTF_8),
                        resource.getString("ciphertext")
                );
                transaction = JSON.parseObject(decryptedData, Transaction.class);
            }

            merchantOrder.setPayMerchantOrderTransactionId(transaction.getTransactionId());
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_SUCCESS);
            payMerchantOrderService.finishWechatOrder(merchantOrder);
            return new WechatOrderCallbackResponse("SUCCESS", "OK", null);
        } catch (Exception e) {
            // 签名验证失败，返回 401 UNAUTHORIZED 状态码
            payMerchantFailureRecordService.saveRecord(createRecord(e));
            log.error("sign verification failed", e);
            throw new RuntimeException("sign verification failed");
        }
    }

    /**
     * 创建支付失败记录
     *
     * @param e 异常
     * @return 记录
     */
    private PayMerchantFailureRecord createRecord(Exception e) {
        PayMerchantFailureRecord record = new PayMerchantFailureRecord();
        record.setPayMerchantFailureRecordBody(requestBody);
        record.setPayMerchantFailureRecordSignature(wechatSignature);
        record.setPayMerchantFailureRecordSignatureType(wechatpaySignatureType);
        record.setPayMerchantFailureRecordNonce(wechatpayNonce);
        record.setPayMerchantFailureRecordSerial(wechatPaySerial);
        record.setPayMerchantMerchantOrderCode(merchantOrder.getPayMerchantOrderCode());
        record.setPayMerchantFailureReason(e.getMessage());
        record.setPayMerchantFailureType("WECHAT_PAY");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }
}
