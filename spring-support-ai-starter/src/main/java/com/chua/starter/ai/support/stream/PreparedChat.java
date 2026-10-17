package com.chua.starter.ai.support.stream;

import com.chua.common.support.ai.agent.ConfirmationDecision;
import com.chua.starter.ai.support.model.AiChatRequest;

import java.util.List;
import java.util.Map;

/**
 * 聊天轮次的同步准备结果。
 *
 * <p>{@link ChatStreamOrchestrator#prepare(java.util.Map)} 在请求线程内完成模型/角色解析、
 * 会话归属校验、用户消息落库与请求组装，产出本对象；归属/权限错误在此阶段以标准 JSON 抛出。
 * {@link ChatStreamOrchestrator#execute(PreparedChat, ChatFrameSink)} 再据本对象异步产出帧。</p>
 *
 * @param sessionId     前端会话标识
 * @param content       本轮用户文本
 * @param modelToUse    实际使用的模型名
 * @param routeInfo     本轮实际命中的供应商信息（{@code providerId}/{@code providerName}/{@code platform}）
 * @param dialogId      后端会话主键
 * @param userMessageId 已落库的用户消息主键
 * @param messageId     助手消息标识
 * @param turnsId       轮次标识
 * @param contentId     内容标识
 * @param userMsgId     用户消息标识（前端）
 * @param chatRequestId 前端生成的请求标识（WS 多路复用路由用）
 * @param request       组装好的模型请求
 * @param memoryUser    本轮归属用户标识（记忆/落库用）
 * @param replyId       人工确认恢复：待确认请求回复 ID
 * @param decisions     人工确认恢复：各待确认工具的决策
 * @param feedback      人工确认恢复：用户修改意见（规划模式「需要修改」）
 * @author CH
 * @since 4.0.0.42
 */
public record PreparedChat(
        String sessionId,
        String content,
        String modelToUse,
        Map<String, Object> routeInfo,
        Long dialogId,
        Long userMessageId,
        String messageId,
        String turnsId,
        String contentId,
        String userMsgId,
        String chatRequestId,
        AiChatRequest request,
        String memoryUser,
        String replyId,
        List<ConfirmationDecision> decisions,
        String feedback) {
}
