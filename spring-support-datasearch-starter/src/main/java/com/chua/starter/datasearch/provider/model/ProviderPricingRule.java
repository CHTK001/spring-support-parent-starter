package com.chua.starter.datasearch.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 一条定价规则：既支持「每百万 token」这种单位价，也支持峰谷价与按 N 次计价。
 *
 * <p>为什么价格不能只有两个数字：</p>
 * <ul>
 *   <li><b>峰谷价</b>：同一模型在不同时段价格不同（DeepSeek 闲时为高峰价的一半，
 *       高峰是 UTC 周一至周五 01:00-04:00 / 06:00-10:00）；只存一个输入价会把
 *       闲时成本算高一倍；</li>
 *   <li><b>按 N 次</b>：搜索、抓取、文件解析、TTS / ASR 按「每 1000 次」「每万字符」
 *       计价，单位不是 token；用 {@link #quantity} 表示 N，价格就是「每 N 个单位」的价格；</li>
 *   <li><b>目录价 vs 渠道价</b>：这里存公开目录价，渠道实际结算价仍以上游 / 手工为准。</li>
 * </ul>
 *
 * <p>金额口径：{@link #peakPrice} / {@link #offPeakPrice} / {@link #standardPrice}
 * 都是「{@link #quantity} 个 {@link #unit} 的价格」，币种见 {@link #currency}。
 * 没有峰谷的条目只填 {@code standardPrice}。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderPricingRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 厂商键（登记表命中后回填）
     */
    private String providerId;

    /**
     * 模型匹配（精确或 `*` 通配；`*` 表示该厂商全部模型）
     */
    private String modelPattern;

    /**
     * 计费项：input_cache_hit / input_cache_miss / output / search / fetch /
     * file_parse / tts / asr / image / request
     */
    private String item;

    /**
     * 计量单位：token / search / fetch / page / character / hour / image / request
     */
    private String unit;

    /**
     * 每 N 个单位计价（1 = 单价；1000000 = 每百万 token；1000 = 每千次）
     */
    private BigDecimal quantity;

    /**
     * 高峰价（每个 quantity 单位）
     */
    private BigDecimal peakPrice;

    /**
     * 低谷价（每个 quantity 单位）
     */
    private BigDecimal offPeakPrice;

    /**
     * 统一价（无峰谷时使用）
     */
    private BigDecimal standardPrice;

    /**
     * 币种（USD / CNY）
     */
    private String currency;

    /**
     * 高峰时段；低谷为其余时段（见 {@link #offPeakPolicy}）
     */
    private List<PriceWindow> peakWindows;

    /**
     * 低谷说明（如「其余时段，含周末与法定节假日」）
     */
    private String offPeakPolicy;

    /**
     * 备注（限时折扣、计费口径等）
     */
    private String note;

    /**
     * 官方价格页
     */
    private String sourceUrl;

    /**
     * 是否按官方原文核对过
     */
    private Boolean verified;

    /**
     * 判断模型是否命中本规则。
     *
     * @param modelId 模型标识
     * @return 命中返回 true
     */
    public boolean matches(String modelId) {
        if (modelPattern == null || modelPattern.isBlank() || "*".equals(modelPattern.trim())) {
            return true;
        }
        if (modelId == null) {
            return false;
        }
        String pattern = modelPattern.trim();
        if (!pattern.contains("*")) {
            return pattern.equalsIgnoreCase(modelId);
        }
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return modelId.matches("(?i)^" + regex + "$");
    }
}
