package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.model.AiChatRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 对话门面服务接口的最小占位实现。
 *
 * <p>接口已定义但原实现类缺失（nomifun → manage 重构遗留），
 * 此处提供空实现使 Spring 上下文可正常装配、应用可独立启动。
 * 若需启用 AI 对话能力，请替换为完整实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class AiChatFacadeServiceImpl implements AiChatFacadeService {

    @Override
    public Map<String, Object> call(AiChatRequest request) {
        return Collections.emptyMap();
    }

    @Override
    public SseEmitter stream(AiChatRequest request,
                             Consumer<String> onChunk,
                             Consumer<String> onComplete) {
        return new SseEmitter();
    }

    @Override
    public List<String> models(Integer groupId) {
        return Collections.emptyList();
    }

    @Override
    public List<ModelDefinition> modelDefinitions(Integer groupId) {
        return Collections.emptyList();
    }
}
