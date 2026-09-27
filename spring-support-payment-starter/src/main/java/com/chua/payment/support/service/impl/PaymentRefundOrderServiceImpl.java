package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CreateRefundOrderDTO;
import com.chua.payment.support.dto.MarkRefundedDTO;
import com.chua.payment.support.dto.RefundOrderQueryRequest;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.enums.OrderState;
import com.chua.payment.support.enums.RefundOrderStatus;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantChannelMapper;
import com.chua.payment.support.mapper.MerchantMapper;
import com.chua.payment.support.mapper.PaymentRefundOrderMapper;
import com.chua.payment.support.service.PaymentRefundOrderService;
import com.chua.payment.support.vo.RefundOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款单服务实现，负责退款单的创建、状态更新、查询等操作。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundOrderServiceImpl implements PaymentRefundOrderService {

    /**
     * 支付退款单映射器
     */
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    /**
     * 商户映射器
     */
    private final MerchantMapper merchantMapper;
    /**
     * 商户渠道映射器
     */
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 创建退款订单
     * @param order 原订单
     * @param refundNo 退款单号
     * @param refundAmount 退款金额
     * @param reason 退款原因
     * @param operator 操作人
     * @param requestPayload 请求报文
     * @param dto dto
     * @return 创建出的退款订单
     */
    public PaymentRefundOrder createRefundOrder(CreateRefundOrderDTO dto) {
        if (dto.order() == null || dto.order().getId() == null) {
            throw new PaymentException("退款订单不存在");
        }
        if (!StringUtils.hasText(dto.refundNo())) {
            throw new PaymentException("退款单号不能为空");
        }
        PaymentRefundOrder entity = new PaymentRefundOrder();
        entity.setRefundNo(dto.refundNo());
        entity.setOrderId(dto.order().getId());
        entity.setOrderNo(dto.order().getOrderNo());
        entity.setMerchantId(dto.order().getMerchantId());
        entity.setChannelId(dto.order().getChannelId());
        entity.setSourceOrderStatus(dto.order().getStatus());
        entity.setRefundAmount(dto.refundAmount());
        entity.setStatus(RefundOrderStatus.PROCESSING.name());
        entity.setReason(dto.reason());
        entity.setNotifyStatus(0);
        entity.setRequestPayload(dto.requestPayload());
        entity.setOperator(dto.operator());
        entity.setRemark(dto.reason());
        paymentRefundOrderMapper.insert(entity);
        return entity;
    }

    @Override
    /**
     * 根据主键获取退款单
     */
    public PaymentRefundOrder getById(Long id) {
        PaymentRefundOrder entity = paymentRefundOrderMapper.selectById(id);
        if (entity == null) {
            throw new PaymentException("退款单不存在");
        }
        return entity;
    }

    @Override
    /**
     * 根据退款单号获取退款单
     */
    public PaymentRefundOrder getByRefundNo(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            return null;
        }
        return paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, refundNo)
                .last("limit 1"));
    }

    @Override
    /**
     * 根据第三方退款单号获取退款单
     */
    public PaymentRefundOrder getByThirdPartyRefundNo(String thirdPartyRefundNo) {
        if (!StringUtils.hasText(thirdPartyRefundNo)) {
            return null;
        }
        return paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getThirdPartyRefundNo, thirdPartyRefundNo)
                .last("limit 1"));
    }

    @Override
    /**
     * 获取退款单详情
     */
    public RefundOrderVO getDetail(Long id) {
        return convertToVO(getById(id));
    }

    @Override
    public Page<RefundOrderVO> page(RefundOrderQueryRequest request) {
        if (request == null) {
            throw new PaymentException("查询参数不能为空");
        }
        Page<PaymentRefundOrder> page = new Page<>(request.pageNum(), request.pageSize());
        LambdaQueryWrapper<PaymentRefundOrder> wrapper = new LambdaQueryWrapper<>();
        if (request.merchantId() != null) {
            wrapper.eq(PaymentRefundOrder::getMerchantId, request.merchantId());
        }
        if (StringUtils.hasText(request.orderNo())) {
            wrapper.eq(PaymentRefundOrder::getOrderNo, request.orderNo());
        }
        if (StringUtils.hasText(request.refundNo())) {
            wrapper.eq(PaymentRefundOrder::getRefundNo, request.refundNo());
        }
        if (StringUtils.hasText(request.status())) {
            wrapper.eq(PaymentRefundOrder::getStatus, request.status());
        }
        wrapper.orderByDesc(PaymentRefundOrder::getCreatedAt);
        Page<PaymentRefundOrder> entityPage = paymentRefundOrderMapper.selectPage(page, wrapper);
        Page<RefundOrderVO> voPage = new Page<>(request.pageNum(), request.pageSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    /**
     * 获取订单最新一笔退款单
     */
    public PaymentRefundOrder getLatestByOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getOrderId, orderId)
                .orderByDesc(PaymentRefundOrder::getId)
                .last("limit 1"));
    }

    @Override
    /**
     * 获取订单处理中的退款单
     */
    public PaymentRefundOrder getProcessingByOrderId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getOrderId, orderId)
                .eq(PaymentRefundOrder::getStatus, RefundOrderStatus.PROCESSING.name())
                .orderByDesc(PaymentRefundOrder::getId)
                .last("limit 1"));
    }

    @Override
    /**
     * 按订单标识查询退款单列表
     */
    public List<PaymentRefundOrder> listByOrderId(Long orderId) {
        return paymentRefundOrderMapper.selectList(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getOrderId, orderId)
                .orderByAsc(PaymentRefundOrder::getCreatedAt));
    }

    @Override
    /**
     * 按订单标识查询退款单视图列表
     */
    public List<RefundOrderVO> listVoByOrderId(Long orderId) {
        return listByOrderId(orderId).stream().map(this::convertToVO).toList();
    }

    @Override
    /**
     * 是否存在处理中的退款单
     */
    public boolean hasProcessingRefund(Long orderId) {
        return getProcessingByOrderId(orderId) != null;
    }

    @Override
    /**
     * 汇总订单已退款金额
     */
    public BigDecimal sumRefundedAmount(Long orderId) {
        return listByOrderId(orderId).stream()
                .filter(item -> RefundOrderStatus.REFUNDED.name().equals(item.getStatus()))
                .map(PaymentRefundOrder::getRefundAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款单为处理中
     * @param refundNo 退款单号
     * @param thirdPartyRefundNo 第三方退款单号
     * @param responsePayload 响应报文
     * @param remark 备注
     */
    public PaymentRefundOrder markProcessing(String refundNo,
                                             String thirdPartyRefundNo,
                                             String responsePayload,
                                             String remark) {
        PaymentRefundOrder entity = requireRefundOrder(refundNo);
        entity.setStatus(RefundOrderStatus.PROCESSING.name());
        if (StringUtils.hasText(thirdPartyRefundNo)) {
            entity.setThirdPartyRefundNo(thirdPartyRefundNo);
        }
        if (responsePayload != null) {
            entity.setResponsePayload(responsePayload);
        }
        if (StringUtils.hasText(remark)) {
            entity.setRemark(remark);
        }
        paymentRefundOrderMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款单为已退款
     * @param refundNo 退款单号
     * @param refundAmount 退款金额
     * @param thirdPartyRefundNo 第三方退款单号
     * @param responsePayload 响应报文
     * @param operator 操作人
     * @param remark 备注
     * @param dto dto
     * @return 标记后的退款单
     */
    public PaymentRefundOrder markRefunded(MarkRefundedDTO dto) {
        PaymentRefundOrder entity = requireRefundOrder(dto.refundNo());
        entity.setStatus(RefundOrderStatus.REFUNDED.name());
        entity.setNotifyStatus(1);
        if (dto.refundAmount() != null && dto.refundAmount().compareTo(BigDecimal.ZERO) > 0) {
            entity.setRefundAmount(dto.refundAmount());
        }
        if (StringUtils.hasText(dto.thirdPartyRefundNo())) {
            entity.setThirdPartyRefundNo(dto.thirdPartyRefundNo());
        }
        if (dto.responsePayload() != null) {
            entity.setResponsePayload(dto.responsePayload());
        }
        if (StringUtils.hasText(dto.operator())) {
            entity.setOperator(dto.operator());
        }
        if (StringUtils.hasText(dto.remark())) {
            entity.setRemark(dto.remark());
        }
        paymentRefundOrderMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 标记退款单为失败
     * @param refundNo 退款单号
     * @param responsePayload 响应报文
     * @param operator 操作人
     * @param remark 备注
     */
    public PaymentRefundOrder markFailed(String refundNo,
                                         String responsePayload,
                                         String operator,
                                         String remark) {
        PaymentRefundOrder entity = requireRefundOrder(refundNo);
        entity.setStatus(RefundOrderStatus.FAILED.name());
        if (responsePayload != null) {
            entity.setResponsePayload(responsePayload);
        }
        if (StringUtils.hasText(operator)) {
            entity.setOperator(operator);
        }
        if (StringUtils.hasText(remark)) {
            entity.setRemark(remark);
        }
        paymentRefundOrderMapper.updateById(entity);
        return entity;
    }

    /**
     * 获取退款单，不存在时抛出异常
     *
     * @param refundNo 退款单号
     * @return 获取到的退款单
     */
    private PaymentRefundOrder requireRefundOrder(String refundNo) {
        PaymentRefundOrder entity = getByRefundNo(refundNo);
        if (entity == null) {
            throw new PaymentException("退款单不存在: " + refundNo);
        }
        return entity;
    }

    /**
     * 转换为视图对象
     *
     * @param entity 退款单实体
     * @return 转换后的退款单视图对象
     */
    private RefundOrderVO convertToVO(PaymentRefundOrder entity) {
        RefundOrderVO vo = new RefundOrderVO();
        BeanUtils.copyProperties(entity, vo);
        Merchant merchant = entity.getMerchantId() == null ? null : merchantMapper.selectById(entity.getMerchantId());
        MerchantChannel channel = entity.getChannelId() == null ? null : merchantChannelMapper.selectById(entity.getChannelId());
        vo.setMerchantName(merchant != null ? merchant.getMerchantName() : null);
        vo.setChannelName(channel != null ? channel.getChannelName() : null);
        vo.setStatusDesc(RefundOrderStatus.descriptionOf(entity.getStatus()));
        vo.setSourceOrderStatusDesc(OrderState.descriptionOf(entity.getSourceOrderStatus()));
        return vo;
    }
}
