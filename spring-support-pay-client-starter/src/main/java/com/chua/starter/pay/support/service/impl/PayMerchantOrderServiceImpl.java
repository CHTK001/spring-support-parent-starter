package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.pojo.PayMerchantOrderPageRequest;
import com.chua.starter.pay.support.pojo.PayMerchantOrderVO;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.enums.PayTradeType;
import com.chua.starter.pay.support.event.CloseOrderEvent;
import com.chua.starter.pay.support.event.FinishPayOrderEvent;
import com.chua.starter.pay.support.event.RefundPayOrderEvent;
import com.chua.starter.pay.support.order.CreateOrderAdaptor;
import com.chua.starter.pay.support.order.CreateSignAdaptor;
import com.chua.starter.pay.support.pojo.*;
import com.chua.starter.pay.support.postprocessor.PayCreateOrderPostprocessor;
import com.chua.starter.pay.support.preprocess.PayCreateOrderPreprocess;
import com.chua.starter.pay.support.preprocess.PayPaymentPointsCreateOrderPreprocess;
import com.chua.starter.pay.support.preprocess.PayRefundOrderPreprocess;
import com.chua.starter.pay.support.refund.RefundOrderAdaptor;
import com.chua.starter.pay.support.service.PayMerchantOrderWaterService;
import com.chua.starter.pay.support.statemachine.PayOrderStateMachineService;
import com.chua.starter.pay.support.enums.PayOrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.pay.support.mapper.PayMerchantOrderMapper;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.*;

