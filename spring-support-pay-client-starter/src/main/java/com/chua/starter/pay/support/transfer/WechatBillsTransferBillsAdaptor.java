package com.chua.starter.pay.support.transfer;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.core.constant.Action;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.core.utils.CollectionUtils;
import com.chua.common.support.core.utils.IdUtils;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.entity.PayMerchantTransferRecord;
import com.chua.starter.pay.support.enums.WechatTransferBatchStatus;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.postprocessor.PayTransferPostprocessor;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantTransferRecordService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.PrivacyEncryptor;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.http.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.pay.support.enums.PayTradeType.PAY_WECHAT_JS_API;
import static com.wechat.pay.java.core.util.GsonUtil.toJson;

/**
 * 微信转账适配器
 *
 * @author CH
 * @since 2025/10/15 10:12
 */
@Slf4j
@Spi("PAY_WECHAT_BILLS")
public class WechatBillsTransferBillsAdaptor implements TransferBillsAdaptor {

    static final BigDecimal MIN_AMOUNT = new BigDecimal("0.03");
    static final BigDecimal PERCENTAGE = new BigDecimal("100");

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

    /**
     * 创建转账订单
     *
     * @param request 创建转账请求参数 {@link CreateTransferV2Request}
     * @return 转账结果 {@link CreateTransferV2Response}
     */
    @Override
    public ReturnResult<CreateTransferV2Response> createOrder(CreateTransferV2Request request) {
        PayMerchantConfigWechatWrapper payMerchantConfigWechatWrapper = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat
                (request.getMerchantId(), PAY_WECHAT_JS_API.getName());
        if (!payMerchantConfigWechatWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启微信支付");
        }

        PayMerchantConfigWechat payMerchantConfigWechat = payMerchantConfigWechatWrapper.getPayMerchantConfigWechat();
        RLock lock = redissonClient.getLock(PayConstant.CREATE_TRANSFER_PREFIX + request.getRequestId());

        lock.lock(3, TimeUnit.SECONDS);
        try {
            return transactionTemplate.execute(it -> {
                ReturnResult<CreateTransferV2Response> transferRecord = createTransferRecord(payMerchantConfigWechat, request);
                createTransferRecord(transferRecord, request);
                return transferRecord;
            });
        } catch (Exception e) {
            return ReturnResult.error("创建转账订单失败：" + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询转账状态
     *
     * @param request 查询转账请求参数 {@link QueryTransferV2Request}
     * @return 转账状态结果 {@link QueryTransferV2Response}
     */
    @Override
    public ReturnResult<QueryTransferV2Response> status(QueryTransferV2Request request) {
        PayMerchantConfigWechatWrapper payMerchantConfigWechatWrapper = payMerchantConfigWechatService.getByCodeForPayMerchantConfigWechat
                (request.getMerchantId(), PAY_WECHAT_JS_API.getName());
        if (!payMerchantConfigWechatWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启微信支付");
        }

        PayMerchantConfigWechat payMerchantConfigWechat = payMerchantConfigWechatWrapper.getPayMerchantConfigWechat();
        RLock lock = redissonClient.getLock(PayConstant.CREATE_TRANSFER_PREFIX + request.getTransferBillNo());

        lock.lock(3, TimeUnit.SECONDS);
        try {
            return transactionTemplate.execute(it -> {
                ReturnResult<TransferBillsStatusResponse> transferRecord = createQueryTransferRecord(payMerchantConfigWechat, request);
                return updateTransferRecord(transferRecord.getData(), request);
            });
        } catch (Exception e) {
            return ReturnResult.error("查询转账状态失败：" + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 更新转账订单状态
     *
     * @param data    转账状态数据 {@link TransferBillsStatusResponse}
     * @param request 查询请求参数 {@link QueryTransferV2Request}
     * @return 查询结果 {@link QueryTransferV2Response}
     */
    private ReturnResult<QueryTransferV2Response> updateTransferRecord(TransferBillsStatusResponse data, QueryTransferV2Request request) {
        PayMerchantTransferRecord payMerchantTransferRecord = payMerchantTransferRecordService.getByCode(request.getTransferBillNo());
        QueryTransferV2Response queryTransferV2Response = new QueryTransferV2Response();
        queryTransferV2Response.setState(data.getState());
        if (null != payMerchantTransferRecord) {
            payMerchantTransferRecord.setPayMerchantTransferRecordStatus(data.getState());
            payMerchantTransferRecord.setPayMerchantTransferRecordReason(data.getFailReason());
            payMerchantTransferRecordService.updateById(payMerchantTransferRecord);
            PayTransferPostprocessor processor = PayTransferPostprocessor.createProcessor();
            processor.publish(payMerchantTransferRecord, Action.UPDATE);
            return ReturnResult.success(queryTransferV2Response);
        }

        return ReturnResult.illegal("未找到转账订单");
    }

    /**
     * 查询转账订单状态
     *
     * @param payMerchantConfigWechat 微信商户配置信息 {@link PayMerchantConfigWechat}
     * @param request                 查询请求参数 {@link QueryTransferV2Request}
     * @return 转账状态结果 {@link TransferBillsStatusResponse}
     */
    private ReturnResult<TransferBillsStatusResponse> createQueryTransferRecord(PayMerchantConfigWechat payMerchantConfigWechat, QueryTransferV2Request request) {
        // 参数配置
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();

        PrivacyEncryptor encryptor = config.createEncryptor();
        String requestPath = "https://api.mch.weixin.qq.com/v3/fund-app/mch-transfer/transfer-bills/transfer-bill-no/" + request.getTransferBillNo();
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
            HttpResponse<TransferBillsStatusResponse> httpResponse =
                    httpClient.execute(httpRequest, TransferBillsStatusResponse.class);
            TransferBillsStatusResponse serviceResponse = httpResponse.getServiceResponse();
            return ReturnResult.ok(serviceResponse);
        } catch (ServiceException e) {
            // 获取错误码和错误信息
            String errorMessage = e.getErrorMessage().trim();
            return ReturnResult.error("查询转账订单失败：" + errorMessage);
        }
    }

    /**
     * 创建本地转账记录
     *
     * @param result  创建结果 {@link ReturnResult<CreateTransferV2Response>}
     * @param request 请求参数 {@link CreateTransferV2Request}
     */
    private ReturnResult<CreateTransferV2Response> createTransferRecord(ReturnResult<CreateTransferV2Response> result, CreateTransferV2Request request) {
        CreateTransferV2Response data = result.getData();
        PayMerchantTransferRecord payMerchantTransferRecord = new PayMerchantTransferRecord();
        payMerchantTransferRecord.setPayMerchantTransferRecordUserOpenid(request.getToUserOpenId());
        payMerchantTransferRecord.setPayMerchantTransferRecordAmount(request.getAmount());
        payMerchantTransferRecord.setPayMerchantTransferRecordCode(data.getOutBatchNo());
        if (result.isFailure()) {
            payMerchantTransferRecord.setPayMerchantTransferRecordReason(result.getMsg());
        }
        List<CreateTransferV2Response.Batch> data1 = data.getData();
        if (CollectionUtils.isNotEmpty(data1)) {
            payMerchantTransferRecord.setPayMerchantTransferRecordStatus(data1.getFirst().getStatus());
        }

        payMerchantTransferRecord.setCreateTime(LocalDateTime.now());
        payMerchantTransferRecord.setPayMerchantTransferRecordPhone(request.getPhone());
        payMerchantTransferRecord.setPayMerchantTransferRecordRealName(request.getRealName());
        payMerchantTransferRecord.setPayMerchantTransferRecordDescription(request.getDescription());
        payMerchantTransferRecord.setPayMerchantId(request.getMerchantId());
        payMerchantTransferRecord.setPayMerchantTransferRecordCreateTime(LocalDateTime.now());

        payMerchantTransferRecordService.save(payMerchantTransferRecord);

        PayTransferPostprocessor processor = PayTransferPostprocessor.createProcessor();
        processor.publish(payMerchantTransferRecord, Action.CREATE);
        return result;
    }

    /**
     * 创建微信转账订单
     *
     * @param payMerchantConfigWechat 微信商户配置信息 {@link PayMerchantConfigWechat}
     * @param request                 创建转账请求参数 {@link CreateTransferV2Request}
     * @return 创建结果 {@link CreateTransferV2Response}
     */
    private ReturnResult<CreateTransferV2Response> createTransferRecord(PayMerchantConfigWechat payMerchantConfigWechat,
                                                                        CreateTransferV2Request request) {
        // 参数配置
        Config config =
                new RSAAutoCertificateConfig.Builder()
                        .merchantId(payMerchantConfigWechat.getPayMerchantConfigWechatMchId())
                        .privateKeyFromPath(payMerchantConfigWechat.getPayMerchantConfigWechatPrivateKeyPath())
                        .merchantSerialNumber(payMerchantConfigWechat.getPayMerchantConfigWechatMchSerialNo())
                        .apiV3Key(payMerchantConfigWechat.getPayMerchantConfigWechatApiKeyV3())
                        .build();
        BigDecimal amount = request.getAmount();
        String realName = request.getRealName();
        TransferBillsRequest transferBillsRequest = new TransferBillsRequest();
        transferBillsRequest.setTransferAmount(amount.multiply(PERCENTAGE).intValue());
        transferBillsRequest.setOutBillNo("T" + IdUtils.createTimeId(31));
        transferBillsRequest.setTransferRemark(request.getDescription());
        transferBillsRequest.setTransferSceneId(request.getTransferSceneId());
        if (!StringUtils.isBlank(realName) && amount.compareTo(MIN_AMOUNT) > 0) {
            transferBillsRequest.setUserName(config.createEncryptor().encrypt(realName));
        }

        String outTradeNo = transferBillsRequest.getOutBillNo();
        transferBillsRequest.setOpenid(request.getToUserOpenId());
        transferBillsRequest.setAppid(payMerchantConfigWechat.getPayMerchantConfigWechatAppId());
        transferBillsRequest.setNotifyUrl(payMerchantConfigWechat.getPayMerchantConfigWechatTransferUrl() + "/" + outTradeNo
        );

        List<CreateTransferV2Request.TransferSceneReportInfo> transferSceneReportInfosList = new LinkedList<>();
        List<CreateTransferV2Request.TransferSceneReportInfo> transferSceneReportInfo = request.getTransferSceneReportInfo();
        for (CreateTransferV2Request.TransferSceneReportInfo sceneReportInfo : transferSceneReportInfo) {
            CreateTransferV2Request.TransferSceneReportInfo item1 = new CreateTransferV2Request.TransferSceneReportInfo();
            item1.setInfoType(sceneReportInfo.getInfoType());
            item1.setInfoContent(sceneReportInfo.getInfoContent());

            transferSceneReportInfosList.add(item1);
        }

        request.setTransferSceneReportInfo(transferSceneReportInfosList);

        PrivacyEncryptor encryptor = config.createEncryptor();
        String requestPath = "https://api.mch.weixin.qq.com/v3/fund-app/mch-transfer/transfer-bills";
        HttpHeaders headers = new HttpHeaders();
        headers.addHeader(Constant.ACCEPT, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.CONTENT_TYPE, MediaType.APPLICATION_JSON.getValue());
        headers.addHeader(Constant.WECHAT_PAY_SERIAL, encryptor.getWechatpaySerial());
        HttpRequest httpRequest =
                new HttpRequest.Builder()
                        .httpMethod(HttpMethod.POST)
                        .url(requestPath)
                        .headers(headers)
                        .body(createRequestBody(transferBillsRequest))
                        .build();
        HttpClient httpClient = new DefaultHttpClientBuilder().config(config).build();
        CreateTransferV2Response createTransferV2Response = new CreateTransferV2Response();
        createTransferV2Response.setOutBatchNo(outTradeNo);
        try {
            HttpResponse<TransferBillsResponse> httpResponse =
                    httpClient.execute(httpRequest, TransferBillsResponse.class);
            TransferBillsResponse serviceResponse = null;
            serviceResponse = httpResponse.getServiceResponse();
            createTransferV2Response.addBatch(CreateTransferV2Response.Batch.builder()
                    .batchId(serviceResponse.getTransferBillNo())
                    .packageInfo(serviceResponse.getPackageInfo())
                    .status(serviceResponse.getState())
                    .batchStatus(WechatTransferBatchStatus.parse(serviceResponse.getState()))
                    .build());
        } catch (ServiceException e) {
            // 获取错误码和错误信息
            String errorMessage = e.getErrorMessage().trim();
            createTransferV2Response.addBatch(CreateTransferV2Response.Batch.builder()
                    .batchStatus(WechatTransferBatchStatus.FAIL)
                    .error(errorMessage)
                    .build()
            );
            return ReturnResult.error("创建微信转账订单失败：" + errorMessage);
        }

        return ReturnResult.ok(createTransferV2Response);
    }

    /**
     * 将对象转换为JSON请求体
     *
     * @param request 请求对象
     * @return JSON请求体 {@link RequestBody}
     */
    private RequestBody createRequestBody(Object request) {
        return new JsonRequestBody.Builder().body(toJson(request)).build();
    }

    /**
     * 转账请求数据
     */
    @Data
    private static class TransferBillsRequest {
        /**
         * 转账金额（单位：分）
         */
        private Integer transferAmount;

        /**
         * 商户转账单号
         */
        private String outBillNo;

        /**
         * 转账备注
         */
        private String transferRemark;

        /**
         * 转账场景ID
         */
        private String transferSceneId;

        /**
         * 用户姓名（加密）
         */
        private String userName;

        /**
         * 用户openid
         */
        private String openid;

        /**
         * 应用ID
         */
        private String appid;

        /**
         * 回调地址
         */
        private String notifyUrl;
    }
}
