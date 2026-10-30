package com.chua.payment.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.configuration.PaymentCallbackProperties;
import com.chua.payment.support.dto.PaymentGlobalConfigDTO;
import com.chua.payment.support.entity.PaymentGlobalConfig;
import com.chua.payment.support.mapper.PaymentGlobalConfigMapper;
import com.chua.payment.support.service.PaymentGlobalConfigService;
import com.chua.payment.support.vo.PaymentGlobalConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付全局配置服务实现，管理系统级支付参数（回调地址、超时等）。
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@RequiredArgsConstructor
public class PaymentGlobalConfigServiceImpl implements PaymentGlobalConfigService {

    /**
     * 默认配置键
     */
    private static final String DEFAULT_KEY = "DEFAULT";

    /**
     * 支付全局配置映射器
     */
    private final PaymentGlobalConfigMapper paymentGlobalConfigMapper;
    /**
     * 支付回调地址配置属性
     */
    private final PaymentCallbackProperties paymentCallbackProperties;

    @Override
    /**
     * 获取支付全局配置
     * @return 返回值
    */
    public PaymentGlobalConfigVO getConfig() {
        return toVO(getConfigEntity());
    }

    @Override
    /**
     * 获取支付全局配置实体
     * @return 返回值
    */
    public PaymentGlobalConfig getConfigEntity() {
        PaymentGlobalConfig entity = paymentGlobalConfigMapper.selectOne(new LambdaQueryWrapper<PaymentGlobalConfig>()
                .eq(PaymentGlobalConfig::getConfigKey, DEFAULT_KEY)
                .last("limit 1"));
        if (entity != null) {
            return entity;
        }
        PaymentGlobalConfig fallback = new PaymentGlobalConfig();
        fallback.setConfigKey(DEFAULT_KEY);
        fallback.setPaymentNotifyBaseUrl(paymentCallbackProperties.getBaseUrl());
        fallback.setPaymentCallbackPathTemplate("/{orderNo}/{merchantId}");
        fallback.setPaymentAutoRefreshSeconds(60);
        fallback.setCouponDeductWithdrawable(true);
        return fallback;
    }

    @Override
    /**
     * 保存或更新支付全局配置
     * @param dto 参数
     * @return 返回值
    */
    public PaymentGlobalConfigVO saveOrUpdate(PaymentGlobalConfigDTO dto) {
        PaymentGlobalConfig entity = paymentGlobalConfigMapper.selectOne(new LambdaQueryWrapper<PaymentGlobalConfig>()
                .eq(PaymentGlobalConfig::getConfigKey, DEFAULT_KEY)
                .last("limit 1"));
        if (entity == null) {
            entity = new PaymentGlobalConfig();
            entity.setConfigKey(DEFAULT_KEY);
        }
        entity.setPaymentNotifyBaseUrl(dto.paymentNotifyBaseUrl());
        entity.setPaymentReturnUrl(dto.paymentReturnUrl());
        entity.setPaymentCallbackPathTemplate(StringUtils.hasText(dto.paymentCallbackPathTemplate())
                ? dto.paymentCallbackPathTemplate()
                : "/{orderNo}/{merchantId}");
        entity.setPaymentAutoRefreshSeconds(dto.paymentAutoRefreshSeconds() != null ? dto.paymentAutoRefreshSeconds() : 60);
        entity.setCouponDeductWithdrawable(dto.couponDeductWithdrawable());
        entity.setRemark(dto.remark());
        if (entity.getId() == null) {
            paymentGlobalConfigMapper.insert(entity);
        } else {
            paymentGlobalConfigMapper.updateById(entity);
        }
        return toVO(entity);
    }

    /**
     * 转换为视图对象
     *
     * @param entity 实体
     * @return 转换后的配置视图对象
     */
    private PaymentGlobalConfigVO toVO(PaymentGlobalConfig entity) {
        String baseUrl = normalize(entity.getPaymentNotifyBaseUrl());
        return new PaymentGlobalConfigVO(
                entity.getConfigKey(),
                entity.getPaymentNotifyBaseUrl(),
                entity.getPaymentReturnUrl(),
                entity.getPaymentCallbackPathTemplate(),
                entity.getPaymentAutoRefreshSeconds(),
                entity.getRemark(),
                baseUrl == null ? null : baseUrl + "/api/notify/wechat/pay/DEMO_ORDER/10001",
                baseUrl == null ? null : baseUrl + "/api/notify/wechat/refund/DEMO_REFUND/10001",
                entity.getCouponDeductWithdrawable(),
                entity.getUpdatedAt());
    }

    /**
     * 规范化地址：去掉末尾斜杠
     * @param value 值
     * @return 返回值
    */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
