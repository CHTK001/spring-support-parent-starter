package com.chua.starter.tencent.support.payment.spi;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.tencent.support.factory.TencentWechatPayClientFactory;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认微信支付网关实现
 */
@Spi({"default", "wechat-pay"})
public class DefaultTencentWechatPayGateway implements TencentWechatPayGateway {

    private static final String DEFAULT_SIGN_TYPE = "WECHATPAY2-SHA256-RSA2048";
    private final TencentWechatPayClientFactory clientFactory = new TencentWechatPayClientFactory();

    @Override
    public TencentWechatPayResponse jsapiPay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        var service = clientFactory.createJsapiService(config);
        var prepayRequest = new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        prepayRequest.setAppid(properties.getAppId());
        prepayRequest.setMchid(properties.getMerchantId());
        prepayRequest.setDescription(request.getDescription());
        prepayRequest.setOutTradeNo(request.getOrderNo());
        prepayRequest.setNotifyUrl(request.getNotifyUrl());
        prepayRequest.setTimeExpire(request.getExpireTime());

        var amount = new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amount.setTotal(request.getAmountFen().intValue());
        amount.setCurrency(firstNonBlank(request.getCurrency(), "CNY"));
        prepayRequest.setAmount(amount);

        var payer = new com.wechat.pay.java.service.payments.jsapi.model.Payer();
        payer.setOpenid(request.getPayerOpenId());
        prepayRequest.setPayer(payer);

        var sceneInfo = new com.wechat.pay.java.service.payments.jsapi.model.SceneInfo();
        sceneInfo.setPayerClientIp(firstNonBlank(request.getClientIp(), "127.0.0.1"));
        sceneInfo.setDeviceId(request.getDeviceId());
        prepayRequest.setSceneInfo(sceneInfo);
        prepayRequest.setAttach(request.getAttach());

        var response = service.prepayWithRequestPayment(prepayRequest);
        Map<String, Object> sdkParams = new LinkedHashMap<>();
        sdkParams.put("appId", response.getAppId());
        sdkParams.put("timeStamp", response.getTimeStamp());
        sdkParams.put("nonceStr", response.getNonceStr());
        sdkParams.put("package", response.getPackageVal());
        sdkParams.put("signType", response.getSignType());
        sdkParams.put("paySign", response.getPaySign());

        TencentWechatPayResponse result = new TencentWechatPayResponse();
        result.setSuccess(true);
        result.setSdkParams(sdkParams);
        result.setMessage("微信 JSAPI 下单成功");
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    @Override
    public TencentWechatPayResponse h5Pay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        H5Service service = clientFactory.createH5Service(config);
        var prepayRequest = new com.wechat.pay.java.service.payments.h5.model.PrepayRequest();
        prepayRequest.setAppid(properties.getAppId());
        prepayRequest.setMchid(properties.getMerchantId());
        prepayRequest.setDescription(request.getDescription());
        prepayRequest.setOutTradeNo(request.getOrderNo());
        prepayRequest.setNotifyUrl(request.getNotifyUrl());
        prepayRequest.setTimeExpire(request.getExpireTime());

        var amount = new com.wechat.pay.java.service.payments.h5.model.Amount();
        amount.setTotal(request.getAmountFen().intValue());
        amount.setCurrency(firstNonBlank(request.getCurrency(), "CNY"));
        prepayRequest.setAmount(amount);

        var sceneInfo = new com.wechat.pay.java.service.payments.h5.model.SceneInfo();
        sceneInfo.setPayerClientIp(firstNonBlank(request.getClientIp(), "127.0.0.1"));
        sceneInfo.setDeviceId(request.getDeviceId());
        H5Info h5Info = new H5Info();
        h5Info.setType(firstNonBlank(request.getH5Type(), "Wap"));
        h5Info.setAppName(firstNonBlank(request.getAppName(), "payment-console"));
        h5Info.setAppUrl(request.getAppUrl());
        sceneInfo.setH5Info(h5Info);
        prepayRequest.setSceneInfo(sceneInfo);
        prepayRequest.setAttach(request.getAttach());

        var response = service.prepay(prepayRequest);
        TencentWechatPayResponse result = new TencentWechatPayResponse();
        result.setSuccess(true);
        result.setPayUrl(response.getH5Url());
        result.setMessage("微信 H5 下单成功");
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    @Override
    public TencentWechatOrderResponse queryJsapiOrder(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        var service = clientFactory.createJsapiService(config);
        var request = new com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        Transaction response = service.queryOrderByOutTradeNo(request);
        return toOrderResponse(response);
    }

    @Override
    public TencentWechatOrderResponse queryH5Order(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        H5Service service = clientFactory.createH5Service(config);
        var request = new com.wechat.pay.java.service.payments.h5.model.QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        Transaction response = service.queryOrderByOutTradeNo(request);
        return toOrderResponse(response);
    }

    @Override
    public boolean closeJsapiOrder(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        var service = clientFactory.createJsapiService(config);
        var request = new com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        service.closeOrder(request);
        return true;
    }

