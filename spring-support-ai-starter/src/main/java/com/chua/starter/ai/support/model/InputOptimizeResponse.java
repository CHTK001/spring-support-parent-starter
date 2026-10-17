package com.chua.starter.ai.support.model;

/**
 * 输入优化响应模型。
 *
 * @param input         原始输入
 * @param optimized     优化后的文本
 * @param model         实际使用的优化模型
 * @param elapsedMillis 优化耗时（毫秒）
 * @since 4.0.0.42
 * @author CH
 */
public record InputOptimizeResponse(
        String input,
        String optimized,
        String model,
        Long elapsedMillis) {

    /**
     * 构建响应。
     *
     * @param input         原始输入
     * @param optimized     优化后文本
     * @param model         实际使用模型
     * @param elapsedMillis 耗时（毫秒）
     * @return 响应对象
     */
    public static InputOptimizeResponse of(String input, String optimized, String model, Long elapsedMillis) {
        return new InputOptimizeResponse(input, optimized, model, elapsedMillis);
    }
}
