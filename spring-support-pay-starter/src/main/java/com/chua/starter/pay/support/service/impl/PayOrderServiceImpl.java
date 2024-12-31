package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.utils.JakartaValidationUtils;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.mapper.PayMerchantMapper;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.order.CreateOrder;
import com.chua.starter.pay.support.order.RefundOrder;
import com.chua.starter.pay.support.order.UpdateOrder;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.result.PayOrderResponse;
import com.chua.starter.pay.support.result.PayRefundResponse;
import com.chua.starter.pay.support.result.PaySignResponse;
import com.chua.starter.pay.support.service.PayOrderService;
import com.chua.starter.pay.support.sign.CreateSign;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.Errors;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付订单
 * @author CH
 * @since 2024/12/30
 */
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements PayOrderService {

    final PayMerchantMapper payMerchantMapper;
    final PayMerchantOrderMapper payMerchantOrderMapper;
    final RedissonClient redissonClient;
    final TransactionTemplate transactionTemplate;
    @Override
    public ReturnResult<PayOrderResponse> createOrder(PayOrderRequest request) {
        Errors errors = JakartaValidationUtils.validate(request, AddGroup.class);
        if(errors.hasErrors()) {
            return ReturnResult.illegal(errors.getAllErrors().get(0).getDefaultMessage());
        }

        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CREATE_PREFIX + request.getTradeType().getName() + request.getOrderId());
        if(!rLock.tryLock()) {
            return ReturnResult.illegal("订单已存在, 请勿重复下单");
        }

        rLock.lock();
        try {
            return new CreateOrder(transactionTemplate, payMerchantMapper, payMerchantOrderMapper).create(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public WechatOrderCallbackResponse notifyOrder(WechatOrderCallbackRequest wechatOrderCallbackRequest, String tradeType) {
        if ("FAIL".equals(wechatOrderCallbackRequest.getReturnCode())) {
           return new UpdateOrder(transactionTemplate, payMerchantMapper, payMerchantOrderMapper).failure(wechatOrderCallbackRequest, tradeType);
        }
        //微信订单号
        String transactionId = wechatOrderCallbackRequest.getTransactionId();
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CREATE_PREFIX + tradeType + transactionId);
        if(!rLock.tryLock()) {
            return new WechatOrderCallbackResponse("SUCCESS", "OK", null);
        }

        rLock.lock();
        try {
            return new UpdateOrder(transactionTemplate, payMerchantMapper, payMerchantOrderMapper).update(wechatOrderCallbackRequest, tradeType);
        } catch (Exception e) {
            throw new RuntimeException("通知失败，订单处理异常");
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public ReturnResult<PayRefundResponse> refund(PayRefundRequest refundRequest) {
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_REFUND_PREFIX);
        if(!rLock.tryLock()) {
            return ReturnResult.illegal("订单正在退款, 请勿重复操作");
        }

        rLock.lock();
        try {
            return new RefundOrder(transactionTemplate, payMerchantMapper, payMerchantOrderMapper).update(refundRequest);
        } catch (Exception e) {
            throw new RuntimeException("退款操作失败，请稍后重试");
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public WechatOrderCallbackResponse refundOrder(WechatOrderCallbackRequest wechatOrderCallbackRequest, String s) {
        return null;
    }

    @Override
    public ReturnResult<PaySignResponse> createSign(PaySignCreateRequest request) {
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CREATE_PREFIX + request.getTradeType() + request.getMerchantCode());
        if(!rLock.tryLock()) {
            return ReturnResult.illegal("签名正在生成, 请勿重复点击");
        }

        rLock.lock();
        try {
            return new CreateSign(transactionTemplate, payMerchantMapper, payMerchantOrderMapper).create(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rLock.unlock();
        }
    }

}
