package com.chua.starter.pay.support.callback.parser;

import com.alibaba.fastjson.JSON;
import com.chua.starter.pay.support.callback.OrderCallbackRequest;
import com.chua.starter.pay.support.callback.WechatOrderCallbackRequest;
import com.chua.starter.pay.support.callback.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantFailureRecord;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantTransferRecordService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 微信转账解析
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class WebchatCallbackTransferNotificationParser implements CallbackNotificationParser {

    private final PayMerchantTransferRecord merchantOrder;
    private final String requestBody;
    private final String wechatSignature;
    private final String wechatpayNonce;
    private final String wechatPaySerial;
    private final String wechatTimestamp;
    private final String wechatpaySignatureType;
    private final OrderCallbackRequest request = new OrderCallbackRequest();
    private final WechatOrderCallbackRequest wechatOrderCallbackRequest;
    private final PayMerchantTransferRecordService transferRecordService;
    private final PayMerchantFailureRecordService payMerchantFailureRecordService;
    private final PayMerchantConfigWechat payMerchantConfigWechat;

    public WebchatCallbackTransferNotificationParser(
            PayMerchantTransferRecord merchantOrder,
            PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat,
            String requestBody,
            String wechatSignature,
            String wechatpayNonce,
            String wechatPaySerial,
            String wechatTimestamp,
            String wechatpaySignatureType,
            PayMerchantTransferRecordService transferRecordService,
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
        this.transferRecordService = transferRecordService;
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
            merchantOrder.setPayMerchantTransferRecordStatus("SUCCESS");
            merchantOrder.setPayMerchantTransferRecordFinishTime(LocalDateTime.now());
            transferRecordService.updateById(merchantOrder);
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
        record.setPayMerchantMerchantOrderCode(merchantOrder.getPayMerchantTransferRecordCode());
        record.setPayMerchantFailureReason(e.getMessage());
        record.setPayMerchantFailureType("WECHAT_TRANSFER");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }
}
