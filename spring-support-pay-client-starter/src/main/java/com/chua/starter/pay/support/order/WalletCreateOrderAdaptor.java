package com.chua.starter.pay.support.order;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.starter.common.support.utils.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.chua.starter.pay.support.service.impl.PayUserWalletServiceImpl;
import com.wechat.pay.java.core.http.JsonRequestBody;
import com.wechat.pay.java.core.http.RequestBody;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.wechat.pay.java.core.util.GsonUtil.toJson;

/**
 * 钱包创建订单适配器
 * @author CH
 * @since 2025/10/14 13:51
 */
@Spi("pay_wallet")
public class WalletCreateOrderAdaptor implements CreateOrderAdaptor{

    @AutoInject
    private PayUserWalletService payUserWalletService;

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
            if(payUserWallet.getPayUserWalletAmount().compareTo(request.getAmount()) < 0) {
                return ReturnResult.error("余额不足");
            }
            return transactionTemplate.execute(it -> {
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId, PayTradeType.PAY_WALLET, PayOrderStatus.PAY_SUCCESS);
                CreateOrderV2Response createOrderV2Response = new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode());
                payUserWalletService.updateWallet(userId, payMerchantOrder);
                PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
                postprocessor.publish(payMerchantOrder);
                return ReturnResult.ok(createOrderV2Response);
            });
        } catch (Exception e) {
            return ReturnResult.error(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建订单对象
     * @param request 请求
     * @param payUserWallet 钱包
     * @param openId openId
     * @return 订单
     */
    protected PayMerchantOrder createOrderObject(CreateOrderV2Request request,
                                               PayUserWallet payUserWallet,
                                               String openId,
                                               PayTradeType payTradeType,
                                               PayOrderStatus payOrderStatus) {
        PayMerchantOrder payMerchantOrder = new PayMerchantOrder();
        // 生成时间戳ID: P + 时间戳 + 随机数
        payMerchantOrder.setPayMerchantOrderCode("P" + System.currentTimeMillis() + (int)(Math.random() * 1000));
        payMerchantOrder.setPayMerchantOrderOpenid(openId);
        payMerchantOrder.setPayMerchantOrderAmount(request.getAmount());
        payMerchantOrder.setPayMerchantOrderType(request.getPayMerchantOrderType());
        payMerchantOrder.setPayMerchantOrderProject(request.getPayMerchantOrderProject());
        payMerchantOrder.setPayMerchantOrderCreateTime(LocalDateTime.now());
        try {
            HttpServletRequest servletRequest = RequestUtils.getRequest();
            String header = servletRequest.getHeader("login-agent");
            if (header != null && !header.isEmpty()) {
                // 简单的 User-Agent 解析，提取浏览器和操作系统信息
                String[] parts = header.split(" ");
                if (parts.length > 0) {
                    payMerchantOrder.setPayMerchantOrderBrowser(parts[0]);
                }
                // 操作系统信息可以从 User-Agent 中提取，这里简化处理
                if (header.contains("Windows")) {
                    payMerchantOrder.setPayMerchantOrderBrowserSystem("Windows");
                } else if (header.contains("Mac")) {
                    payMerchantOrder.setPayMerchantOrderBrowserSystem("Mac");
                } else if (header.contains("Linux")) {
                    payMerchantOrder.setPayMerchantOrderBrowserSystem("Linux");
                } else if (header.contains("Android")) {
                    payMerchantOrder.setPayMerchantOrderBrowserSystem("Android");
                } else if (header.contains("iOS")) {
                    payMerchantOrder.setPayMerchantOrderBrowserSystem("iOS");
                }
            }
        } catch (Exception ignored) {
        }
        payMerchantOrder.setPayMerchantTradeType(payTradeType);
        payMerchantOrder.setPayMerchantOrderOriginId(request.getOriginalDataId());
        payMerchantOrder.setPayMerchantOrderStatus(payOrderStatus);
        payMerchantOrder.setPayMerchantId(request.getPayMerchantId());
        payMerchantOrder.setPayMerchantCurrentWalletAmount(payUserWallet.getPayUserWalletAmount());
        if(payOrderStatus == PayOrderStatus.PAY_SUCCESS) {
            payMerchantOrder.setPayMerchantOrderPayTime(LocalDateTime.now());
            payMerchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
        }
        payMerchantOrderService.saveOrder(payMerchantOrder);
        return payMerchantOrder;
    }

    /**
     * 创建请求体
     * @param request 请求
     * @return 请求体
     */
    protected RequestBody createRequestBody(Object request) {
        return new JsonRequestBody.Builder().body(toJson(request)).build();
    }
}
