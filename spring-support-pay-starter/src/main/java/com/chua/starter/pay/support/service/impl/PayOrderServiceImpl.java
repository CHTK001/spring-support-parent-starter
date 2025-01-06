package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.rpc.RpcService;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.starter.common.support.utils.JakartaValidationUtils;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.configuration.PayListenerService;
import com.chua.starter.pay.support.constant.PayConstant;
import com.chua.starter.pay.support.emuns.TradeType;
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
import com.chua.starter.pay.support.service.PayService;
import com.chua.starter.pay.support.sign.CreateSign;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.Errors;

import java.util.concurrent.TimeUnit;

/**
 * 支付订单
 *
 * @author CH
 * @since 2024/12/30
 */
@Service
@RequiredArgsConstructor
@RpcService(interfaceClass = PayService.class)
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

        TradeType tradeType = request.getTradeType();
        if(null == tradeType) {
            return ReturnResult.illegal("交易类型不能为空");
        }
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CREATE_PREFIX + tradeType.getName() + request.getOrderId());
        if (!rLock.tryLock()) {
            return ReturnResult.illegal("订单已存在, 请勿重复下单");
        }

        rLock.lock(5, TimeUnit.SECONDS);
        try {
            return new CreateOrder(transactionTemplate, payMerchantService, payMerchantOrderMapper).create(request);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            rLock.unlock();
        }
    }

    @Override
    public ReturnPageResult<PayMerchantOrder> order(Query<PayMerchantOrder> query, PayOrderV1Request request, Integer sysUserId) {
        Errors validate = JakartaValidationUtils.validate(request, SelectGroup.class);
        if (validate.hasErrors()) {
            return ReturnPageResult.illegal(validate.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnPageResultUtils.ok(payMerchantOrderMapper.order(query.createPage(), request, sysUserId));
    }

    @Override
    public WechatOrderCallbackResponse notifyOrder(CallbackNotificationParser parser) {
        //订单号
        String id = parser.id();
        RLock rLock = redissonClient.getLock(PayConstant.ORDER_CALLBACK_PREFIX + id);
        if (!rLock.tryLock()) {
            return new WechatOrderCallbackResponse("FAIL", "正在处理", null);
        }

        rLock.lock(10, TimeUnit.SECONDS);
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
        } catch (Throwable e) {
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

        rLock.lock(10, TimeUnit.SECONDS);
        try {
            ReturnResult<PayRefundResponse> update = new RefundOrder(transactionTemplate, payMerchantService, payMerchantOrderMapper).update(refundRequest);
            if(update.isOk()) {
                payListenerService.listen(update.getData().getOrder());
            }
            return update;
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
        rLock.lock(5, TimeUnit.SECONDS);
        try {
            return new CreateSign(transactionTemplate, payMerchantService, payMerchantOrderMapper).create(request, payMerchantOrder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            rLock.unlock();
        }
    }

}
