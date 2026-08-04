package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.model.AiChatRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface AiChatFacadeService {

    Map<String, Object> call(AiChatRequest request);

    org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(AiChatRequest request);

    default List<String> models(Integer groupId) {
        return Collections.emptyList();
    }

    /**
     * 获取模型定义列表（含 capabilities 能力标签）。
     *
     * <p>供前端根据模型能力判断是否显示「深度思考」「智能搜索」等开关，
     * 返回结构通过 {@link ModelDefinition#getCapabilities()} 携带能力信息，
     * 能力标签约定：{@code think}（深度思考）、{@code search}（智能搜索）、{@code vision}（多模态）等。</p>
     *
     * @param groupId 分组 ID（可为 null，表示系统内部调用）
     * @return 模型定义列表
     */
    default List<ModelDefinition> modelDefinitions(Integer groupId) {
        return Collections.emptyList();
    }
}
