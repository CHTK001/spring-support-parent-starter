package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentChannel;
import com.chua.payment.support.channel.PaymentChannelRegistry;
import com.chua.payment.support.channel.PaymentRequest;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.channel.RefundRequest;
import com.chua.payment.support.channel.RefundResult;
import com.chua.payment.support.dto.CreateRefundOrderDTO;
import com.chua.payment.support.dto.MarkRefundedDTO;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.OrderStateLogRequest;
import com.chua.payment.support.dto.PaymentOrderPageRequest;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.enums.ChannelStatus;
import com.chua.payment.support.enums.MerchantStatus;
import com.chua.payment.support.enums.OrderEvent;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.enums.OrderTransitionResult;
import com.chua.payment.support.enums.RefundOrderStatus;
import com.chua.payment.support.event.OrderCreatedEvent;
import com.chua.payment.support.event.PaymentSuccessEvent;
import com.chua.payment.support.event.RefundSuccessEvent;
import com.chua.payment.support.dto.RefundSuccessDTO;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.OrderStateLogService;
import com.chua.payment.support.service.OrderStateMachineService;
import com.chua.payment.support.service.MerchantPaymentConfigService;
import com.chua.payment.support.service.PaymentCallbackUrlResolver;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.service.PaymentRefundOrderService;
import com.chua.payment.support.service.TransactionRecordService;
import com.chua.payment.support.spi.UserOpenIdResolver;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 支付订单服务实现，负责订单创建、支付、退款、状态流转等核心业务。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOrderServiceImpl implements PaymentOrderService {

    /**
     * 订单映射器
     */
    private final PaymentOrderMapper orderMapper;
    /**
     * 商户映射器
     */
    private final MerchantMapper merchantMapper;
    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;
    /**
     * 订单状态机服务
     */
    private final OrderStateMachineService stateMachineService;
    /**
     * 订单状态日志服务
     */
    private final OrderStateLogService orderStateLogService;
    /**
     * 交易流水服务
     */
    private final TransactionRecordService transactionRecordService;
    /**
     * 支付渠道注册表
     */
    private final PaymentChannelRegistry paymentChannelRegistry;
    /**
     * 支付退款单服务
     */
    private final PaymentRefundOrderService paymentRefundOrderService;
    /**
     * 支付回调地址解析器
     */
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;
    /**
     * 商户支付配置服务
     */
    private final MerchantPaymentConfigService merchantPaymentConfigService;
    /**
     * 事件发布器
     */
    private final ApplicationEventPublisher eventPublisher;
    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;
    /**
     * 用户 OpenId 解析器
     */
    private final UserOpenIdResolver userOpenIdResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建支付订单
     *
     * @param dto dto
     * @return 创建支付订单的结果
     */
    public OrderVO createOrder(OrderCreateDTO dto) {
        if (dto.merchantId() == null) {
            throw new PaymentException("商户ID不能为空");
        }
        if (dto.orderAmount() == null || dto.orderAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new PaymentException("订单金额不能为负数");
        }

        merchantPaymentConfigService.checkCanCreateOrder(dto.merchantId(), dto.userId());

        Merchant merchant = requireActiveMerchant(dto.merchantId());
        MerchantChannel channel = resolveChannel(dto);
        String orderNo = generateOrderNo();
        String businessOrderNo = StringUtils.hasText(dto.businessOrderNo()) ? dto.businessOrderNo() : orderNo;
        PaymentOrder existing = findActiveOrderByBusinessOrderNo(dto.merchantId(), businessOrderNo);
        if (existing != null) {
            /**
             * 校验为同一笔业务订单
             * @param existing 已存在的订单
             * @param dto dto
             * @param channel 渠道
             */
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
        order.setDiscountAmount(nullToZero(dto.discountAmount()));
        order.setCurrency(StringUtils.hasText(dto.currency()) ? dto.currency() : "CNY");
        order.setStatus(OrderState.PENDING.name());
        order.setSubject(StringUtils.hasText(dto.subject()) ? dto.subject() : "支付订单");
        order.setBody(dto.body());
        order.setNotifyUrl(paymentCallbackUrlResolver.resolvePayNotifyUrl(dto.notifyUrl(), channel, merchant, orderNo));
        order.setReturnUrl(firstNonBlank(dto.returnUrl(), channel.getReturnUrl(), merchant.getDefaultReturnUrl()));
        order.setExpireTime(LocalDateTime.now().plusMinutes(resolveExpireMinutes(dto, merchant)));
        order.setDeleted(0);

        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        com.chua.payment.support.channel.OrderCreateRequest createRequest = new com.chua.payment.support.channel.OrderCreateRequest(
                orderNo,
                dto.userId(),
                dto.orderAmount(),
                order.getSubject(),
                order.getBody(),
                order.getCurrency(),
                businessOrderNo);

        com.chua.payment.support.channel.OrderCreateResult createResult = paymentChannel.createOrder(channel, createRequest);
        if (!createResult.success()) {
            throw new PaymentException("订单创建失败: " + createResult.message());
        }

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            PaymentOrder duplicated = findActiveOrderByBusinessOrderNo(dto.merchantId(), businessOrderNo);
            if (duplicated != null) {
                /**
                 * 校验为同一笔业务订单
                 * @param duplicated 重复的订单
                 * @param dto dto
                 * @param channel 渠道
                 */
                assertSameBusinessOrder(duplicated, dto, channel);
                return convertToVO(duplicated);
            }
            throw new PaymentException("业务订单号已存在，订单创建失败", e);
        }

        stateMachineService.createStateMachine(order.getId());
        orderStateLogService.log(new OrderStateLogRequest(order.getId(), null, OrderState.PENDING.name(), "CREATE", "system", "订单创建"));

        eventPublisher.publishEvent(new OrderCreatedEvent(
                this,
                null,
                order.getMerchantId(),
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getOrderAmount(),
                order.getChannelType(),
                order.getChannelSubType(),
                order.getSubject()
        ));

        return convertToVO(order);
    }

    @Override
    /**
     * 获取订单
     * @param id 长整数
     * @return 返回值
    */
    public OrderVO getOrder(Long id) {
        return convertToVO(requireOrder(id));
    }

    @Override
    public Page<OrderVO> listOrders(PaymentOrderPageRequest request) {
        if (request == null) {
            throw new PaymentException("查询参数不能为空");
        }
        LambdaQueryWrapper<PaymentOrder> wrapper = baseOrderWrapper()
                .orderByDesc(PaymentOrder::getCreatedAt);
        if (request.merchantId() != null) {
            wrapper.eq(PaymentOrder::getMerchantId, request.merchantId());
        }
        if (StringUtils.hasText(request.orderNo())) {
            wrapper.eq(PaymentOrder::getOrderNo, request.orderNo());
        }
        if (StringUtils.hasText(request.status())) {
            wrapper.eq(PaymentOrder::getStatus, request.status());
        }
        Page<PaymentOrder> orderPage = orderMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        Page<OrderVO> voPage = new Page<>(request.page(), request.size(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Page<OrderVO> listMerchantOrders(Long merchantId, int page, int size, String status) {
        return listOrders(new PaymentOrderPageRequest(page, size, merchantId, null, status));
    }

    @Override
    /**
     * 查询订单状态日志
     * @param id 长整数
     * @return 返回值
    */
    public List<OrderStateLogVO> listOrderLogs(Long id) {
        /**
         * 先确认订单存在
         * @param id 标识
         */
        requireOrder(id);
        return orderStateLogService.listByOrderId(id).stream().map(this::convertLogToVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 发起支付
     *
     * @param id 标识
     * @param dto dto
     * @return 发起支付的结果
     */
    public PaymentResult payOrder(Long id, OrderPayDTO dto) {
        merchantPaymentConfigService.checkCanPayOrder(id);

        PaymentOrder order = requireOrder(id);
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        String operator = defaultOperator(dto != null ? dto.operator() : null);
        /**
         * 确保订单进入支付中状态
         * @param order 订单
         * @param operator 操作人
         */
        ensurePaying(order, operator);

        // 零金额订单：跳过渠道支付，直接标记成功
        if (order.getOrderAmount().compareTo(BigDecimal.ZERO) == 0) {
            /**
             * 标记支付成功
             * @param id 标识
             * @param BigDecimal.ZERO 零金额
             * @param order.getOrderNo() 订单号
             */
            paySuccess(id, BigDecimal.ZERO, "ZERO_AMOUNT_" + order.getOrderNo(), operator);
            PaymentResult result = new PaymentResult(true, null, "零金额订单已自动支付成功", null,
                    null, null, null, null, null, "PAID", null);
            return result;
        }

        String payerOpenId = dto != null ? dto.payerOpenId() : null;
        if (!StringUtils.hasText(payerOpenId)) {
            payerOpenId = userOpenIdResolver.resolveOpenId(order.getMerchantId(), order.getUserId(), order.getChannelType());
        }
        PaymentRequest request = new PaymentRequest(
                order.getOrderNo(),
                order.getUserId(),
                order.getOrderAmount(),
                order.getSubject(),
                order.getBody(),
                order.getNotifyUrl(),
                order.getReturnUrl(),
                order.getCurrency(),
                order.getExpireTime(),
                payerOpenId,
                dto != null ? dto.clientIp() : null,
                dto != null ? dto.deviceId() : null,
                dto != null ? dto.userAgent() : null,
                order.getBusinessOrderNo());

        PaymentResult result = paymentChannel.pay(channel, request);
        PaymentOrder latest = requireOrder(id);
        if (StringUtils.hasText(result.tradeNo())) {
            latest.setThirdPartyOrderNo(result.tradeNo());
            orderMapper.updateById(latest);
        }
        if ("PAID".equals(result.status())) {
            paySuccess(id,
                    result.paidAmount() != null ? result.paidAmount() : latest.getOrderAmount(),
                    result.tradeNo(),
                    operator);
            return result;
        }
        if ("FAILED".equals(result.status())) {
            /**
             * 标记支付失败
             * @param id 标识
             * @param firstNonBlank(result.message() 渠道返回的失败原因
             */
            payFail(id, firstNonBlank(result.message(), "支付失败"), operator);
            return result;
        }
        if ("CANCELLED".equals(result.status())) {
            /**
             * 取消订单
             * @param id 标识
             * @param operator 操作人
             * @param firstNonBlank(result.message() 渠道返回的关闭原因
             */
            cancelOrder(id, operator, firstNonBlank(result.message(), "订单已关闭"));
            return result;
        }
        recordTransaction(latest,
                "PAY",
                latest.getOrderAmount(),
                /**
                 * 流水状态取支付结果对应的状态
                 * @param result 支付结果
                 */
                transactionStatusOfPaymentResult(result),
                result.tradeNo(),
                /**
                 * 安全序列化
                 * @param request 请求
                 */
                safeJson(request),
                /**
                 * 安全序列化
                 * @param result 支付结果
                 */
                safeJson(result),
                "真实支付发起");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 同步订单状态
     *
     * @param id 标识
     * @return 同步后的订单
     */
    public OrderVO syncOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        PaymentResult result = paymentChannel.query(channel, order.getOrderNo());
        if ("PAID".equals(result.status())) {
            /**
             * 标记支付成功
             * @param id 标识
             * @param result.paidAmount() 渠道返回的实付金额
             */
            paySuccess(id, result.paidAmount(), result.tradeNo(), "channel-sync");
        } else if ("FAILED".equals(result.status())) {
            /**
             * 标记支付失败
             * @param id 标识
             * @param firstNonBlank(result.message() 渠道返回的失败原因
             */
            payFail(id, firstNonBlank(result.message(), "渠道返回支付失败"), "channel-sync");
        } else if ("CANCELLED".equals(result.status()) && canCancelByState(requireOrder(id).getStatus())) {
            /**
             * 取消订单
             * @param id 标识
             * @param firstNonBlank(result.message() 渠道返回的关闭原因
             */
            cancelOrder(id, "channel-sync", firstNonBlank(result.message(), "渠道返回订单关闭"));
        }
        return convertToVO(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 启动支付
     *
     * @param id 标识
     * @param operator 操作人
     * @return 是否启动成功
     */
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
            /**
             * 记录发起支付流水
             * @param order 订单
             * @param order.getOrderAmount() 订单金额
             */
            recordTransaction(order, "PAY", order.getOrderAmount(), 2, null, null, null, "发起支付");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记支付成功
     *
     * @param id 标识
     * @param paidAmount 实付金额
     * @param thirdPartyOrderNo 第三方订单号
     * @param operator 操作人
     * @return 是否处理成功
     */
    public boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator) {
        PaymentOrder order = requireOrder(id);
        if (isPaidState(order.getStatus())) {
            /**
             * 幂等回填支付成功信息
             * @param order 订单
             * @param paidAmount 实付金额
             * @param thirdPartyOrderNo 第三方订单号
             */
            backfillPaymentSuccess(order, paidAmount, thirdPartyOrderNo);
            return true;
        }
        if (!(OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许标记支付成功");
        }
        /**
         * 确保订单进入支付中状态
         * @param order 订单
         * @param defaultOperator(operator) 默认操作人
         */
        ensurePaying(order, defaultOperator(operator));
        order = requireOrder(id);
        if (isPaidState(order.getStatus())) {
            /**
             * 幂等回填支付成功信息
             * @param order 订单
             * @param paidAmount 实付金额
             * @param thirdPartyOrderNo 第三方订单号
             */
            backfillPaymentSuccess(order, paidAmount, thirdPartyOrderNo);
            return true;
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY_SUCCESS, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        /**
         * 幂等回填支付成功信息
         * @param latest 最新订单
         * @param paidAmount 实付金额
         * @param thirdPartyOrderNo 第三方订单号
         */
        backfillPaymentSuccess(latest, paidAmount, thirdPartyOrderNo);
        latest = requireOrder(id);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            /**
             * 记录支付成功流水
             * @param latest 最新订单
             * @param latest.getPaidAmount() 实付金额
             */
            recordTransaction(latest, "PAY", latest.getPaidAmount(), 1, thirdPartyOrderNo, null, null, "支付成功");
            eventPublisher.publishEvent(new PaymentSuccessEvent(
                this, null, latest.getMerchantId(), latest.getId(),
                latest.getOrderNo(), latest.getUserId(), latest.getPaidAmount(),
                thirdPartyOrderNo, latest.getChannelType(), latest.getChannelSubType()
            ));
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记支付失败
     *
     * @param id 标识
     * @param reason 失败原因
     * @param operator 操作人
     * @return 是否处理成功
     */
    public boolean payFail(Long id, String reason, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.FAILED.name().equals(order.getStatus())) {
            /**
             * 幂等回填备注
             * @param order 订单
             * @param reason 失败原因
             */
            backfillRemark(order, reason);
            return true;
        }
        if (!(OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许标记支付失败");
        }
        /**
         * 确保订单进入支付中状态
         * @param order 订单
         * @param defaultOperator(operator) 默认操作人
         */
        ensurePaying(order, defaultOperator(operator));
        order = requireOrder(id);
        if (OrderState.FAILED.name().equals(order.getStatus())) {
            /**
             * 幂等回填备注
             * @param order 订单
             * @param reason 失败原因
             */
            backfillRemark(order, reason);
            return true;
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY_FAIL, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        /**
         * 幂等回填备注
         * @param latest 最新订单
         * @param reason 失败原因
         */
        backfillRemark(latest, reason);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            /**
             * 记录支付失败流水
             * @param latest 最新订单
             * @param latest.getOrderAmount() 订单金额
             */
            recordTransaction(latest, "PAY", latest.getOrderAmount(), 0, null, null, reason, "支付失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 取消订单（不退款）
     *
     * @param id 标识
     * @param operator 操作人
     * @param reason 取消原因
     * @return 取消订单的结果
     */
    public boolean cancelOrder(Long id, String operator, String reason) {
        return cancelOrder(id, operator, reason, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 取消订单
     *
     * @param id 标识
     * @param operator 操作人
     * @param reason 取消原因
     * @param refund 是否退款
     * @return 取消订单的结果
     */
    public boolean cancelOrder(Long id, String operator, String reason, boolean refund) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.CANCELLED.name().equals(order.getStatus())) {
            /**
             * 幂等回填备注
             * @param order 订单
             * @param reason 取消原因
             */
            backfillRemark(order, reason);
            return true;
        }

        // 不退款取消：仅 PENDING/PAYING 可取消
        if (!refund) {
            if (!canCancelByState(order.getStatus())) {
                throw new PaymentException("当前订单状态不允许取消");
            }
            /**
             * 先关闭渠道侧订单
             * @param order 订单
             */
            closeChannelOrder(order);
            /**
             * 发送状态机取消事件
             * @param order.getId() 订单标识
             */
            sendStateEvent(order.getId(), OrderEvent.CANCEL, defaultOperator(operator));
            PaymentOrder latest = requireOrder(id);
            /**
             * 幂等回填备注
             * @param latest 最新订单
             * @param reason 取消原因
             */
            backfillRemark(latest, reason);
            return true;
        }

        // === 退款取消 ===
        if (OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus())) {
            // 未支付订单：直接取消，无需退款
            /**
             * 先关闭渠道侧订单
             * @param order 订单
             */
            closeChannelOrder(order);
            /**
             * 发送状态机取消事件
             * @param order.getId() 订单标识
             */
            sendStateEvent(order.getId(), OrderEvent.CANCEL, defaultOperator(operator));
            PaymentOrder latest = requireOrder(id);
            /**
             * 幂等回填备注
             * @param latest 最新订单
             * @param reason 取消原因
             */
            backfillRemark(latest, reason);
            return true;
        }

        if (!(OrderState.PAID.name().equals(order.getStatus()) || OrderState.COMPLETED.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许退款取消");
        }

        // 发起退款
        if (paymentRefundOrderService.hasProcessingRefund(order.getId())) {
            throw new PaymentException("存在处理中退款单，请稍后再试");
        }

        BigDecimal refundAmount = nullToZero(order.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0
                ? order.getPaidAmount() : order.getOrderAmount();
        String operatorVal = defaultOperator(operator);

        // 先标记为退款中
        /**
         * 发送状态机退款事件
         * @param order.getId() 订单标识
         */
        sendStateEvent(order.getId(), OrderEvent.REFUND, operatorVal);

        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());

        String refundNo = generateRefundNo();
        RefundRequest request = new RefundRequest(
                order.getOrderNo(),
                order.getUserId(),
                order.getThirdPartyOrderNo(),
                refundNo,
                refundAmount,
                resolvedPaidAmount(order),
                StringUtils.hasText(reason) ? "取消退款:" + reason : "取消退款",
                paymentCallbackUrlResolver.resolveRefundNotifyUrl(channel, order.getMerchantId(), refundNo, order.getNotifyUrl()));
        String requestPayload = safeJson(request);

        paymentRefundOrderService.createRefundOrder(new CreateRefundOrderDTO(order, refundNo, refundAmount,
                StringUtils.hasText(reason) ? "取消退款:" + reason : "取消退款",
                operatorVal, requestPayload));

        RefundResult result = paymentChannel.refund(channel, request);
        String responsePayload = safeJson(result);
        String thirdPartyRefundNo = firstNonBlank(result.tradeNo(), result.refundNo());

        if ("REFUNDED".equals(result.status())) {
            // 退款成功：标记退款成功，再补 CANCELLED
            BigDecimal actualAmount = result.refundAmount() != null ? result.refundAmount() : refundAmount;
            paymentRefundOrderService.markRefunded(new MarkRefundedDTO(refundNo, actualAmount, thirdPartyRefundNo,
                    responsePayload, operatorVal, "取消退款成功"));
            PaymentOrder latestOrder = requireOrder(id);
            BigDecimal totalRefunded = aggregateRefundedAmount(latestOrder);
            /**
             * 同步退款累计金额
             * @param latestOrder 最新订单
             * @param totalRefunded 累计已退金额
             */
            syncRefundAggregate(latestOrder, totalRefunded);
            // 直接更新为 CANCELLED（不走状态机，因为订单流已定义 CANCELLED 状态）
            /**
             * 强制设置订单状态
             * @param id 标识
             * @param OrderState.CANCELLED.name() 已取消状态名称
             */
            forceOrderStatus(id, OrderState.CANCELLED.name());
            orderStateLogService.log(new OrderStateLogRequest(id, OrderState.REFUNDED.name(), OrderState.CANCELLED.name(),
                    "CANCEL", operatorVal, "退款方式取消"));
            recordTransaction(latestOrder, "REFUND", actualAmount, 1,
                    thirdPartyRefundNo, requestPayload, responsePayload, "取消退款");
            return true;
        }

        if ("FAILED".equals(result.status())) {
            paymentRefundOrderService.markFailed(refundNo, responsePayload, operatorVal,
                    firstNonBlank(result.message(), "取消退款失败"));
            // 退款失败：回退为 PAID
            /**
             * 强制设置订单状态
             * @param id 标识
             * @param OrderState.PAID.name() 已支付状态名称
             */
            forceOrderStatus(id, OrderState.PAID.name());
            orderStateLogService.log(new OrderStateLogRequest(id, OrderState.REFUNDING.name(), OrderState.PAID.name(),
                    "REFUND_FAIL", operatorVal,
                    firstNonBlank(result.message(), "取消退款失败")));
            throw new PaymentException("取消退款失败: " + result.message());
        }

        // 异步退款处理中：标记退款处理中
        paymentRefundOrderService.markProcessing(refundNo, thirdPartyRefundNo, responsePayload,
                firstNonBlank(result.message(), "取消退款发起"));
        recordTransaction(order, "REFUND", refundAmount, 2, thirdPartyRefundNo,
                requestPayload, responsePayload, "取消退款发起");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 完成订单
     *
     * @param id 标识
     * @param operator 操作人
     * @return 是否完成成功
     */
    public boolean completeOrder(Long id, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.COMPLETED.name().equals(order.getStatus())) {
            /**
             * 幂等回填完成时间
             * @param order 订单
             */
            backfillCompleteTime(order);
            return true;
        }
        if (!OrderState.PAID.name().equals(order.getStatus())) {
            throw new PaymentException("当前订单状态不允许完成");
        }
        /**
         * 发送状态机完成事件
         * @param order.getId() 订单标识
         */
        sendStateEvent(order.getId(), OrderEvent.COMPLETE, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        /**
         * 幂等回填完成时间
         * @param latest 最新订单
         */
        backfillCompleteTime(latest);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 发起退款
     *
     * @param id 标识
     * @param dto dto
     * @return 发起退款的结果
     */
    public boolean refundOrder(Long id, RefundApplyDTO dto) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.REFUNDED.name().equals(order.getStatus())) {
            return true;
        }
        if (!(OrderState.PAID.name().equals(order.getStatus())
                || OrderState.COMPLETED.name().equals(order.getStatus())
                || OrderState.REFUNDING.name().equals(order.getStatus()))) {
            throw new PaymentException("当前订单状态不允许退款");
        }
        if (paymentRefundOrderService.hasProcessingRefund(order.getId())) {
            throw new PaymentException("存在处理中退款单，请稍后再试");
        }
        if (dto == null || !StringUtils.hasText(dto.refundReason())) {
            throw new PaymentException("退款原因不能为空");
        }

        BigDecimal currentRefunded = aggregateRefundedAmount(order);
        /**
         * 同步退款累计金额
         * @param order 订单
         * @param currentRefunded 当前累计已退金额
         */
        syncRefundAggregate(order, currentRefunded);
        order = requireOrder(id);
        BigDecimal refundAmount = dto != null && dto.refundAmount() != null ? dto.refundAmount() : refundableAmount(order, currentRefunded);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("退款金额必须大于零");
        }
        if (refundAmount.compareTo(refundableAmount(order, currentRefunded)) > 0) {
            throw new PaymentException("退款金额超过可退金额");
        }

        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        String operator = defaultOperator(dto != null ? dto.operator() : null);
        if (!OrderState.REFUNDING.name().equals(order.getStatus())) {
            OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.REFUND, operator);
            if (transitionResult == OrderTransitionResult.DUPLICATED && OrderState.REFUNDED.name().equals(requireOrder(id).getStatus())) {
                return true;
            }
        }

        PaymentOrder latest = requireOrder(id);
        /**
         * 幂等回填备注
         * @param latest 最新订单
         * @param dto.refundReason() 退款原因
         */
        backfillRemark(latest, dto != null ? dto.refundReason() : null);

        String refundNo = generateRefundNo();
        RefundRequest request = new RefundRequest(
                latest.getOrderNo(),
                latest.getUserId(),
                latest.getThirdPartyOrderNo(),
                refundNo,
                refundAmount,
                resolvedPaidAmount(latest),
                dto != null ? dto.refundReason() : null,
                paymentCallbackUrlResolver.resolveRefundNotifyUrl(channel, latest.getMerchantId(), refundNo, latest.getNotifyUrl()));
        String requestPayload = safeJson(request);

        paymentRefundOrderService.createRefundOrder(new CreateRefundOrderDTO(latest,
                refundNo,
                refundAmount,
                dto != null ? dto.refundReason() : null,
                operator,
                requestPayload));

        RefundResult result = paymentChannel.refund(channel, request);
        String responsePayload = safeJson(result);
        String thirdPartyRefundNo = firstNonBlank(result.tradeNo(), result.refundNo());
        if ("REFUNDED".equals(result.status())) {
            return refundSuccess(new RefundSuccessDTO(
                    refundNo,
                    result.refundAmount() != null ? result.refundAmount() : refundAmount,
                    thirdPartyRefundNo,
                    operator,
                    responsePayload,
                    firstNonBlank(result.message(), dto != null ? dto.refundReason() : null)));
        }
        if ("FAILED".equals(result.status())) {
            return refundFail(refundNo,
                    responsePayload,
                    operator,
                    firstNonBlank(result.message(), "渠道退款失败"));
        }
        paymentRefundOrderService.markProcessing(refundNo, thirdPartyRefundNo, responsePayload, firstNonBlank(result.message(), "真实退款发起"));
        recordTransaction(latest,
                "REFUND",
                refundAmount,
                /**
                 * 流水状态取退款结果对应的状态
                 * @param result 退款结果
                 */
                transactionStatusOfRefundResult(result),
                thirdPartyRefundNo,
                requestPayload,
                responsePayload,
                "真实退款发起");
        return result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款成功
     *
     * @param id 标识
     * @param refundAmount 退款金额
     * @param operator 操作人
     * @return 标记结果
     */
    public boolean refundSuccess(Long id, BigDecimal refundAmount, String operator) {
        PaymentOrder order = requireOrder(id);
        PaymentRefundOrder refundOrder = resolveManualRefundOrder(order, refundAmount, defaultOperator(operator), "手动标记退款成功");
        if (refundOrder == null) {
            /**
             * 同步退款累计金额
             * @param order 订单
             * @param aggregateRefundedAmount(order) 累计已退金额
             */
            syncRefundAggregate(order, aggregateRefundedAmount(order));
            return true;
        }
        return refundSuccess(new RefundSuccessDTO(refundOrder.getRefundNo(), refundAmount, refundOrder.getThirdPartyRefundNo(), operator, null, "手动标记退款成功"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款失败
     *
     * @param id 标识
     * @param reason 失败原因
     * @param operator 操作人
     * @return 标记结果
     */
    public boolean refundFail(Long id, String reason, String operator) {
        PaymentOrder order = requireOrder(id);
        PaymentRefundOrder refundOrder = resolveManualRefundOrder(order, null, defaultOperator(operator), reason);
        if (refundOrder == null) {
            /**
             * 幂等回填备注
             * @param order 订单
             * @param reason 失败原因
             */
            backfillRemark(order, reason);
            return true;
        }
        return refundFail(refundOrder.getRefundNo(), null, operator, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款成功
     *
     * @param refundNo 退款单号
     * @param refundAmount 退款金额
     * @param thirdPartyRefundNo 第三方退款单号
     * @param operator 操作人
     * @param responsePayload 响应报文
     * @param remark 备注
     * @param dto dto
     * @return 标记结果
     */
    public boolean refundSuccess(RefundSuccessDTO dto) {
        PaymentRefundOrder refundOrder = requireRefundOrder(dto.refundNo());
        boolean alreadyRefunded = RefundOrderStatus.REFUNDED.name().equals(refundOrder.getStatus());
        BigDecimal amount = dto.refundAmount() != null && dto.refundAmount().compareTo(BigDecimal.ZERO) > 0
                ? dto.refundAmount()
                : nullToZero(refundOrder.getRefundAmount());
        PaymentRefundOrder latestRefundOrder = paymentRefundOrderService.markRefunded(new MarkRefundedDTO(
                dto.refundNo(),
                amount,
                dto.thirdPartyRefundNo(),
                dto.responsePayload(),
                /**
                 * 默认操作人
                 * @param dto.operator() 操作人
                 */
                defaultOperator(dto.operator()),
                firstNonBlank(dto.remark(), refundOrder.getReason())));

        PaymentOrder order = requireOrder(refundOrder.getOrderId());
        BigDecimal totalRefunded = aggregateRefundedAmount(order);
        /**
         * 同步退款累计金额
         * @param order 订单
         * @param totalRefunded 累计已退金额
         */
        syncRefundAggregate(order, totalRefunded);
        PaymentOrder latestOrder = requireOrder(order.getId());
        String targetStatus = resolveOrderStatusAfterRefundSuccess(latestOrder, totalRefunded);
        /**
         * 必要时改变订单状态
         * @param latestOrder 最新订单
         * @param targetStatus 目标状态
         * @param OrderEvent.REFUND_SUCCESS 退款成功事件
         * @param dto.operator() 操作人
         */
        changeOrderStatusIfNecessary(latestOrder, targetStatus, OrderEvent.REFUND_SUCCESS, dto.operator(), firstNonBlank(dto.remark(), "退款成功"));
        latestOrder = requireOrder(order.getId());
        if (!alreadyRefunded) {
            recordTransaction(latestOrder,
                    "REFUND",
                    latestRefundOrder.getRefundAmount(),
                    1,
                    /**
                     * 依次取本地第三方退款号与回调传入的退款号
                     * @param latestRefundOrder.getThirdPartyRefundNo() 本地第三方退款号
                     */
                    firstNonBlank(latestRefundOrder.getThirdPartyRefundNo(), dto.thirdPartyRefundNo()),
                    latestRefundOrder.getRequestPayload(),
                    dto.responsePayload(),
                    firstNonBlank(dto.remark(), "退款成功"));
            eventPublisher.publishEvent(new RefundSuccessEvent(
                this, null, latestOrder.getMerchantId(), latestOrder.getId(),
                latestOrder.getOrderNo(), latestRefundOrder.getId(), latestRefundOrder.getRefundNo(),
                latestOrder.getUserId(), latestRefundOrder.getRefundAmount(),
                 /**
                 * 依次取本地第三方退款号与回调传入的退款号
                 * @param latestRefundOrder.getThirdPartyRefundNo() 本地第三方退款号
                  */
                 firstNonBlank(latestRefundOrder.getThirdPartyRefundNo(), dto.thirdPartyRefundNo()),
                latestOrder.getChannelType(), latestOrder.getChannelSubType()
            ));
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款失败
     *
     * @param refundNo 退款单号
     * @param responsePayload 响应报文
     * @param operator 操作人
     * @param reason 失败原因
     * @return 标记结果
     */
    public boolean refundFail(String refundNo, String responsePayload, String operator, String reason) {
        PaymentRefundOrder refundOrder = requireRefundOrder(refundNo);
        boolean alreadyFailed = RefundOrderStatus.FAILED.name().equals(refundOrder.getStatus());
        PaymentRefundOrder latestRefundOrder = paymentRefundOrderService.markFailed(refundNo,
                responsePayload,
                /**
                 * 默认操作人
                 * @param operator 操作人
                 */
                defaultOperator(operator),
                firstNonBlank(reason, refundOrder.getReason(), "退款失败"));

        PaymentOrder order = requireOrder(refundOrder.getOrderId());
        BigDecimal totalRefunded = aggregateRefundedAmount(order);
        /**
         * 同步退款累计金额
         * @param order 订单
         * @param totalRefunded 累计已退金额
         */
        syncRefundAggregate(order, totalRefunded);
        PaymentOrder latestOrder = requireOrder(order.getId());
        String targetStatus = resolveOrderStatusAfterRefundFailure(latestOrder, totalRefunded, latestRefundOrder);
        /**
         * 必要时改变订单状态
         * @param latestOrder 最新订单
         * @param targetStatus 目标状态
         * @param OrderEvent.REFUND_FAIL 退款失败事件
         * @param operator 操作人
         */
        changeOrderStatusIfNecessary(latestOrder, targetStatus, OrderEvent.REFUND_FAIL, operator, firstNonBlank(reason, "退款失败"));
        latestOrder = requireOrder(order.getId());
        /**
         * 幂等回填备注
         * @param latestOrder 最新订单
         * @param reason 失败原因
         */
        backfillRemark(latestOrder, reason);
        if (!alreadyFailed) {
            recordTransaction(latestOrder,
                    "REFUND",
                    latestRefundOrder.getRefundAmount(),
                    0,
                    latestRefundOrder.getThirdPartyRefundNo(),
                    latestRefundOrder.getRequestPayload(),
                    responsePayload,
                    firstNonBlank(reason, "退款失败"));
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除订单
     *
     * @param id 标识
     * @return 是否删除成功
     */
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
    /**
     * 生成订单号
     * @return 返回值
    */
    public String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 超时关闭订单
     *
     * @param id 标识
     */
    public void timeoutOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        if (!OrderState.PENDING.name().equals(order.getStatus())) {
            return;
        }
        /**
         * 发送状态机超时事件
         * @param order.getId() 订单标识
         */
        sendStateEvent(order.getId(), OrderEvent.TIMEOUT, "system");
        PaymentOrder latest = requireOrder(id);
        /**
         * 幂等回填备注
         * @param latest 最新订单
         */
        backfillRemark(latest, "支付超时自动取消");
    }

    /**
     * 获取活跃状态的商户
     *
     * @param merchantId 商户标识
     * @return 获取到的活跃商户
     */
    private Merchant requireActiveMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new PaymentException("商户不存在");
        }
        if (!MerchantStatus.ACTIVE.getCode().equals(merchant.getStatus())) {
            throw new PaymentException("商户未激活，不能创建订单");
        }
        return merchant;
    }

    /**
     * 获取订单，不存在或已删除时抛出异常
     *
     * @param id 标识
     * @return 获取到的订单
     */
    private PaymentOrder requireOrder(Long id) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            // Converter 未覆盖 Boolean→Int 转换，删除标记比较场景，保留手写转换（规避四方对象配置）
            throw new PaymentException("订单不存在");
        }
        return order;
    }

    /**
     * 获取退款单，不存在时抛出异常
     *
     * @param refundNo 退款单号
     * @return 获取到的退款单
     */
    private PaymentRefundOrder requireRefundOrder(String refundNo) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getByRefundNo(refundNo);
        if (refundOrder == null) {
            throw new PaymentException("退款单不存在: " + refundNo);
        }
        return refundOrder;
    }

    /**
     * 获取已启用的渠道
     *
     * @param channelId 渠道标识
     * @return 获取到的已启用渠道
     */
    private MerchantChannel requireEnabledChannel(Long channelId) {
        if (channelId == null) {
            throw new PaymentException("订单未绑定支付方式");
        }
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new PaymentException("支付方式不存在");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    /**
     * 解析支付渠道
     *
     * @param dto dto
     * @return 解析出的支付渠道
     */
    private MerchantChannel resolveChannel(OrderCreateDTO dto) {
        MerchantChannel channel;
        if (dto.channelId() != null) {
            channel = merchantChannelMapper.selectById(dto.channelId());
            if (channel == null) {
                throw new PaymentException("支付方式不存在");
            }
        } else {
            if (!StringUtils.hasText(dto.channelType())) {
                throw new PaymentException("支付方式不能为空");
            }
            LambdaQueryWrapper<MerchantChannel> wrapper = new LambdaQueryWrapper<MerchantChannel>()
                    .eq(MerchantChannel::getMerchantId, dto.merchantId())
                    .eq(MerchantChannel::getChannelType, dto.channelType())
                    .eq(MerchantChannel::getStatus, ChannelStatus.ENABLED.getCode())
                    .last("limit 1");
            if (StringUtils.hasText(dto.channelSubType())) {
                wrapper.eq(MerchantChannel::getChannelSubType, dto.channelSubType());
            }
            channel = merchantChannelMapper.selectOne(wrapper);
            if (channel == null) {
                throw new PaymentException("未找到可用的支付方式配置");
            }
        }
        if (!dto.merchantId().equals(channel.getMerchantId())) {
            throw new PaymentException("订单商户和支付方式不匹配");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            throw new PaymentException("支付方式未启用");
        }
        return resolveEffectiveChannel(channel, dto.merchantId());
    }

    /**
     * 解析运营配置（COMPOSITE/AGGREGATE_ROUTE）指向的实际渠道
     *
     * @param channel 渠道
     * @param merchantId 商户标识
     * @return 解析出的实际生效渠道
     */
    private MerchantChannel resolveEffectiveChannel(MerchantChannel channel, Long merchantId) {
        if (channel == null || !"COMPOSITE".equalsIgnoreCase(channel.getChannelType())
                || !"AGGREGATE_ROUTE".equalsIgnoreCase(channel.getChannelSubType())) {
            return channel;
        }
        Long targetChannelId = resolveRouteChannelId(channel);
        if (targetChannelId == null) {
            throw new PaymentException("运营配置未提供 targetChannelId 或 defaultChannelId");
        }
        if (channel.getId() != null && channel.getId().equals(targetChannelId)) {
            throw new PaymentException("运营配置不能指向自己");
        }
        MerchantChannel targetChannel = merchantChannelMapper.selectById(targetChannelId);
        if (targetChannel == null) {
            throw new PaymentException("运营配置目标渠道不存在: " + targetChannelId);
        }
        if (!merchantId.equals(targetChannel.getMerchantId())) {
            throw new PaymentException("运营配置目标渠道和订单商户不匹配");
        }
        if ("COMPOSITE".equalsIgnoreCase(targetChannel.getChannelType())) {
            throw new PaymentException("运营配置不能嵌套 COMPOSITE 渠道");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(targetChannel.getStatus())) {
            throw new PaymentException("运营配置目标渠道未启用");
        }
        if (!paymentChannelRegistry.supports(targetChannel.getChannelType(), targetChannel.getChannelSubType())) {
            throw new PaymentException("运营配置目标渠道当前版本不可执行: "
                    + targetChannel.getChannelType() + "/" + targetChannel.getChannelSubType());
        }
        return targetChannel;
    }

    /**
     * 解析运营配置的目标渠道标识
     *
     * @param channel 渠道
     * @return 解析出的目标渠道标识
     */
    private Long resolveRouteChannelId(MerchantChannel channel) {
        if (!StringUtils.hasText(channel.getExtConfig())) {
            return null;
        }
        try {
            java.util.Map<String, Object> config = objectMapper.readValue(channel.getExtConfig(), new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
            });
            return parseLong(config.get("targetChannelId"), config.get("defaultChannelId"));
        } catch (Exception e) {
            throw new PaymentException("运营配置格式错误", e);
        }
    }

    /**
     * 解析长整型值
     *
     * @param values 候选值
     * @return 解析出的长整型值
     */
    private Long parseLong(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException e) {
                    throw new PaymentException("运营配置中的 channelId 格式错误: " + text, e);
                }
            }
        }
        return null;
    }

    /**
     * 按业务订单号查找未删除的订单
     *
     * @param merchantId 商户标识
     * @param businessOrderNo 业务订单号
     * @return 查找到的订单
     */
    private PaymentOrder findActiveOrderByBusinessOrderNo(Long merchantId, String businessOrderNo) {
        if (merchantId == null || !StringUtils.hasText(businessOrderNo)) {
            return null;
        }
        return orderMapper.selectOne(baseOrderWrapper()
                .eq(PaymentOrder::getMerchantId, merchantId)
                .eq(PaymentOrder::getBusinessOrderNo, businessOrderNo)
                .last("limit 1"));
    }

    /**
     * 校验已存在订单与本次请求属于同一笔业务订单
     *
     * @param existing 已存在的订单
     * @param dto dto
     * @param channel 渠道
     */
    private void assertSameBusinessOrder(PaymentOrder existing, OrderCreateDTO dto, MerchantChannel channel) {
        if (!existing.getMerchantId().equals(dto.merchantId())) {
            throw new PaymentException("业务订单号已存在但商户不一致");
        }
        if (channel.getId() != null && !channel.getId().equals(existing.getChannelId())) {
            throw new PaymentException("业务订单号已存在且支付方式不一致");
        }
        if (existing.getOrderAmount() != null && dto.orderAmount() != null
                && existing.getOrderAmount().compareTo(dto.orderAmount()) != 0) {
            throw new PaymentException("业务订单号已存在且订单金额不一致");
        }
        String currency = StringUtils.hasText(dto.currency()) ? dto.currency() : "CNY";
        if (StringUtils.hasText(existing.getCurrency()) && !existing.getCurrency().equalsIgnoreCase(currency)) {
            throw new PaymentException("业务订单号已存在且币种不一致");
        }
    }

    /**
     * 确保订单进入支付中状态
     *
     * @param order 订单
     * @param operator 操作人
     */
    private void ensurePaying(PaymentOrder order, String operator) {
        if (OrderState.PENDING.name().equals(order.getStatus())) {
            /**
             * 启动支付
             * @param order.getId() 订单标识
             */
            startPay(order.getId(), operator);
        }
    }

    /**
     * 发送状态机事件，流转被拒绝时抛出异常
     *
     * @param orderId 订单标识
     * @param event 事件
     * @param operator 操作人
     * @return 状态机事件发送结果
     */
    private OrderTransitionResult sendStateEvent(Long orderId, OrderEvent event, String operator) {
        OrderTransitionResult result = stateMachineService.sendEvent(orderId, event, operator);
        if (result == OrderTransitionResult.REJECTED) {
            throw new PaymentException("订单状态流转失败: " + event.name());
        }
        return result;
    }

    /**
     * 解析订单过期分钟数
     * @param dto dto
     * @param merchant 商户
     * @return 返回值
    */
    private int resolveExpireMinutes(OrderCreateDTO dto, Merchant merchant) {
        if (dto.expireMinutes() != null && dto.expireMinutes() > 0) {
            return dto.expireMinutes();
        }
        if (Boolean.TRUE.equals(merchant.getPaymentAutoCloseEnabled())
                && merchant.getPaymentAutoCloseMinutes() != null
                && merchant.getPaymentAutoCloseMinutes() > 0) {
            return merchant.getPaymentAutoCloseMinutes();
        }
        return 30;
    }

    /**
     * 解析或创建人工操作对应的退款单
     *
     * @param order 订单
     * @param refundAmount 退款金额
     * @param operator 操作人
     * @param reason 失败或退款原因
     */
    private PaymentRefundOrder resolveManualRefundOrder(PaymentOrder order,
                                                        BigDecimal refundAmount,
                                                        String operator,
                                                        String reason) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getProcessingByOrderId(order.getId());
        if (refundOrder != null) {
            return refundOrder;
        }
        refundOrder = paymentRefundOrderService.getLatestByOrderId(order.getId());
        if (refundOrder != null) {
            return refundOrder;
        }
        if (OrderState.REFUNDED.name().equals(order.getStatus())) {
            return null;
        }
        BigDecimal amount = refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0
                ? refundAmount
                : refundableAmount(order);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = resolvedPaidAmount(order);
        }
        return paymentRefundOrderService.createRefundOrder(new CreateRefundOrderDTO(order,
                /**
                 * 生成退款单号
                 */
                generateRefundNo(),
                amount,
                reason,
                operator,
                null));
    }

    /**
     * 汇总已退款金额
     *
     * @param order 订单
     * @return 累计已退金额
     */
    private BigDecimal aggregateRefundedAmount(PaymentOrder order) {
        BigDecimal refundedFromOrder = nullToZero(order.getRefundAmount());
        BigDecimal refundedFromRefundOrder = paymentRefundOrderService.sumRefundedAmount(order.getId());
        return refundedFromOrder.compareTo(refundedFromRefundOrder) >= 0 ? refundedFromOrder : refundedFromRefundOrder;
    }

    /**
     * 计算可退金额
     *
     * @param order 订单
     * @return 可退金额
     */
    private BigDecimal refundableAmount(PaymentOrder order) {
        return refundableAmount(order, aggregateRefundedAmount(order));
    }

    /**
     * 计算可退金额
     *
     * @param order 订单
     * @param refundedAmount 已退金额
     * @return 可退金额
     */
    private BigDecimal refundableAmount(PaymentOrder order, BigDecimal refundedAmount) {
        BigDecimal refundable = resolvedPaidAmount(order).subtract(nullToZero(refundedAmount));
        return refundable.compareTo(BigDecimal.ZERO) > 0 ? refundable : BigDecimal.ZERO;
    }

    /**
     * 取实付金额，未支付时退回订单金额
     * @param order 订单
     * @return 返回值
    */
    private BigDecimal resolvedPaidAmount(PaymentOrder order) {
        BigDecimal paidAmount = nullToZero(order.getPaidAmount());
        return paidAmount.compareTo(BigDecimal.ZERO) > 0 ? paidAmount : nullToZero(order.getOrderAmount());
    }

    /**
     * 空值转零
     *
     * @param value 值
     * @return 空值返回零，否则原样返回
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 判断当前状态是否允许直接取消
     *
     * @param status 订单状态
     * @return 是否允许取消
     */
    private boolean canCancelByState(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    /**
     * 判断是否为已支付及之后的成功态
     *
     * @param status 订单状态
     * @return 是否为已支付态
     */
    private boolean isPaidState(String status) {
        return OrderState.PAID.name().equals(status)
                || OrderState.COMPLETED.name().equals(status)
                || OrderState.REFUNDING.name().equals(status)
                || OrderState.REFUNDED.name().equals(status);
    }

    /**
     * 幂等回填支付成功信息
     *
     * @param order 订单
     * @param paidAmount 实付金额
     * @param thirdPartyOrderNo 第三方订单号
     */
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
        /**
         * 有变更时才更新订单
         * @param order 订单
         * @param changed 是否发生变更
         */
        updateOrderIfChanged(order, changed);
    }

    /**
     * 同步退款累计金额与退款时间
     *
     * @param order 订单
     * @param refundAmount 累计已退金额
     */
    private void syncRefundAggregate(PaymentOrder order, BigDecimal refundAmount) {
        if (order == null) {
            return;
        }
        boolean changed = false;
        BigDecimal normalizedRefundAmount = nullToZero(refundAmount);
        if (normalizedRefundAmount.compareTo(nullToZero(order.getRefundAmount())) != 0) {
            order.setRefundAmount(normalizedRefundAmount);
            changed = true;
        }
        if (normalizedRefundAmount.compareTo(BigDecimal.ZERO) > 0 && order.getRefundTime() == null) {
            order.setRefundTime(LocalDateTime.now());
            changed = true;
        }
        /**
         * 有变更时才更新订单
         * @param order 订单
         * @param changed 是否发生变更
         */
        updateOrderIfChanged(order, changed);
    }

    /**
     * 计算退款成功后的订单状态
     *
     * @param order 订单
     * @param refundedAmount 累计已退金额
     * @return 退款成功后的订单状态
     */
    private String resolveOrderStatusAfterRefundSuccess(PaymentOrder order, BigDecimal refundedAmount) {
        BigDecimal paidAmount = resolvedPaidAmount(order);
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0 && nullToZero(refundedAmount).compareTo(paidAmount) >= 0) {
            return OrderState.REFUNDED.name();
        }
        return OrderState.REFUNDING.name();
    }

    /**
     * 计算退款失败后的订单状态
     *
     * @param order 订单
     * @param refundedAmount 累计已退金额
     * @param refundOrder 退款单
     */
    private String resolveOrderStatusAfterRefundFailure(PaymentOrder order,
                                                        BigDecimal refundedAmount,
                                                        PaymentRefundOrder refundOrder) {
        BigDecimal paidAmount = resolvedPaidAmount(order);
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0 && nullToZero(refundedAmount).compareTo(paidAmount) >= 0) {
            return OrderState.REFUNDED.name();
        }
        if (nullToZero(refundedAmount).compareTo(BigDecimal.ZERO) > 0) {
            return OrderState.REFUNDING.name();
        }
        if (refundOrder != null && isRefundBaseState(refundOrder.getSourceOrderStatus())) {
            return refundOrder.getSourceOrderStatus();
        }
        return OrderState.PAID.name();
    }

    /**
     * 判断是否为允许回退的退款基准状态
     *
     * @param status 订单状态
     * @return 是否为退款基准状态
     */
    private boolean isRefundBaseState(String status) {
        return OrderState.PAID.name().equals(status) || OrderState.COMPLETED.name().equals(status);
    }

    /**
     * 必要时以乐观锁方式改变订单状态并记录日志
     * @param order 订单
     * @param targetStatus 目标状态
     * @param event 事件
     * @param operator 操作人
     * @param remark 备注
     */
    private void changeOrderStatusIfNecessary(PaymentOrder order,
                                              String targetStatus,
                                              OrderEvent event,
                                              String operator,
                                              String remark) {
        if (order == null || !StringUtils.hasText(targetStatus) || targetStatus.equals(order.getStatus())) {
            return;
        }
        OrderState fromState = OrderState.valueOf(order.getStatus());
        OrderState toState = OrderState.valueOf(targetStatus);
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, order.getStatus())
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .set(PaymentOrder::getStatus, targetStatus));
        if (updated == 0) {
            PaymentOrder latest = requireOrder(order.getId());
            if (targetStatus.equals(latest.getStatus())) {
                return;
            }
            throw new PaymentException("订单状态更新失败: " + event.name());
        }
        orderStateLogService.log(new OrderStateLogRequest(
                order.getId(),
                fromState.name(),
                toState.name(),
                event.name(),
                /**
                 * 默认操作人
                 * @param operator 操作人
                 */
                defaultOperator(operator),
                StringUtils.hasText(remark)
                        ? remark
                        : String.format("Status change: %s -> %s", fromState.getDescription(), toState.getDescription())));
    }

    /**
     * 幂等回填订单完成时间
     *
     * @param order 订单
     */
    private void backfillCompleteTime(PaymentOrder order) {
        if (order == null || order.getCompleteTime() != null) {
            return;
        }
        order.setCompleteTime(LocalDateTime.now());
        /**
         * 有变更时才更新订单
         * @param order 订单
         * @param true 存在变更
         */
        updateOrderIfChanged(order, true);
    }

    /**
     * 幂等回填订单备注
     *
     * @param order 订单
     * @param remark 备注
     */
    private void backfillRemark(PaymentOrder order, String remark) {
        if (order == null || !StringUtils.hasText(remark) || StringUtils.hasText(order.getRemark())) {
            return;
        }
        order.setRemark(remark);
        /**
         * 有变更时才更新订单
         * @param order 订单
         * @param true 存在变更
         */
        updateOrderIfChanged(order, true);
    }

    /**
     * 关闭渠道侧订单，失败仅告警不影响本地状态流转
     *
     * @param order 订单
     */
    private void closeChannelOrder(PaymentOrder order) {
        MerchantChannel channel = order.getChannelId() != null ? merchantChannelMapper.selectById(order.getChannelId()) : null;
        if (channel != null && StringUtils.hasText(order.getChannelType())) {
            try {
                PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
                paymentChannel.close(channel, order.getOrderNo());
            } catch (Exception e) {
                log.warn("关闭第三方订单失败 orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 强制设置订单状态（不经过状态机，仅用于取消退款等特殊流转）
     *
     * @param orderId 订单标识
     * @param targetStatus 目标状态
     */
    private void forceOrderStatus(Long orderId, String targetStatus) {
        orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, orderId)
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .set(PaymentOrder::getStatus, targetStatus));
    }

    /**
     * 有变更时才更新订单
     *
     * @param order 订单
     * @param changed 是否发生变更
     */
    private void updateOrderIfChanged(PaymentOrder order, boolean changed) {
        if (changed) {
            orderMapper.updateById(order);
        }
    }

    /**
     * 取默认操作人
     *
     * @param operator 操作人
     * @return 操作人，缺省为 system
     */
    private String defaultOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "system";
    }

    /**
     * 生成退款单号
     *
     * @return 生成的退款单号
     */
    private String generateRefundNo() {
        return "REF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 基础订单查询条件封装
     *
     * @return 基础订单查询条件
     */
    private LambdaQueryWrapper<PaymentOrder> baseOrderWrapper() {
        return new LambdaQueryWrapper<PaymentOrder>()
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0));
    }

    /**
     * 返回第一个非空白值
     *
     * @param values 候选值
     * @return 第一个非空白值
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 记录交易流水
     * @param order 订单
     * @param transactionType 交易类型
     * @param amount 金额
     * @param status 流水状态
     * @param thirdPartyTransactionNo 第三方流水号
     * @param requestPayload 请求报文
     * @param responsePayload 响应报文
     * @param remark 备注
     */
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

    /**
     * 流水状态取支付结果对应的状态
     *
     * @param result 支付结果
     * @return 支付结果对应的流水状态
     */
    private Integer transactionStatusOfPaymentResult(PaymentResult result) {
        if ("PAID".equals(result.status())) {
            return 1;
        }
        if ("FAILED".equals(result.status()) || "CANCELLED".equals(result.status())) {
            return 0;
        }
        return 2;
    }

    /**
     * 流水状态取退款结果对应的状态
     *
     * @param result 退款结果
     * @return 退款结果对应的流水状态
     */
    private Integer transactionStatusOfRefundResult(RefundResult result) {
        if ("REFUNDED".equals(result.status())) {
            return 1;
        }
        if ("FAILED".equals(result.status())) {
            return 0;
        }
        return 2;
    }

    /**
     * 安全序列化，失败时退回字符串表示
     * @param value 值
     * @return 返回值
    */
    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 转换为视图对象
     *
     * @param order 订单
     * @return 转换后的订单视图对象
     */
    private OrderVO convertToVO(PaymentOrder order) {
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        MerchantChannel channel = order.getChannelId() != null ? merchantChannelMapper.selectById(order.getChannelId()) : null;
        return new OrderVO(
                order.getId(),
                order.getOrderNo(),
                order.getBusinessOrderNo(),
                order.getMerchantId(),
                merchant != null ? merchant.getMerchantName() : null,
                order.getChannelId(),
                channel != null ? channel.getChannelName() : null,
                order.getUserId(),
                order.getChannelType(),
                order.getChannelSubType(),
                order.getOrderAmount(),
                order.getPaidAmount(),
                order.getRefundAmount(),
                order.getDiscountAmount(),
                order.getCurrency(),
                order.getStatus(),
                OrderState.descriptionOf(order.getStatus()),
                order.getSubject(),
                order.getBody(),
                order.getNotifyUrl(),
                order.getReturnUrl(),
                order.getExpireTime(),
                order.getPayTime(),
                order.getCompleteTime(),
                order.getRefundTime(),
                order.getThirdPartyOrderNo(),
                order.getRemark(),
                order.getDeleted(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    /**
     * 转换状态日志为视图对象
     *
     * @param log 日志
     * @return 转换后的日志视图对象
     */
    private OrderStateLogVO convertLogToVO(OrderStateLog log) {
        return new OrderStateLogVO(
                log.getId(),
                log.getOrderId(),
                log.getFromState(),
                log.getToState(),
                log.getEvent(),
                log.getOperator(),
                log.getRemark(),
                log.getCreatedAt());
    }
}
