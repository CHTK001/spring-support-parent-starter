package com.chua.starter.pay.support.order;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.text.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.time.date.DateTime;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.enums.PaymentPointState;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.rules.PayPaymentPointsRule;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.PrivacyEncryptor;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.*;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 微信小程序支付
 *
 * @author CH
 * @since 2025/10/14 13:51
 */
@Spi({"pay_wechat_payment_points"})
public class WechatPaymentPointsCreateOrderAdaptor extends WalletCreateOrderAdaptor {

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

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request, String userId, String openId) {
        RLock lock = redissonClient.getLock(PayConstant.CREATE_ORDER_PREFIX + request.getRequestId() + userId);

        lock.lock(3, TimeUnit.SECONDS);
        try {
            PayUserWallet payUserWallet = payUserWalletService.getByUser(userId);
            return transactionTemplate.execute(it -> {
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId, PayTradeType.PAY_WECHAT_PAYMENT_POINTS, PayOrderStatus.PAY_CREATE);
                return createWechatOrder(payMerchantOrder);
            });
        } catch (Exception e) {
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    private ReturnResult<CreateOrderV2Response> createWechatOrder(PayMerchantOrder payMerchantOrder) {
        PayMerchantConfigWechatWrapper byCodeForPayMerchantConfigWechat = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat(payMerchantOrder.getPayMerchantId(), payMerchantOrder.getPayMerchantTradeType().getName());
        if (!byCodeForPayMerchantConfigWechat.hasConfig()) {
            return ReturnResult.illegal("商户未开启配置");
        }
        PayMerchantConfigWechat payMerchantConfigWechat = byCodeForPayMerchantConfigWechat.getPayMerchantConfigWechat();
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();
        // 构建service
        PaymentPointRequest request = new PaymentPointRequest();
        String payMerchantOrderCode = payMerchantOrder.getPayMerchantOrderCode();
        request.setOutOrderNo(payMerchantOrderCode);
        request.setAppid(payMerchantConfigWechat.getPayMerchantConfigWechatAppId());
        request.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatPaymentPointNotifyUrl()
                + "/" + payMerchantOrderCode
        );
        request.setServiceId(payMerchantConfigWechat.getPayMerchantConfigWechatPaymentPointServiceId());
        request.setServiceIntroduction(payMerchantConfigWechat.getPayMerchantConfigWechatPaymentPointServiceName());
        request.setTimeRange(new PaymentPointRequest.TimeRangeDTO());
        request.getTimeRange().setStartTime("OnAccept");
        request.setRiskFund(new PaymentPointRequest.RiskFundDTO());
        request.getRiskFund().setName("ESTIMATE_ORDER_COST");
        request.getRiskFund().setAmount(payMerchantConfigWechat.getPayMerchantConfigWechatPaymentPointRiskAmount().intValue());
        registerRule(request, payMerchantConfigWechat);
        return createOrder(request, payMerchantOrder, config);
    }

    private ReturnResult<CreateOrderV2Response> createOrder(PaymentPointRequest request, PayMerchantOrder payMerchantOrder, Config config) {
        PrivacyEncryptor encryptor = config.createEncryptor();
        String requestPath = "https://api.mch.weixin.qq.com/v3/payscore/serviceorder";
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader(Constant.ACCEPT, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.CONTENT_TYPE, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.WECHAT_PAY_SERIAL, encryptor.getWechatpaySerial());
        HttpRequest httpRequest =
                new HttpRequest.Builder()
                        .httpMethod(HttpMethod.POST)
                        .url(requestPath)
                        .headers(headers)
                        .body(createRequestBody(request))
                        .build();
        HttpClient httpClient = new DefaultHttpClientBuilder().config(config).build();
        PaymentPointCreateResponse transferResponse = new PaymentPointCreateResponse();
        transferResponse.setOutBatchNo(payMerchantOrder.getPayMerchantOrderCode());
        try {
            HttpResponse<PaymentPointResponse> httpResponse =
                    httpClient.execute(httpRequest, PaymentPointResponse.class);
            PaymentPointResponse serviceResponse = httpResponse.getServiceResponse();
            transferResponse.setPackageInfo(serviceResponse.getPackageX());
            transferResponse.setState(PaymentPointState.parse(serviceResponse.getState()));
            transferResponse.setStateDescription(serviceResponse.getStateDescription());
            payMerchantOrder.setPayMerchantOrderFailureReason(transferResponse.getStateDescription());
            updateOrder(payMerchantOrder, transferResponse, serviceResponse);
        } catch (ServiceException e) {
            // 获取错误码和错误信息
            String errorMessage = e.getErrorMessage().trim();
            transferResponse.setState(PaymentPointState.FAILED);
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CREATE_FAILED);
            payMerchantOrder.setPayMerchantOrderFailureReason(errorMessage);
            payMerchantOrder.setPayMerchantOrderCreateTime(LocalDateTime.now());
            payMerchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
            transferResponse.setStateDescription(errorMessage);
            payMerchantOrderService.updateById(payMerchantOrder);
            return ReturnResult.error(errorMessage);
        }
        return ReturnResult.ok(new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode()));
    }

    /**
     * 更新订单
     *
     * @param payMerchantOrder 订单
     * @param transferResponse 响应
     * @param serviceResponse 响应
     */
    private void updateOrder(PayMerchantOrder payMerchantOrder, PaymentPointCreateResponse transferResponse, PaymentPointResponse serviceResponse) {
        if (transferResponse.getState() == PaymentPointState.CREATED) {
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CREATE);
            payMerchantOrder.setPayMerchantOrderCreateTime(LocalDateTime.now());
            payMerchantOrderService.updateById(payMerchantOrder);
            PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
            postprocessor.publish(payMerchantOrder);
            return;
        }

        if (transferResponse.getState() == PaymentPointState.FAILED) {
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CREATE_FAILED);
            payMerchantOrder.setPayMerchantOrderFailureReason(transferResponse.getStateDescription());
            payMerchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
            payMerchantOrderService.updateById(payMerchantOrder);
        }

        if (transferResponse.getState() == PaymentPointState.EXPIRED) {
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CREATE_FAILED);
            payMerchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
            payMerchantOrderService.updateById(payMerchantOrder);
            return;
        }

        if (transferResponse.getState() == PaymentPointState.DOING) {
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_WAITING);
            payMerchantOrderService.updateById(payMerchantOrder);
            return;
        }

        if (transferResponse.getState() == PaymentPointState.DONE) {
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_SUCCESS);
            PaymentPointResponse.TimeRangeDTO timeRange = serviceResponse.getTimeRange();
            try {
                payMerchantOrder.setPayMerchantOrderFinishedTime(DateTime.of(timeRange.getEndTime()).toLocalDateTime());
                payMerchantOrder.setPayMerchantOrderPayTime(DateTime.of(timeRange.getEndTime()).toLocalDateTime());
            } catch (Exception ignored) {
            }
            payMerchantOrderService.updateById(payMerchantOrder);
            return;
        }

        if (transferResponse.getState() == PaymentPointState.REVOKED) {
            payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CANCEL_SUCCESS);
            PaymentPointResponse.TimeRangeDTO timeRange = serviceResponse.getTimeRange();
            try {
                payMerchantOrder.setPayMerchantOrderFinishedTime(DateTime.of(timeRange.getEndTime()).toLocalDateTime());
                payMerchantOrder.setPayMerchantOrderPayTime(DateTime.of(timeRange.getEndTime()).toLocalDateTime());
            } catch (Exception ignored) {
            }
            payMerchantOrderService.updateById(payMerchantOrder);
        }
    }

    /**
     * 注册规则
     *
     * @param request                 请求
     * @param payMerchantConfigWechat 配置
     */
    private void registerRule(PaymentPointRequest request, PayMerchantConfigWechat payMerchantConfigWechat) {
        try {
            String payMerchantConfigWechatPaymentPointRule = payMerchantConfigWechat.getPayMerchantConfigWechatPaymentPointRule();
            if (null == payMerchantConfigWechatPaymentPointRule) {
                List<PaymentPointRequest.PostPaymentsDTO> postPayments = new ArrayList<>();
                postPayments.add(new PaymentPointRequest.PostPaymentsDTO());
                postPayments.getFirst().setName("计费规则");
                postPayments.getFirst().setDescription("具体标准查看小程序");
                request.setPostPayments(postPayments);
            } else {
                List<PaymentPointRequest.PostPaymentsDTO> postPayments = new ArrayList<>();
                List<PaymentPointRequest.PostPaymentsDTO> postPaymentsDTOS = Json.fromJsonToList(payMerchantConfigWechatPaymentPointRule, PaymentPointRequest.PostPaymentsDTO.class);
                for (PaymentPointRequest.PostPaymentsDTO postPaymentsDTO : postPaymentsDTOS) {
                    PaymentPointRequest.PostPaymentsDTO item = new PaymentPointRequest.PostPaymentsDTO();
                    item.setName(postPaymentsDTO.getName());
                    item.setDescription(PayPaymentPointsRule.create(payMerchantConfigWechat, postPaymentsDTO).getDescription(postPaymentsDTO));
                    postPayments.add(item);
                }
                request.setPostPayments(postPayments);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 构建请求
     *
     * @param payMerchantOrder        支付订单
     * @param payMerchantConfigWechat 微信配置
     * @return PrepayRequest
     */
    private PrepayRequest getPrepayRequest(PayMerchantOrder payMerchantOrder, PayMerchantConfigWechat payMerchantConfigWechat) {
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(payMerchantOrder.getPayMerchantOrderAmount().multiply(new BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).intValue());
        request.setAmount(amount);
        request.setAppid(payMerchantConfigWechat.getPayMerchantConfigWechatAppId());
        request.setMchid(payMerchantConfigWechat.getPayMerchantConfigWechatMchId());
        request.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatPayNotifyUrl() + "/" + payMerchantOrder.getPayMerchantOrderCode());
        request.setOutTradeNo(payMerchantOrder.getPayMerchantOrderCode());
        request.setAttach(payMerchantOrder.getPayMerchantOrderAttach());

        Payer payer = new Payer();
        payer.setOpenid(payMerchantOrder.getPayMerchantOrderOpenid());
        request.setPayer(payer);
        return request;
    }

}
