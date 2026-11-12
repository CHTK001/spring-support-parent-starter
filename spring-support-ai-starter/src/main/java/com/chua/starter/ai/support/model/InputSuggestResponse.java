package com.chua.starter.ai.support.model;

/**
 * 输入框预测响应模型。
 *
 * <p>{@code suggestion} 为空表示本次没有可用的预测：原因在 {@code message} 里
 * （未开启 / 会话无消息 / 模型未解析到 / 调用失败 / 模型没给出内容）。
 * 前端据此静默不展示，而不是弹错误 —— 预测是辅助能力，失败不应打断输入。</p>
 *
 * @param suggestion    预测出的下一条消息（可空）
 * @param model         实际使用的预测模型
 * @param elapsedMillis 预测耗时（毫秒）
 * @param message       不可用时的原因文案（可用时为 null）
 * @since 4.0.0.51
 * @author CH
 */
public record InputSuggestResponse(
        String suggestion,
        String model,
        Long elapsedMillis,
        String message) {

    /**
     * 构建响应。
     *
     * @param suggestion    预测文本
     * @param model         实际使用模型
     * @param elapsedMillis 耗时（毫秒）
     * @param message       原因文案
     * @return 响应对象
     */
    public static InputSuggestResponse of(String suggestion, String model, Long elapsedMillis, String message) {
        return new InputSuggestResponse(
                suggestion == null ? "" : suggestion,
                model == null ? "" : model,
                elapsedMillis == null ? 0L : elapsedMillis,
                message);
    }
}
