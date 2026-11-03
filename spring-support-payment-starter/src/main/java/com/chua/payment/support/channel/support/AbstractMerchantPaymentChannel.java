// [P3C A20 豁免] A20/A30/A31: 仅含常量定义行，豁免。
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
 * 商户支付通道公共能力
 * @author CH
 * @since 4.0.0
 */
@RequiredArgsConstructor
public abstract class AbstractMerchantPaymentChannel {

    /**
     * ext 扩展配置键：提供方 SPI
     */
    public static final String EXT_PROVIDER_SPI = "providerSpi";
    /**
     * default 默认值：提供方 SPI
     */
    public static final String DEFAULT_PROVIDER_SPI = "default";

    /**
     * merchant 通道服务
     */
    protected final MerchantChannelService merchantChannelService;
    /**
     * ObjectMapper 对象映射器
     */
    protected final ObjectMapper objectMapper;

    /**
     * 解析 ext 配置
     *
     * @param channel 通道配置
     * @return ext 配置
     */
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

    /**
     * 读取 ext 文本
     *
     * @param channel 通道配置
     * @param key 键
     * @return ext 文本
     */
    protected String extText(MerchantChannel channel, String key) {
        Object value = extConfig(channel).get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 获取提供方 SPI
     *
     * @param channel 通道配置
     * @return 提供方 SPI
     */
    protected String providerSpi(MerchantChannel channel) {
        return providerSpi(channel, DEFAULT_PROVIDER_SPI);
    }

    /**
     * 获取提供方 SPI
     *
     * @param channel 通道配置
     * @param fallback 降级默认值
     * @return 提供方 SPI
     */
    protected String providerSpi(MerchantChannel channel, String fallback) {
        return firstNonBlank(
                /**
                 * ext 文本。
                 * @param channel 通道配置
                 * @param EXT_PROVIDER_SPI ext 提供方 SPI
                 */
                extText(channel, EXT_PROVIDER_SPI),
                /**
                 * ext 文本。
                 * @param channel 通道配置
                 */
                extText(channel, "providerExtension"),
                /**
                 * ext 文本。
                 * @param channel 通道配置
                 */
                extText(channel, "spi"),
                channel != null && "EPAY".equalsIgnoreCase(channel.getChannelType()) ? "epay" : null,
                fallback);
    }

    /**
     * 解密文本
     *
     * @param encryptedValue 加密值
     * @return 解密后的值
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
     * 元转分
     *
     * @param amount 金额
     * @return 元转分后的值
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
     * 分转元
     *
     * @param amount 金额
     * @return 分转元后的值
     */
    protected BigDecimal fenToYuan(Number amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount.longValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 取第一个非空白值
     *
     * @param values 候选值
     * @return 第一个非空白值
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
     * 必填文本校验
     *
     * @param value 值
     * @param message 校验失败提示
     * @return 校验通过的文本
     */
    protected String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new PaymentException(message);
        }
        return value;
    }

    /**
     * 序列化为 JSON
     *
     * @param value 值
     * @return JSON 文本
     */
    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
