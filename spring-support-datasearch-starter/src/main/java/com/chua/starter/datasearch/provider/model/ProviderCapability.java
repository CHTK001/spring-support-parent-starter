package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 供应商能力位（流式 / 异步）。
 *
 * <p>口径与 {@code ProviderProtocolCapabilities} 一致：流式按协议判定
 * （openai / anthropic / gemini 的 SSE 是协议必备行为），异步只认官方批量端点
 * 实测过的厂商（openai / anthropic / google）；{@code null} 表示「未收录」，
 * 不是「不支持」，本类从不产出 {@code false}。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Data
public class ProviderCapability implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否支持流式输出
     */
    private Boolean streaming;

    /**
     * 流式结论的来源（protocol；未收录为 null）
     */
    private String streamingSource;

    /**
     * 是否支持异步调用（官方 Batch / 后台任务）
     */
    private Boolean asyncSupported;

    /**
     * 异步结论的来源（probe；未收录为 null）
     */
    private String asyncSource;

    /**
     * 协议（openai / anthropic / gemini）
     */
    private String protocol;

    /**
     * 结论的数据来源（registry / protocol；登记表口径为 registry）
     */
    private String source;

    /**
     * 补充说明
     */
    private String notes;
}
