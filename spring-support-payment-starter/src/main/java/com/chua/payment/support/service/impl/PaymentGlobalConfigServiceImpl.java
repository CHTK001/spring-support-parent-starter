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
 * 支付全局配置服务实现
 */
@Service
@RequiredArgsConstructor
public class PaymentGlobalConfigServiceImpl implements PaymentGlobalConfigService {

    private static final String DEFAULT_KEY = "DEFAULT";

    private final PaymentGlobalConfigMapper paymentGlobalConfigMapper;
    private final PaymentCallbackProperties paymentCallbackProperties;

    @Override
    public PaymentGlobalConfigVO getConfig() {
        return toVO(getConfigEntity());
    }

    @Override
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
        return fallback;
    }

    @Override
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
        entity.setRemark(dto.getRemark());
        if (entity.getId() == null) {
            paymentGlobalConfigMapper.insert(entity);
        } else {
            paymentGlobalConfigMapper.updateById(entity);
        }
        return toVO(entity);
    }

    private PaymentGlobalConfigVO toVO(PaymentGlobalConfig entity) {
        PaymentGlobalConfigVO vo = new PaymentGlobalConfigVO();
        BeanUtils.copyProperties(entity, vo);
        String baseUrl = normalize(entity.getPaymentNotifyBaseUrl());
        vo.setPaymentSamplePayNotifyUrl(baseUrl == null ? null : baseUrl + "/api/notify/wechat/pay/DEMO_ORDER/10001");
        vo.setPaymentSampleRefundNotifyUrl(baseUrl == null ? null : baseUrl + "/api/notify/wechat/refund/DEMO_REFUND/10001");
        return vo;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
