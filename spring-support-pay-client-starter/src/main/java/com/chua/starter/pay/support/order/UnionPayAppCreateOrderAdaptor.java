package com.chua.starter.pay.support.order;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantConfigUnionPay;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.pojo.PayMerchantConfigUnionPayWrapper;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.service.PayMerchantConfigUnionPayService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 云闪付APP支付创建订单适配器
 *
 * @author CH
 * @since 2025/10/15 16:00
 */
@Slf4j
@Spi("pay_unionpay_app")
public class UnionPayAppCreateOrderAdaptor extends WalletCreateOrderAdaptor {

    @AutoInject
    protected PayUserWalletService payUserWalletService;

    @AutoInject
    protected PayMerchantConfigUnionPayService payMerchantConfigUnionPayService;

    @AutoInject
    protected PayMerchantOrderService payMerchantOrderService;

    @AutoInject
    protected RedissonClient redissonClient;

    @AutoInject
    protected TransactionTemplate transactionTemplate;

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request, String userId, String openId) {
        RLock lock = redissonClient.getLock(PayConstant.CREATE_ORDER_PREFIX + request.getRequestId() + userId);

        lock.lock(3, TimeUnit.SECONDS);
        try {
            PayUserWallet payUserWallet = payUserWalletService.getByUser(userId);
            return transactionTemplate.execute(it -> {
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId, PayTradeType.PAY_UNIONPAY_APP, PayOrderStatus.PAY_CREATE);
                return createUnionPayOrder(payMerchantOrder);
            });
        } catch (Exception e) {
            log.error("[支付][创建订单]云闪付APP支付创建订单失败", e);
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建云闪付订单
     *
     * @param payMerchantOrder 支付订单
     * @return 创建订单结果
     */
    private ReturnResult<CreateOrderV2Response> createUnionPayOrder(PayMerchantOrder payMerchantOrder) {
        PayMerchantConfigUnionPayWrapper configWrapper = payMerchantConfigUnionPayService.getByCodeForPayMerchantConfigUnionPay(
                payMerchantOrder.getPayMerchantId(),
                payMerchantOrder.getPayMerchantTradeType().getName()
        );
        if (!configWrapper.hasConfig()) {
            return ReturnResult.illegal("商户未开启配置");
        }

        PayMerchantConfigUnionPay config = configWrapper.getPayMerchantConfigUnionPay();
        try {
            // TODO: 根据实际的云闪付 SDK 实现订单创建逻辑
            // 这里提供一个基础框架，需要根据实际的云闪付 API 进行实现
            // 云闪付通常使用银联的 SDK 或者 HTTP API
            
            // 示例：创建订单请求
            // UnionPayClient unionPayClient = createUnionPayClient(config);
            // UnionPayRequest unionPayRequest = buildUnionPayRequest(payMerchantOrder, config);
            // UnionPayResponse response = unionPayClient.execute(unionPayRequest);
            
            // 临时实现：返回订单号，实际需要调用云闪付 API
            CreateOrderV2Response createOrderV2Response = new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode());
            // createOrderV2Response.setPrepayId(response.getPrepayId());
            
            PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
            postprocessor.publish(payMerchantOrder);
            return ReturnResult.ok(createOrderV2Response);
        } catch (Exception e) {
            log.error("[支付][创建订单]云闪付APP支付创建订单异常", e);
            return ReturnResult.error("创建订单失败: " + e.getMessage());
        }
    }

    /**
     * 创建云闪付客户端
     *
     * @param config 云闪付配置
     * @return 云闪付客户端
     */
    protected Object createUnionPayClient(PayMerchantConfigUnionPay config) {
        // TODO: 根据实际的云闪付 SDK 创建客户端
        // 示例：
        // String gatewayUrl = config.getPayMerchantConfigUnionPayGatewayUrl();
        // if (gatewayUrl == null || gatewayUrl.isEmpty()) {
        //     gatewayUrl = "https://gateway.95516.com/gateway/api/";
        // }
        // return new UnionPayClient(gatewayUrl, config.getPayMerchantConfigUnionPayAppId(), ...);
        return null;
    }
}

