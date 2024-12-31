package com.chua.starter.pay.support.order;

import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.CallbackNotificationParser;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.OrderCallbackRequest;
import com.chua.starter.pay.support.pojo.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.service.PayMerchantService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 更新订单
 *
 * @author CH
 * @since 2024/12/30
 */
public class UpdateOrder {
    private final TransactionTemplate transactionTemplate;
    private final PayMerchantService payMerchantService;
    private final PayMerchantOrderMapper payMerchantOrderMapper;

    public UpdateOrder(TransactionTemplate transactionTemplate, PayMerchantService payMerchantService, PayMerchantOrderMapper payMerchantOrderMapper) {
        this.transactionTemplate = transactionTemplate;
        this.payMerchantService = payMerchantService;
        this.payMerchantOrderMapper = payMerchantOrderMapper;
    }

    /**
     * 支付操作
     *
     * @param request   回调
     * @return 回调
     */
    public WechatOrderCallbackResponse update(OrderCallbackRequest request, PayMerchantOrder payMerchantOrder) {
        payMerchantOrder.setPayMerchantOrderStatus("2000");
        payMerchantOrder.setPayMerchantOrderTransactionId(request.getTransactionId());
        try {
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return new WechatOrderCallbackResponse("SUCCESS", "OK", null);
        } catch (Exception e) {
            return trySuccessUpdate(payMerchantOrder, request, 1, 3);
        }

    }

    /**
     * 更新
     *
     * @param payMerchantOrder           订单
     * @param wechatOrderCallbackRequest 回调
     * @param i                          循环
     * @param max                        最大循环
     * @return
     */
    private WechatOrderCallbackResponse trySuccessUpdate(PayMerchantOrder payMerchantOrder, OrderCallbackRequest wechatOrderCallbackRequest, int i, int max) {
        if (i >= max) {
            return new WechatOrderCallbackResponse("FAIL", "FAIL", null);
        }
        try {
            payMerchantOrder = getPayMerchantOrder(wechatOrderCallbackRequest);
            payMerchantOrder.setPayMerchantOrderTransactionId(wechatOrderCallbackRequest.getTransactionId());
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return new WechatOrderCallbackResponse("SUCCESS", "OK", null);
        } catch (Exception ignored) {
        }
        return trySuccessUpdate(payMerchantOrder, wechatOrderCallbackRequest, i + 1, max);
    }

    /**
     * 支付失败
     *
     * @param wechatOrderCallbackRequest 回调
     * @return 回调
     */
    public WechatOrderCallbackResponse failure(OrderCallbackRequest wechatOrderCallbackRequest, PayMerchantOrder payMerchantOrder) {
        payMerchantOrder.setPayMerchantOrderFailMessage(wechatOrderCallbackRequest.getMessage());
        payMerchantOrder.setPayMerchantOrderStatus("3000");
        try {
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return new WechatOrderCallbackResponse("SUCCESS", wechatOrderCallbackRequest.getMessage(), null);
        } catch (Exception e) {
            return tryFailureUpdate(payMerchantOrder, wechatOrderCallbackRequest, 1, 3);
        }
    }

    /**
     * 更新
     *
     * @param payMerchantOrder           订单
     * @param wechatOrderCallbackRequest 回调
     * @param i                          循环
     * @param max                        最大循环
     * @return
     */
    private WechatOrderCallbackResponse tryFailureUpdate(PayMerchantOrder payMerchantOrder, OrderCallbackRequest wechatOrderCallbackRequest, int i, int max) {
        if (i >= max) {
            return new WechatOrderCallbackResponse("FAILURE", wechatOrderCallbackRequest.getMessage(), null);
        }
        try {
            payMerchantOrder = getPayMerchantOrder(wechatOrderCallbackRequest);
            payMerchantOrder.setPayMerchantOrderFailMessage(wechatOrderCallbackRequest.getMessage());
            payMerchantOrder.setPayMerchantOrderStatus("3000");
            payMerchantOrderMapper.updateById(payMerchantOrder);
            return new WechatOrderCallbackResponse("SUCCESS", wechatOrderCallbackRequest.getMessage(), null);
        } catch (Exception ex) {
            return tryFailureUpdate(payMerchantOrder, wechatOrderCallbackRequest, i + 1, max);
        }
    }

    /**
     * 获取订单
     *
     * @param request 回调
     * @return 订单
     */
    private PayMerchantOrder getPayMerchantOrder(OrderCallbackRequest request) {
        return CallbackNotificationParser.getPayMerchantOrder(payMerchantOrderMapper, request);
    }
}
