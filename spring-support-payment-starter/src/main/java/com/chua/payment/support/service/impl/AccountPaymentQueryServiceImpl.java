package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

@Service
@RequiredArgsConstructor
public class AccountPaymentQueryServiceImpl implements AccountPaymentQueryService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final TransactionRecordMapper transactionRecordMapper;
    private final OrderStateLogService orderStateLogService;
    private final MerchantMapper merchantMapper;
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    public Page<OrderVO> listOrdersByUser(Long userId, int page, int size, String orderNo, String status) {
        Page<PaymentOrder> source = paymentOrderMapper.selectPage(new Page<>(page, size), baseOrderWrapper(userId, orderNo, status));
        Page<OrderVO> result = new Page<>(page, size, source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toOrderVO).toList());
        return result;
    }

    @Override
    public OrderVO getOrderByUser(Long userId, Long id) {
        return toOrderVO(requireUserOrder(userId, id));
    }

    @Override
    public List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id) {
        requireUserOrder(userId, id);
        return orderStateLogService.listByOrderId(id).stream().map(this::toLogVO).toList();
    }

    @Override
    public Page<TransactionRecord> listTransactionsByUser(Long userId, int pageNum, int pageSize, String orderNo, String transactionType, Integer status) {
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.inSql(TransactionRecord::getOrderId, "select id from payment_order where user_id = " + userId + " and (deleted is null or deleted = 0)");
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(TransactionRecord::getOrderNo, orderNo.trim());
        }
        if (StringUtils.hasText(transactionType)) {
            wrapper.eq(TransactionRecord::getTransactionType, transactionType.trim());
        }
        if (status != null) {
            wrapper.eq(TransactionRecord::getStatus, status);
        }
        wrapper.orderByDesc(TransactionRecord::getCreateTime);
        return transactionRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

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

    private OrderStateLogVO toLogVO(OrderStateLog log) {
        OrderStateLogVO vo = new OrderStateLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
