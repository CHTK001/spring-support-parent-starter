package com.chua.starter.ai.support.model;

/**
 * 文生图请求参数
 *
 * @param prompt 提示词
 * @param model 模型名称，可选
 * @param size 尺寸，如 1024x1024，可选
 * @param style 风格，可选
 * @param quality 质量，可选
 *
 * @author CH
 * @since 4.0.0
 */
public record ImageGenRequest(
        String prompt,
        String model,
        String size,
        String style,
        String quality
) {
}
