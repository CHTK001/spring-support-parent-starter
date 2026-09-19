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

    /** 薪酬排序映射器 */
    private final PaymentOrderMapper paymentOrderMapper;
    /** Transactionrecordmapper */
    private final TransactionRecordMapper transactionRecordMapper;
    /** 排序状态日志服务 */
    private final OrderStateLogService orderStateLogService;
    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    public Page<OrderVO> listOrdersByUser(UserOrderQueryRequest request) {
        if (request == null) {
            /**
    * 薪酬异常。
    * @param "查询参数不能为空" "查询参数不能为空"
    * @return 薪酬异常的结果
    */
            throw new PaymentException("查询参数不能为空");
        }
        Page<PaymentOrder> source = paymentOrderMapper.selectPage(new Page<>(request.page(), request.size()), baseOrderWrapper(request.userId(), request.orderNo(), request.status()));
        Page<OrderVO> result = new Page<>(request.page(), request.size(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::toOrderVO).toList());
        return result;
    }

    @Override
    /** 获取订单by用户 */
    public OrderVO getOrderByUser(Long userId, Long id) {
        return toOrderVO(requireUserOrder(userId, id));
    }

    @Override
    /** 列表订单日志by用户 */
    public List<OrderStateLogVO> listOrderLogsByUser(Long userId, Long id) {
        /**
         * require用户订单。
         * @param userId 用户标识
         * @param id 标识
         */
        requireUserOrder(userId, id);
        return orderStateLogService.listByOrderId(id).stream().map(this::toLogVO).toList();
    }

    @Override
    public Page<TransactionRecord> listTransactionsByUser(UserTransactionQueryRequest request) {
        if (request == null) {
            /**
             * 薪酬异常。
             * @param "查询参数不能为空" "查询参数不能为空"
             * @return 薪酬异常的结果
             */
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

    /**
     * 基础订单包装器
     *
     * @param userId 用户标识
     * @param orderNo 订单no
     * @param status 状态
     * @return base订单包装器的结果
     */
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

    /**
     * require用户订单
     *
     * @param userId 用户标识
     * @param id 标识
     * @return require用户订单的结果
     */
    private PaymentOrder requireUserOrder(Long userId, Long id) {
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getId, id)
                .eq(PaymentOrder::getUserId, userId)
                .and(item -> item.isNull(PaymentOrder::getDeleted).or().eq(PaymentOrder::getDeleted, 0))
                .last("limit 1"));
        if (order == null) {
            /**
             * 薪酬异常。
             * @param "订单不存在或无权访问" "订单不存在或无权访问"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("订单不存在或无权访问");
        }
        return order;
    }

    /**
     * 转为订单vo
     *
     * @param order 订单
     * @return 转为订单vo的结果
     */
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

    /**
     * 转为记录日志VO
     *
     * @param log 日志
     * @return 转为日志vo的结果
     */
    private OrderStateLogVO toLogVO(OrderStateLog log) {
        OrderStateLogVO vo = new OrderStateLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}

