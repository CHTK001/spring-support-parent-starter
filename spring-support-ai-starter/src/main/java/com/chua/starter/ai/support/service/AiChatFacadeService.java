package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.common.support.ai.chat.ChatToolCallData;
import com.chua.common.support.ai.chat.ModelDefinition;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.model.AiClientProbe;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * AI 对话门面服务接口。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface AiChatFacadeService {

    /**
     * 日志持有者（接口不允许私有静态字段，用嵌套类持有）
     */
    final class LogHolder {
        /**
         * 日志
         */
        private static final System.Logger LOG = System.getLogger(AiChatFacadeService.class.getName());

        private LogHolder() {
        }
    }

    /**
     * 同步对话调用。
     *
     * @param request 对话请求
     * @return 对话结果
     */
    Map<String, Object> call(AiChatRequest request);

    /**
     * 同步对话调用（强类型出参）。
     *
     * <p>与 {@link #call(AiChatRequest)} 走同一条链路（含用量落库），差别在于返回
     * {@link AiChatResult} 记录而非按字符串键取值的 Map，供服务端代码直接按字段消费。</p>
     *
     * @param request 对话请求
     * @return 对话结果，失败时 {@code success()} 为 false 并携带错误原因
     */
    AiChatResult callForResult(AiChatRequest request);

    /**
     * 探测指定模型是否存在可用的对话客户端（不发起网络请求）。
     *
     * <p>与 {@link #callForResult(AiChatRequest)} 共用同一条解析链路，因此「状态显示可用」与
     * 「点击后真能对话」是同一个判据；{@code model} 为空时按注册中心默认客户端探测。</p>
     *
     * @param model 模型名称，可为空
     * @return 探测结果，不可用时携带原因
     */
    AiClientProbe probeChatClient(String model);

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
     * 流式对话调用（完成回调额外携带模型回传的真实用量）。
     *
     * <p>与 {@link #stream(AiChatRequest, Consumer, Consumer, Consumer)} 的差别只在完成回调：
     * 落库的消息行与前端气泡里的 Token 数必须取模型回传值，用字符数估算会和 {@code sys_ai_usage}
     * 的计量结果对不上，用户看到的用量与统计口径就成了两套数。</p>
     *
     * <p>默认实现退回普通流式入口并以空用量调用完成回调，实现方按需覆盖即可拿到真实用量。</p>
     *
     * @param request             对话请求
     * @param onChunk             每段增量正文回调，可为 空
     * @param onCompleteWithUsage 完成后回调（完整回复文本, 模型回传用量；客户端未回传时用量为 空），可为 空
     * @param onError             失败回调，可为 空
     * @param onThinkingChunk     每段思考内容增量回调，可为 空
     * @return SSE 发射器
     */
    default SseEmitter streamWithUsage(AiChatRequest request,
                                       Consumer<String> onChunk,
                                       BiConsumer<String, AiUsage> onCompleteWithUsage,
                                       Consumer<Throwable> onError,
                                       Consumer<String> onThinkingChunk) {
        return streamWithUsage(request, onChunk, onCompleteWithUsage, onError, onThinkingChunk, null);
    }

    /**
     * 流式对话调用（额外支持执行轨迹回调）。
     *
     * <p>{@code onTrace} 逐条接收 Agent 运行时的执行轨迹（轮次 / 深度思考 / 模型输出 / 工具调用与结果 /
     * token 用量 / 等待确认等边缘事件），供调用方把「为什么卡住」的完整过程转发到前端；不传时轨迹仅写入
     * 服务端日志，不影响正文与思考回调。</p>
     *
     * @param onTrace 每条执行轨迹回调，可为 空
     * @return SSE 发射器
     */
    default SseEmitter streamWithUsage(AiChatRequest request,
                                       Consumer<String> onChunk,
                                       BiConsumer<String, AiUsage> onCompleteWithUsage,
                                       Consumer<Throwable> onError,
                                       Consumer<String> onThinkingChunk,
                                       Consumer<AgentTrace> onTrace) {
        return stream(request, onChunk,
                onCompleteWithUsage == null ? null : reply -> onCompleteWithUsage.accept(reply, null),
                onError, onThinkingChunk);
    }

    /**
     * 流式对话调用（额外支持工具调用增量回调）。
     *
     * <p>{@code onToolCall} 逐帧接收模型发起的函数调用（序号/id/名称/入参 JSON 增量），
     * 供对外协议适配层把 OpenAI 的 tool_calls 翻译为 Anthropic 的 tool_use 块；不传时工具调用
     * 仅在底层处理、不下发给调用方。</p>
     *
     * @param onToolCall 工具调用增量回调（每帧一批），可为 空
     * @return Sse 发射器
     */
    default SseEmitter streamWithUsage(AiChatRequest request,
                                       Consumer<String> onChunk,
                                       BiConsumer<String, AiUsage> onCompleteWithUsage,
                                       Consumer<Throwable> onError,
                                       Consumer<String> onThinkingChunk,
                                       Consumer<AgentTrace> onTrace,
                                       Consumer<List<ChatToolCallData>> onToolCall) {
        return streamWithUsage(request, onChunk, onCompleteWithUsage, onError,
                onThinkingChunk, onTrace, onToolCall, null);
    }

    /**
     * 流式对话调用（额外支持人工确认请求回调）。
     *
     * <p>{@code onConfirmation} 在 Agent 请求人工确认（如规划模式 plan_exit）时触发，
     * 携带 replyId 与待确认项，供编排层下发确认卡片；不传时确认请求不下发给调用方。</p>
     *
     * @param onConfirmation 人工确认请求回调，可为 空
     * @return SSE 发射器
     */
    default SseEmitter streamWithUsage(AiChatRequest request,
                                       Consumer<String> onChunk,
                                       BiConsumer<String, AiUsage> onCompleteWithUsage,
                                       Consumer<Throwable> onError,
                                       Consumer<String> onThinkingChunk,
                                       Consumer<AgentTrace> onTrace,
                                       Consumer<List<ChatToolCallData>> onToolCall,
                                       Consumer<com.chua.common.support.ai.agent.ConfirmationRequest> onConfirmation) {
        return streamWithUsage(request, onChunk, onCompleteWithUsage, onError,
                onThinkingChunk, onTrace, onToolCall);
    }

    /**
     * 恢复执行：回传人工确认（human-in-the-loop）决策，订阅 Agent 恢复后的事件流。
     *
     * <p>当流式对话下发 confirmation 事件（如规划模式 plan_exit）后，前端在用户点击
     * 「批准 / 拒绝」时调用本方法，携带原请求（同会话/模型/设置）、replyId 与各工具决策；
     * 批准则 Agent 继续执行（进入 BUILD），拒绝则回到上一阶段（继续 PLAN）。
     *
     * @param request   原对话请求（携带会话/模型/角色设置）
     * @param replyId   待确认请求回复 ID
     * @param decisions 各待确认工具的决策
     * @return SSE 发射器（恢复后的事件流）
     */
    default SseEmitter resume(AiChatRequest request, String replyId,
                              List<com.chua.common.support.ai.agent.ConfirmationDecision> decisions) {
        SseEmitter emitter = new SseEmitter(60_000L);
        try {
            emitter.completeWithError(new IllegalStateException("当前实现不支持人工确认恢复"));
        } catch (Exception e) {
            LogHolder.LOG.log(System.Logger.Level.DEBUG, "[ai] 下发错误失败，已忽略: " + e.getMessage(), e);
        }
        return emitter;
    }

    /**
     * 仅组装已配置的客户端（解析 + 参数配置 + 按需 Agent 包装），不发起对话。
     *
     * <p>供人工确认恢复等需要直接持有 {@code AgentChatClient} 再调用其 resume 的场景；
     * 调用方负责在用完后关闭返回的客户端。</p>
     *
     * @param request 对话请求
     * @return 已配置客户端；无法解析时返回 空
     */
    default com.chua.common.support.ai.chat.ChatClient prepareConfigured(AiChatRequest request) {
        return null;
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
