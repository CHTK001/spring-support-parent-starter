package com.chua.starter.tencent.support.payment.spi;

import com.chua.common.support.spi.annotations.Spi;
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
* @author CH
* @since 4.0.0
 */
@Spi({"default", "wechat-pay"})
public class DefaultTencentWechatPayGateway implements TencentWechatPayGateway {

    /** 默认_标志_类型 */
    private static final String DEFAULT_SIGN_TYPE = "WECHATPAY2-SHA256-RSA2048";
    /** Wechat_薪酬_基础_url */
    private static final String WECHAT_PAY_BASE_URL = "https://api.mch.weixin.qq.com";
    /** 客户端工厂 */
    private final TencentWechatPayClientFactory clientFactory = new TencentWechatPayClientFactory();
    /** Objectmapper */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    /** jsapi薪酬 */
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
    /** h薪酬 */
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
    /** 查询jsapi订单 */
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
    /** 查询订单 */
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
    /** 关闭jsapi订单 */
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
    /** 关闭订单 */
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
    /** NAT薪酬 */
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
    /** 查询NAT订单 */
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
    /** 关闭NAT订单 */
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
    /** app薪酬 */
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
    /** 查询app订单 */
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
    /** 关闭app订单 */
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
    /** miniprogram薪酬 */
    public TencentWechatPayResponse miniProgramPay(TencentWechatPayProperties properties, TencentWechatPayRequest request) {
 // 小程序支付与 JSAPI 使用相同通道，打开标识 必须是小程序 打开标识
        return jsapiPay(properties, request);
    }

    @Override
    /** 查询miniprogram订单 */
    public TencentWechatOrderResponse queryMiniProgramOrder(TencentWechatPayProperties properties, String orderNo) {
        return queryJsapiOrder(properties, orderNo);
    }

    @Override
    /** 关闭miniprogram订单 */
    public boolean closeMiniProgramOrder(TencentWechatPayProperties properties, String orderNo) {
        return closeJsapiOrder(properties, orderNo);
    }

