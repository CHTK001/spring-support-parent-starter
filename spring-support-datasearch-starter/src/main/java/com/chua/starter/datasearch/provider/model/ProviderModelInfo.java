package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模型信息（来自公开目录或上游 /models）。
 *
 * <p>字段口径：价格都是 <b>USD / 百万 token</b>；{@link #streaming} 与
 * {@link #asyncSupported} 目前没有公开目录提供，由
 * {@link com.chua.starter.datasearch.provider.support.ProviderProtocolCapabilities}
 * 按「流式走协议、异步只认实测」的口径补位，{@code null} 表示「未收录」——
 * 调用方需要自己判断「未知」和「不支持」，不能把 null 当 false 用。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Data
public class ProviderModelInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模型标识（上游 id / slug）
     */
    private String id;

    /**
     * 展示名
     */
    private String name;

    /**
     * 模型说明（可空）
     */
    private String description;

    /**
     * 输入价（USD / 百万 token）
     */
    private Double priceInputPerMusd;

    /**
     * 输出价（USD / 百万 token）
     */
    private Double priceOutputPerMusd;

    /**
     * 缓存命中价（USD / 百万 token）
     */
    private Double priceCacheHitPerMusd;

    /**
     * 缓存写入价（USD / 百万 token）
     */
    private Double priceCacheWritePerMusd;

    /**
     * 上下文窗口（token）
     */
    private Long contextWindow;

    /**
     * 最大输出 token
     */
    private Long maxOutputTokens;

    /**
     * 输入模态（text / image / audio / video / pdf）
     */
    private List<String> inputModalities;

    /**
     * 输出模态
     */
    private List<String> outputModalities;

    /**
     * 是否支持附件
     */
    private Boolean attachment;

    /**
     * 是否支持深度思考
     */
    private Boolean reasoning;

    /**
     * 是否支持工具调用
     */
    private Boolean toolCall;

    /**
     * 是否支持结构化输出
     */
    private Boolean structuredOutput;

    /**
     * 是否支持温度调节
     */
    private Boolean temperature;

    /**
     * 是否开源权重
     */
    private Boolean openWeights;

    /**
     * 是否支持流式（目录未提供时为 null）
     */
    private Boolean streaming;

    /**
     * 流式结论的来源（protocol / doc / probe；未收录为 null）
     */
    private String streamingSource;

    /**
     * 是否支持异步调用（目录未提供时为 null）
     */
    private Boolean asyncSupported;

    /**
     * 异步结论的来源（protocol / doc / probe；未收录为 null）
     */
    private String asyncSource;

    /**
     * 知识截止日期
     */
    private String knowledge;

    /**
     * 发布日期
     */
    private String releaseDate;

    /**
     * 最后更新日期
     */
    private String lastUpdated;
}
