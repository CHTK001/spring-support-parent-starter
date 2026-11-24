package com.chua.starter.common.support.collect;

/**
 * 采集平台 AI 节点的对话调用契约。
 *
 * <p>collect-starter 不直接依赖 ai-starter：本契约定义在 common-starter，
 * 由 ai-starter 提供适配实现（Spring Bean），collect 通过
 * {@code ObjectProvider<CollectAiInvoker>} 懒加载注入。宿主未集成 ai-starter 时
 * 容器中不存在实现，采集 AI 节点执行时给出明确提示。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface CollectAiInvoker {

    /**
     * 同步调用一次模型对话。
     *
     * @param request 对话请求（提示词必填，模型/系统提示词可选）
     * @return 对话结果，失败时 {@code errorMessage()} 非空
     */
    CollectAiResult invoke(CollectAiRequest request);

    /**
     * 采集 AI 节点的对话请求。
     *
     * @param prompt       用户提示词（必填，采集变量展开后的文本）
     * @param model        模型名称，空则用 ai 侧默认模型
     * @param systemPrompt 系统提示词，可空
     */
    record CollectAiRequest(String prompt, String model, String systemPrompt) {
    }

    /**
     * 采集 AI 节点的对话结果。
     *
     * @param text         模型回答，失败或无回答时为空
     * @param errorMessage 失败原因，成功时为空
     */
    record CollectAiResult(String text, String errorMessage) {
    }
}
