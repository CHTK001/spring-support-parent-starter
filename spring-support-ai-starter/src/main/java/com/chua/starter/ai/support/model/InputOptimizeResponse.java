package com.chua.starter.ai.support.model;

/**
 * 输入优化响应模型。
 *
 * <p>{@code status} 是「这次优化到底发生了什么」的唯一权威口径，
 * 供调用方区分「模型给出了新文本」与「没真正调模型、原样退回」——
 * 只看 {@code optimized} 是否等于 {@code input} 是判不出来的：
 * 模型返回原文、优化被关闭、没有可用客户端，这三种情况在文本上完全一样。</p>
 *
 * @param input         原始输入
 * @param optimized     优化后的文本（未成功时为原文）
 * @param model         实际使用的优化模型
 * @param elapsedMillis 优化耗时（毫秒）
 * @param status        优化状态，取值见 {@code STATUS_*} 常量
 * @since 4.0.0.42
 * @author CH
 */
public record InputOptimizeResponse(
        String input,
        String optimized,
        String model,
        Long elapsedMillis,
        String status) {

    /**
     * 优化成功：{@code optimized} 是模型新产出的文本
     */
    public static final String STATUS_OK = "OK";

    /**
     * 输入为空，未调用模型
     */
    public static final String STATUS_BLANK = "BLANK";

    /**
     * 管理端关闭了输入优化，未调用模型
     */
    public static final String STATUS_DISABLED = "DISABLED";

    /**
     * 没有可用的优化模型客户端，优化没有真正执行
     */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /**
     * 模型返回空内容
     */
    public static final String STATUS_EMPTY = "EMPTY";

    /**
     * 调用异常，已回退原文
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 模型返回的文本与原文相同
     */
    public static final String STATUS_UNCHANGED = "UNCHANGED";

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
        return new InputOptimizeResponse(input, optimized, model, elapsedMillis, STATUS_OK);
    }

    /**
     * 构建带状态的响应。
     *
     * @param input         原始输入
     * @param optimized     优化后文本（未成功时为原文）
     * @param model         实际使用模型
     * @param elapsedMillis 耗时（毫秒）
     * @param status        优化状态
     * @return 响应对象
     */
    public static InputOptimizeResponse of(String input, String optimized, String model,
                                           Long elapsedMillis, String status) {
        return new InputOptimizeResponse(input, optimized, model, elapsedMillis, status);
    }
}
