package com.chua.starter.pay.support.order;

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
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import com.chua.starter.pay.support.service.PayMerchantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 创建订单
 * @author CH
 * @since 2024/12/30
 */
public class CreateOrder {

    private final TransactionTemplate transactionTemplate;
    private final PayMerchantService payMerchantService;
    private final PayMerchantOrderWaterService payMerchantOrderWaterService;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public CreateOrder(TransactionTemplate transactionTemplate,
                       PayMerchantService payMerchantService,
                       PayMerchantOrderWaterService payMerchantOrderWaterService,
                       PayMerchantOrderMapper payMerchantOrderMapper) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantService = payMerchantService;
        this.payMerchantOrderWaterService = payMerchantOrderWaterService;
        this.payMerchantOrderMapper = payMerchantOrderMapper;
    }

    public ReturnResult<PayOrderResponse> create(PayOrderRequest request) {
        ReturnResult<PayMerchant> payMerchantValue = payMerchantService.getOneByCode(request.getMerchantCode());
        if(null == payMerchantValue || !payMerchantValue.isOk()) {
            return ReturnResult.illegal("商户不存在");
        }
        PayMerchant payMerchant = payMerchantValue.getData();

        if(null == payMerchant.getPayMerchantStatus() || payMerchant.getPayMerchantStatus() == 0) {
            return ReturnResult.illegal("商户未启用");
        }

        TradeType type = request.getTradeType();
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
        payMerchantOrder.setPayMerchantOrderTradeType(tradeType);
        payMerchantOrder.setPayMerchantCode(request.getMerchantCode());
        payMerchantOrder.setPayMerchantOrderUserId(request.getUserId());
        payMerchantOrder.setPayMerchantOrderOrigin(request.getOrigin());
        payMerchantOrder.setPayMerchantOrderAttach(request.getAttach());
        payMerchantOrder.setPayMerchantOrderProductName(request.getProductName());
        payMerchantOrder.setPayMerchantOrderProductCode(request.getProductCode());
        payMerchantOrder.setPayMerchantOrderRemark(request.getRemark());
        payMerchantOrder.setPayMerchantOrderPrice(request.getPrice());
        payMerchantOrder.setPayMerchantOrderCode("P" + IdUtils.createTimeId(31));
        payMerchantOrder.setPayMerchantOrderTotalPrice(request.getTotalPrice());
        payMerchantOrder.setPayMerchantCouponCode(request.getCouponCode());
        payMerchantOrder.setPayMerchantOrderStatus("1000");
        return transactionTemplate.execute(status -> {
            PayOrderCreator payOrderCreator = ServiceProvider.of(PayOrderCreator.class).getNewExtension(tradeType, checked.getData());
            if(null == payOrderCreator) {
                return ReturnResult.illegal("当前系统不支持该"+ payMerchantOrder.getPayMerchantOrderTradeType() +"下单方式");
            }

            ReturnResult<PayOrderResponse> handle = payOrderCreator.handle(payMerchantOrder);
            if(handle.isOk()) {
                payMerchantOrderMapper.insert(payMerchantOrder);
                payMerchantOrderWaterService.createOrderWater(payMerchantOrder);
                PayOrderResponse handleData = handle.getData();
                handleData.setPayMerchantCode(payMerchantOrder.getPayMerchantOrderCode());
                payOrderCreator.onFinish(payMerchantOrder);
                return handle;
            }
            throw new RuntimeException(handle.getMsg());
        });
    }
}
