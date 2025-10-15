package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.event.FinishPayOrderEvent;
import com.chua.starter.pay.support.event.RefundPayOrderEvent;
import com.chua.starter.pay.support.order.CreateOrderAdaptor;
import com.chua.starter.pay.support.order.CreateSignAdaptor;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.preprocess.PayCreateOrderPreprocess;
import com.chua.starter.pay.support.preprocess.PayRefundOrderPreprocess;
import com.chua.starter.pay.support.refund.RefundOrderAdaptor;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *
 * @author CH
 * @since 2025/10/14 11:28
 */
@Service
@RequiredArgsConstructor
public class PayMerchantOrderServiceImpl extends ServiceImpl<PayMerchantOrderMapper, PayMerchantOrder> implements PayMerchantOrderService{

    final TransactionTemplate transactionTemplate;
    final PayMerchantOrderWaterService payMerchantOrderWaterService;
    final ApplicationContext applicationContext;

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request) {
        String userId = RequestUtils.getUserId();
        String openId = RequestUtils.getUserInfo(UserResume.class).getOpenId();
        if(!request.hasTradeType()) {
            return ReturnResult.illegal("请选择交易类型");
        }

        if(!request.hasAmount()) {
            return ReturnResult.illegal("请选择金额");
        }

        if(null == request.getPayMerchantId()) {
            return ReturnResult.illegal("请选择商户");
        }
        //创建订单预处理 -> 支持SPI优先级覆盖
        PayCreateOrderPreprocess createOrderPreprocess = PayCreateOrderPreprocess.createProcessor();
        ReturnResult<CreateOrderV2Request> preprocess = createOrderPreprocess.preprocess(request, userId, openId);
        if(preprocess.isFailure()) {
            return ReturnResult.illegal(preprocess.getMsg());
        }
        //以预处理的结果作为依据, 预处理可能讲原始数据ID转成后端数据库中的金额, 防止前端参数异常
        request = preprocess.getData();
        CreateOrderAdaptor createOrderAdaptor = ServiceProvider.of(CreateOrderAdaptor.class).getNewExtension(request.getPayTradeType());
        if(null == createOrderAdaptor) {
            return ReturnResult.illegal("请选择正确的交易类型");
        }
        ReturnResult<CreateOrderV2Response> order = createOrderAdaptor.createOrder(request, userId, openId);
        if(order.isOk()) {
            //后缀 处理
            PayCreateOrderPostprocessor postprocessor = PayCreateOrderPostprocessor.createProcessor();
            postprocessor.publish(order.getData());
        }
        return order;
    }

    @Override
    public boolean saveOrder(PayMerchantOrder payMerchantOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            this.save(payMerchantOrder);
            PayMerchantOrderWater payMerchantOrderWater = new PayMerchantOrderWater();
            payMerchantOrderWater.setPayMerchantOrderCode(payMerchantOrder.getPayMerchantOrderCode());
            payMerchantOrderWater.setPayMerchantOrderStatus(payMerchantOrder.getPayMerchantOrderStatus());
            payMerchantOrderWater.setPayMerchantOrderWaterCode("W" + IdUtils.createTimeId(31));
            return payMerchantOrderWaterService.save(payMerchantOrderWater);
        }));
    }

    @Override
    public ReturnResult<PaySignResponse> createSign(CreateOrderV2Response request) {
        PayMerchantOrder merchantOrder = this.getByCode(request.getPayMerchantOrderCode());
        if(null == merchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }

        CreateSignAdaptor createSignAdaptor = ServiceProvider.of(CreateSignAdaptor.class).getNewExtension(merchantOrder.getPayMerchantTradeType());
        if(null == createSignAdaptor) {
            return ReturnResult.illegal("当前订单不支持签名");
        }
        ReturnResult<PaySignResponse> sign = createSignAdaptor.createSign(merchantOrder, request.getPrepayId());
        if(sign.isSuccess()) {
            merchantOrder.setPayMerchantOrderPayTime(LocalDateTime.now());
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_WAITING);
            this.updateById(merchantOrder);
        }
        return sign;
    }

    @Override
    public PayMerchantOrder getByCode(String payMerchantOrderCode) {
        return this.getOne(Wrappers.<PayMerchantOrder>lambdaQuery().eq(PayMerchantOrder::getPayMerchantOrderCode, payMerchantOrderCode), false);
    }

    @Override
    public boolean updateWechatOrder(PayMerchantOrder payMerchantOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            payMerchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
            this.updateById(payMerchantOrder);
            PayMerchantOrderWater payMerchantOrderWater = new PayMerchantOrderWater();
            payMerchantOrderWater.setPayMerchantOrderCode(payMerchantOrder.getPayMerchantOrderCode());
            payMerchantOrderWater.setPayMerchantOrderStatus(payMerchantOrder.getPayMerchantOrderStatus());
            payMerchantOrderWater.setPayMerchantOrderWaterCode("W" + IdUtils.createTimeId(31));
            boolean save = payMerchantOrderWaterService.save(payMerchantOrderWater);
            if(save) {
                FinishPayOrderEvent finishPayOrderEvent = new FinishPayOrderEvent(payMerchantOrder.getPayMerchantOrderCode());
                finishPayOrderEvent.setPayMerchantOrder(payMerchantOrder);
                applicationContext.publishEvent(finishPayOrderEvent);
            }
            return save;
        }));
    }

    @Override
    public boolean updateRefundOrder(PayMerchantOrder payMerchantOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            payMerchantOrder.setPayMerchantOrderRefundSuccessTime(DateUtils.currentDateString());
            this.updateById(payMerchantOrder);
            PayMerchantOrderWater payMerchantOrderWater = new PayMerchantOrderWater();
            payMerchantOrderWater.setPayMerchantOrderCode(payMerchantOrder.getPayMerchantOrderCode());
            payMerchantOrderWater.setPayMerchantOrderStatus(payMerchantOrder.getPayMerchantOrderStatus());
            payMerchantOrderWater.setPayMerchantOrderWaterCode("W" + IdUtils.createTimeId(31));
            boolean save = payMerchantOrderWaterService.save(payMerchantOrderWater);
            if(save) {
                RefundPayOrderEvent refundPayOrderEvent = new RefundPayOrderEvent(payMerchantOrder.getPayMerchantOrderCode());
                refundPayOrderEvent.setPayMerchantOrder(payMerchantOrder);
                applicationContext.publishEvent(refundPayOrderEvent);
            }
            return save;
        }));
    }

    @Override
    public ReturnResult<RefundOrderV2Response> refundOrder(String payMerchantOrderCode, RefundOrderV2Request request) {
        PayMerchantOrder merchantOrder = this.getByCode(payMerchantOrderCode);
        ReturnResult<RefundOrderV2Request> stringReturnResult = hasReasonRefuse(merchantOrder, request);
        if(stringReturnResult.isFailure()) {
            return ReturnResult.illegal(stringReturnResult.getMsg());
        }

        request = stringReturnResult.getData();
        RefundOrderAdaptor refundOrderAdaptor = ServiceProvider.of(RefundOrderAdaptor.class).getNewExtension(merchantOrder.getPayMerchantTradeType());
        return refundOrderAdaptor.refundOrder(merchantOrder, request);
    }

    @Override
    public ReturnResult<RefundOrderV2Response> refundOrderToWallet(String payMerchantOrderCode, RefundOrderV2Request request) {
        PayMerchantOrder merchantOrder = this.getByCode(payMerchantOrderCode);
        ReturnResult<RefundOrderV2Request> stringReturnResult = hasReasonRefuse(merchantOrder, request);
        if(stringReturnResult.isFailure()) {
            return ReturnResult.illegal(stringReturnResult.getMsg());
        }
        request = stringReturnResult.getData();
        RefundOrderAdaptor refundOrderAdaptor = ServiceProvider.of(RefundOrderAdaptor.class).getNewExtension(PayTradeType.PAY_WALLET);
        return refundOrderAdaptor.refundOrder(merchantOrder, request);
    }

    /**
     * 检测订单是否可以退款
     * @param merchantOrder 订单
     * @param request 退款参数
     * @return 检测订单是否可以退款
     */
    private ReturnResult<RefundOrderV2Request> hasReasonRefuse(PayMerchantOrder merchantOrder, RefundOrderV2Request request) {
        if(null == merchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }

        String userId = RequestUtils.getUserId();
        if(!merchantOrder.getUserId().equals(userId)) {
            return ReturnResult.illegal("订单不属于当前用户");
        }

        PayRefundOrderPreprocess processor = PayRefundOrderPreprocess.createProcessor();
        ReturnResult<RefundOrderV2Request> preprocess = processor.preprocess(request, merchantOrder);
        if(preprocess.isFailure()) {
            return ReturnResult.illegal("当前订单不支持退款");
        }

        BigDecimal refundAmount = request.getRefundAmount();
        if(refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ReturnResult.error("退款金额不能小于零元");
        }

        BigDecimal decimal = merchantOrder.getPayMerchantOrderAmount();
        if(decimal.compareTo(refundAmount) < 0) {
            return ReturnResult.error("退款金额不能大于订单金额");
        }

        PayOrderStatus payMerchantOrderStatus = merchantOrder.getPayMerchantOrderStatus();
        if(payMerchantOrderStatus == PayOrderStatus.PAY_CREATE) {
            return ReturnResult.error("订单未支付");
        }

        if(payMerchantOrderStatus == PayOrderStatus.PAY_PAYING) {
            return ReturnResult.error("订单正在支付中");
        }

        if(payMerchantOrderStatus == PayOrderStatus.PAY_REFUND_SUCCESS) {
            return ReturnResult.error("订单已退款");
        }

        if(payMerchantOrderStatus == PayOrderStatus.PAY_CANCEL_SUCCESS) {
            return ReturnResult.error("订单已取消");
        }

        if(payMerchantOrderStatus == PayOrderStatus.PAY_TIMEOUT) {
            return ReturnResult.error("订单已超时");
        }

        if(payMerchantOrderStatus == PayOrderStatus.PAY_CLOSE_SUCCESS) {
            return ReturnResult.error("订单已关闭");
        }

        return ReturnResult.SUCCESS;
    }
}
