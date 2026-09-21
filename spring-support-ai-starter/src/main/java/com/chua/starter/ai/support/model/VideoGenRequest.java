package com.chua.starter.ai.support.model;

/**
 * 文生视频请求参数
 *
 * @param prompt 提示词
 * @param model 模型名称，可选
 * @param duration 时长（秒），可选
 * @param size 尺寸，可选
 * @param style 风格，可选
 *
 * @author CH
 * @since 4.0.0
 */
public record VideoGenRequest(
        String prompt,
        String model,
        Integer duration,
        String size,
        String style
) {
}