    @Override
    public boolean closeH5Order(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        H5Service service = clientFactory.createH5Service(config);
        var request = new com.wechat.pay.java.service.payments.h5.model.CloseOrderRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        service.closeOrder(request);
        return true;
    }

    @Override
    public TencentWechatPayResponse nativePay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        NativePayService service = clientFactory.createNativePayService(config);
        var prepayRequest = new com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest();
        prepayRequest.setAppid(properties.getAppId());
        prepayRequest.setMchid(properties.getMerchantId());
        prepayRequest.setDescription(request.getDescription());
        prepayRequest.setOutTradeNo(request.getOrderNo());
        prepayRequest.setNotifyUrl(request.getNotifyUrl());
        prepayRequest.setTimeExpire(request.getExpireTime());
        prepayRequest.setAttach(request.getAttach());
        var amount = new com.wechat.pay.java.service.payments.nativepay.model.Amount();
        amount.setTotal(request.getAmountFen().intValue());
        amount.setCurrency(firstNonBlank(request.getCurrency(), "CNY"));
        prepayRequest.setAmount(amount);
        var response = service.prepay(prepayRequest);
        TencentWechatPayResponse result = new TencentWechatPayResponse();
        result.setSuccess(true);
        result.setPayUrl(response.getCodeUrl());
        result.setMessage("微信 Native 支付下单成功");
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    @Override
    public TencentWechatOrderResponse queryNativeOrder(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        NativePayService service = clientFactory.createNativePayService(config);
        var request = new com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        Transaction response = service.queryOrderByOutTradeNo(request);
        return toOrderResponse(response);
    }

    @Override
    public boolean closeNativeOrder(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        NativePayService service = clientFactory.createNativePayService(config);
        var request = new com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        service.closeOrder(request);
        return true;
    }

    @Override
    public TencentWechatPayResponse appPay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        var service = clientFactory.createAppServiceExtension(config);
        var prepayRequest = new com.wechat.pay.java.service.payments.app.model.PrepayRequest();
        prepayRequest.setAppid(properties.getAppId());
        prepayRequest.setMchid(properties.getMerchantId());
        prepayRequest.setDescription(request.getDescription());
        prepayRequest.setOutTradeNo(request.getOrderNo());
        prepayRequest.setNotifyUrl(request.getNotifyUrl());
        prepayRequest.setTimeExpire(request.getExpireTime());
        prepayRequest.setAttach(request.getAttach());
        var amount = new com.wechat.pay.java.service.payments.app.model.Amount();
        amount.setTotal(request.getAmountFen().intValue());
        amount.setCurrency(firstNonBlank(request.getCurrency(), "CNY"));
        prepayRequest.setAmount(amount);
        var response = service.prepayWithRequestPayment(prepayRequest);
        TencentWechatPayResponse result = new TencentWechatPayResponse();
        result.setSuccess(true);
        Map<String, Object> sdkParams = new LinkedHashMap<>();
        sdkParams.put("appid", response.getAppid());
        sdkParams.put("partnerid", response.getPartnerId());
        sdkParams.put("prepayid", response.getPrepayId());
        sdkParams.put("package", response.getPackageVal());
        sdkParams.put("noncestr", response.getNonceStr());
        sdkParams.put("timestamp", response.getTimestamp());
        sdkParams.put("sign", response.getSign());
        result.setSdkParams(sdkParams);
        result.setMessage("微信 App 支付下单成功");
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    @Override
    public TencentWechatOrderResponse queryAppOrder(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        var service = clientFactory.createAppService(config);
        var request = new com.wechat.pay.java.service.payments.app.model.QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        Transaction response = service.queryOrderByOutTradeNo(request);
        return toOrderResponse(response);
    }

