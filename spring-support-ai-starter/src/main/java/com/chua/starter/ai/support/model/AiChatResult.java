package com.chua.starter.ai.support.model;

import com.chua.common.support.ai.AiUsage;

/**
 * AI 同步对话的强类型结果。
 *
 * <p>替代 {@code Map<String, Object>} 形式的对话返回，避免调用方按字符串键取值。</p>
 *
 * @param text         模型完整回答，失败或无回答时为空串
 * @param model        实际使用的模型名称，未解析出时为空
 * @param usage        模型回传的令牌用量，客户端未回传时为空
 * @param errorMessage 失败原因，成功时为空
 * @author CH
 * @since 4.0.0.42
 */
public record AiChatResult(String text, String model, AiUsage usage, String errorMessage) {

    /**
     * 是否为成功结果。
     *
     * @return true 表示模型正常返回
     */
    public boolean success() {
        return errorMessage == null;
    }

    /**
     * 构造成功结果。
     *
     * @param text  模型回答
     * @param model 实际模型
     * @param usage 用量
     * @return 成功结果
     */
    public static AiChatResult ok(String text, String model, AiUsage usage) {
        return new AiChatResult(text == null ? "" : text, model, usage, null);
    }

    /**
     * 构造失败结果。
     *
     * @param model        请求的目标模型（可为空）
     * @param errorMessage 失败原因
     * @return 失败结果
     */
    public static AiChatResult failed(String model, String errorMessage) {
        return new AiChatResult("", model, null,
                errorMessage == null || errorMessage.isBlank() ? "对话失败" : errorMessage);
    }
}
