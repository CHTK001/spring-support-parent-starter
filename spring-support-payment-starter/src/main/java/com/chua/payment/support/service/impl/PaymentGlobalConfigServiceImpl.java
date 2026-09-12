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

    /** 默认_钥匙 */
    private static final String DEFAULT_KEY = "DEFAULT";

    /** 薪酬全局配置映射器 */
    private final PaymentGlobalConfigMapper paymentGlobalConfigMapper;
    /** Paymentcallback属性 */
    private final PaymentCallbackProperties paymentCallbackProperties;

    @Override
    /** 获取配置 */
    public PaymentGlobalConfigVO getConfig() {
        return toVO(getConfigEntity());
    }

    @Override
    /** 获取配置实体 */
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
    /** 保存或更新 */
    public PaymentGlobalConfigVO saveOrUpdate(PaymentGlobalConfigDTO dto) {
        PaymentGlobalConfig entity = paymentGlobalConfigMapper.selectOne(new LambdaQueryWrapper<PaymentGlobalConfig>()
                .eq(PaymentGlobalConfig::getConfigKey, DEFAULT_KEY)
                .last("limit 1"));
        if (entity == null) {
            entity = new PaymentGlobalConfig();
            entity.setConfigKey(DEFAULT_KEY);
        }
        entity.setPaymentNotifyBaseUrl(dto.getPaymentNotifyBaseUrl());
        entity.setPaymentReturnUrl(dto.getPaymentReturnUrl());
        entity.setPaymentCallbackPathTemplate(StringUtils.hasText(dto.getPaymentCallbackPathTemplate())
                ? dto.getPaymentCallbackPathTemplate()
                : "/{orderNo}/{merchantId}");
        entity.setPaymentAutoRefreshSeconds(dto.getPaymentAutoRefreshSeconds() != null ? dto.getPaymentAutoRefreshSeconds() : 60);
        entity.setCouponDeductWithdrawable(dto.getCouponDeductWithdrawable());
        entity.setRemark(dto.getRemark());
        if (entity.getId() == null) {
            paymentGlobalConfigMapper.insert(entity);
        } else {
            paymentGlobalConfigMapper.updateById(entity);
        }
        return toVO(entity);
    }

    /**
     * 转为vo
     *
     * @param entity 实体
     * @return 转为vo的结果
     */
    private PaymentGlobalConfigVO toVO(PaymentGlobalConfig entity) {
        PaymentGlobalConfigVO vo = new PaymentGlobalConfigVO();
        BeanUtils.copyProperties(entity, vo);
        String baseUrl = normalize(entity.getPaymentNotifyBaseUrl());
        vo.setPaymentSamplePayNotifyUrl(baseUrl == null ? null : baseUrl + "/api/notify/wechat/pay/DEMO_ORDER/10001");
        vo.setPaymentSampleRefundNotifyUrl(baseUrl == null ? null : baseUrl + "/api/notify/wechat/refund/DEMO_REFUND/10001");
        return vo;
    }

    /**
     * Normalize
     *
     * @param value 价值观
     * @return normalize的结果
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

