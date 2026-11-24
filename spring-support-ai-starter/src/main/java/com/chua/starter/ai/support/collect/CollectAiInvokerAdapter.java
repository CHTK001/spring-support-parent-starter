package com.chua.starter.ai.support.collect;

import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.common.support.collect.CollectAiInvoker;
import org.springframework.stereotype.Component;

/**
 * 采集平台 AI 节点契约的 ai-starter 适配实现。
 *
 * <p>把 common-starter 的 {@link CollectAiInvoker} 契约落到本模块同步对话门面
 * {@link AiChatFacadeService#callForResult(AiChatRequest)}，采集侧因此无需反射
 * ai 内部类（审计 2.4 整改）。用量记账口径与原采集执行器的反射设值保持一致：
 * callType=collect_ai_node、usageSource=service。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Component
public class CollectAiInvokerAdapter implements CollectAiInvoker {

    /**
     * 采集 AI 节点的用量记账调用来源标识。
     */
    private static final String CALL_TYPE_COLLECT_AI_NODE = "collect_ai_node";

    /**
     * 采集 AI 节点的用量记账来源（对外聚合服务口径）。
     */
    private static final String USAGE_SOURCE_SERVICE = "service";

    private final AiChatFacadeService chatFacadeService;

    /**
     * 构造适配器。
     *
     * @param chatFacadeService AI 同步对话门面
     */
    public CollectAiInvokerAdapter(AiChatFacadeService chatFacadeService) {
        this.chatFacadeService = chatFacadeService;
    }

    @Override
    public CollectAiResult invoke(CollectAiRequest request) {
        AiChatRequest aiRequest = new AiChatRequest();
        aiRequest.setPrompt(request.prompt());
        if (request.model() != null && !request.model().isBlank()) {
            aiRequest.setModel(request.model());
        }
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            aiRequest.setSystemPrompt(request.systemPrompt());
        }
        aiRequest.setCallType(CALL_TYPE_COLLECT_AI_NODE);
        aiRequest.setUsageSource(USAGE_SOURCE_SERVICE);

        AiChatResult result = chatFacadeService.callForResult(aiRequest);
        if (result == null) {
            return new CollectAiResult("", "AI 服务未返回结果");
        }
        return new CollectAiResult(result.text(), result.errorMessage());
    }
}
