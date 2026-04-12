package com.chua.payment.support.channel.support;

import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.MerchantChannelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Map;

/**
 * 商户支付渠道公共能力
 */
@RequiredArgsConstructor
public abstract class AbstractMerchantPaymentChannel {

    public static final String EXT_PROVIDER_SPI = "providerSpi";
    public static final String DEFAULT_PROVIDER_SPI = "default";

    protected final MerchantChannelService merchantChannelService;
    protected final ObjectMapper objectMapper;

    protected Map<String, Object> extConfig(MerchantChannel channel) {
        if (!StringUtils.hasText(channel.getExtConfig())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(channel.getExtConfig(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new PaymentException("支付方式扩展配置格式错误", e);
        }
    }

    protected String extText(MerchantChannel channel, String key) {
        Object value = extConfig(channel).get(key);
        return value == null ? null : String.valueOf(value);
    }

    protected String providerSpi(MerchantChannel channel) {
        return providerSpi(channel, DEFAULT_PROVIDER_SPI);
    }

    protected String providerSpi(MerchantChannel channel, String fallback) {
        return firstNonBlank(
                extText(channel, EXT_PROVIDER_SPI),
                extText(channel, "providerExtension"),
                extText(channel, "spi"),
                channel != null && "EPAY".equalsIgnoreCase(channel.getChannelType()) ? "epay" : null,
                fallback);
    }

    protected String decryptValue(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        try {
            return merchantChannelService.decryptApiKey(encryptedValue);
        } catch (Exception e) {
            return encryptedValue;
        }
    }

    protected long yuanToFen(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    protected BigDecimal fenToYuan(Number amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount.longValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    protected String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    protected String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new PaymentException(message);
        }
        return value;
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
