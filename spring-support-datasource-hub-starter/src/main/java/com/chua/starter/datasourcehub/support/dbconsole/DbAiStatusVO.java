package com.chua.starter.datasourcehub.support.dbconsole;

/**
 * AI 文本生成 SQL 的可用状态。
 *
 * <p>前端按 {@code available} 决定是否显示 text2sql 入口 —— 「后端集成 ai-starter
 * 才显示」：未集成（实现缺失）与集成但未配置可用模型（探测不可用）都如实上报，
 * 由 {@code reason} 说明原因，便于现场排查。</p>
 *
 * @param available 是否可用（存在实现且探测到可用对话客户端）
 * @param model     解析后的模型名，不可用时为空
 * @param reason    不可用原因，可用时为空
 * @author CH
 * @since 4.0.0.42
 */
public record DbAiStatusVO(
        boolean available,
        String model,
        String reason
) {
}
