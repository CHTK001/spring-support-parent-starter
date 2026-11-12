package com.chua.starter.ai.support.model;

/**
 * 输入框预测请求模型。
 *
 * @param model 当前会话/用户选中的模型（可空；为空时后端按配置与生效模型链解析）
 * @since 4.0.0.51
 * @author CH
 */
public record InputSuggestRequest(
        String model) {
}
