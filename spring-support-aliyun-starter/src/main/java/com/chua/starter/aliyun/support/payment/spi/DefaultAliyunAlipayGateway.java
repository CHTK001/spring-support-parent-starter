package com.chua.starter.aliyun.support.payment.spi;

import com.alipay.api.AlipayApiException;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.annotations.Spi;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayDataDataserviceBillDownloadurlQueryModel;
import com.alipay.api.domain.AlipayTradeOrderSettleModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.OpenApiRoyaltyDetailInfoPojo;
import com.alipay.api.domain.RoyaltyDetail;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeOrderSettleQueryRequest;
import com.alipay.api.request.AlipayTradeOrderSettleRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeOrderSettleQueryResponse;
import com.alipay.api.response.AlipayTradeOrderSettleResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.chua.starter.aliyun.support.factory.AliyunAlipayClientFactory;
import com.chua.starter.aliyun.support.payment.AliyunAlipayGateway;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayNotifyRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayNotifyPayload;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayPayResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingQueryResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingReturnRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayProfitSharingReturnResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayRefundResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTradeQueryResponse;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTransferRequest;
import com.chua.starter.aliyun.support.payment.dto.AliyunAlipayTransferResponse;
import com.chua.starter.aliyun.support.properties.AliyunAlipayProperties;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 默认支付宝网关实现
 * @author CH
 * @since 4.0.0
 */
@Spi({"default", "alipay"})
public class DefaultAliyunAlipayGateway implements AliyunAlipayGateway {

    /**
     * 客户端工厂
    */
    private final AliyunAlipayClientFactory clientFactory = new AliyunAlipayClientFactory();

