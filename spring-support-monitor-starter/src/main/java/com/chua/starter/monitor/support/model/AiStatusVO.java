package com.chua.starter.monitor.support.model;

/**
 * AI 分析可用性状态
 *
 * @param enabled  是否能发起对话（与真实对话同一判据）
 * @param model    默认模型名，未启用时省略
 * @param provider 模型提供方，未启用时省略
 * @param reason   不可用原因，可用时省略
 * @author CH
 * @since 4.0.0.42
 */
public record AiStatusVO(
        boolean enabled,
        String model,
        String provider,
        String reason
) {
}
