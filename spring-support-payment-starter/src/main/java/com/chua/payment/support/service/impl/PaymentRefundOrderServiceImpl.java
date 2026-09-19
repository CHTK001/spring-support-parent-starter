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

    /** Paymentrefund排序映射器 */
    private final PaymentRefundOrderMapper paymentRefundOrderMapper;
    /** Merchantmapper */
    private final MerchantMapper merchantMapper;
    /** Merchant通道映射器 */
    private final MerchantChannelMapper merchantChannelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 创建退税订单
    * @param order 订单
    * @param refundNo 退税no
    * @param refundAmount 退税amount
    * @param reason ReasonMLmlml
    * @param operator 操作符
    * @param requestPayload 请求payload
    * @param dto dto
    * @return 创建退税订单的结果
    */
    public PaymentRefundOrder createRefundOrder(CreateRefundOrderDTO dto) {
        if (dto.order() == null || dto.order().getId() == null) {
            /**
             * 薪酬异常。
             * @param "退款订单不存在" "退款订单不存在"
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款订单不存在");
        }
        if (!StringUtils.hasText(dto.refundNo())) {
            /**
             * 薪酬异常。
             * @param "退款单号不能为空" "退款单号不能为空"
             * @return 薪酬异常的结果
             */
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
    /** 获取byid */
    public PaymentRefundOrder getById(Long id) {
        PaymentRefundOrder entity = paymentRefundOrderMapper.selectById(id);
        if (entity == null) {
            /**
    * 薪酬异常。
    * @param "退款单不存在" "退款单不存在"
    * @return 薪酬异常的结果
    */
            throw new PaymentException("退款单不存在");
        }
        return entity;
    }

    @Override
    /** 获取by退税no */
    public PaymentRefundOrder getByRefundNo(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            return null;
        }
        return paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getRefundNo, refundNo)
                .last("limit 1"));
    }

    @Override
    /** 获取bythirdparty退税no */
    public PaymentRefundOrder getByThirdPartyRefundNo(String thirdPartyRefundNo) {
        if (!StringUtils.hasText(thirdPartyRefundNo)) {
            return null;
        }
        return paymentRefundOrderMapper.selectOne(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getThirdPartyRefundNo, thirdPartyRefundNo)
                .last("limit 1"));
    }

    @Override
    /** 获取Detail */
    public RefundOrderVO getDetail(Long id) {
        return convertToVO(getById(id));
    }

    @Override
    public Page<RefundOrderVO> page(RefundOrderQueryRequest request) {
        if (request == null) {
            /**
    * 薪酬异常。
    * @param "查询参数不能为空" "查询参数不能为空"
    * @return 薪酬异常的结果
    */
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
    /** 获取latestby订单标识 */
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
    /** 获取处理by订单标识 */
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
    /** 列表by订单标识 */
    public List<PaymentRefundOrder> listByOrderId(Long orderId) {
        return paymentRefundOrderMapper.selectList(new LambdaQueryWrapper<PaymentRefundOrder>()
                .eq(PaymentRefundOrder::getOrderId, orderId)
                .orderByAsc(PaymentRefundOrder::getCreatedAt));
    }

    @Override
    /** 列表voby订单标识 */
    public List<RefundOrderVO> listVoByOrderId(Long orderId) {
        return listByOrderId(orderId).stream().map(this::convertToVO).toList();
    }

    @Override
    /** 是否拥有处理退税 */
    public boolean hasProcessingRefund(Long orderId) {
        return getProcessingByOrderId(orderId) != null;
    }

    @Override
    /** 求和退税amount */
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
     * mark处理
     * @param refundNo 退税no
     * @param thirdPartyRefundNo thirdparty退税no
     * @param responsePayload 响应payload
     * @param remark 评论
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
     * mark退税
     * @param refundNo 退税no
     * @param refundAmount 退税amount
     * @param thirdPartyRefundNo thirdparty退税no
     * @param responsePayload 响应payload
     * @param operator 操作符
     * @param remark 评论
     * @param dto dto
     * @return mark退税的结果
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
     * mark失败
     * @param refundNo 退税no
     * @param responsePayload 响应payload
     * @param operator 操作符
     * @param remark 评论
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
     * require退税订单
     *
     * @param refundNo 退税no
     * @return require退税订单的结果
     */
    private PaymentRefundOrder requireRefundOrder(String refundNo) {
        PaymentRefundOrder entity = getByRefundNo(refundNo);
        if (entity == null) {
            /**
             * 薪酬异常。
             * @param refundNo 退税no
             * @return 薪酬异常的结果
             */
            throw new PaymentException("退款单不存在: " + refundNo);
        }
        return entity;
    }

    /**
     * 转换转为vo
     *
     * @param entity 实体
     * @return 转换转为vo的结果
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

