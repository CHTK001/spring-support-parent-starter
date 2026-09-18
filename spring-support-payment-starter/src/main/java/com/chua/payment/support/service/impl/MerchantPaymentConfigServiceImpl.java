package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.MerchantPaymentConfig;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.mapper.MerchantPaymentConfigMapper;
import com.chua.payment.support.mapper.PaymentOrderMapper;
import com.chua.payment.support.service.MerchantPaymentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
* 商户支付配置服务实现，管理商户维度的支付功能开关与行为参数。
*
* @author CH
* @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class MerchantPaymentConfigServiceImpl implements MerchantPaymentConfigService {

    /** 配置映射器 */
    private final MerchantPaymentConfigMapper configMapper;
    /** 排序映射器 */
    private final PaymentOrderMapper orderMapper;

    @Override
    /** 获取配置 */
    public MerchantPaymentConfig getConfig(Long merchantId) {
        if (merchantId == null) {
            return getDefaultConfig();
        }
        MerchantPaymentConfig config = configMapper.selectOne(new LambdaQueryWrapper<MerchantPaymentConfig>()
                .eq(MerchantPaymentConfig::getMerchantId, merchantId)
                .last("limit 1"));
        return config != null ? config : getDefaultConfig();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存或更新
    *
    * @param config 配置
    */
    public void saveOrUpdate(MerchantPaymentConfig config) {
        if (config == null || config.getMerchantId() == null) {
            /**
            * 薪酬异常。
            * @param "配置不能为空" "配置不能为空"
            * @return 薪酬异常的结果
            */
            throw new PaymentException("配置不能为空");
        }
        MerchantPaymentConfig existing = configMapper.selectOne(new LambdaQueryWrapper<MerchantPaymentConfig>()
                .eq(MerchantPaymentConfig::getMerchantId, config.getMerchantId())
                .last("limit 1"));
        if (existing != null) {
            existing.setOrderReusable(config.getOrderReusable());
            existing.setOrderTimeoutMinutes(config.getOrderTimeoutMinutes());
            existing.setPendingOrderLimit(config.getPendingOrderLimit());
            existing.setAutoCancelTimeoutOrder(config.getAutoCancelTimeoutOrder());
            configMapper.updateById(existing);
        } else {
            configMapper.insert(config);
        }
    }

    @Override
    /** 校验是否可以创建订单 */
    public void checkCanCreateOrder(Long merchantId, Long userId) {
        MerchantPaymentConfig config = getConfig(merchantId);

        if (config.getPendingOrderLimit() != null && config.getPendingOrderLimit() > 0) {
            long pendingCount = orderMapper.selectCount(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getMerchantId, merchantId)
                    .eq(PaymentOrder::getUserId, userId)
                    .in(PaymentOrder::getStatus, "PENDING", "PAYING"));

            if (pendingCount >= config.getPendingOrderLimit()) {
                /**
    * 薪酬异常。
    * @param "待支付订单数量已达上限，请先完成或取消现有订单" "待支付订单数量已达上限，请先完成或取消现有订单"
    * @return 薪酬异常的结果
    */
                throw new PaymentException("待支付订单数量已达上限，请先完成或取消现有订单");
            }
        }
    }

    @Override
    /** 校验是否可以薪酬订单 */
    public void checkCanPayOrder(Long orderId) {
        PaymentOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            /**
    * 薪酬异常。
    * @param "订单不存在" "订单不存在"
    * @return 薪酬异常的结果
    */
            throw new PaymentException("订单不存在");
        }

        MerchantPaymentConfig config = getConfig(order.getMerchantId());

        if (!Boolean.TRUE.equals(config.getOrderReusable())) {
            if ("PAYING".equals(order.getStatus()) || "PAID".equals(order.getStatus())) {
                /**
                * 薪酬异常。
                * @param "该订单为一次性订单，不可重复支付" "该订单为一次性订单，不可重复支付"
                * @return 薪酬异常的结果
                */
                throw new PaymentException("该订单为一次性订单，不可重复支付");
            }
        }

        if (config.getOrderTimeoutMinutes() != null && config.getOrderTimeoutMinutes() > 0) {
            LocalDateTime baseTime = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();
            LocalDateTime expireTime = baseTime.plusMinutes(config.getOrderTimeoutMinutes());
            if (LocalDateTime.now().isAfter(expireTime)) {
                /**
                * 薪酬异常。
                * @param "订单已超时，无法继续支付" "订单已超时，无法继续支付"
                * @return 薪酬异常的结果
                */
                throw new PaymentException("订单已超时，无法继续支付");
            }
        }
    }

    @Override
    /** 获取订单超时minutes */
    public Integer getOrderTimeoutMinutes(Long merchantId) {
        MerchantPaymentConfig config = getConfig(merchantId);
        return config.getOrderTimeoutMinutes();
    }

    /**
    * 获取默认配置
    *
    * @return 获取默认配置的结果
    */
    private MerchantPaymentConfig getDefaultConfig() {
        MerchantPaymentConfig config = new MerchantPaymentConfig();
        config.setOrderReusable(true);
        config.setOrderTimeoutMinutes(null);
        config.setPendingOrderLimit(null);
        config.setAutoCancelTimeoutOrder(true);
        return config;
    }
}