    @Override
    /** 创建薪酬score订单 */
    public TencentWechatPayScoreResponse createPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        /**
    * 放入if是否包含文本。
    * @param body 主体
    * @param "appid" "appid"
    * @param firstNonBlank(request.getAppId( 第一个nonblank(请求.getappid(
    */
        putIfHasText(body, "appid", firstNonBlank(request.getAppId(), properties.getAppId()));
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "service_id" "服务_标识"
        * @param request.getServiceId( 请求.获取服务标识(
        */
        putIfHasText(body, "service_id", request.getServiceId());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "out_order_no" "出订单no"
        * @param request.getOutOrderNo( 请求.获取出订单no(
        */
        putIfHasText(body, "out_order_no", request.getOutOrderNo());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "openid" "openid"
        * @param request.getOpenId( 请求.获取打开标识(
        */
        putIfHasText(body, "openid", request.getOpenId());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "notify_url" "通知_url"
        * @param firstNonBlank(request.getNotifyUrl( 第一个nonblank(请求.获取通知url(
        */
        putIfHasText(body, "notify_url", firstNonBlank(request.getNotifyUrl(), properties.getNotifyUrl()));
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "service_introduction" "服务_introduction"
        * @param request.getServiceIntroduction( 请求.获取服务introduction(
        */
        putIfHasText(body, "service_introduction", request.getServiceIntroduction());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "attach" "attach"
        * @param request.getAttach( 请求.获取attach(
        */
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
        /**
        * 合并extra参数。
        * @param body 主体
        * @param request.getExtraParams( 请求.获取extra参数(
        */
        mergeExtraParams(body, request.getExtraParams());
        return callPayScoreApi(properties, "/v3/payscore/serviceorder", body, request.getOutOrderNo(), "微信支付分创建成功");
    }

    @Override
    /** 查询薪酬score订单 */
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
    /** 完成薪酬score订单 */
    public TencentWechatPayScoreResponse completePayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        /**
    * 放入if是否包含文本。
    * @param body 主体
    * @param "appid" "appid"
    * @param firstNonBlank(request.getAppId( 第一个nonblank(请求.getappid(
    */
        putIfHasText(body, "appid", firstNonBlank(request.getAppId(), properties.getAppId()));
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "service_id" "服务_标识"
        * @param request.getServiceId( 请求.获取服务标识(
        */
        putIfHasText(body, "service_id", request.getServiceId());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "finish_type" "饰面_类型"
        * @param request.getFinishType( 请求.获取饰面类型(
        */
        putIfHasText(body, "finish_type", request.getFinishType());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "reason" "ReasonMLML"
        * @param request.getReason( 请求.获取ReasonMLML(
        */
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
        /**
        * 合并extra参数。
        * @param body 主体
        * @param request.getExtraParams( 请求.获取extra参数(
        */
        mergeExtraParams(body, request.getExtraParams());
        return callPayScoreApi(properties,
                "/v3/payscore/serviceorder/" + urlEncode(request.getOutOrderNo()) + "/complete",
                body,
                request.getOutOrderNo(),
                "微信支付分完结成功");
    }

    @Override
    /** cancel薪酬score订单 */
    public TencentWechatPayScoreResponse cancelPayScoreOrder(TencentWechatPayProperties properties, TencentWechatPayScoreRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        /**
    * 放入if是否包含文本。
    * @param body 主体
    * @param "appid" "appid"
    * @param firstNonBlank(request.getAppId( 第一个nonblank(请求.getappid(
    */
        putIfHasText(body, "appid", firstNonBlank(request.getAppId(), properties.getAppId()));
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "service_id" "服务_标识"
        * @param request.getServiceId( 请求.获取服务标识(
        */
        putIfHasText(body, "service_id", request.getServiceId());
        /**
        * 放入if是否包含文本。
        * @param body 主体
        * @param "reason" "ReasonMLML"
        * @param request.getReason( 请求.获取ReasonMLML(
        */
        putIfHasText(body, "reason", request.getReason());
        /**
        * 合并extra参数。
        * @param body 主体
        * @param request.getExtraParams( 请求.获取extra参数(
        */
        mergeExtraParams(body, request.getExtraParams());
        return callPayScoreApi(properties,
                "/v3/payscore/serviceorder/" + urlEncode(request.getOutOrderNo()) + "/cancel",
                body,
                request.getOutOrderNo(),
                "微信支付分取消成功");
    }

    @Override
    /** 解析薪酬score通知 */
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
    /** 退税 */
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
    /** 查询退税 */
    public TencentWechatRefundResponse queryRefund(TencentWechatPayProperties properties, TencentWechatRefundRequest request) {
        RefundService service = clientFactory.createRefundService(clientFactory.createAutoCertificateConfig(properties));
        var queryRequest = new com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest();
        queryRequest.setOutRefundNo(request.getRefundNo());
        Refund response = service.queryByOutRefundNo(queryRequest);
        return toRefundResponse(response, request.getRefundNo(), request.getRefundAmountFen());
    }

    @Override
    /** 解析薪酬通知 */
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
    /** 解析退税通知 */
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

    /**
    * 创建notificationparser
    *
    * @param properties 属性
    * @return 创建notificationparser的结果
    */
    private NotificationParser createNotificationParser(TencentWechatPayProperties properties) {
        return clientFactory.createNotificationParser(clientFactory.createAutoCertificateConfig(properties));
    }

    /**
    * 转为请求参数
    *
    * @param request 请求
    * @return 转为请求参数的结果
    */
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

    /**
    * 转为订单响应
    *
    * @param response 响应
    * @return 转为订单响应的结果
    */
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

    /**
    * 转为退税响应
    *
    * @param response 响应
    * @param refundNo 退税no
    * @param fallbackRefundAmountFen 降级退税amountfen
    * @return 转为退税响应的结果
    */
    private TencentWechatRefundResponse toRefundResponse(Refund response, String refundNo, Long fallbackRefundAmountFen) {
        TencentWechatRefundResponse result = new TencentWechatRefundResponse();
        result.setSuccess(true);
        result.setRefundNo(firstNonBlank(response.getOutRefundNo(), refundNo));
        result.setRefundId(response.getRefundId());
        result.setRefundStatus(response.getStatus() != null ? response.getStatus().name() : null);
        result.setMessage(response.getStatus() != null ? response.getStatus().name() : null);
        result.setRefundAmountFen(response.getAmount() != null && response.getAmount().getRefund() != null
                /**
                * long值。
                * @param response.getAmount( 响应.获取amount(
                * @return long值的结果
                */
                ? longValue(response.getAmount().getRefund())
                : fallbackRefundAmountFen);
        result.setRawResponse(String.valueOf(response));
        return result;
    }

    /**
    * long值
    *
    * @param value 值
    * @return long值的结果
    */
    private Long longValue(Number value) {
        return value == null ? null : value.longValue();
    }

    /**
    * 调用薪酬scoreapi
    * @param properties 属性
    * @param path 路径
    * @param body 主体
    * @param outOrderNo 出订单no
    * @param successMessage 成功消息
    */
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

    /**
    * 转为薪酬score响应
    * @param body 主体
    * @param fallbackOutOrderNo 降级出订单no
    * @param successMessage 成功消息
    */
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

    /**
    * 构建时间范围
    *
    * @param request 请求
    * @return 构建时间范围的结果
    */
    private Map<String, Object> buildTimeRange(TencentWechatPayScoreRequest request) {
        Map<String, Object> timeRange = new LinkedHashMap<>();
        /**
        * 放入if是否包含文本。
        * @param timeRange 时间范围
        * @param "start_time" "启动_时间"
        * @param request.getStartTime( 请求.获取启动时间(
        */
        putIfHasText(timeRange, "start_time", request.getStartTime());
        /**
        * 放入if是否包含文本。
        * @param timeRange 时间范围
        * @param "end_time" "结束_时间"
        * @param request.getEndTime( 请求.获取结束时间(
        */
        putIfHasText(timeRange, "end_time", request.getEndTime());
        return timeRange;
    }

    /**
    * 合并extra参数
    *
    * @param body 主体
    * @param extraParams extra参数
    */
    private void mergeExtraParams(Map<String, Object> body, Map<String, Object> extraParams) {
        if (extraParams != null && !extraParams.isEmpty()) {
            body.putAll(extraParams);
        }
    }

    /**
    * 放入if是否拥有文本
    *
    * @param body 主体
    * @param key Key
    * @param value 值
    */
    private void putIfHasText(Map<String, Object> body, String key, String value) {
        if (StringUtils.hasText(value)) {
            body.put(key, value);
        }
    }

    /**
    * 第一个文本
    *
    * @param body 主体
    * @param keys Key
    * @return 第一个文本的结果
    */
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

    /**
    * Url编码
    *
    * @param value 值
    * @return urlEncode的结果
    */
    private String urlEncode(String value) {
        return URLEncoder.encode(requiredText(value, "微信支付分缺少必要参数"), StandardCharsets.UTF_8);
    }

    /**
    * 转为json
    *
    * @param value 值
    * @return 转为json的结果
    */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
    * 第一个nonblank
    *
    * @param values 值
    * @return 第一个nonblank的结果
    */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
    * required文本
    *
    * @param value 值
    * @param message 消息
    * @return required文本的结果
    */
    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            /**
            * illegal状态异常。
            * @param message 消息
            * @return illegal状态异常的结果
            */
            throw new IllegalStateException(message);
        }
        return value;
    }
}

