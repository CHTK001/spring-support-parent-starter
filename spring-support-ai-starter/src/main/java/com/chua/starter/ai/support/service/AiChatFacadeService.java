package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.model.AiChatRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 对话门面服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiChatFacadeService {

    /**
     * 同步对话调用。
     *
     * @param request 对话请求
     * @return 对话结果
     */
    Map<String, Object> call(AiChatRequest request);

    /**
     * 流式对话调用。
     *
     * @param request 对话请求
     * @return SSE 发射器
     */
    default SseEmitter stream(AiChatRequest request) {
        return stream(request, null);
    }

    /**
     * 流式对话调用，并在流结束后通过 {@code onComplete} 回调返回完整回复内容。
     *
     * @param request    对话请求
     * @param onComplete 完成后回调（携带完整回复文本，可能为 空），可为 空
     * @return SSE 发射器
     */
    default SseEmitter stream(AiChatRequest request, Consumer<String> onComplete) {
        return stream(request, null, onComplete);
    }

    /**
     * 流式对话调用，逐增量文本通过 {@code onChunk} 回调（用于 WS 流式推送），
     * 流结束后通过 {@code onComplete} 回调返回完整回复内容。
     *
     * @param request    对话请求
     * @param onChunk    每段增量文本回调（可为 空）
     * @param onComplete 完成后回调（携带完整回复文本，可能为 空），可为 空
     * @return SSE 发射器
     */
    default SseEmitter stream(AiChatRequest request,
                              Consumer<String> onChunk,
                              Consumer<String> onComplete) {
        return stream(request, onChunk, onComplete, null);
    }

    /**
     * 流式对话调用（含失败回调）。上游全链路失败时通过 {@code onError} 通知调用方，
     * 调用方据此推送带错误语义的 {@code turn.completed}，避免前端会话卡在生成态。
     *
     * @param request    对话请求
     * @param onChunk    每段增量文本回调（可为 空）
     * @param onComplete 完成后回调（携带完整回复文本，可能为 空），可为 空
     * @param onError    失败回调（携带异常，仅在全链路失败时触发，与 on完成 互斥），可为 空
     * @return SSE 发射器
     */
    SseEmitter stream(AiChatRequest request,
                      Consumer<String> onChunk,
                      Consumer<String> onComplete,
                      Consumer<Throwable> onError);

    /**
     * 流式对话调用（含思考内容回调）。
     *
     * @param request          对话请求
     * @param onChunk          每段正式回答增量文本回调
     * @param onComplete       完成后回调
     * @param onError          失败回调
     * @param onThinkingChunk  每段思考内容增量回调（深度思考时）
     * @return SSE 发射器
     */
    default SseEmitter stream(AiChatRequest request,
                              Consumer<String> onChunk,
                              Consumer<String> onComplete,
                              Consumer<Throwable> onError,
                              Consumer<String> onThinkingChunk) {
        return stream(request, onChunk, onComplete, onError);
    }

    /**
     * 获取分组可用模型列表。
     *
     * @param groupId 分组 标识
     * @return 模型列表
     */
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
     * @param groupId 分组 标识（可为 空，表示系统内部调用）
     * @return 模型定义列表
     */
    default List<ModelDefinition> modelDefinitions(Integer groupId) {
        return Collections.emptyList();
    }
}
