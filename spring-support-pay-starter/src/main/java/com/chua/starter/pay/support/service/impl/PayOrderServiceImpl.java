package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.common.support.utils.JakartaValidationUtils;
import com.chua.starter.pay.support.configuration.PayListenerService;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.handler.CallbackNotificationParser;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.order.CreateOrder;
import com.chua.starter.pay.support.order.RefundOrder;
import com.chua.starter.pay.support.order.UpdateOrder;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PaySignResponse;
import com.chua.starter.pay.support.service.PayMerchantService;
import com.chua.starter.pay.support.service.PayOrderService;
import com.chua.starter.pay.support.sign.CreateSign;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.Errors;

/**
 * 支付订单
 *
 * @author CH
 * @since 2024/12/30
 */
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements PayOrderService {

    final PayMerchantService payMerchantService;
    final PayMerchantOrderMapper payMerchantOrderMapper;
    final RedissonClient redissonClient;
    final PayListenerService payListenerService;
    final TransactionTemplate transactionTemplate;
    final ApplicationContext applicationContext;

    @Override
    public ReturnResult<PayOrderResponse> createOrder(PayOrderRequest request) {
        Errors errors = JakartaValidationUtils.validate(request, AddGroup.class);
        if (errors.hasErrors()) {
            return ReturnResult.illegal(errors.getAllErrors().get(0).getDefaultMessage());
        }

        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CREATE_PREFIX + request.getTradeType().getName() + request.getOrderId());
        if (!rLock.tryLock()) {
            return ReturnResult.illegal("订单已存在, 请勿重复下单");
        }

        rLock.lock();
        try {
            return new CreateOrder(transactionTemplate, payMerchantService, payMerchantOrderMapper).create(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public WechatOrderCallbackResponse notifyOrder(CallbackNotificationParser parser) {
        //订单号
        String id = parser.id();
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CALLBACK_PREFIX + id);
        if (!rLock.tryLock()) {
            return new WechatOrderCallbackResponse("FAIL", "正在处理", null);
        }

        rLock.lock();
        try {
            if (!parser.parser(payMerchantService, payMerchantOrderMapper)) {
                return new WechatOrderCallbackResponse("FAIL", "订单不存在", null);
            }

            OrderCallbackRequest request = parser.getRequest();
            UpdateOrder updateOrder = new UpdateOrder(payMerchantOrderMapper);
            if (request.getBusinessStatus() == OrderCallbackRequest.Status.FAILURE) {
                return updateOrder.failure(request, parser.getOrder());
            }

            WechatOrderCallbackResponse success = updateOrder.success(request, parser.getOrder());
            payListenerService.listen(parser.getOrder());
            return success;
        } catch (Exception e) {
            throw new RuntimeException("通知失败，订单处理异常");
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public ReturnResult<PayRefundResponse> refund(PayRefundRequest refundRequest) {
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_REFUND_PREFIX);
        if (!rLock.tryLock()) {
            return ReturnResult.illegal("订单正在退款, 请勿重复操作");
        }

        rLock.lock();
        try {
            return new RefundOrder(transactionTemplate, payMerchantService, payMerchantOrderMapper).update(refundRequest);
        } catch (Exception e) {
            throw new RuntimeException("退款操作失败，请稍后重试");
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public WechatOrderCallbackResponse refundOrder(CallbackNotificationParser wechatOrderCallbackRequest) {
        return null;
    }


    @Override
    public ReturnResult<PaySignResponse> createSign(PaySignCreateRequest request) {
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_SIGN_PREFIX + request.getPayMerchantCode());
        if (!rLock.tryLock()) {
            return ReturnResult.illegal("签名正在生成, 请勿重复点击");
        }
        PayMerchantOrder payMerchantOrder = payMerchantOrderMapper.selectOne(Wrappers.lambdaQuery(PayMerchantOrder.class).eq(PayMerchantOrder::getPayMerchantOrderCode, request.getPayMerchantCode()));
        if (null == payMerchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }
        rLock.lock();
        try {
            return new CreateSign(transactionTemplate, payMerchantService, payMerchantOrderMapper).create(request, payMerchantOrder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rLock.unlock();
        }
    }

}
