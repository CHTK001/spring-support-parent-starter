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

    /** 排序映射器 */
    private final PaymentOrderMapper orderMapper;
    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;
    /** 状态machine服务 */
    private final OrderStateMachineService stateMachineService;
    /** 排序状态日志服务 */
    private final OrderStateLogService orderStateLogService;
    /** Transactionrecord服务 */
    private final TransactionRecordService transactionRecordService;
    /** Payment通道注册表 */
    private final PaymentChannelRegistry paymentChannelRegistry;
    /** Paymentrefund排序服务 */
    private final PaymentRefundOrderService paymentRefundOrderService;
    /** paymentcallbackurl解析器 */
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;
    /** Merchantpayment配置服务 */
    private final MerchantPaymentConfigService merchantPaymentConfigService;
    /** 事件publisher */
    private final ApplicationEventPublisher eventPublisher;
    /** Objectmapper */
    private final ObjectMapper objectMapper;
    /** 用户打开标识解析器 */
    private final UserOpenIdResolver userOpenIdResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建订单
     *
     * @param dto dto
     * @return 创建订单的结果
     */
    public OrderVO createOrder(OrderCreateDTO dto) {
        if (dto.getMerchantId() == null) {
            /**
             * 薪酬异常。
             * @param "商户ID不能为空" "商户标识不能为空"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("商户ID不能为空");
        }
        if (dto.getOrderAmount() == null || dto.getOrderAmount().compareTo(BigDecimal.ZERO) < 0) {
            /**
             * 薪酬异常。
             * @param "订单金额不能为负数" "订单金额不能为负数"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单金额不能为负数");
        }

        merchantPaymentConfigService.checkCanCreateOrder(dto.getMerchantId(), dto.getUserId());

        Merchant merchant = requireActiveMerchant(dto.getMerchantId());
        MerchantChannel channel = resolveChannel(dto);
        String orderNo = generateOrderNo();
        String businessOrderNo = StringUtils.hasText(dto.getBusinessOrderNo()) ? dto.getBusinessOrderNo() : orderNo;
        PaymentOrder existing = findActiveOrderByBusinessOrderNo(dto.getMerchantId(), businessOrderNo);
        if (existing != null) {
            /**
             * 断言same业务订单。
             * @param existing existing
             * @param dto dto
             * @param channel 通道
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
        order.setDiscountAmount(nullToZero(dto.getDiscountAmount()));
        order.setCurrency(StringUtils.hasText(dto.getCurrency()) ? dto.getCurrency() : "CNY");
        order.setStatus(OrderState.PENDING.name());
        order.setSubject(StringUtils.hasText(dto.getSubject()) ? dto.getSubject() : "支付订单");
        order.setBody(dto.getBody());
        order.setNotifyUrl(paymentCallbackUrlResolver.resolvePayNotifyUrl(dto.getNotifyUrl(), channel, merchant, orderNo));
        order.setReturnUrl(firstNonBlank(dto.getReturnUrl(), channel.getReturnUrl(), merchant.getDefaultReturnUrl()));
        order.setExpireTime(LocalDateTime.now().plusMinutes(resolveExpireMinutes(dto, merchant)));
        order.setDeleted(0);

        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        com.chua.payment.support.channel.OrderCreateRequest createRequest = new com.chua.payment.support.channel.OrderCreateRequest();
        createRequest.setOrderNo(orderNo);
        createRequest.setUserId(dto.getUserId());
        createRequest.setAmount(dto.getOrderAmount());
        createRequest.setSubject(order.getSubject());
        createRequest.setBody(order.getBody());
        createRequest.setCurrency(order.getCurrency());
        createRequest.setAttach(businessOrderNo);

        com.chua.payment.support.channel.OrderCreateResult createResult = paymentChannel.createOrder(channel, createRequest);
        if (!createResult.isSuccess()) {
            /**
             * 薪酬异常。
             * @param createResult.getMessage( 创建结果.getmessage(
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单创建失败: " + createResult.getMessage());
        }

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            PaymentOrder duplicated = findActiveOrderByBusinessOrderNo(dto.getMerchantId(), businessOrderNo);
            if (duplicated != null) {
                /**
                 * 断言same业务订单。
                 * @param duplicated 重复
                 * @param dto dto
                 * @param channel 通道
                 */
                assertSameBusinessOrder(duplicated, dto, channel);
                return convertToVO(duplicated);
            }
            /**
             * 薪酬异常。
             * @param "业务订单号已存在，订单创建失败" "业务订单号已存在，订单创建失败"
             * @param e e
             * @return 薪酬异常的结果
             */
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
    /** 获取订单 */
    public OrderVO getOrder(Long id) {
        return convertToVO(requireOrder(id));
    }

    @Override
    public Page<OrderVO> listOrders(PaymentOrderPageRequest request) {
        if (request == null) {
            /**
             * 薪酬异常。
             * @param "查询参数不能为空" "查询参数不能为空"
             * @return 薪酬异常的结果
             */
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
    /** 列表订单日志 */
    public List<OrderStateLogVO> listOrderLogs(Long id) {
        /**
         * require订单。
         * @param id 标识
         */
        requireOrder(id);
        return orderStateLogService.listByOrderId(id).stream().map(this::convertLogToVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 薪酬订单
     *
     * @param id 标识
     * @param dto dto
     * @return 薪酬订单的结果
     */
    public PaymentResult payOrder(Long id, OrderPayDTO dto) {
        merchantPaymentConfigService.checkCanPayOrder(id);

        PaymentOrder order = requireOrder(id);
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        String operator = defaultOperator(dto != null ? dto.getOperator() : null);
        /**
         * ensure薪酬。
         * @param order 订单
         * @param operator 操作符
         */
        ensurePaying(order, operator);

        // 零金额订单：跳过渠道支付，直接标记成功
        if (order.getOrderAmount().compareTo(BigDecimal.ZERO) == 0) {
            /**
             * 薪酬成功。
             * @param id 标识
             * @param BigDecimal.ZERO bigdecimal.ZERO
             * @param order.getOrderNo( 订单.获取订单no(
             */
            paySuccess(id, BigDecimal.ZERO, "ZERO_AMOUNT_" + order.getOrderNo(), operator);
            PaymentResult result = new PaymentResult();
            result.setSuccess(true);
            result.setStatus("PAID");
            result.setMessage("零金额订单已自动支付成功");
            return result;
        }

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
        String payerOpenId = dto != null ? dto.getPayerOpenId() : null;
        if (!StringUtils.hasText(payerOpenId)) {
            payerOpenId = userOpenIdResolver.resolveOpenId(order.getMerchantId(), order.getUserId(), order.getChannelType());
        }
        request.setPayerOpenId(payerOpenId);
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
            /**
             * 薪酬失败。
             * @param id 标识
             * @param firstNonBlank(result.getMessage( 第一个nonblank(结果.getmessage(
             */
            payFail(id, firstNonBlank(result.getMessage(), "支付失败"), operator);
            return result;
        }
        if ("CANCELLED".equals(result.getStatus())) {
            /**
             * cancel订单。
             * @param id 标识
             * @param operator 操作符
             * @param firstNonBlank(result.getMessage( 第一个nonblank(结果.getmessage(
             */
            cancelOrder(id, operator, firstNonBlank(result.getMessage(), "订单已关闭"));
            return result;
        }
        recordTransaction(latest,
                "PAY",
                latest.getOrderAmount(),
                /**
                 * transaction状态的薪酬结果。
                 * @param result 结果
                 */
                transactionStatusOfPaymentResult(result),
                result.getTradeNo(),
                /**
                 * safejson。
                 * @param request 请求
                 */
                safeJson(request),
                /**
                 * safejson。
                 * @param result 结果
                 */
                safeJson(result),
                "真实支付发起");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 同步订单
     *
     * @param id 标识
     * @return 同步订单的结果
     */
    public OrderVO syncOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        PaymentResult result = paymentChannel.query(channel, order.getOrderNo());
        if ("PAID".equals(result.getStatus())) {
            /**
             * 薪酬成功。
             * @param id 标识
             * @param result.getPaidAmount( 结果.获取paidamount(
             */
            paySuccess(id, result.getPaidAmount(), result.getTradeNo(), "channel-sync");
        } else if ("FAILED".equals(result.getStatus())) {
            /**
             * 薪酬失败。
             * @param id 标识
             * @param firstNonBlank(result.getMessage( 第一个nonblank(结果.getmessage(
             */
            payFail(id, firstNonBlank(result.getMessage(), "渠道返回支付失败"), "channel-sync");
        } else if ("CANCELLED".equals(result.getStatus()) && canCancelByState(requireOrder(id).getStatus())) {
            /**
             * cancel订单。
             * @param id 标识
             * @param "channel-sync" "通道-同步"
             * @param firstNonBlank(result.getMessage( 第一个nonblank(结果.getmessage(
             */
            cancelOrder(id, "channel-sync", firstNonBlank(result.getMessage(), "渠道返回订单关闭"));
        }
        return convertToVO(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 开始薪酬
     *
     * @param id 标识
     * @param operator 操作符
     * @return 启动薪酬的结果
     */
    public boolean startPay(Long id, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.PAYING.name().equals(order.getStatus()) || isPaidState(order.getStatus())) {
            return true;
        }
        if (!OrderState.PENDING.name().equals(order.getStatus())) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许发起支付" "当前订单状态不允许发起支付"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许发起支付");
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY, defaultOperator(operator));
        if (transitionResult == OrderTransitionResult.APPLIED) {
            /**
             * recordtransaction。
             * @param order 订单
             * @param "PAY" "薪酬"
             * @param order.getOrderAmount( 订单.获取订单amount(
             */
            recordTransaction(order, "PAY", order.getOrderAmount(), 2, null, null, null, "发起支付");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 薪酬成功
     *
     * @param id 标识
     * @param paidAmount paidamount
     * @param thirdPartyOrderNo thirdparty订单no
     * @param operator 操作符
     * @return 薪酬成功的结果
     */
    public boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator) {
        PaymentOrder order = requireOrder(id);
        if (isPaidState(order.getStatus())) {
            /**
             * backfill薪酬成功。
             * @param order 订单
             * @param paidAmount paidamount
             * @param thirdPartyOrderNo thirdparty订单no
             */
            backfillPaymentSuccess(order, paidAmount, thirdPartyOrderNo);
            return true;
        }
        if (!(OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus()))) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许标记支付成功" "当前订单状态不允许标记支付成功"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许标记支付成功");
        }
        /**
         * ensure薪酬。
         * @param order 订单
         * @param defaultOperator(operator 默认操作符(操作符
         */
        ensurePaying(order, defaultOperator(operator));
        order = requireOrder(id);
        if (isPaidState(order.getStatus())) {
            /**
             * backfill薪酬成功。
             * @param order 订单
             * @param paidAmount paidamount
             * @param thirdPartyOrderNo thirdparty订单no
             */
            backfillPaymentSuccess(order, paidAmount, thirdPartyOrderNo);
            return true;
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY_SUCCESS, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        /**
         * backfill薪酬成功。
         * @param latest latest
         * @param paidAmount paidamount
         * @param thirdPartyOrderNo thirdparty订单no
         */
        backfillPaymentSuccess(latest, paidAmount, thirdPartyOrderNo);
        latest = requireOrder(id);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            /**
             * recordtransaction。
             * @param latest latest
             * @param "PAY" "薪酬"
             * @param latest.getPaidAmount( latest.获取paidamount(
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
     * 薪酬失败
     *
     * @param id 标识
     * @param reason ReasonMLmlml
     * @param operator 操作符
     * @return 薪酬失败的结果
     */
    public boolean payFail(Long id, String reason, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.FAILED.name().equals(order.getStatus())) {
            /**
             * backfill评论。
             * @param order 订单
             * @param reason ReasonMLmlml
             */
            backfillRemark(order, reason);
            return true;
        }
        if (!(OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus()))) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许标记支付失败" "当前订单状态不允许标记支付失败"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许标记支付失败");
        }
        /**
         * ensure薪酬。
         * @param order 订单
         * @param defaultOperator(operator 默认操作符(操作符
         */
        ensurePaying(order, defaultOperator(operator));
        order = requireOrder(id);
        if (OrderState.FAILED.name().equals(order.getStatus())) {
            /**
             * backfill评论。
             * @param order 订单
             * @param reason ReasonMLmlml
             */
            backfillRemark(order, reason);
            return true;
        }
        OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.PAY_FAIL, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        /**
         * backfill评论。
         * @param latest latest
         * @param reason ReasonMLmlml
         */
        backfillRemark(latest, reason);
        if (transitionResult == OrderTransitionResult.APPLIED) {
            /**
             * recordtransaction。
             * @param latest latest
             * @param "PAY" "薪酬"
             * @param latest.getOrderAmount( latest.获取订单amount(
             */
            recordTransaction(latest, "PAY", latest.getOrderAmount(), 0, null, null, reason, "支付失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * cancel订单
     *
     * @param id 标识
     * @param operator 操作符
     * @param reason ReasonMLmlml
     * @return cancel订单的结果
     */
    public boolean cancelOrder(Long id, String operator, String reason) {
        return cancelOrder(id, operator, reason, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * cancel订单
     *
     * @param id 标识
     * @param operator 操作符
     * @param reason ReasonMLmlml
     * @param refund 退税
     * @return cancel订单的结果
     */
    public boolean cancelOrder(Long id, String operator, String reason, boolean refund) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.CANCELLED.name().equals(order.getStatus())) {
            /**
             * backfill评论。
             * @param order 订单
             * @param reason ReasonMLmlml
             */
            backfillRemark(order, reason);
            return true;
        }

 // 不退款取消：仅 PENDING/薪酬 可取消
        if (!refund) {
            if (!canCancelByState(order.getStatus())) {
                /**
                 * 薪酬异常。
                 * @param "当前订单状态不允许取消" "当前订单状态不允许取消"
                 * @return 薪酬异常的结果
                 */
                throw new PaymentException("当前订单状态不允许取消");
            }
            /**
             * 关闭通道订单。
             * @param order 订单
             */
            closeChannelOrder(order);
            /**
             * 发送状态事件。
             * @param order.getId( 订单.获取标识(
             */
            sendStateEvent(order.getId(), OrderEvent.CANCEL, defaultOperator(operator));
            PaymentOrder latest = requireOrder(id);
            /**
             * backfill评论。
             * @param latest latest
             * @param reason ReasonMLmlml
             */
            backfillRemark(latest, reason);
            return true;
        }

        // === 退款取消 ===
        if (OrderState.PENDING.name().equals(order.getStatus()) || OrderState.PAYING.name().equals(order.getStatus())) {
            // 未支付订单：直接取消，无需退款
            /**
             * 关闭通道订单。
             * @param order 订单
             */
            closeChannelOrder(order);
            /**
             * 发送状态事件。
             * @param order.getId( 订单.获取标识(
             */
            sendStateEvent(order.getId(), OrderEvent.CANCEL, defaultOperator(operator));
            PaymentOrder latest = requireOrder(id);
            /**
             * backfill评论。
             * @param latest latest
             * @param reason ReasonMLmlml
             */
            backfillRemark(latest, reason);
            return true;
        }

        if (!(OrderState.PAID.name().equals(order.getStatus()) || OrderState.COMPLETED.name().equals(order.getStatus()))) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许退款取消" "当前订单状态不允许退款取消"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许退款取消");
        }

        // 发起退款
        if (paymentRefundOrderService.hasProcessingRefund(order.getId())) {
            /**
             * 薪酬异常。
             * @param "存在处理中退款单，请稍后再试" "存在处理中退款单，请稍后再试"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("存在处理中退款单，请稍后再试");
        }

        BigDecimal refundAmount = nullToZero(order.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0
                ? order.getPaidAmount() : order.getOrderAmount();
        String operatorVal = defaultOperator(operator);

 // 先标记 退税
        /**
         * 发送状态事件。
         * @param order.getId( 订单.获取标识(
         */
        sendStateEvent(order.getId(), OrderEvent.REFUND, operatorVal);

        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());

        String refundNo = generateRefundNo();
        RefundRequest request = new RefundRequest();
        request.setOrderNo(order.getOrderNo());
        request.setUserId(order.getUserId());
        request.setTradeNo(order.getThirdPartyOrderNo());
        request.setRefundNo(refundNo);
        request.setRefundAmount(refundAmount);
        request.setTotalAmount(resolvedPaidAmount(order));
        request.setReason(StringUtils.hasText(reason) ? "取消退款:" + reason : "取消退款");
        request.setNotifyUrl(paymentCallbackUrlResolver.resolveRefundNotifyUrl(channel, order.getMerchantId(), refundNo, order.getNotifyUrl()));
        String requestPayload = safeJson(request);

        paymentRefundOrderService.createRefundOrder(new CreateRefundOrderDTO(order, refundNo, refundAmount,
                StringUtils.hasText(reason) ? "取消退款:" + reason : "取消退款",
                operatorVal, requestPayload));

        RefundResult result = paymentChannel.refund(channel, request);
        String responsePayload = safeJson(result);
        String thirdPartyRefundNo = firstNonBlank(result.getTradeNo(), result.getRefundNo());

        if ("REFUNDED".equals(result.getStatus())) {
            // 退款成功 → 标记退款成功 → 再改 CANCELLED
            BigDecimal actualAmount = result.getRefundAmount() != null ? result.getRefundAmount() : refundAmount;
            paymentRefundOrderService.markRefunded(new MarkRefundedDTO(refundNo, actualAmount, thirdPartyRefundNo,
                    responsePayload, operatorVal, "取消退款成功"));
            PaymentOrder latestOrder = requireOrder(id);
            BigDecimal totalRefunded = aggregateRefundedAmount(latestOrder);
            /**
             * 同步退税aggregate。
             * @param latestOrder latest订单
             * @param totalRefunded total退税
             */
            syncRefundAggregate(latestOrder, totalRefunded);
 // 直接更新为 CANCELLED（不走状态机，因为 退税→CANCELLED 未定义）
            /**
             * force订单状态。
             * @param id 标识
             * @param OrderState.CANCELLED.name( 订单状态.cancelled.名称(
             */
            forceOrderStatus(id, OrderState.CANCELLED.name());
            orderStateLogService.log(new OrderStateLogRequest(id, OrderState.REFUNDED.name(), OrderState.CANCELLED.name(),
                    "CANCEL", operatorVal, "退款方式取消"));
            recordTransaction(latestOrder, "REFUND", actualAmount, 1,
                    thirdPartyRefundNo, requestPayload, responsePayload, "取消退款");
            return true;
        }

        if ("FAILED".equals(result.getStatus())) {
            paymentRefundOrderService.markFailed(refundNo, responsePayload, operatorVal,
                    firstNonBlank(result.getMessage(), "取消退款失败"));
            // 退款失败 → 回退到 PAID
            /**
             * force订单状态。
             * @param id 标识
             * @param OrderState.PAID.name( 订单状态.paid.名称(
             */
            forceOrderStatus(id, OrderState.PAID.name());
            orderStateLogService.log(new OrderStateLogRequest(id, OrderState.REFUNDING.name(), OrderState.PAID.name(),
                    "REFUND_FAIL", operatorVal,
                    firstNonBlank(result.getMessage(), "取消退款失败")));
            /**
             * 薪酬异常。
             * @param result.getMessage( 结果.获取消息(
             * @return 薪酬异常的结果
             */
            throw new PaymentException("取消退款失败: " + result.getMessage());
        }

        // 异步退款处理中 → 标记退款处理中
        paymentRefundOrderService.markProcessing(refundNo, thirdPartyRefundNo, responsePayload,
                firstNonBlank(result.getMessage(), "取消退款发起"));
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
     * @param operator 操作符
     * @return 完成订单的结果
     */
    public boolean completeOrder(Long id, String operator) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.COMPLETED.name().equals(order.getStatus())) {
            /**
             * backfill完成时间。
             * @param order 订单
             */
            backfillCompleteTime(order);
            return true;
        }
        if (!OrderState.PAID.name().equals(order.getStatus())) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许完成" "当前订单状态不允许完成"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许完成");
        }
        /**
         * 发送状态事件。
         * @param order.getId( 订单.获取标识(
         */
        sendStateEvent(order.getId(), OrderEvent.COMPLETE, defaultOperator(operator));
        PaymentOrder latest = requireOrder(id);
        /**
         * backfill完成时间。
         * @param latest latest
         */
        backfillCompleteTime(latest);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 退税订单
     *
     * @param id 标识
     * @param dto dto
     * @return 退税订单的结果
     */
    public boolean refundOrder(Long id, RefundApplyDTO dto) {
        PaymentOrder order = requireOrder(id);
        if (OrderState.REFUNDED.name().equals(order.getStatus())) {
            return true;
        }
        if (!(OrderState.PAID.name().equals(order.getStatus())
                || OrderState.COMPLETED.name().equals(order.getStatus())
                || OrderState.REFUNDING.name().equals(order.getStatus()))) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许退款" "当前订单状态不允许退款"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许退款");
        }
        if (paymentRefundOrderService.hasProcessingRefund(order.getId())) {
            /**
             * 薪酬异常。
             * @param "存在处理中退款单，请稍后再试" "存在处理中退款单，请稍后再试"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("存在处理中退款单，请稍后再试");
        }
        if (dto == null || !StringUtils.hasText(dto.getRefundReason())) {
            /**
             * 薪酬异常。
             * @param "退款原因不能为空" "退款原因不能为空"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款原因不能为空");
        }

        BigDecimal currentRefunded = aggregateRefundedAmount(order);
        /**
         * 同步退税aggregate。
         * @param order 订单
         * @param currentRefunded 当前退税
         */
        syncRefundAggregate(order, currentRefunded);
        order = requireOrder(id);
        BigDecimal refundAmount = dto != null && dto.getRefundAmount() != null ? dto.getRefundAmount() : refundableAmount(order, currentRefunded);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            /**
             * 薪酬异常。
             * @param "退款金额必须大于0" "退款金额必须大于0"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款金额必须大于0");
        }
        if (refundAmount.compareTo(refundableAmount(order, currentRefunded)) > 0) {
            /**
             * 薪酬异常。
             * @param "退款金额超过可退金额" "退款金额超过可退金额"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款金额超过可退金额");
        }

        MerchantChannel channel = requireEnabledChannel(order.getChannelId());
        PaymentChannel paymentChannel = paymentChannelRegistry.getChannel(order.getChannelType(), order.getChannelSubType());
        String operator = defaultOperator(dto != null ? dto.getOperator() : null);
        if (!OrderState.REFUNDING.name().equals(order.getStatus())) {
            OrderTransitionResult transitionResult = sendStateEvent(order.getId(), OrderEvent.REFUND, operator);
            if (transitionResult == OrderTransitionResult.DUPLICATED && OrderState.REFUNDED.name().equals(requireOrder(id).getStatus())) {
                return true;
            }
        }

        PaymentOrder latest = requireOrder(id);
        /**
         * backfill评论。
         * @param latest latest
         * @param dto.getRefundReason( dto.获取退税ReasonMLML(
         */
        backfillRemark(latest, dto != null ? dto.getRefundReason() : null);

        RefundRequest request = new RefundRequest();
        request.setOrderNo(latest.getOrderNo());
        request.setUserId(latest.getUserId());
        request.setTradeNo(latest.getThirdPartyOrderNo());
        request.setRefundNo(generateRefundNo());
        request.setRefundAmount(refundAmount);
        request.setTotalAmount(resolvedPaidAmount(latest));
        request.setReason(dto != null ? dto.getRefundReason() : null);
        request.setNotifyUrl(paymentCallbackUrlResolver.resolveRefundNotifyUrl(channel, latest.getMerchantId(), request.getRefundNo(), latest.getNotifyUrl()));
        String requestPayload = safeJson(request);

        paymentRefundOrderService.createRefundOrder(new CreateRefundOrderDTO(latest,
                request.getRefundNo(),
                refundAmount,
                dto != null ? dto.getRefundReason() : null,
                operator,
                requestPayload));

        RefundResult result = paymentChannel.refund(channel, request);
        String responsePayload = safeJson(result);
        String thirdPartyRefundNo = firstNonBlank(result.getTradeNo(), result.getRefundNo());
        if ("REFUNDED".equals(result.getStatus())) {
            return refundSuccess(new RefundSuccessDTO(
                    request.getRefundNo(),
                    result.getRefundAmount() != null ? result.getRefundAmount() : refundAmount,
                    thirdPartyRefundNo,
                    operator,
                    responsePayload,
                    firstNonBlank(result.getMessage(), dto != null ? dto.getRefundReason() : null)));
        }
        if ("FAILED".equals(result.getStatus())) {
            return refundFail(request.getRefundNo(),
                    responsePayload,
                    operator,
                    firstNonBlank(result.getMessage(), "渠道退款失败"));
        }
        paymentRefundOrderService.markProcessing(request.getRefundNo(), thirdPartyRefundNo, responsePayload, firstNonBlank(result.getMessage(), "真实退款发起"));
        recordTransaction(latest,
                "REFUND",
                refundAmount,
                /**
                 * transaction状态的退税结果。
                 * @param result 结果
                 */
                transactionStatusOfRefundResult(result),
                thirdPartyRefundNo,
                requestPayload,
                responsePayload,
                "真实退款发起");
        return result.isSuccess();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 退税成功
     *
     * @param id 标识
     * @param refundAmount 退税amount
     * @param operator 操作符
     * @return 退税成功的结果
     */
    public boolean refundSuccess(Long id, BigDecimal refundAmount, String operator) {
        PaymentOrder order = requireOrder(id);
        PaymentRefundOrder refundOrder = resolveManualRefundOrder(order, refundAmount, defaultOperator(operator), "手动标记退款成功");
        if (refundOrder == null) {
            /**
             * 同步退税aggregate。
             * @param order 订单
             * @param aggregateRefundedAmount(order aggregate退税amount(订单
             */
            syncRefundAggregate(order, aggregateRefundedAmount(order));
            return true;
        }
        return refundSuccess(new RefundSuccessDTO(refundOrder.getRefundNo(), refundAmount, refundOrder.getThirdPartyRefundNo(), operator, null, "手动标记退款成功"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 退税失败
     *
     * @param id 标识
     * @param reason ReasonMLmlml
     * @param operator 操作符
     * @return 退税失败的结果
     */
    public boolean refundFail(Long id, String reason, String operator) {
        PaymentOrder order = requireOrder(id);
        PaymentRefundOrder refundOrder = resolveManualRefundOrder(order, null, defaultOperator(operator), reason);
        if (refundOrder == null) {
            /**
             * backfill评论。
             * @param order 订单
             * @param reason ReasonMLmlml
             */
            backfillRemark(order, reason);
            return true;
        }
        return refundFail(refundOrder.getRefundNo(), null, operator, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 退税成功
     * @param refundNo 退税no
     * @param refundAmount 退税amount
     * @param thirdPartyRefundNo thirdparty退税no
     * @param operator 操作符
     * @param responsePayload 响应payload
     * @param remark 评论
     * @param dto dto
     * @return 退税成功的结果
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
                 * 默认操作符。
                 * @param dto.operator( dto.操作符(
                 */
                defaultOperator(dto.operator()),
                firstNonBlank(dto.remark(), refundOrder.getReason())));

        PaymentOrder order = requireOrder(refundOrder.getOrderId());
        BigDecimal totalRefunded = aggregateRefundedAmount(order);
        /**
         * 同步退税aggregate。
         * @param order 订单
         * @param totalRefunded total退税
         */
        syncRefundAggregate(order, totalRefunded);
        PaymentOrder latestOrder = requireOrder(order.getId());
        String targetStatus = resolveOrderStatusAfterRefundSuccess(latestOrder, totalRefunded);
        /**
         * 改变订单状态ifnecessary。
         * @param latestOrder latest订单
         * @param targetStatus Target状态
         * @param OrderEvent.REFUND_SUCCESS 订单事件.退税成功
         * @param dto.operator( dto.操作符(
         */
        changeOrderStatusIfNecessary(latestOrder, targetStatus, OrderEvent.REFUND_SUCCESS, dto.operator(), firstNonBlank(dto.remark(), "退款成功"));
        latestOrder = requireOrder(order.getId());
        if (!alreadyRefunded) {
            recordTransaction(latestOrder,
                    "REFUND",
                    latestRefundOrder.getRefundAmount(),
                    1,
                    /**
                     * 第一个nonblank。
                     * @param latestRefundOrder.getThirdPartyRefundNo( latest退税订单.getthirdparty退税no(
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
                  * 第一个nonblank。
                  * @param latestRefundOrder.getThirdPartyRefundNo( latest退税订单.getthirdparty退税no(
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
     * 退税失败
     *
     * @param refundNo 退税no
     * @param responsePayload 响应payload
     * @param operator 操作符
     * @param reason ReasonMLmlml
     * @return 退税失败的结果
     */
    public boolean refundFail(String refundNo, String responsePayload, String operator, String reason) {
        PaymentRefundOrder refundOrder = requireRefundOrder(refundNo);
        boolean alreadyFailed = RefundOrderStatus.FAILED.name().equals(refundOrder.getStatus());
        PaymentRefundOrder latestRefundOrder = paymentRefundOrderService.markFailed(refundNo,
                responsePayload,
                /**
                 * 默认操作符。
                 * @param operator 操作符
                 */
                defaultOperator(operator),
                firstNonBlank(reason, refundOrder.getReason(), "退款失败"));

        PaymentOrder order = requireOrder(refundOrder.getOrderId());
        BigDecimal totalRefunded = aggregateRefundedAmount(order);
        /**
         * 同步退税aggregate。
         * @param order 订单
         * @param totalRefunded total退税
         */
        syncRefundAggregate(order, totalRefunded);
        PaymentOrder latestOrder = requireOrder(order.getId());
        String targetStatus = resolveOrderStatusAfterRefundFailure(latestOrder, totalRefunded, latestRefundOrder);
        /**
         * 改变订单状态ifnecessary。
         * @param latestOrder latest订单
         * @param targetStatus Target状态
         * @param OrderEvent.REFUND_FAIL 订单事件.退税失败
         * @param operator 操作符
         * @param "退款失败" "退款失败"
         */
        changeOrderStatusIfNecessary(latestOrder, targetStatus, OrderEvent.REFUND_FAIL, operator, firstNonBlank(reason, "退款失败"));
        latestOrder = requireOrder(order.getId());
        /**
         * backfill评论。
         * @param latestOrder latest订单
         * @param reason ReasonMLmlml
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
     * @return 删除订单的结果
     */
    public boolean deleteOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        if (!(OrderState.PENDING.name().equals(order.getStatus())
                || OrderState.CANCELLED.name().equals(order.getStatus())
                || OrderState.FAILED.name().equals(order.getStatus())
                || OrderState.REFUNDED.name().equals(order.getStatus()))) {
            /**
             * 薪酬异常。
             * @param "当前订单状态不允许删除" "当前订单状态不允许删除"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("当前订单状态不允许删除");
        }
        order.setDeleted(1);
        return orderMapper.updateById(order) > 0;
    }

    @Override
    /** generate订单no */
    public String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 超时订单
     *
     * @param id 标识
     */
    public void timeoutOrder(Long id) {
        PaymentOrder order = requireOrder(id);
        if (!OrderState.PENDING.name().equals(order.getStatus())) {
            return;
        }
        /**
         * 发送状态事件。
         * @param order.getId( 订单.获取标识(
         */
        sendStateEvent(order.getId(), OrderEvent.TIMEOUT, "system");
        PaymentOrder latest = requireOrder(id);
        /**
         * backfill评论。
         * @param latest latest
         * @param "支付超时自动取消" "支付超时自动取消"
         */
        backfillRemark(latest, "支付超时自动取消");
    }

    /**
     * require活跃merchant
     *
     * @param merchantId merchantid
     * @return require活跃merchant的结果
     */
    private Merchant requireActiveMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            /**
             * 薪酬异常。
             * @param "商户不存在" "商户不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("商户不存在");
        }
        if (!MerchantStatus.ACTIVE.getCode().equals(merchant.getStatus())) {
            /**
             * 薪酬异常。
             * @param "商户未激活，不能创建订单" "商户未激活，不能创建订单"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("商户未激活，不能创建订单");
        }
        return merchant;
    }

    /**
     * require订单
     *
     * @param id 标识
     * @return require订单的结果
     */
    private PaymentOrder requireOrder(Long id) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null || Integer.valueOf(1).equals(order.getDeleted())) {
            // Converter 未覆盖 Boolean→int 软删除标记比较场景，保留手写转换（规约四十二豁免）
            /**
             * 薪酬异常。
             * @param "订单不存在" "订单不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单不存在");
        }
        return order;
    }

    /**
     * require退税订单
     *
     * @param refundNo 退税no
     * @return require退税订单的结果
     */
    private PaymentRefundOrder requireRefundOrder(String refundNo) {
        PaymentRefundOrder refundOrder = paymentRefundOrderService.getByRefundNo(refundNo);
        if (refundOrder == null) {
            /**
             * 薪酬异常。
             * @param refundNo 退税no
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款单不存在: " + refundNo);
        }
        return refundOrder;
    }

    /**
     * require已启用通道
     *
     * @param channelId 通道标识
     * @return require已启用通道的结果
     */
    private MerchantChannel requireEnabledChannel(Long channelId) {
        if (channelId == null) {
            /**
             * 薪酬异常。
             * @param "订单未绑定支付方式" "订单未绑定支付方式"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单未绑定支付方式");
        }
        MerchantChannel channel = merchantChannelMapper.selectById(channelId);
        if (channel == null) {
            /**
             * 薪酬异常。
             * @param "支付方式不存在" "支付方式不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式不存在");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            /**
             * 薪酬异常。
             * @param "支付方式未启用" "支付方式未启用"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式未启用");
        }
        return channel;
    }

    /**
     * 解析通道
     *
     * @param dto dto
     * @return resolve通道的结果
     */
    private MerchantChannel resolveChannel(OrderCreateDTO dto) {
        MerchantChannel channel;
        if (dto.getChannelId() != null) {
            channel = merchantChannelMapper.selectById(dto.getChannelId());
            if (channel == null) {
                /**
                 * 薪酬异常。
                 * @param "支付方式不存在" "支付方式不存在"
                 * @return 薪酬异常的结果
                 */
                throw new PaymentException("支付方式不存在");
            }
        } else {
            if (!StringUtils.hasText(dto.getChannelType())) {
                /**
                 * 薪酬异常。
                 * @param "支付方式不能为空" "支付方式不能为空"
                 * @return 薪酬异常的结果
                 */
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
                /**
                 * 薪酬异常。
                 * @param "未找到可用的支付方式配置" "未找到可用的支付方式配置"
                 * @return 薪酬异常的结果
                 */
                throw new PaymentException("未找到可用的支付方式配置");
            }
        }
        if (!dto.getMerchantId().equals(channel.getMerchantId())) {
            /**
             * 薪酬异常。
             * @param "订单商户和支付方式不匹配" "订单商户和支付方式不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单商户和支付方式不匹配");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(channel.getStatus())) {
            /**
             * 薪酬异常。
             * @param "支付方式未启用" "支付方式未启用"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式未启用");
        }
        return resolveEffectiveChannel(channel, dto.getMerchantId());
    }

    /**
     * 解析effective通道
     *
     * @param channel 通道
     * @param merchantId merchantid
     * @return resolveeffective通道的结果
     */
    private MerchantChannel resolveEffectiveChannel(MerchantChannel channel, Long merchantId) {
        if (channel == null || !"COMPOSITE".equalsIgnoreCase(channel.getChannelType())
                || !"AGGREGATE_ROUTE".equalsIgnoreCase(channel.getChannelSubType())) {
            return channel;
        }
        Long targetChannelId = resolveRouteChannelId(channel);
        if (targetChannelId == null) {
            /**
             * 薪酬异常。
             * @param defaultChannelId" 默认通道标识"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置未提供 targetChannelId 或 defaultChannelId");
        }
        if (channel.getId() != null && channel.getId().equals(targetChannelId)) {
            /**
             * 薪酬异常。
             * @param "直营网关配置不能指向自身" "直营网关配置不能指向自身"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置不能指向自身");
        }
        MerchantChannel targetChannel = merchantChannelMapper.selectById(targetChannelId);
        if (targetChannel == null) {
            /**
             * 薪酬异常。
             * @param targetChannelId Target通道标识
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关目标渠道不存在: " + targetChannelId);
        }
        if (!merchantId.equals(targetChannel.getMerchantId())) {
            /**
             * 薪酬异常。
             * @param "直营网关目标渠道和订单商户不匹配" "直营网关目标渠道和订单商户不匹配"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关目标渠道和订单商户不匹配");
        }
        if ("COMPOSITE".equalsIgnoreCase(targetChannel.getChannelType())) {
            /**
             * 薪酬异常。
             * @param 渠道" 渠道"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置不能嵌套 COMPOSITE 渠道");
        }
        if (!ChannelStatus.ENABLED.getCode().equals(targetChannel.getStatus())) {
            /**
             * 薪酬异常。
             * @param "直营网关目标渠道未启用" "直营网关目标渠道未启用"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关目标渠道未启用");
        }
        if (!paymentChannelRegistry.supports(targetChannel.getChannelType(), targetChannel.getChannelSubType())) {
            throw new PaymentException("直营网关目标渠道当前版本不可执行: "
                    + targetChannel.getChannelType() + "/" + targetChannel.getChannelSubType());
        }
        return targetChannel;
    }

    /**
     * 解析route通道标识
     *
     * @param channel 通道
     * @return resolveroute通道id的结果
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
            /**
             * 薪酬异常。
             * @param "直营网关配置格式错误" "直营网关配置格式错误"
             * @param e e
             * @return 薪酬异常的结果
             */
            throw new PaymentException("直营网关配置格式错误", e);
        }
    }

    /**
     * 解析Long
     *
     * @param values 值
     * @return 解析long的结果
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
                    /**
                     * 薪酬异常。
                     * @param text 文本
                     * @param e e
                     * @return 薪酬异常的结果
                     */
                    throw new PaymentException("直营网关 channelId 格式错误: " + text, e);
                }
            }
        }
        return null;
    }

    /**
     * 查找活跃订单by业务订单no
     *
     * @param merchantId merchantid
     * @param businessOrderNo 业务订单no
     * @return find活跃订单by业务订单no的结果
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
     * 断言same业务订单
     *
     * @param existing existing
     * @param dto dto
     * @param channel 通道
     */
    private void assertSameBusinessOrder(PaymentOrder existing, OrderCreateDTO dto, MerchantChannel channel) {
        if (!existing.getMerchantId().equals(dto.getMerchantId())) {
            /**
             * 薪酬异常。
             * @param "业务订单号已存在且商户不一致" "业务订单号已存在且商户不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("业务订单号已存在且商户不一致");
        }
        if (channel.getId() != null && !channel.getId().equals(existing.getChannelId())) {
            /**
             * 薪酬异常。
             * @param "业务订单号已存在且支付方式不一致" "业务订单号已存在且支付方式不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("业务订单号已存在且支付方式不一致");
        }
        if (existing.getOrderAmount() != null && dto.getOrderAmount() != null
                && existing.getOrderAmount().compareTo(dto.getOrderAmount()) != 0) {
            /**
             * 薪酬异常。
             * @param "业务订单号已存在且订单金额不一致" "业务订单号已存在且订单金额不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("业务订单号已存在且订单金额不一致");
        }
        String currency = StringUtils.hasText(dto.getCurrency()) ? dto.getCurrency() : "CNY";
        if (StringUtils.hasText(existing.getCurrency()) && !existing.getCurrency().equalsIgnoreCase(currency)) {
            /**
             * 薪酬异常。
             * @param "业务订单号已存在且币种不一致" "业务订单号已存在且币种不一致"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("业务订单号已存在且币种不一致");
        }
    }

    /**
     * ensure薪酬
     *
     * @param order 订单
     * @param operator 操作符
     */
    private void ensurePaying(PaymentOrder order, String operator) {
        if (OrderState.PENDING.name().equals(order.getStatus())) {
            /**
             * 启动薪酬。
             * @param order.getId( 订单.获取标识(
             */
            startPay(order.getId(), operator);
        }
    }

    /**
     * 发送状态事件
     *
     * @param orderId 订单标识
     * @param event 事件
     * @param operator 操作符
     * @return 发送状态事件的结果
     */
    private OrderTransitionResult sendStateEvent(Long orderId, OrderEvent event, String operator) {
        OrderTransitionResult result = stateMachineService.sendEvent(orderId, event, operator);
        if (result == OrderTransitionResult.REJECTED) {
            /**
             * 薪酬异常。
             * @param event.name( 事件.名称(
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单状态流转失败: " + event.name());
        }
        return result;
    }

    /**
     * 解析expireminutes
     *
     * @param dto dto
     * @param merchant merchant
     * @return resolveExpireMinutes的结果
     */
    private int resolveExpireMinutes(OrderCreateDTO dto, Merchant merchant) {
        if (dto.getExpireMinutes() != null && dto.getExpireMinutes() > 0) {
            return dto.getExpireMinutes();
        }
        if (Boolean.TRUE.equals(merchant.getPaymentAutoCloseEnabled())
                && merchant.getPaymentAutoCloseMinutes() != null
                && merchant.getPaymentAutoCloseMinutes() > 0) {
            return merchant.getPaymentAutoCloseMinutes();
        }
        return 30;
    }

    /**
     * 解析manual退税订单
     * @param order 订单
     * @param refundAmount 退税amount
     * @param operator 操作符
     * @param reason ReasonMLmlml
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
                 * generate退税no。
                 */
                generateRefundNo(),
                amount,
                reason,
                operator,
                null));
    }

    /**
     * aggregate退税amount
     *
     * @param order 订单
     * @return aggregate退税amount的结果
     */
    private BigDecimal aggregateRefundedAmount(PaymentOrder order) {
        BigDecimal refundedFromOrder = nullToZero(order.getRefundAmount());
        BigDecimal refundedFromRefundOrder = paymentRefundOrderService.sumRefundedAmount(order.getId());
        return refundedFromOrder.compareTo(refundedFromRefundOrder) >= 0 ? refundedFromOrder : refundedFromRefundOrder;
    }

    /**
     * 退税amount
     *
     * @param order 订单
     * @return 退税amount的结果
     */
    private BigDecimal refundableAmount(PaymentOrder order) {
        return refundableAmount(order, aggregateRefundedAmount(order));
    }

    /**
     * 退税amount
     *
     * @param order 订单
     * @param refundedAmount 退税amount
     * @return 退税amount的结果
     */
    private BigDecimal refundableAmount(PaymentOrder order, BigDecimal refundedAmount) {
        BigDecimal refundable = resolvedPaidAmount(order).subtract(nullToZero(refundedAmount));
        return refundable.compareTo(BigDecimal.ZERO) > 0 ? refundable : BigDecimal.ZERO;
    }

    /**
     * resolvedpaidamount
     *
     * @param order 订单
     * @return resolvedPaidAmount的结果
     */
    private BigDecimal resolvedPaidAmount(PaymentOrder order) {
        BigDecimal paidAmount = nullToZero(order.getPaidAmount());
        return paidAmount.compareTo(BigDecimal.ZERO) > 0 ? paidAmount : nullToZero(order.getOrderAmount());
    }

    /**
     * 空转为zero
     *
     * @param value 值
     * @return 空转为zero的结果
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 是否可以cancelby状态
     *
     * @param status 状态
     * @return 能否cancelby状态的结果
     */
    private boolean canCancelByState(String status) {
        return OrderState.PENDING.name().equals(status) || OrderState.PAYING.name().equals(status);
    }

    /**
     * 是否paid状态
     *
     * @param status 状态
     * @return 是否paid状态的结果
     */
    private boolean isPaidState(String status) {
        return OrderState.PAID.name().equals(status)
                || OrderState.COMPLETED.name().equals(status)
                || OrderState.REFUNDING.name().equals(status)
                || OrderState.REFUNDED.name().equals(status);
    }

    /**
     * backfill薪酬成功
     *
     * @param order 订单
     * @param paidAmount paidamount
     * @param thirdPartyOrderNo thirdparty订单no
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
         * 更新订单if改变。
         * @param order 订单
         * @param changed 改变
         */
        updateOrderIfChanged(order, changed);
    }

    /**
     * 同步退税aggregate
     *
     * @param order 订单
     * @param refundAmount 退税amount
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
         * 更新订单if改变。
         * @param order 订单
         * @param changed 改变
         */
        updateOrderIfChanged(order, changed);
    }

    /**
     * 解析订单状态之后退税成功
     *
     * @param order 订单
     * @param refundedAmount 退税amount
     * @return resolve订单状态之后退税成功的结果
     */
    private String resolveOrderStatusAfterRefundSuccess(PaymentOrder order, BigDecimal refundedAmount) {
        BigDecimal paidAmount = resolvedPaidAmount(order);
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0 && nullToZero(refundedAmount).compareTo(paidAmount) >= 0) {
            return OrderState.REFUNDED.name();
        }
        return OrderState.REFUNDING.name();
    }

    /**
     * 解析订单状态之后退税失败
     * @param order 订单
     * @param refundedAmount 退税amount
     * @param refundOrder 退税订单
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
     * 是否退税基础状态
     *
     * @param status 状态
     * @return 是否退税base状态的结果
     */
    private boolean isRefundBaseState(String status) {
        return OrderState.PAID.name().equals(status) || OrderState.COMPLETED.name().equals(status);
    }

    /**
     * 改变订单状态ifnecessary
     * @param order 订单
     * @param targetStatus Target状态
     * @param event 事件
     * @param operator 操作符
     * @param remark 评论
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
            /**
             * 薪酬异常。
             * @param event.name( 事件.名称(
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单状态更新失败: " + event.name());
        }
        orderStateLogService.log(new OrderStateLogRequest(
                order.getId(),
                fromState.name(),
                toState.name(),
                event.name(),
                /**
                 * 默认操作符。
                 * @param operator 操作符
                 */
                defaultOperator(operator),
                StringUtils.hasText(remark)
                        ? remark
                        : String.format("Status change: %s -> %s", fromState.getDescription(), toState.getDescription())));
    }

    /**
     * backfill完成时间
     *
     * @param order 订单
     */
    private void backfillCompleteTime(PaymentOrder order) {
        if (order == null || order.getCompleteTime() != null) {
            return;
        }
        order.setCompleteTime(LocalDateTime.now());
        /**
         * 更新订单if改变。
         * @param order 订单
         * @param true true
         */
        updateOrderIfChanged(order, true);
    }

    /**
     * backfill评论
     *
     * @param order 订单
     * @param remark 评论
     */
    private void backfillRemark(PaymentOrder order, String remark) {
        if (order == null || !StringUtils.hasText(remark) || StringUtils.hasText(order.getRemark())) {
            return;
        }
        order.setRemark(remark);
        /**
         * 更新订单if改变。
         * @param order 订单
         * @param true true
         */
        updateOrderIfChanged(order, true);
    }

    /**
     * 关闭通道订单
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
                log.warn("关闭第三方订单失败, orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * force订单状态
     *
     * @param orderId 订单标识
     * @param targetStatus Target状态
     */
    private void forceOrderStatus(Long orderId, String targetStatus) {
        orderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, orderId)
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .set(PaymentOrder::getStatus, targetStatus));
    }

    /**
     * 更新订单if改变
     *
     * @param order 订单
     * @param changed 改变
     */
    private void updateOrderIfChanged(PaymentOrder order, boolean changed) {
        if (changed) {
            orderMapper.updateById(order);
        }
    }

    /**
     * 默认操作符
     *
     * @param operator 操作符
     * @return 默认操作符的结果
     */
    private String defaultOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "system";
    }

    /**
     * generate退税no
     *
     * @return generate退税no的结果
     */
    private String generateRefundNo() {
        return "REF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 基础订单包装器
     *
     * @return base订单包装器的结果
     */
    private LambdaQueryWrapper<PaymentOrder> baseOrderWrapper() {
        return new LambdaQueryWrapper<PaymentOrder>()
                .and(wrapper -> wrapper.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0));
    }

    /**
     * 第一个nonblank
     *
     * @param values 值
     * @return 第一个nonblank的结果
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
     * recordtransaction
     * @param order 订单
     * @param transactionType transaction类型
     * @param amount amount
     * @param status 状态
     * @param thirdPartyTransactionNo thirdpartytransactionno
     * @param requestPayload 请求payload
     * @param responsePayload 响应payload
     * @param remark 评论
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
     * transaction状态的薪酬结果
     *
     * @param result 结果
     * @return transaction状态的薪酬结果的结果
     */
    private Integer transactionStatusOfPaymentResult(PaymentResult result) {
        if ("PAID".equals(result.getStatus())) {
            return 1;
        }
        if ("FAILED".equals(result.getStatus()) || "CANCELLED".equals(result.getStatus())) {
            return 0;
        }
        return 2;
    }

    /**
     * transaction状态的退税结果
     *
     * @param result 结果
     * @return transaction状态的退税结果的结果
     */
    private Integer transactionStatusOfRefundResult(RefundResult result) {
        if ("REFUNDED".equals(result.getStatus())) {
            return 1;
        }
        if ("FAILED".equals(result.getStatus())) {
            return 0;
        }
        return 2;
    }

    /**
     * safejson
     *
     * @param value 值
     * @return safeJson的结果
     */
    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 转换转为vo
     *
     * @param order 订单
     * @return 转换转为vo的结果
     */
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

    /**
     * 转换记录日志转为vo
     *
     * @param log 日志
     * @return 转换日志转为vo的结果
     */
    private OrderStateLogVO convertLogToVO(OrderStateLog log) {
        OrderStateLogVO vo = new OrderStateLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}

