package com.chua.starter.pay.support.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.net.UserAgent;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.pay.support.emuns.TradeType;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.PayConfigDetector;
import com.chua.starter.pay.support.handler.PayOrderCreator;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayOrderRequest;
import com.chua.starter.pay.support.pojo.PayReOrderRequest;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import com.chua.starter.pay.support.service.PayMerchantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 创建订单
 * @author CH
 * @since 2024/12/30
 */
public class ReCreateOrder {

    private final TransactionTemplate transactionTemplate;
    private final PayMerchantService payMerchantService;
    private final PayMerchantOrderService payMerchantOrderService;

    public ReCreateOrder(TransactionTemplate transactionTemplate, PayMerchantService payMerchantService, PayMerchantOrderService payMerchantOrderService) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantService = payMerchantService;
        this.payMerchantOrderService = payMerchantOrderService;
    }

    public ReturnResult<PayOrderResponse> create(PayReOrderRequest request) {
        PayMerchantOrder merchantOrder = payMerchantOrderService.getOne(Wrappers.<PayMerchantOrder>lambdaQuery().eq(PayMerchantOrder::getPayMerchantOrderCode, request.getPayMerchantOrderCode()));
        ReturnResult<String> checkResult = checkOrder(merchantOrder);
        if(!checkResult.isOk()) {
            return ReturnResult.of(checkResult);
        }
        ReturnResult<PayMerchant> payMerchantValue = payMerchantService.getOneByCode(merchantOrder.getPayMerchantCode());
        if(null == payMerchantValue || !payMerchantValue.isOk()) {
            return ReturnResult.illegal("商户不存在");
        }
        PayMerchant payMerchant = payMerchantValue.getData();

        if(null == payMerchant.getPayMerchantStatus() || payMerchant.getPayMerchantStatus() == 0) {
            return ReturnResult.illegal("商户未启用");
        }

        TradeType type = TradeType.valueOf(merchantOrder.getPayMerchantOrderTradeType());
        String tradeType = type.getName();
        PayConfigDetector<?> payConfigDetector = ServiceProvider.of(PayConfigDetector.class).getNewExtension(tradeType);
        if(null == payConfigDetector) {
            return ReturnResult.illegal("当前系统不支持该下单方式");
        }

        ReturnResult<?> checked = payConfigDetector.check(payMerchant, type);
        if(!checked.isOk()) {
            return ReturnResult.illegal(checked.getMsg());
        }

        PayMerchantOrder payMerchantOrder = new PayMerchantOrder();
        try {
            HttpServletRequest servletRequest = RequestUtils.getRequest();
            String header = servletRequest.getHeader("user-agent");
            UserAgent userAgent = UserAgent.parseUserAgentString(header);
            payMerchantOrder.setPayMerchantOrderBrowserSystem(userAgent.getOperatingSystem().getName());
            payMerchantOrder.setPayMerchantOrderBrowser(userAgent.getBrowser().toString());
        } catch (Exception ignored) {
        }
        payMerchantOrder.setPayMerchantOrderStatus("1000");
        return transactionTemplate.execute(status -> {
            PayOrderCreator payOrderCreator = ServiceProvider.of(PayOrderCreator.class).getNewExtension(tradeType, checked.getData());
            if(null == payOrderCreator) {
                return ReturnResult.illegal("当前系统不支持该"+ payMerchantOrder.getPayMerchantOrderTradeType() +"下单方式");
            }

            ReturnResult<PayOrderResponse> handle = payOrderCreator.handle(payMerchantOrder);
            if(handle.isOk()) {
                payMerchantOrderService.updateById(payMerchantOrder);
                PayOrderResponse handleData = handle.getData();
                handleData.setPayMerchantCode(payMerchantOrder.getPayMerchantOrderCode());
                payOrderCreator.onFinish(payMerchantOrder);
                return handle;
            }
            throw new RuntimeException(handle.getMsg());
        });
    }

    /**
     * 检查订单状态
     * @param merchantOrder 订单
     * @return 结果
     */
    private ReturnResult<String> checkOrder(PayMerchantOrder merchantOrder) {
        String payMerchantOrderStatus = merchantOrder.getPayMerchantOrderStatus();
        if("1000".equals(payMerchantOrderStatus)) {
            return ReturnResult.ok();
        }

        if(payMerchantOrderStatus.startsWith("200")) {
            return ReturnResult.illegal("订单已支付");
        }

        if(payMerchantOrderStatus.startsWith("500")) {
            return ReturnResult.illegal("订单已关闭");
        }

        return ReturnResult.illegal("订单状态异常");
    }
}
