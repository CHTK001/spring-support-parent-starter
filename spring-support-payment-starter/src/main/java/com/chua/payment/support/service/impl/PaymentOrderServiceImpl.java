package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentChannelRegistry;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.MerchantStatus;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.enums.OrderTransitionResult;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.OrderStateLogService;
import com.chua.payment.support.service.OrderStateMachineService;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.TransactionRecordService;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 支付订单服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private final PaymentOrderMapper orderMapper;
    private final MerchantMapper merchantMapper;
    private final MerchantChannelMapper merchantChannelMapper;
    private final OrderStateMachineService stateMachineService;
    private final OrderStateLogService orderStateLogService;
    private final TransactionRecordService transactionRecordService;
    private final PaymentChannelRegistry paymentChannelRegistry;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(OrderCreateDTO dto) {
        if (dto.getMerchantId() == null) {
            throw new PaymentException("商户ID不能为空");
        }
        if (dto.getOrderAmount() == null || dto.getOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("订单金额必须大于0");
        }

        Merchant merchant = requireActiveMerchant(dto.getMerchantId());
        MerchantChannel channel = resolveChannel(dto);
        String orderNo = generateOrderNo();
        String businessOrderNo = StringUtils.hasText(dto.getBusinessOrderNo()) ? dto.getBusinessOrderNo() : orderNo;
        PaymentOrder existing = findActiveOrderByBusinessOrderNo(dto.getMerchantId(), businessOrderNo);
        if (existing != null) {
            assertSameBusinessOrder(existing, dto, channel);
            return convertToVO(existing);
        }

        PaymentOrder order = new PaymentOrder();
        BeanUtils.copyProperties(dto, order);
        order.setOrderNo(orderNo);
        order.setBusinessOrderNo(businessOrderNo);
        order.setChannelId(channel.getId());
        order.setChannelType(channel.getChannelType());
        order.setChannelSubType(channel.getChannelSubType());
        order.setPaidAmount(BigDecimal.ZERO);
        order.setRefundAmount(BigDecimal.ZERO);
        order.setDiscountAmount(nullToZero(dto.getDiscountAmount()));
        order.setCurrency(StringUtils.hasText(dto.getCurrency()) ? dto.getCurrency() : "CNY");
        order.setStatus(OrderState.PENDING.name());
        order.setSubject(StringUtils.hasText(dto.getSubject()) ? dto.getSubject() : "支付订单");
        order.setBody(dto.getBody());
        order.setNotifyUrl(firstNonBlank(dto.getNotifyUrl(), channel.getNotifyUrl(), merchant.getDefaultNotifyUrl()));
        order.setReturnUrl(firstNonBlank(dto.getReturnUrl(), channel.getReturnUrl(), merchant.getDefaultReturnUrl()));
        order.setExpireTime(LocalDateTime.now().plusMinutes(resolveExpireMinutes(dto, merchant)));
        order.setDeleted(0);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            PaymentOrder duplicated = findActiveOrderByBusinessOrderNo(dto.getMerchantId(), businessOrderNo);
            if (duplicated != null) {
                assertSameBusinessOrder(duplicated, dto, channel);
                return convertToVO(duplicated);
            }
            throw new PaymentException("业务订单号已存在，订单创建失败", e);
        }

        stateMachineService.createStateMachine(order.getId());
        orderStateLogService.log(order.getId(), null, OrderState.PENDING, "CREATE", "system", "订单创建");
        return convertToVO(order);
    }

    @Override
    public OrderVO getOrder(Long id) {
        return convertToVO(requireOrder(id));
    }

    @Override
    public Page<OrderVO> listOrders(int page, int size, Long merchantId, String orderNo, String status) {
        LambdaQueryWrapper<PaymentOrder> wrapper = baseOrderWrapper()
                .orderByDesc(PaymentOrder::getCreatedAt);
        if (merchantId != null) {
            wrapper.eq(PaymentOrder::getMerchantId, merchantId);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(PaymentOrder::getOrderNo, orderNo);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PaymentOrder::getStatus, status);
        }
        Page<PaymentOrder> orderPage = orderMapper.selectPage(new Page<>(page, size), wrapper);
        Page<OrderVO> voPage = new Page<>(page, size, orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Page<OrderVO> listMerchantOrders(Long merchantId, int page, int size, String status) {
        return listOrders(page, size, merchantId, null, status);
    }

    @Override
    public List<OrderStateLogVO> listOrderLogs(Long id) {
        requireOrder(id);
        return orderStateLogService.listByOrderId(id).stream().map(this::convertLogToVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResult payOrder(Long id, OrderPayDTO dto) {
        PaymentOrder order = requireOrder(id);
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        String operator = defaultOperator(dto != null ? dto.getOperator() : null);
        ensurePaying(order, operator);

        PaymentRequest request = new PaymentRequest();
        request.setOrderNo(order.getOrderNo());
        request.setUserId(order.getUserId());
        request.setAmount(order.getOrderAmount());
        request.setSubject(order.getSubject());
        request.setBody(order.getBody());
        request.setNotifyUrl(order.getNotifyUrl());
        request.setReturnUrl(order.getReturnUrl());
        request.setCurrency(order.getCurrency());
        request.setExpireTime(order.getExpireTime());
        request.setPayerOpenId(dto != null ? dto.getPayerOpenId() : null);
        request.setClientIp(dto != null ? dto.getClientIp() : null);
        request.setDeviceId(dto != null ? dto.getDeviceId() : null);
        request.setUserAgent(dto != null ? dto.getUserAgent() : null);
        request.setAttach(order.getBusinessOrderNo());

        PaymentResult result = paymentChannel.pay(channel, request);
        PaymentOrder latest = requireOrder(id);
        if (StringUtils.hasText(result.getTradeNo())) {
            latest.setThirdPartyOrderNo(result.getTradeNo());
            orderMapper.updateById(latest);
        }
        if ("PAID".equals(result.getStatus())) {
            paySuccess(id,
                    result.getPaidAmount() != null ? result.getPaidAmount() : latest.getOrderAmount(),
                    result.getTradeNo(),
                    operator);
            return result;
        }
        if ("FAILED".equals(result.getStatus())) {
            payFail(id, firstNonBlank(result.getMessage(), "支付失败"), operator);
            return result;
        }
        if ("CANCELLED".equals(result.getStatus())) {
            cancelOrder(id, operator, firstNonBlank(result.getMessage(), "订单已关闭"));
            return result;
        }
        recordTransaction(latest,
                "PAY",
                latest.getOrderAmount(),
                transactionStatusOfPaymentResult(result),
                result.getTradeNo(),
                safeJson(request),
                safeJson(result),
                "真实支付发起");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO syncOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        PaymentResult result = paymentChannel.query(channel, order.getOrderNo());
        if ("PAID".equals(result.getStatus())) {
            paySuccess(id, result.getPaidAmount(), result.getTradeNo(), "channel-sync");
        } else if ("FAILED".equals(result.getStatus())) {
            payFail(id, firstNonBlank(result.getMessage(), "渠道返回支付失败"), "channel-sync");
        } else if ("CANCELLED".equals(result.getStatus()) && canCancelByState(requireOrder(id).getStatus())) {
            cancelOrder(id, "channel-sync", firstNonBlank(result.getMessage(), "渠道返回订单关闭"));
        }
        return convertToVO(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startPay(Long id, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.PAYING.name().equals(order.getStatus()) || isPaidState(order.getStatus())) {
            return true;
        }
        if (!OrderState.PENDING.name().equals(order.getStatus())) {
            throw new PaymentException("当前订单状态不允许发起支付");
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY, defaultOperator(operator));
        if (transitionResult == OrderTransitionResult.APPLIED) {
            recordTransaction(order, "PAY", order.getOrderAmount(), 2, null, null, null, "发起支付");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator) {
        PaymentOrder order = requireOrder(id);
        if (isPaidState(order.getStatus())) {
            backfillPaymentSuccess(order, paidAmount, thirdPartyOrderNo);
            return true;
        }
        if (!(OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许标记支付成功");
        }
        ensurePaying(order, defaultOperator(operator));
        order = requireOrder(id);
        if (isPaidState(order.getStatus())) {
            backfillPaymentSuccess(order, paidAmount, thirdPartyOrderNo);
            return true;
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY_SUCCESS, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        backfillPaymentSuccess(latest, paidAmount, thirdPartyOrderNo);
        latest = requireOrder(id);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            recordTransaction(latest, "PAY", latest.getPaidAmount(), 1, thirdPartyOrderNo, null, null, "支付成功");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payFail(Long id, String reason, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.FAILED.name().equals(order.getStatus())) {
            backfillRemark(order, reason);
            return true;
        }
        if (!(OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许标记支付失败");
        }
        ensurePaying(order, defaultOperator(operator));
        order = requireOrder(id);
        if (OrderState.FAILED.name().equals(order.getStatus())) {
            backfillRemark(order, reason);
            return true;
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY_FAIL, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        backfillRemark(latest, reason);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            recordTransaction(latest, "PAY", latest.getOrderAmount(), 0, null, null, reason, "支付失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long id, String operator, String reason) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.CANCELLED.name().equals(order.getStatus())) {
            backfillRemark(order, reason);
            return true;
        }
        if (!canCancelByState(order.getStatus())) {
            throw new PaymentException("当前订单状态不允许取消");
        }
        MerchantChannel channel = order.getChannelId() != null ? merchantChannelMapper.selectById(order.getChannelId()) : null;
        if (channel != null && StringUtils.hasText(order.getChannelType())) {
            try {
                PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
                paymentChannel.close(channel, order.getOrderNo());
            } catch (Exception e) {
                log.warn("关闭第三方订单失败, orderNo={}", order.getOrderNo(), e);
            }
        }
        sendStateEvent(order.getId(), OrderEvent.CANCEL, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        backfillRemark(latest, reason);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long id, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.COMPLETED.name().equals(order.getStatus())) {
            backfillCompleteTime(order);
            return true;
        }
        if (!OrderState.PAID.name().equals(order.getStatus())) {
            throw new PaymentException("当前订单状态不允许完成");
        }
        sendStateEvent(order.getId(), OrderEvent.COMPLETE, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        backfillCompleteTime(latest);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundOrder(Long id, RefundApplyDTO dto) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.REFUNDING.name().equals(order.getStatus()) || OrderState.REFUNDED.name().equals(order.getStatus())) {
            return true;
        }
        if (!(OrderState.PAID.name().equals(order.getStatus()) || OrderState.COMPLETED.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许退款");
        }
        BigDecimal refundAmount = dto != null && dto.getRefundAmount() != null ? dto.getRefundAmount() : refundableAmount(order);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("退款金额必须大于0");
        }
        if (refundAmount.compareTo(refundableAmount(order)) > 0) {
            throw new PaymentException("退款金额超过可退金额");
        }
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.REFUND, defaultOperator(dto != null ? dto.getOperator() : null));
        if (transitionResult == OrderTransitionResult.DUPLICATED) {
            return true;
        }
        PaymentOrder latest = requireOrder(id);
        latest.setRemark(dto != null ? dto.getRefundReason() : null);
        orderMapper.updateById(latest);

        RefundRequest request = new RefundRequest();
        request.setOrderNo(order.getOrderNo());
        request.setUserId(order.getUserId());
        request.setTradeNo(order.getThirdPartyOrderNo());
        request.setRefundNo(generateRefundNo());
        request.setRefundAmount(refundAmount);
        request.setTotalAmount(order.getOrderAmount());
        request.setReason(dto != null ? dto.getRefundReason() : null);
        request.setNotifyUrl(order.getNotifyUrl());

        RefundResult result = paymentChannel.refund(channel, request);
        if ("REFUNDED".equals(result.getStatus())) {
            return refundSuccess(id, refundAmount, defaultOperator(dto != null ? dto.getOperator() : null));
        }
        if ("FAILED".equals(result.getStatus())) {
            return refundFail(id, firstNonBlank(result.getMessage(), "渠道退款失败"), defaultOperator(dto != null ? dto.getOperator() : null));
        }
        recordTransaction(latest,
                "REFUND",
                refundAmount,
                transactionStatusOfRefundResult(result),
                result.getTradeNo(),
                safeJson(request),
                safeJson(result),
                "真实退款发起");
        return result.isSuccess();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundSuccess(Long id, BigDecimal refundAmount, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.REFUNDED.name().equals(order.getStatus())) {
            backfillRefundSuccess(order, refundAmount);
            return true;
        }
        if (!OrderState.REFUNDING.name().equals(order.getStatus())) {
            throw new PaymentException("当前订单状态不允许标记退款成功");
        }
        BigDecimal amount = refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0 ? refundAmount : refundableAmount(order);
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.REFUND_SUCCESS, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        backfillRefundSuccess(latest, amount);
        latest = requireOrder(id);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            recordTransaction(latest, "REFUND", amount, 1, null, null, null, "退款成功");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundFail(Long id, String reason, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.PAID.name().equals(order.getStatus()) || OrderState.COMPLETED.name().equals(order.getStatus())) {
            backfillRemark(order, reason);
            return true;
        }
        if (!OrderState.REFUNDING.name().equals(order.getStatus())) {
            throw new PaymentException("当前订单状态不允许标记退款失败");
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.REFUND_FAIL, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        backfillRemark(latest, reason);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            recordTransaction(latest, "REFUND", refundableAmount(order), 0, null, null, reason, "退款失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        if (!(OrderState.PENDING.name().equals(order.getStatus())
                || OrderState.CANCELLED.name().equals(order.getStatus())
                || OrderState.FAILED.name().equals(order.getStatus())
                || OrderState.REFUNDED.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许删除");
        }
        order.setDeleted(1);
        return orderMapper.updateById(order) > 0;
    }

    @Override
    public String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoCancelTimeoutOrders() {
        LambdaQueryWrapper<PaymentOrder> wrapper = baseOrderWrapper()
                .eq(PaymentOrder::getStatus, OrderState.PENDING.name())
                .lt(PaymentOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(PaymentOrder::getExpireTime)
                .last("limit 200");
        orderMapper.selectList(wrapper).forEach(order -> cancelOrder(order.getId(), "system", "超时自动取消"));
    }

    private Merchant requireActiveMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        if (!Integer.valueOf(MerchantStatus.ACTIVE.getCode()).equals(merchant.getStatus())) {
            throw new PaymentException("商户未激活，不能创建订单");
        }
        return merchant;
    }

    private PaymentOrder requireOrder(Long id) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            throw new PaymentException("订单不存在");
        }
        return order;
    }

    private MerchantChannel requireEnabledChannel(Long channelId) {
        if (channelId == null) {
            throw new PaymentException("订单未绑定支付方式");
        }
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new PaymentException("支付方式不存在");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    private MerchantChannel resolveChannel(OrderCreateDTO dto) {
        MerchantChannel channel;
        if (dto.getChannelId() != null) {
            channel = merchantChannelMapper.selectById(dto.getChannelId());
            if (channel == null) {
                throw new PaymentException("支付方式不存在");
            }
        } else {
            if (!StringUtils.hasText(dto.getChannelType())) {
                throw new PaymentException("支付方式不能为空");
            }
            LambdaQueryWrapper<MerchantChannel> wrapper = new LambdaQueryWrapper<MerchantChannel>()
                    .eq(MerchantChannel::getMerchantId, dto.getMerchantId())
                    .eq(MerchantChannel::getChannelType, dto.getChannelType())
                    .eq(MerchantChannel::getStatus, ChannelStatus.ENABLED.getCode())
                    .last("limit 1");
            if (StringUtils.hasText(dto.getChannelSubType())) {
                wrapper.eq(MerchantChannel::getChannelSubType, dto.getChannelSubType());
            }
            channel = merchantChannelMapper.selectOne(wrapper);
            if (channel == null) {
                throw new PaymentException("未找到可用的支付方式配置");
            }
        }
        if (!dto.getMerchantId().equals(channel.getMerchantId())) {
            throw new PaymentException("订单商户和支付方式不匹配");
        }
        if (!Integer.valueOf(ChannelStatus.ENABLED.getCode()).equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    private PaymentOrder findActiveOrderByBusinessOrderNo(Long merchantId, String businessOrderNo) {
        if (merchantId == null || !StringUtils.hasText(businessOrderNo)) {
            return null;
        }
        return orderMapper.selectOne(baseOrderWrapper()
                .eq(PaymentOrder::getMerchantId, merchantId)
                .eq(PaymentOrder::getBusinessOrderNo, businessOrderNo)
                .last("limit 1"));
    }

    private void assertSameBusinessOrder(PaymentOrder existing, OrderCreateDTO dto, MerchantChannel channel) {
        if (!existing.getMerchantId().equals(dto.getMerchantId())) {
            throw new PaymentException("业务订单号已存在且商户不一致");
        }
        if (channel.getId() != null && !channel.getId().equals(existing.getChannelId())) {
            throw new PaymentException("业务订单号已存在且支付方式不一致");
        }
        if (existing.getOrderAmount() != null && dto.getOrderAmount() != null
                && existing.getOrderAmount().compareTo(dto.getOrderAmount()) != 0) {
            throw new PaymentException("业务订单号已存在且订单金额不一致");
        }
        String currency = StringUtils.hasText(dto.getCurrency()) ? dto.getCurrency() : "CNY";
        if (StringUtils.hasText(existing.getCurrency()) && !existing.getCurrency().equalsIgnoreCase(currency)) {
            throw new PaymentException("业务订单号已存在且币种不一致");
        }
    }

    private void ensurePaying(PaymentOrder order, String operator) {
        if (OrderState.PENDING.name().equals(order.getStatus())) {
            startPay(order.getId(), operator);
        }
    }

    private OrderTransitionResult sendStateEvent(Long orderId, OrderEvent event, String operator) {
        OrderTransitionResult result = stateMachineService.sendEvent(orderId, event, operator);
        if (result == OrderTransitionResult.REJECTED) {
            throw new PaymentException("订单状态流转失败: " + event.name());
        }
        return result;
    }

    private int resolveExpireMinutes(OrderCreateDTO dto, Merchant merchant) {
        if (dto.getExpireMinutes() != null && dto.getExpireMinutes() > 0) {
            return dto.getExpireMinutes();
        }
        if (Boolean.TRUE.equals(merchant.getAutoCloseEnabled()) && merchant.getAutoCloseMinutes() != null && merchant.getAutoCloseMinutes() > 0) {
            return merchant.getAutoCloseMinutes();
        }
        return 30;
    }

    private BigDecimal refundableAmount(PaymentOrder order) {
        return nullToZero(order.getPaidAmount()).subtract(nullToZero(order.getRefundAmount()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return nullToZero(left).compareTo(nullToZero(right)) >= 0 ? nullToZero(left) : nullToZero(right);
    }

    private boolean canCancelByState(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    private boolean isPaidState(String status) {
        return OrderState.PAID.name().equals(status)
                || OrderState.COMPLETED.name().equals(status)
                || OrderState.REFUNDING.name().equals(status)
                || OrderState.REFUNDED.name().equals(status);
    }

    private void backfillPaymentSuccess(PaymentOrder order, BigDecimal paidAmount, String thirdPartyOrderNo) {
        if (order == null) {
            return;
        }
        boolean changed = false;
        BigDecimal expectedPaidAmount = paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0
                ? paidAmount
                : order.getOrderAmount();
        if (nullToZero(order.getPaidAmount()).compareTo(BigDecimal.ZERO) <= 0 && expectedPaidAmount != null) {
            order.setPaidAmount(expectedPaidAmount);
            changed = true;
        }
        if (!StringUtils.hasText(order.getThirdPartyOrderNo()) && StringUtils.hasText(thirdPartyOrderNo)) {
            order.setThirdPartyOrderNo(thirdPartyOrderNo);
            changed = true;
        }
        if (order.getPayTime() == null) {
            order.setPayTime(LocalDateTime.now());
            changed = true;
        }
        updateOrderIfChanged(order, changed);
    }

    private void backfillRefundSuccess(PaymentOrder order, BigDecimal refundAmount) {
        if (order == null) {
            return;
        }
        boolean changed = false;
        BigDecimal targetRefundAmount = refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0
                ? max(order.getRefundAmount(), refundAmount)
                : order.getRefundAmount();
        if (refundAmount != null && targetRefundAmount.compareTo(nullToZero(order.getRefundAmount())) != 0) {
            order.setRefundAmount(targetRefundAmount);
            changed = true;
        }
        if (order.getRefundTime() == null) {
            order.setRefundTime(LocalDateTime.now());
            changed = true;
        }
        updateOrderIfChanged(order, changed);
    }

    private void backfillCompleteTime(PaymentOrder order) {
        if (order == null || order.getCompleteTime() != null) {
            return;
        }
        order.setCompleteTime(LocalDateTime.now());
        updateOrderIfChanged(order, true);
    }

    private void backfillRemark(PaymentOrder order, String remark) {
        if (order == null || !StringUtils.hasText(remark) || StringUtils.hasText(order.getRemark())) {
            return;
        }
        order.setRemark(remark);
        updateOrderIfChanged(order, true);
    }

    private void updateOrderIfChanged(PaymentOrder order, boolean changed) {
        if (changed) {
            orderMapper.updateById(order);
        }
    }

    private String defaultOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "system";
    }

    private String generateRefundNo() {
        return "REF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private LambdaQueryWrapper<PaymentOrder> baseOrderWrapper() {
        return new LambdaQueryWrapper<PaymentOrder>()
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void recordTransaction(PaymentOrder order,
                                   String transactionType,
                                   BigDecimal amount,
                                   Integer status,
                                   String thirdPartyTransactionNo,
                                   String requestPayload,
                                   String responsePayload,
                                   String remark) {
        TransactionRecord record = new TransactionRecord();
        record.setTransactionNo("TRX" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setMerchantId(order.getMerchantId());
        record.setChannelId(order.getChannelId());
        record.setTransactionType(transactionType);
        record.setAmount(amount);
        record.setChannelType(order.getChannelType());
        record.setThirdPartyTransactionNo(thirdPartyTransactionNo);
        record.setStatus(status);
        record.setRequestPayload(requestPayload);
        record.setResponsePayload(responsePayload);
        record.setRemark(remark);
        transactionRecordService.createRecord(record);
    }

    private Integer transactionStatusOfPaymentResult(PaymentResult result) {
        if ("PAID".equals(result.getStatus())) {
            return 1;
        }
        if ("FAILED".equals(result.getStatus()) || "CANCELLED".equals(result.getStatus())) {
            return 0;
        }
        return 2;
    }

    private Integer transactionStatusOfRefundResult(RefundResult result) {
        if ("REFUNDED".equals(result.getStatus())) {
            return 1;
        }
        if ("FAILED".equals(result.getStatus())) {
            return 0;
        }
        return 2;
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private OrderVO convertToVO(PaymentOrder order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        MerchantChannel channel = order.getChannelId() != null ? merchantChannelMapper.selectById(order.getChannelId()) : null;
        vo.setMerchantName(merchant != null ? merchant.getMerchantName() : null);
        vo.setChannelName(channel != null ? channel.getChannelName() : null);
        vo.setStatusDesc(OrderState.descriptionOf(order.getStatus()));
        return vo;
    }

    private OrderStateLogVO convertLogToVO(OrderStateLog log) {
        OrderStateLogVO vo = new OrderStateLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
