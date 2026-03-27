package com.chua.starter.tencent.support.payment.spi;

import com.chua.common.support.core.annotation.Spi;
import com.chua.starter.tencent.support.factory.TencentWechatPayClientFactory;
import com.chua.starter.tencent.support.payment.TencentWechatPayGateway;
import com.chua.starter.tencent.support.payment.dto.TencentWechatNotifyRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatOrderResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatPayScoreResponse;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundNotifyPayload;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundRequest;
import com.chua.starter.tencent.support.payment.dto.TencentWechatRefundResponse;
import com.chua.starter.tencent.support.properties.TencentWechatPayProperties;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.http.HttpHeaders;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.service.payments.h5.H5Service;
import com.wechat.pay.java.service.payments.h5.model.H5Info;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认微信支付网关实现
 */
@Spi({"default", "wechat-pay"})
public class DefaultTencentWechatPayGateway implements TencentWechatPayGateway {

    private static final String DEFAULT_SIGN_TYPE = "WECHATPAY2-SHA256-RSA2048";
    private static final String WECHAT_PAY_BASE_URL = "https://api.mch.weixin.qq.com";
    private final TencentWechatPayClientFactory clientFactory = new TencentWechatPayClientFactory();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public TencentWechatPayScoreResponse createPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfHasText(body, "appid", firstNonBlank(request.getAppId(), properties.getAppId()));
        putIfHasText(body, "service_id", request.getServiceId());
        putIfHasText(body, "out_order_no", request.getOutOrderNo());
        putIfHasText(body, "openid", request.getOpenId());
        putIfHasText(body, "notify_url", firstNonBlank(request.getNotifyUrl(), properties.getNotifyUrl()));
        putIfHasText(body, "service_introduction", request.getServiceIntroduction());
        putIfHasText(body, "attach", request.getAttach());
        if (request.getNeedUserConfirm() != null) {
            body.put("need_user_confirm", request.getNeedUserConfirm());
        }
        Map<String, Object> timeRange = buildTimeRange(request);
        if (!timeRange.isEmpty()) {
            body.put("time_range", timeRange);
        }
        if (request.getPostPayments() != null && !request.getPostPayments().isEmpty()) {
            body.put("post_payments", request.getPostPayments());
        }
        if (request.getPostDiscounts() != null && !request.getPostDiscounts().isEmpty()) {
            body.put("post_discounts", request.getPostDiscounts());
        }
        if (request.getTotalAmountFen() != null) {
            body.put("total_amount", request.getTotalAmountFen());
        }
        mergeExtraParams(body, request.getExtraParams());
        return callPayScoreApi(properties, "/v3/payscore/serviceorder", body, request.getOutOrderNo(), "微信支付分创建成功");
    }

    @Override
    public TencentWechatPayScoreResponse queryPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        HttpClient client = clientFactory.createHttpClient(config);
        String url = WECHAT_PAY_BASE_URL
                + "/v3/payscore/serviceorder?out_order_no=" + urlEncode(request.getOutOrderNo())
                + "&service_id=" + urlEncode(request.getServiceId())
                + "&appid=" + urlEncode(firstNonBlank(request.getAppId(), properties.getAppId()));
        Map<String, Object> body = client.get(new HttpHeaders(), url, Map.class).getServiceResponse();
        return toPayScoreResponse(body, request.getOutOrderNo(), "微信支付分查询成功");
    }

    @Override
    public TencentWechatPayScoreResponse completePayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfHasText(body, "appid", firstNonBlank(request.getAppId(), properties.getAppId()));
        putIfHasText(body, "service_id", request.getServiceId());
        putIfHasText(body, "finish_type", request.getFinishType());
        putIfHasText(body, "reason", request.getReason());
        Map<String, Object> timeRange = buildTimeRange(request);
        if (!timeRange.isEmpty()) {
            body.put("time_range", timeRange);
        }
        if (request.getPostPayments() != null && !request.getPostPayments().isEmpty()) {
            body.put("post_payments", request.getPostPayments());
        }
        if (request.getPostDiscounts() != null && !request.getPostDiscounts().isEmpty()) {
            body.put("post_discounts", request.getPostDiscounts());
        }
        if (request.getTotalAmountFen() != null) {
            body.put("total_amount", request.getTotalAmountFen());
        }
        mergeExtraParams(body, request.getExtraParams());
        return callPayScoreApi(properties,
                "/v3/payscore/serviceorder/" + urlEncode(request.getOutOrderNo()) + "/complete",
                body,
                request.getOutOrderNo(),
                "微信支付分完结成功");
    }

    @Override
    public TencentWechatPayScoreResponse cancelPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfHasText(body, "appid", firstNonBlank(request.getAppId(), properties.getAppId()));
        putIfHasText(body, "service_id", request.getServiceId());
        putIfHasText(body, "reason", request.getReason());
        mergeExtraParams(body, request.getExtraParams());
        return callPayScoreApi(properties,
                "/v3/payscore/serviceorder/" + urlEncode(request.getOutOrderNo()) + "/cancel",
                body,
                request.getOutOrderNo(),
                "微信支付分取消成功");
    }

    @Override
    public TencentWechatPayScoreNotifyPayload parsePayScoreNotify(TencentWechatPayProperties properties, TencentWechatNotifyRequest request) {
        Map<String, Object> notification = createNotificationParser(properties).parse(toRequestParam(request), Map.class);
        TencentWechatPayScoreNotifyPayload payload = new TencentWechatPayScoreNotifyPayload();
        payload.setOutOrderNo(firstText(notification, "out_order_no", "outOrderNo"));
        payload.setServiceOrderNo(firstText(notification, "service_order_no", "serviceOrderNo"));
        payload.setAppId(firstText(notification, "appid", "appId"));
        payload.setServiceId(firstText(notification, "service_id", "serviceId"));
        payload.setOpenId(firstText(notification, "openid", "openId"));
        payload.setState(firstText(notification, "state", "service_state", "serviceState", "order_status", "orderStatus"));
        payload.setFinishReason(firstText(notification, "finish_reason", "finishReason"));
        payload.setRawData(notification);
        return payload;
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

    private TencentWechatPayScoreResponse callPayScoreApi(TencentWechatPayProperties properties,
                                                          String path,
                                                          Map<String, Object> body,
                                                          String outOrderNo,
                                                          String successMessage) {
        Config config = clientFactory.createAutoCertificateConfig(properties);
        HttpClient client = clientFactory.createHttpClient(config);
        String bodyText = toJson(body);
        Map<String, Object> response = client.post(
                new HttpHeaders(),
                WECHAT_PAY_BASE_URL + path,
                new JsonRequestBody.Builder().body(bodyText).build(),
                Map.class
        ).getServiceResponse();
        return toPayScoreResponse(response, outOrderNo, successMessage);
    }

    private TencentWechatPayScoreResponse toPayScoreResponse(Map<String, Object> body,
                                                             String fallbackOutOrderNo,
                                                             String successMessage) {
        TencentWechatPayScoreResponse response = new TencentWechatPayScoreResponse();
        response.setSuccess(true);
        response.setOutOrderNo(firstText(body, "out_order_no", "outOrderNo", fallbackOutOrderNo));
        response.setServiceOrderNo(firstText(body, "service_order_no", "serviceOrderNo"));
        response.setState(firstText(body, "state", "service_state", "serviceState", "order_status", "orderStatus"));
        response.setPackageInfo(firstText(body, "package_info", "packageInfo", "package"));
        response.setMessage(successMessage);
        response.setRawData(body);
        response.setRawResponse(toJson(body));
        return response;
    }

    private Map<String, Object> buildTimeRange(TencentWechatPayScoreRequest request) {
        Map<String, Object> timeRange = new LinkedHashMap<>();
        putIfHasText(timeRange, "start_time", request.getStartTime());
        putIfHasText(timeRange, "end_time", request.getEndTime());
        return timeRange;
    }

    private void mergeExtraParams(Map<String, Object> body, Map<String, Object> extraParams) {
        if (extraParams != null && !extraParams.isEmpty()) {
            body.putAll(extraParams);
        }
    }

    private void putIfHasText(Map<String, Object> body, String key, String value) {
        if (StringUtils.hasText(value)) {
            body.put(key, value);
        }
    }

    private String firstText(Map<String, Object> body, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = body.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(requiredText(value, "微信支付分缺少必要参数"), StandardCharsets.UTF_8);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
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
