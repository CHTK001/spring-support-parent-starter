package com.chua.starter.pay.support.callback.parser;

import com.alibaba.fastjson.JSON;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.starter.pay.support.callback.OrderCallbackRequest;
import com.chua.starter.pay.support.callback.WechatOrderCallbackRequest;
import com.chua.starter.pay.support.callback.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantFailureRecord;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.event.ConfirmPayOrderEvent;
import com.chua.starter.pay.support.pojo.PayMerchantConfigWechatWrapper;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;

/**
 * 微信信用分解析
 *
 * @author CH
 * @since 2024/12/31
 */
@Slf4j
public class WebchatCallbackPaymentPointsNotificationParser implements CallbackNotificationParser {

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
    private final ApplicationContext applicationContext;
    private final PayMerchantConfigWechat payMerchantConfigWechat;

    public WebchatCallbackPaymentPointsNotificationParser(
            PayMerchantOrder merchantOrder,
            PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat,
            String requestBody,
            String wechatSignature,
            String wechatpayNonce,
            String wechatPaySerial,
            String wechatTimestamp,
            String wechatpaySignatureType,
            PayMerchantOrderService payMerchantOrderService,
            PayMerchantFailureRecordService payMerchantFailureRecordService,
            ApplicationContext applicationContext) {
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
        this.applicationContext = applicationContext;
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
        JsonObject jsonObject1 = Json.getJsonObject(requestBody);
        String eventType = jsonObject1.getString("event_type");
        log.info("当前订单事件类型{}", eventType);
        try {
            //信用分扣款回调
            if ("PAYSCORE.USER_PAID".equals(eventType)) {
                merchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
                merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_SUCCESS);
                merchantOrder.setPayMerchantOrderPayTime(LocalDateTime.now());
                payMerchantOrderService.finishWechatOrder(merchantOrder);
            }
            //用户确认
            if ("PAYSCORE.USER_CONFIRM".equals(eventType)) {
                merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_WAITING);
                ConfirmPayOrderEvent confirmPayOrderEvent = new ConfirmPayOrderEvent(merchantOrder);
                confirmPayOrderEvent.setPayMerchantOrder(merchantOrder);
                applicationContext.publishEvent(confirmPayOrderEvent);
                payMerchantOrderService.updateById(merchantOrder);
            }
            return new WechatOrderCallbackResponse("SUCCESS", "OK", null);
        } catch (Exception e) {
            payMerchantFailureRecordService.saveRecord(createRecord(e));
            // 签名验证失败，返回 401 UNAUTHORIZED 状态码
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