/**
 * 订单服务实现类（使用状态机管理订单状态流转）
 *
 * @author CH
 * @version 1.0
 * @since 2025/10/14 11:28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayMerchantOrderServiceImpl extends ServiceImpl<PayMerchantOrderMapper, PayMerchantOrder> implements PayMerchantOrderService {

    final TransactionTemplate transactionTemplate;
    final PayMerchantOrderWaterService payMerchantOrderWaterService;
    final ApplicationContext applicationContext;
    final PayOrderStateMachineService stateMachineService;

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreateOrderV2Request request) {
        String userId = RequestUtils.getUserId();
        String openId = RequestUtils.getUserInfo(UserResume.class).getOpenId();
        if (!request.hasTradeType()) {
            return ReturnResult.illegal("请选择交易类型");
        }

        if (!request.hasAmount()) {
            return ReturnResult.illegal("请选择金额");
        }

        if (null == request.getPayMerchantId()) {
            return ReturnResult.illegal("请选择商户");
        }
        //创建订单预处理 -> 支持SPI优先级覆盖
        PayCreateOrderPreprocess createOrderPreprocess = PayCreateOrderPreprocess.createProcessor();
        ReturnResult<CreateOrderV2Request> preprocess = createOrderPreprocess.preprocess(request, userId, openId);
        if (preprocess.isFailure()) {
            return ReturnResult.illegal(preprocess.getMsg());
        }
        //以预处理的结果作为依据, 预处理可能讲原始数据ID转成后端数据库中的金额, 防止前端参数异常
        request = preprocess.getData();
        CreateOrderAdaptor createOrderAdaptor = ServiceProvider.of(CreateOrderAdaptor.class).getNewExtension(request.getPayTradeType());
        if (null == createOrderAdaptor) {
            return ReturnResult.illegal("请选择正确的交易类型");
        }
        return createOrderAdaptor.createOrder(request, userId, openId);
    }

    @Override
    public ReturnResult<CreateOrderV2Response> createOrder(CreatePaymentPointsOrderV2Request request) {
        String userId = RequestUtils.getUserId();
        String openId = RequestUtils.getUserInfo(UserResume.class).getOpenId();

        if (null == request.getPayMerchantId()) {
            return ReturnResult.illegal("请选择商户");
        }
        //创建订单预处理 -> 支持SPI优先级覆盖
        PayPaymentPointsCreateOrderPreprocess createOrderPreprocess = PayPaymentPointsCreateOrderPreprocess.createProcessor();
        ReturnResult<CreatePaymentPointsOrderV2Request> preprocess = createOrderPreprocess.preprocess(request, userId, openId);
        if (preprocess.isFailure()) {
            return ReturnResult.illegal(preprocess.getMsg());
        }
        //以预处理的结果作为依据, 预处理可能讲原始数据ID转成后端数据库中的金额, 防止前端参数异常
        request = preprocess.getData();
        CreateOrderAdaptor createOrderAdaptor = ServiceProvider.of(CreateOrderAdaptor.class).getNewExtension(PayTradeType.PAY_WECHAT_PAYMENT_POINTS);
        if (null == createOrderAdaptor) {
            return ReturnResult.illegal("请选择正确的交易类型");
        }
        return createOrderAdaptor.createOrder(request.cloneAndGet(), userId, openId);
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
        if (null == merchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }

        CreateSignAdaptor createSignAdaptor = ServiceProvider.of(CreateSignAdaptor.class).getNewExtension(merchantOrder.getPayMerchantTradeType());
        if (null == createSignAdaptor) {
            return ReturnResult.illegal("当前订单不支持签名");
        }
        ReturnResult<PaySignResponse> sign = createSignAdaptor.createSign(merchantOrder, request.getPrepayId());
        if (sign.isSuccess()) {
            merchantOrder.setPayMerchantOrderPayTime(LocalDateTime.now());
            
            // 使用状态机转换状态：创建 -> 待支付
            boolean stateChanged = stateMachineService.sendEvent(
                    merchantOrder.getPayMerchantOrderCode(),
                    merchantOrder.getPayMerchantOrderStatus(),
                    PayOrderEvent.WAIT_PAY,
                    merchantOrder
            );
            
            if (stateChanged) {
                merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_WAITING);
                this.updateById(merchantOrder);
            }
        }
        return sign;
    }

    @Override
    public PayMerchantOrder getByCode(String payMerchantOrderCode) {
        return this.getOne(Wrappers.<PayMerchantOrder>lambdaQuery().eq(PayMerchantOrder::getPayMerchantOrderCode, payMerchantOrderCode), false);
    }

    @Override
    public boolean finishWechatOrder(PayMerchantOrder payMerchantOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            PayOrderStatus currentStatus = payMerchantOrder.getPayMerchantOrderStatus();
            
            // 使用状态机转换状态：待支付/支付中 -> 支付成功
            // Action 会自动处理订单状态更新、流水保存、时间字段更新等
            boolean stateChanged = stateMachineService.sendEvent(
                    payMerchantOrder.getPayMerchantOrderCode(),
                    currentStatus,
                    PayOrderEvent.PAY_SUCCESS,
                    payMerchantOrder
            );
            
            if (!stateChanged) {
                log.warn("订单状态转换失败, 订单编号: {}, 当前状态: {}", 
                        payMerchantOrder.getPayMerchantOrderCode(), 
                        currentStatus.getName());
                return false;
            }
            
            // 状态机 Action 已自动处理：
            // 1. 订单状态更新
            // 2. 流水保存
            // 3. 支付时间、完成时间等字段更新
            
            // 发布支付成功事件
            FinishPayOrderEvent finishPayOrderEvent = new FinishPayOrderEvent(payMerchantOrder.getPayMerchantOrderCode());
            finishPayOrderEvent.setPayMerchantOrder(payMerchantOrder);
            applicationContext.publishEvent(finishPayOrderEvent);
            
            return true;
        }));
    }

    @Override
    public boolean refundOrder(PayMerchantOrder payMerchantOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            PayOrderStatus targetStatus = payMerchantOrder.getPayMerchantOrderStatus();
            PayOrderStatus currentStatus = this.getByCode(payMerchantOrder.getPayMerchantOrderCode()).getPayMerchantOrderStatus();
            
            // 根据目标状态选择事件
            PayOrderEvent event;
            if (targetStatus == PayOrderStatus.PAY_REFUND_SUCCESS) {
                event = PayOrderEvent.REFUND_SUCCESS;
            } else if (targetStatus == PayOrderStatus.PAY_REFUND_PART_SUCCESS) {
                event = PayOrderEvent.REFUND_PART_SUCCESS;
            } else if (targetStatus == PayOrderStatus.PAY_REFUND_WAITING) {
                event = PayOrderEvent.REFUND;
            } else {
                log.warn("不支持的退款状态转换, 订单编号: {}, 目标状态: {}", 
                        payMerchantOrder.getPayMerchantOrderCode(), 
                        targetStatus.getName());
                return false;
            }
            
            // 使用状态机转换状态
            boolean stateChanged = stateMachineService.sendEvent(
                    payMerchantOrder.getPayMerchantOrderCode(),
                    currentStatus,
                    event,
                    payMerchantOrder
            );
            
            if (!stateChanged) {
                log.warn("订单退款状态转换失败, 订单编号: {}, 当前状态: {}, 目标状态: {}", 
                        payMerchantOrder.getPayMerchantOrderCode(), 
                        currentStatus.getName(),
                        targetStatus.getName());
                return false;
            }
            
            // 状态机 Action 已自动处理：
            // 1. 订单状态更新
            // 2. 流水保存
            // 3. 退款相关字段更新（退款编号、退款原因、退款时间等）
            
            // 发布退款成功事件
            RefundPayOrderEvent refundPayOrderEvent = new RefundPayOrderEvent(payMerchantOrder.getPayMerchantOrderCode());
            refundPayOrderEvent.setPayMerchantOrder(payMerchantOrder);
            applicationContext.publishEvent(refundPayOrderEvent);
            
            return true;
        }));
    }

    @Override
    public ReturnResult<RefundOrderV2Response> refundOrder(String payMerchantOrderCode, RefundOrderV2Request request) {
        PayMerchantOrder merchantOrder = this.getByCode(payMerchantOrderCode);
        ReturnResult<RefundOrderV2Request> stringReturnResult = hasReasonRefuse(merchantOrder, request);
        if (stringReturnResult.isFailure()) {
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
        if (stringReturnResult.isFailure()) {
            return ReturnResult.illegal(stringReturnResult.getMsg());
        }
        request = stringReturnResult.getData();
        RefundOrderAdaptor refundOrderAdaptor = ServiceProvider.of(RefundOrderAdaptor.class).getNewExtension(PayTradeType.PAY_WALLET);
        return refundOrderAdaptor.refundOrder(merchantOrder, request);
    }

    @Override
    public int timeout(Integer payMerchantId, Integer payMerchantOpenTimeoutTime) {
        // 查询超时的订单
        List<PayMerchantOrder> timeoutOrders = this.list(
                Wrappers.<PayMerchantOrder>lambdaQuery()
                        .eq(PayMerchantOrder::getPayMerchantId, payMerchantId)
                        .in(PayMerchantOrder::getPayMerchantOrderStatus, PayOrderStatus.PAY_WAITING, PayOrderStatus.PAY_CREATE)
                        .lt(PayMerchantOrder::getCreateTime, LocalDateTime.now().minusMinutes(payMerchantOpenTimeoutTime))
        );
        
        int count = 0;
        // 使用状态机逐个处理超时订单
        for (PayMerchantOrder order : timeoutOrders) {
            try {
                boolean stateChanged = stateMachineService.sendEvent(
                        order.getPayMerchantOrderCode(),
                        order.getPayMerchantOrderStatus(),
                        PayOrderEvent.TIMEOUT,
                        order
                );
                
                if (stateChanged) {
                    order.setPayMerchantOrderStatus(PayOrderStatus.PAY_TIMEOUT);
                    this.updateById(order);
                    count++;
                }
            } catch (Exception e) {
                log.error("订单超时状态转换失败, 订单编号: {}", order.getPayMerchantOrderCode(), e);
            }
        }
        
        return count;
    }

    @Override
    public ReturnResult<Boolean> closeOrder(String payMerchantOrderCode) {
        PayMerchantOrder merchantOrder = this.getByCode(payMerchantOrderCode);
        if (null == merchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }

        if (!merchantOrder.getUserId().equals(RequestUtils.getUserId())) {
            return ReturnResult.illegal("无权限");
        }

        PayOrderStatus payMerchantOrderStatus = merchantOrder.getPayMerchantOrderStatus();
        if(payMerchantOrderStatus == PayOrderStatus.PAY_REFUND_WAITING) {
            return ReturnResult.illegal("订单正在退款, 暂无法关闭");
        }

        try {
            // 使用状态机转换状态
            boolean stateChanged = stateMachineService.sendEvent(
                    payMerchantOrderCode,
                    payMerchantOrderStatus,
                    PayOrderEvent.CLOSE,
                    merchantOrder
            );
            
            if (!stateChanged) {
                return ReturnResult.illegal("当前订单状态无法关闭, 当前状态: " + payMerchantOrderStatus.getName());
            }
            
            merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_CLOSE_SUCCESS);
            int update = this.baseMapper.updateById(merchantOrder);
            
            if (update <= 0) {
                return ReturnResult.illegal("订单更新失败");
            }
            
            CloseOrderEvent closeOrderEvent = new CloseOrderEvent(payMerchantOrderCode);
            closeOrderEvent.setPayMerchantOrder(merchantOrder);
            applicationContext.publishEvent(closeOrderEvent);
        } catch (Exception e) {
            log.error("关闭订单失败, 订单编号: {}", payMerchantOrderCode, e);
            return ReturnResult.error("关闭订单失败: " + e.getMessage());
        }
        return ReturnResult.ok();
    }

    @Override
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_TEN_SECOND, key = "'PAY:ORDER:CODE:' +#payMerchantOrderCode" , keyGenerator = "customTenantedKeyGenerator")
    public PayOrderStatus getOrderStatus(String payMerchantOrderCode) {
        PayMerchantOrder merchantOrder = getByCode(payMerchantOrderCode);
        return ObjectUtils.defaultIfNull(merchantOrder.getPayMerchantOrderStatus(), PayOrderStatus.PAY_NOT_EXIST);
    }

    /**
     * 分页查询订单
     */
    @Override
    public IPage<PayMerchantOrderVO> pageForPayMerchantOrder(Query<PayMerchantOrder> page, PayMerchantOrder entity, PayMerchantOrderPageRequest cond) {
        Page<?> mpPage = page.createFullPage();
        return baseMapper.selectPageForOrder(mpPage, entity, cond);
    }

    /**
     * 检测订单是否可以退款
     *
     * @param merchantOrder 订单
     * @param request       退款参数
     * @return 检测订单是否可以退款
     */
    private ReturnResult<RefundOrderV2Request> hasReasonRefuse(PayMerchantOrder merchantOrder, RefundOrderV2Request request) {
        if (null == merchantOrder) {
            return ReturnResult.illegal("订单不存在");
        }

        String userId = RequestUtils.getUserId();
        if (!merchantOrder.getUserId().equals(userId)) {
            return ReturnResult.illegal("订单不属于当前用户");
        }

        PayRefundOrderPreprocess processor = PayRefundOrderPreprocess.createProcessor();
        ReturnResult<RefundOrderV2Request> preprocess = processor.preprocess(request, merchantOrder);
        if (preprocess.isFailure()) {
            return ReturnResult.illegal("当前订单不支持退款");
        }

        BigDecimal refundAmount = request.getRefundAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ReturnResult.error("退款金额不能小于零元");
        }

        BigDecimal decimal = merchantOrder.getPayMerchantOrderAmount();
        if (decimal.compareTo(refundAmount) < 0) {
            return ReturnResult.error("退款金额不能大于订单金额");
        }

        PayOrderStatus payMerchantOrderStatus = merchantOrder.getPayMerchantOrderStatus();
        if (payMerchantOrderStatus == PayOrderStatus.PAY_CREATE) {
            return ReturnResult.error("订单未支付");
        }

        if (payMerchantOrderStatus == PayOrderStatus.PAY_PAYING) {
            return ReturnResult.error("订单正在支付中");
        }

        if (payMerchantOrderStatus == PayOrderStatus.PAY_REFUND_SUCCESS) {
            return ReturnResult.error("订单已退款");
        }

        if (payMerchantOrderStatus == PayOrderStatus.PAY_CANCEL_SUCCESS) {
            return ReturnResult.error("订单已取消");
        }

        if (payMerchantOrderStatus == PayOrderStatus.PAY_TIMEOUT) {
            return ReturnResult.error("订单已超时");
        }

        if (payMerchantOrderStatus == PayOrderStatus.PAY_CLOSE_SUCCESS) {
            return ReturnResult.error("订单已关闭");
        }

        return ReturnResult.ok(preprocess.getData());
    }
}