    @Override
    public boolean closeAppOrder(TencentWechatPayProperties properties, String orderNo) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        var service = clientFactory.createAppService(config);
        var request = new com.wechat.pay.java.service.payments.app.model.CloseOrderRequest();
        request.setMchid(properties.getMerchantId());
        request.setOutTradeNo(orderNo);
        service.closeOrder(request);
        return true;
    }

    @Override
    public TencentWechatPayResponse miniProgramPay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
        // 小程序支付与 JSAPI 使用相同通道，openId 必须是小程序 openId
        return jsapiPay(properties, request);
    }

    @Override
    public TencentWechatOrderResponse queryMiniProgramOrder(TencentWechatPayProperties properties, String orderNo) {
        return queryJsapiOrder(properties, orderNo);
    }

    @Override
    public boolean closeMiniProgramOrder(TencentWechatPayProperties properties, String orderNo) {
        return closeJsapiOrder(properties, orderNo);
    }

    @Override
    public TencentWechatRefundResponse refund(TencentWechatPayProperties properties, TencentWechatRefundRequest request) {
        RefundService service = clientFactory.createRefundService(clientFactory.createAutoCertificateConfig(properties));
        var createRequest = new com.wechat.pay.java.service.refund.model.CreateRequest();
        createRequest.setOutTradeNo(request.getOrderNo());
        createRequest.setTransactionId(request.getTradeNo());
        createRequest.setOutRefundNo(request.getRefundNo());
        createRequest.setReason(request.getReason());
        createRequest.setNotifyUrl(firstNonBlank(request.getNotifyUrl(), properties.getNotifyUrl()));

        var amount = new com.wechat.pay.java.service.refund.model.AmountReq();
        amount.setTotal(request.getTotalAmountFen());
        amount.setRefund(request.getRefundAmountFen());
        amount.setCurrency("CNY");
        createRequest.setAmount(amount);

        Refund response = service.create(createRequest);
        return toRefundResponse(response, request.getRefundNo(), request.getRefundAmountFen());
    }

    @Override
    public TencentWechatRefundResponse queryRefund(TencentWechatPayProperties properties, TencentWechatRefundRequest request) {
        RefundService service = clientFactory.createRefundService(clientFactory.createAutoCertificateConfig(properties));
        var queryRequest = new com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest();
        queryRequest.setOutRefundNo(request.getRefundNo());
        Refund response = service.queryByOutRefundNo(queryRequest);
        return toRefundResponse(response, request.getRefundNo(), request.getRefundAmountFen());
    }

    @Override
    public TencentWechatPayNotifyPayload parsePayNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request) {
        Transaction transaction = createNotificationParser(properties).parse(toRequestParam(request), Transaction.class);
        TencentWechatPayNotifyPayload payload = new TencentWechatPayNotifyPayload();
        payload.setOrderNo(transaction.getOutTradeNo());
        payload.setTransactionId(transaction.getTransactionId());
        payload.setAppId(transaction.getAppid());
        payload.setMerchantId(transaction.getMchid());
        payload.setTradeState(transaction.getTradeState() != null ? transaction.getTradeState().name() : null);
        payload.setTradeStateDesc(transaction.getTradeStateDesc());
        if (transaction.getAmount() != null) {
            payload.setTotalAmountFen(longValue(transaction.getAmount().getTotal()));
            payload.setPayerTotalAmountFen(longValue(transaction.getAmount().getPayerTotal()));
        }
        return payload;
    }

    @Override
    public TencentWechatRefundNotifyPayload parseRefundNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request) {
        RefundNotification notification = createNotificationParser(properties).parse(toRequestParam(request), RefundNotification.class);
        TencentWechatRefundNotifyPayload payload = new TencentWechatRefundNotifyPayload();
        payload.setOrderNo(notification.getOutTradeNo());
        payload.setRefundNo(notification.getOutRefundNo());
        payload.setRefundId(notification.getRefundId());
        payload.setRefundStatus(notification.getRefundStatus() != null ? notification.getRefundStatus().name() : null);
        if (notification.getAmount() != null) {
            payload.setTotalAmountFen(longValue(notification.getAmount().getTotal()));
            payload.setRefundAmountFen(longValue(notification.getAmount().getRefund()));
        }
        return payload;
    }

    private NotificationParser createNotificationParser(TencentWechatPayProperties properties) {
        return clientFactory.createNotificationParser(clientFactory.createAutoCertificateConfig(properties));
    }

    private RequestParam toRequestParam(TencentWechatNotifyRequest request) {
        return new RequestParam.Builder()
                .serialNumber(requiredText(request.getSerialNumber(), "微信支付通知缺少证书序列号"))
                .timestamp(requiredText(request.getTimestamp(), "微信支付通知缺少时间戳"))
                .nonce(requiredText(request.getNonce(), "微信支付通知缺少随机串"))
                .signature(requiredText(request.getSignature(), "微信支付通知缺少签名"))
                .signType(firstNonBlank(request.getSignType(), DEFAULT_SIGN_TYPE))
                .body(requiredText(request.getBody(), "微信支付通知缺少请求体"))
                .build();
    }

    private TencentWechatOrderResponse toOrderResponse(Transaction response) {
        TencentWechatOrderResponse result = new TencentWechatOrderResponse();
        result.setSuccess(true);
        result.setTransactionId(response.getTransactionId());
        result.setTradeState(response.getTradeState() != null ? response.getTradeState().name() : null);
        result.setTradeStateDesc(response.getTradeStateDesc());
        if (response.getAmount() != null) {
            result.setTotalAmountFen(longValue(response.getAmount().getTotal()));
            result.setPayerTotalAmountFen(longValue(response.getAmount().getPayerTotal()));
        }
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    private TencentWechatRefundResponse toRefundResponse(Refund response, String refundNo, Long fallbackRefundAmountFen) {
        TencentWechatRefundResponse result = new TencentWechatRefundResponse();
        result.setSuccess(true);
        result.setRefundNo(firstNonBlank(response.getOutRefundNo(), refundNo));
        result.setRefundId(response.getRefundId());
        result.setRefundStatus(response.getStatus() != null ? response.getStatus().name() : null);
        result.setMessage(response.getStatus() != null ? response.getStatus().name() : null);
        result.setRefundAmountFen(response.getAmount() != null && response.getAmount().getRefund() != null
                ? longValue(response.getAmount().getRefund())
                : fallbackRefundAmountFen);
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    private Long longValue(Number value) {
        return value == null ? null : value.longValue();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