    @Override
    /**
     * page薪酬
     * @param properties 参数
     * @param request 参数
     * @return 返回值
    */
    public AliyunAlipayPayResponse pagePay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        try {
            AlipayTradePagePayRequest payRequest = new AlipayTradePagePayRequest();
            payRequest.setNotifyUrl(request.notifyUrl());
            payRequest.setReturnUrl(request.returnUrl());
            payRequest.setBizContent(toJson(buildPayBizContent(request)));
            AlipayTradePagePayResponse response = client(properties).pageExecute(payRequest);

            AliyunAlipayPayResponse result = new AliyunAlipayPayResponse(
                    StringUtils.hasText(response.getBody()),
                    response.getBody(),
                    response.getTradeNo(),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody()
            );
            return result;
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "发起支付宝电脑网站支付失败" "发起支付宝电脑网站支付失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("发起支付宝电脑网站支付失败", e);
        }
    }

    @Override
    /**
     * wap薪酬
     * @param properties 参数
     * @param request 参数
     * @return 返回值
    */
    public AliyunAlipayPayResponse wapPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        try {
            AlipayTradeWapPayRequest payRequest = new AlipayTradeWapPayRequest();
            payRequest.setNotifyUrl(request.notifyUrl());
            payRequest.setReturnUrl(request.returnUrl());
            payRequest.setBizContent(toJson(buildPayBizContent(request)));
            AlipayTradeWapPayResponse response = client(properties).pageExecute(payRequest);

            AliyunAlipayPayResponse result = new AliyunAlipayPayResponse(
                    StringUtils.hasText(response.getBody()),
                    response.getBody(),
                    response.getTradeNo(),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody()
            );
            return result;
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "发起支付宝手机网站支付失败" "发起支付宝手机网站支付失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("发起支付宝手机网站支付失败", e);
        }
    }

    @Override
    /**
     * app薪酬
     * @param properties 参数
     * @param request 参数
     * @return 返回值
    */
    public AliyunAlipayPayResponse appPay(AliyunAlipayProperties properties, AliyunAlipayPayRequest request) {
        try {
            AlipayTradeAppPayRequest payRequest = new AlipayTradeAppPayRequest();
            payRequest.setNotifyUrl(request.notifyUrl());
            payRequest.setBizContent(toJson(buildPayBizContent(request)));
            com.alipay.api.response.AlipayTradeAppPayResponse response = client(properties).sdkExecute(payRequest);

            AliyunAlipayPayResponse result = new AliyunAlipayPayResponse(
                    StringUtils.hasText(response.getBody()),
                    response.getBody(),
                    response.getTradeNo(),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody()
            );
            return result;
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param 支付失败" 支付失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("发起支付宝 App 支付失败", e);
        }
    }

    @Override
    /**
     * 查询订单
     * @param orderNo 字符串
     * @param properties 参数
     * @return 返回值
    */
    public AliyunAlipayTradeQueryResponse queryOrder(AliyunAlipayProperties properties, String orderNo) {
        try {
            AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
            queryRequest.setBizContent(toJson(Map.of("out_trade_no", orderNo)));
            AlipayTradeQueryResponse response = client(properties).execute(queryRequest);

            AliyunAlipayTradeQueryResponse result = new AliyunAlipayTradeQueryResponse(
                    response.isSuccess(),
                    response.getTradeNo(),
                    StringUtils.hasText(response.getTotalAmount()) ? new BigDecimal(response.getTotalAmount()) : null,
                    response.getTradeStatus(),
                    firstNonBlank(response.getSubMsg(), response.getMsg(), response.getTradeStatus()),
                    response.getBody()
            );
            return result;
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "查询支付宝订单失败" "查询支付宝订单失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("查询支付宝订单失败", e);
        }
    }

    @Override
    /**
     * 关闭订单
     * @param orderNo 字符串
     * @param properties 参数
     * @return 返回值
    */
    public boolean closeOrder(AliyunAlipayProperties properties, String orderNo) {
        try {
            AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
            closeRequest.setBizContent(toJson(Map.of("out_trade_no", orderNo)));
            return client(properties).execute(closeRequest).isSuccess();
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "关闭支付宝订单失败" "关闭支付宝订单失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("关闭支付宝订单失败", e);
        }
    }

    @Override
    /**
     * 退税
     * @param properties 参数
     * @param request 参数
     * @return 返回值
    */
    public AliyunAlipayRefundResponse refund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request) {
        try {
            AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", request.orderNo());
            bizContent.put("refund_amount", request.refundAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            bizContent.put("refund_reason", request.reason());
            bizContent.put("out_request_no", request.refundNo());
            refundRequest.setBizContent(toJson(bizContent));
            AlipayTradeRefundResponse response = client(properties).execute(refundRequest);

            AliyunAlipayRefundResponse result = new AliyunAlipayRefundResponse(
                    response.isSuccess(),
                    request.refundNo(),
                    response.getTradeNo(),
                    StringUtils.hasText(response.getRefundFee()) ? new BigDecimal(response.getRefundFee()) : request.refundAmount(),
                    response.isSuccess() ? "SUCCESS" : "FAILED",
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody()
            );
            return result;
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "支付宝退款失败" "支付宝退款失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("支付宝退款失败", e);
        }
    }

    @Override
    /**
     * 查询退税
     * @param properties 参数
     * @param request 参数
     * @return 返回值
    */
    public AliyunAlipayRefundResponse queryRefund(AliyunAlipayProperties properties, AliyunAlipayRefundRequest request) {
        try {
            AlipayTradeFastpayRefundQueryRequest queryRequest = new AlipayTradeFastpayRefundQueryRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", request.orderNo());
            bizContent.put("out_request_no", request.refundNo());
            queryRequest.setBizContent(toJson(bizContent));
            AlipayTradeFastpayRefundQueryResponse response = client(properties).execute(queryRequest);

            AliyunAlipayRefundResponse result = new AliyunAlipayRefundResponse(
                    response.isSuccess(),
                    response.getOutRequestNo(),
                    response.getTradeNo(),
                    StringUtils.hasText(response.getRefundAmount()) ? new BigDecimal(response.getRefundAmount()) : request.refundAmount(),
                    response.getRefundStatus(),
                    firstNonBlank(response.getErrorCode(), response.getSubMsg(), response.getMsg(), response.getRefundStatus()),
                    response.getBody()
            );
            return result;
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "查询支付宝退款失败" "查询支付宝退款失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("查询支付宝退款失败", e);
        }
    }

    @Override
    /**
     * 验证和解析薪酬通知
     * @param properties 属性
     * @param request 请求
     */
    public AliyunAlipayPayNotifyPayload verifyAndParsePayNotify(AliyunAlipayProperties properties,
                                                                AliyunAlipayNotifyRequest request) {
        Map<String, String> params = request.params();
        if (CollectionUtils.isEmpty(params)) {
            /**
             * illegal状态异常。
             * @param "支付宝通知参数不能为空" "支付宝通知参数不能为空"
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("支付宝通知参数不能为空");
        }
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipayPublicKey(),
                    /**
                     * 第一个nonblank。
                     * @param params.get("charset" 参数.获取("字符集"
                     */
                    firstNonBlank(params.get("charset"), properties.getCharset(), "UTF-8"),
                    firstNonBlank(params.get("sign_type"), properties.getSignType(), "RSA2"));
            if (!verified) {
                /**
                 * illegal状态异常。
                 * @param "支付宝通知验签失败" "支付宝通知验签失败"
                 * @return illegal状态异常的结果
                 */
                throw new IllegalStateException("支付宝通知验签失败");
            }
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "支付宝通知验签失败" "支付宝通知验签失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("支付宝通知验签失败", e);
        }

        return new AliyunAlipayPayNotifyPayload(
                params.get("out_trade_no"),
                params.get("trade_no"),
                params.get("app_id"),
                params.get("seller_id"),
                StringUtils.hasText(params.get("total_amount")) ? new BigDecimal(params.get("total_amount")) : null,
                params.get("trade_status"),
                params);
    }

    @Override
    /**
     * 调动
     * @param properties 参数
     * @param request 参数
     * @return 返回值
    */
    public AliyunAlipayTransferResponse transfer(AliyunAlipayProperties properties, AliyunAlipayTransferRequest request) {
        try {
            AlipayFundTransUniTransferRequest transferRequest = new AlipayFundTransUniTransferRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_biz_no", request.outBizNo());
            bizContent.put("trans_amount", request.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
            bizContent.put("product_code", "TRANS_ACCOUNT_NO_PWD");
            bizContent.put("biz_scene", "DIRECT_TRANSFER");
            bizContent.put("order_title", request.remark());
            Map<String, Object> payeeInfo = new LinkedHashMap<>();
            payeeInfo.put("identity", request.payeeAccount());
            payeeInfo.put("identity_type", "ALIPAY_LOGON_ID");
            payeeInfo.put("name", request.payeeName());
            bizContent.put("payee_info", payeeInfo);
            if (StringUtils.hasText(request.remark())) {
                bizContent.put("remark", request.remark());
            }
            transferRequest.setBizContent(toJson(bizContent));
            AlipayFundTransUniTransferResponse response = client(properties).execute(transferRequest);

            return new AliyunAlipayTransferResponse(
                    response.isSuccess(),
                    response.getOrderId(),
                    response.getOutBizNo(),
                    null,
                    response.isSuccess() ? "SUCCESS" : "FAIL",
                    response.getTransDate(),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody());
        } catch (AlipayApiException e) {
            /**
             * illegal状态异常。
             * @param "支付宝单笔转账失败" "支付宝单笔转账失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("支付宝单笔转账失败", e);
        }
    }

    /**
     * 客户端
     *
     * @param properties 属性
     * @return 客户端的结果
     */
    private AlipayClient client(AliyunAlipayProperties properties) {
        return clientFactory.createClient(properties);
    }

    /**
     * 构建薪酬biz内容
     *
     * @param request 请求
     * @return 构建薪酬biz内容的结果
     */
    private Map<String, Object> buildPayBizContent(AliyunAlipayPayRequest request) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", request.orderNo());
        bizContent.put("total_amount", request.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        bizContent.put("subject", firstNonBlank(request.subject(), request.orderNo()));
        bizContent.put("body", request.body());
        bizContent.put("product_code", request.productCode());
        if (StringUtils.hasText(request.payerOpenId())) {
            bizContent.put("buyer_id", request.payerOpenId());
        }
        return bizContent;
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
     * 转为json
     *
     * @param value 值
     * @return 转为json的结果
     */
    private String toJson(Object value) {
        try {
            return Json.toJson(value);
        } catch (Exception e) {
            /**
             * illegal状态异常。
             * @param "支付宝请求序列化失败" "支付宝请求序列化失败"
             * @param e e
             * @return illegal状态异常的结果
             */
            throw new IllegalStateException("支付宝请求序列化失败", e);
        }
    }

    @Override
    public AliyunAlipayProfitSharingResponse createProfitSharingOrder(AliyunAlipayProperties properties, AliyunAlipayProfitSharingRequest request) {
        if (request == null || !StringUtils.hasText(request.outRequestNo()) || !StringUtils.hasText(request.tradeNo())) {
            throw new IllegalArgumentException("支付宝分账创建缺少必要参数：outRequestNo 与 tradeNo 必填");
        }
        try {
            AlipayTradeOrderSettleRequest settleRequest = new AlipayTradeOrderSettleRequest();
            AlipayTradeOrderSettleModel model = new AlipayTradeOrderSettleModel();
            model.setOutRequestNo(request.outRequestNo());
            model.setTradeNo(request.tradeNo());
            model.setRoyaltyParameters(toRoyaltyDetails(request.royaltyParameters()));
            settleRequest.setBizModel(model);
            AlipayTradeOrderSettleResponse response = client(properties).execute(settleRequest);
            return new AliyunAlipayProfitSharingResponse(
                    response.isSuccess(),
                    response.getSettleNo(),
                    response.getTradeNo(),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody());
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝分账创建失败", e);
        }
    }

    @Override
    public AliyunAlipayProfitSharingQueryResponse queryProfitSharingOrder(AliyunAlipayProperties properties, String outRequestNo, String tradeNo) {
        if (!StringUtils.hasText(outRequestNo) && !StringUtils.hasText(tradeNo)) {
            throw new IllegalArgumentException("支付宝分账查询缺少必要参数：outRequestNo 与 tradeNo 至少填一项");
        }
        try {
            AlipayTradeOrderSettleQueryRequest queryRequest = new AlipayTradeOrderSettleQueryRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            if (StringUtils.hasText(outRequestNo)) {
                bizContent.put("out_request_no", outRequestNo);
            }
            if (StringUtils.hasText(tradeNo)) {
                bizContent.put("trade_no", tradeNo);
            }
            queryRequest.setBizContent(toJson(bizContent));
            AlipayTradeOrderSettleQueryResponse response = client(properties).execute(queryRequest);
            return new AliyunAlipayProfitSharingQueryResponse(
                    response.isSuccess(),
                    firstNonBlank(response.getOutRequestNo(), outRequestNo),
                    tradeNo,
                    toRoyaltyDetailMaps(response.getRoyaltyDetailList()),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody());
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝分账查询失败", e);
        }
    }

    @Override
    public AliyunAlipayProfitSharingReturnResponse returnProfitSharing(AliyunAlipayProperties properties, AliyunAlipayProfitSharingReturnRequest request) {
        if (request == null || !StringUtils.hasText(request.outBizNo()) || !StringUtils.hasText(request.tradeNo())
                || !StringUtils.hasText(request.transIn()) || request.returnAmount() == null) {
            throw new IllegalArgumentException("支付宝分账回退缺少必要参数：outBizNo/tradeNo/transIn/returnAmount 必填");
        }
        try {
            AlipayTradeRefundRequest refundRequest = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setTradeNo(request.tradeNo());
            model.setOutRequestNo(request.outBizNo());
            // 仅退分账：退款金额固定为 0，回退资金通过 refund_royalty_parameters 指定
            model.setRefundAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).toPlainString());
            model.setRefundReason(firstNonBlank(request.reason(), "分账回退"));
            OpenApiRoyaltyDetailInfoPojo detail = new OpenApiRoyaltyDetailInfoPojo();
            detail.setTransIn(request.transIn());
            detail.setAmount(AliyunAlipayProfitSharingRequest.amountText(request.returnAmount()));
            detail.setDesc(firstNonBlank(request.reason(), "分账回退"));
            model.setRefundRoyaltyParameters(Collections.singletonList(detail));
            refundRequest.setBizModel(model);
            AlipayTradeRefundResponse response = client(properties).execute(refundRequest);
            return new AliyunAlipayProfitSharingReturnResponse(
                    response.isSuccess(),
                    request.outBizNo(),
                    response.getTradeNo(),
                    firstNonBlank(response.getSubMsg(), response.getMsg()),
                    response.getBody());
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝分账回退失败", e);
        }
    }

    /**
     * 将分账明细原始列表转换为支付宝 SDK 明细对象列表
     *
     * @param royaltyParameters 分账明细原始列表，元素形如 {trans_in, amount, desc, royalty_type}
     * @return SDK 明细对象列表
     */
    private List<OpenApiRoyaltyDetailInfoPojo> toRoyaltyDetails(List<Map<String, Object>> royaltyParameters) {
        if (CollectionUtils.isEmpty(royaltyParameters)) {
            throw new IllegalArgumentException("支付宝分账明细不能为空");
        }
        List<OpenApiRoyaltyDetailInfoPojo> details = new ArrayList<>();
        for (Map<String, Object> item : royaltyParameters) {
            if (item == null) {
                continue;
            }
            OpenApiRoyaltyDetailInfoPojo detail = new OpenApiRoyaltyDetailInfoPojo();
            detail.setTransIn(textValue(item.get("trans_in")));
            detail.setTransInType(textValue(item.get("trans_in_type")));
            detail.setTransOut(textValue(item.get("trans_out")));
            detail.setTransOutType(textValue(item.get("trans_out_type")));
            detail.setAmount(textValue(item.get("amount")));
            detail.setDesc(textValue(item.get("desc")));
            detail.setRoyaltyType(textValue(item.get("royalty_type")));
            detail.setRoyaltyScene(textValue(item.get("royalty_scene")));
            details.add(detail);
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("支付宝分账明细不能为空");
        }
        return details;
    }

    /**
     * 将支付宝分账查询明细转换为原始 Map 列表
     *
     * @param royaltyDetails SDK 分账明细列表
     * @return 原始 Map 列表
     */
    private List<Map<String, Object>> toRoyaltyDetailMaps(List<RoyaltyDetail> royaltyDetails) {
        if (CollectionUtils.isEmpty(royaltyDetails)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (RoyaltyDetail detail : royaltyDetails) {
            if (detail == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("detail_id", detail.getDetailId());
            item.put("trans_in", detail.getTransIn());
            item.put("trans_in_type", detail.getTransInType());
            item.put("trans_out", detail.getTransOut());
            item.put("trans_out_type", detail.getTransOutType());
            item.put("amount", detail.getAmount());
            item.put("state", detail.getState());
            item.put("operation_type", detail.getOperationType());
            item.put("error_code", detail.getErrorCode());
            item.put("error_desc", detail.getErrorDesc());
            result.add(item);
        }
        return result;
    }

    /**
     * 对象转文本
     *
     * @param value 值
     * @return 文本值
     */
    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public String queryBillDownloadUrl(AliyunAlipayProperties properties, String billDate) {
        if (!StringUtils.hasText(billDate)) {
            throw new IllegalArgumentException("支付宝账单下载缺少账单日期");
        }
        try {
            AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
            AlipayDataDataserviceBillDownloadurlQueryModel model = new AlipayDataDataserviceBillDownloadurlQueryModel();
            model.setBillDate(billDate);
            model.setBillType("trade");
            request.setBizModel(model);
            AlipayDataDataserviceBillDownloadurlQueryResponse response = client(properties).execute(request);
            if (response == null || !response.isSuccess() || !StringUtils.hasText(response.getBillDownloadUrl())) {
                throw new IllegalStateException("支付宝账单下载未返回下载地址："
                        + (response == null ? "无响应" : firstNonBlank(response.getSubMsg(), response.getMsg())));
            }
            return response.getBillDownloadUrl();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("支付宝账单下载地址查询失败", e);
        }
    }
}


