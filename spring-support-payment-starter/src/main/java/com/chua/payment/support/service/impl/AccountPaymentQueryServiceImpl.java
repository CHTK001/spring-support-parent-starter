package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.UserOrderQueryRequest;
import com.chua.payment.support.dto.UserTransactionQueryRequest;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.OrderStateLog;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.mapper.TransactionRecordMapper;
import com.chua.payment.support.service.AccountPaymentQueryService;
import com.chua.payment.support.service.OrderStateLogService;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
/**
 * 账户支付查询服务实现，提供用户维度的订单、交易流水、状态日志查询。
 *
 * @author CH
 * @since 4.0.0.50
 */

@Service
@RequiredArgsConstructor
public class AccountPaymentQueryServiceImpl implements AccountPaymentQueryService {

    /** Payment排序mapper */
    private final PaymentOrderMapper paymentOrderMapper;
    /** Transactionrecordmapper */
    private final TransactionRecordMapper transactionRecordMapper;
    /** 排序状态日志服务 */
    private final OrderStateLogService orderStateLogService;
    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Merchant通道mapper */
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    public Page<OrderVO> listOrdersByUser(UserOrderQueryRequest request) {
        if (request == null) {
            throw new PaymentException("查询参数不能为空");
        }
        Page<PaymentOrder> source = paymentOrderMapper.selectPage(new Page<>(request.page(), request.size()), baseOrderWrapper(request.userId(), request.orderNo(), request.status()));
        Page<OrderVO> result = new Page<>(request.page(), request.size(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toOrderVO).toList());
        return result;
    }

    @Override
    /** 获取OrderByUser */
    public OrderVO getOrderByUser(Long userId, Long id) {
        return toOrderVO(requireUserOrder(userId, id));
    }

    @Override
    /** ListOrderLogsByUser */
    public List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id) {
        requireUserOrder(userId, id);
        return orderStateLogService.listByOrderId(id).stream().map(this::toLogVO).toList();
    }

    @Override
    public Page<TransactionRecord> listTransactionsByUser(UserTransactionQueryRequest request) {
        if (request == null) {
            throw new PaymentException("查询参数不能为空");
        }
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("order_id IN (SELECT pay_order_id FROM pay_order WHERE pay_order_user_id = {0} AND (pay_order_deleted IS NULL OR pay_order_deleted = 0))", request.userId());
        if (StringUtils.hasText(request.orderNo())) {
            wrapper.eq(TransactionRecord::getOrderNo, request.orderNo().trim());
        }
        if (StringUtils.hasText(request.transactionType())) {
            wrapper.eq(TransactionRecord::getTransactionType, request.transactionType().trim());
        }
        if (request.status() != null) {
            wrapper.eq(TransactionRecord::getStatus, request.status());
        }
        wrapper.orderByDesc(TransactionRecord::getCreatedAt);
        return transactionRecordMapper.selectPage(new Page<>(request.pageNum(), request.pageSize()), wrapper);
    }

    /** BaseOrderWrapper */
    private LambdaQueryWrapper<PaymentOrder> baseOrderWrapper(Long userId, String orderNo, String status) {
        LambdaQueryWrapper<PaymentOrder> wrapper = new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getUserId, userId)
                .and(item -> item.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .orderByDesc(PaymentOrder::getCreatedAt);
        if (StringUtils.hasText(orderNo)) {
            wrapper.and(item -> item.eq(PaymentOrder::getOrderNo, orderNo.trim()).or().eq(PaymentOrder::getBusinessOrderNo, orderNo.trim()));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PaymentOrder::getStatus, status.trim());
        }
        return wrapper;
    }

    /** RequireUserOrder */
    private PaymentOrder requireUserOrder(Long userId, Long id) {
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, id)
                .eq(PaymentOrder::getUserId, userId)
                .and(item -> item.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .last("limit 1"));
        if (order == null) {
            throw new PaymentException("订单不存在或无权访问");
        }
        return order;
    }

    /** ToOrderVO */
    private OrderVO toOrderVO(PaymentOrder order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        MerchantChannel channel = order.getChannelId() != null ? merchantChannelMapper.selectById(order.getChannelId()) : null;
        vo.setMerchantName(merchant != null ? merchant.getMerchantName() : null);
        vo.setChannelName(channel != null ? channel.getChannelName() : null);
        vo.setStatusDesc(OrderState.descriptionOf(order.getStatus()));
        return vo;
    }

    /** To记录日志VO */
    private OrderStateLogVO toLogVO(OrderStateLog log) {
        OrderStateLogVO vo = new OrderStateLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}

