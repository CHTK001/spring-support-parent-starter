package com.chua.starter.pay.support.order;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.net.UserAgent;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.entity.PayUserWallet;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.pojo.CreateOrderV2Request;
import com.chua.starter.pay.support.pojo.CreateOrderV2Response;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayUserWalletService;
import com.chua.starter.pay.support.service.impl.PayUserWalletServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

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
                PayMerchantOrder payMerchantOrder = createOrderObject(request, payUserWallet, openId);
                CreateOrderV2Response createOrderV2Response = new CreateOrderV2Response(payMerchantOrder.getPayMerchantOrderCode(), null);
                payUserWalletService.updateWallet(userId, payMerchantOrder);
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
    private PayMerchantOrder createOrderObject(CreateOrderV2Request request, PayUserWallet payUserWallet, String openId) {
        PayMerchantOrder payMerchantOrder = new PayMerchantOrder();
        payMerchantOrder.setPayMerchantOrderCode("P" + IdUtils.createTimeId(31));
        payMerchantOrder.setPayMerchantOrderOpenid(openId);
        payMerchantOrder.setPayMerchantOrderAmount(request.getAmount());
        payMerchantOrder.setPayMerchantOrderType(request.getPayMerchantOrderType());
        payMerchantOrder.setPayMerchantOrderProject(request.getPayMerchantOrderProject());
        try {
            HttpServletRequest servletRequest = RequestUtils.getRequest();
            String header = servletRequest.getHeader("user-agent");
            UserAgent userAgent = UserAgent.parseUserAgentString(header);
            payMerchantOrder.setPayMerchantOrderBrowserSystem(userAgent.getOperatingSystem().getName());
            payMerchantOrder.setPayMerchantOrderBrowser(userAgent.getBrowser().toString());
        } catch (Exception ignored) {
        }
        payMerchantOrder.setPayMerchantTradeType(PayTradeType.PAY_WALLET);
        payMerchantOrder.setPayMerchantOrderOriginId(request.getOriginalDataId());
        payMerchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_SUCCESS);
        payMerchantOrder.setPayMerchantId(request.getPayMerchantId());
        payMerchantOrder.setPayMerchantCurrentWalletAmount(payUserWallet.getPayUserWalletAmount());
        payMerchantOrderService.saveOrder(payMerchantOrder);
        return payMerchantOrder;
    }
}
