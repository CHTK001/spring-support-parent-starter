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
* @author CH
* @since 4.0.0
 */
@RequiredArgsConstructor
public abstract class AbstractMerchantPaymentChannel {

    /** Ext_提供者_spi */
    public static final String EXT_PROVIDER_SPI = "providerSpi";
    /** 默认_提供者_spi */
    public static final String DEFAULT_PROVIDER_SPI = "default";

    /** Merchant通道服务 */
    protected final MerchantChannelService merchantChannelService;
    /** Objectmapper */
    protected final ObjectMapper objectMapper;

    /**
    * ext配置
    *
    * @param channel 通道
    * @return ext配置的结果
     */
    protected Map<String, Object> extConfig(MerchantChannel channel) {
        if (!StringUtils.hasText(channel.getExtConfig())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(channel.getExtConfig(), new TypeReference<>() {
            });
        } catch (Exception e) {
            /**
            * 薪酬异常。
            * @param "支付方式扩展配置格式错误" "支付方式扩展配置格式错误"
            * @param e e
            * @return 薪酬异常的结果
             */
            throw new PaymentException("支付方式扩展配置格式错误", e);
        }
    }

    /**
    * ext文本
    *
    * @param channel 通道
    * @param key 钥匙
    * @return ext文本的结果
     */
    protected String extText(MerchantChannel channel, String key) {
        Object value = extConfig(channel).get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
    * 提供者spi
    *
    * @param channel 通道
    * @return 提供者spi的结果
     */
    protected String providerSpi(MerchantChannel channel) {
        return providerSpi(channel, DEFAULT_PROVIDER_SPI);
    }

    /**
    * 提供者spi
    *
    * @param channel 通道
    * @param fallback 降级
    * @return 提供者spi的结果
     */
    protected String providerSpi(MerchantChannel channel, String fallback) {
        return firstNonBlank(
                /**
                * ext文本。
                * @param channel 通道
                * @param EXT_PROVIDER_SPI ext提供者spi
                 */
                extText(channel, EXT_PROVIDER_SPI),
                /**
                * ext文本。
                * @param channel 通道
                * @param "providerExtension" "提供者延伸"
                 */
                extText(channel, "providerExtension"),
                /**
                * ext文本。
                * @param channel 通道
                * @param "spi" "spi"
                 */
                extText(channel, "spi"),
                channel != null && "EPAY".equalsIgnoreCase(channel.getChannelType()) ? "epay" : null,
                fallback);
    }

    /**
    * decrypt价值观
    *
    * @param encryptedValue encrypted价值观
    * @return decrypt价值观的结果
     */
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

    /**
    * yuan转为fen
    *
    * @param amount amount
    * @return yuan转为fen的结果
     */
    protected long yuanToFen(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
    * fen转为yuan
    *
    * @param amount amount
    * @return fen转为yuan的结果
     */
    protected BigDecimal fenToYuan(Number amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount.longValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
    * 第一个nonblank
    *
    * @param values 价值观
    * @return 第一个nonblank的结果
     */
    protected String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
    * required文本
    *
    * @param value 价值观
    * @param message 消息
    * @return required文本的结果
     */
    protected String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            /**
            * 薪酬异常。
            * @param message 消息
            * @return 薪酬异常的结果
             */
            throw new PaymentException(message);
        }
        return value;
    }

    /**
    * 转为json
    *
    * @param value 价值观
    * @return 转为json的结果
     */
    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}

