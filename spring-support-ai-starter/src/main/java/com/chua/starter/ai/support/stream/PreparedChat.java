package com.chua.starter.ai.support.stream;

import com.chua.starter.ai.support.model.AiChatRequest;
import lombok.Data;

/**
 * 聊天轮次的同步准备结果。
 *
 * <p>{@link ChatStreamOrchestrator#prepare(java.util.Map)} 在请求线程内完成模型/角色解析、
 * 会话归属校验、用户消息落库与请求组装，产出本对象；归属/权限错误在此阶段以标准 JSON 抛出。
 * {@link ChatStreamOrchestrator#execute(PreparedChat, ChatFrameSink)} 再据本对象异步产出帧。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class PreparedChat {

    /** 前端会话标识 */
    private String sessionId;

    /** 本轮用户文本 */
    private String content;

    /** 实际使用的模型名 */
    private String modelToUse;

    /** 后端会话主键 */
    private Long dialogId;

    /** 已落库的用户消息主键 */
    private Long userMessageId;

    /** 助手消息标识 */
    private String messageId;

    /** 轮次标识 */
    private String turnsId;

    /** 内容标识 */
    private String contentId;

    /** 用户消息标识（前端） */
    private String userMsgId;

    /** 前端生成的请求标识（WS 多路复用路由用） */
    private String chatRequestId;

    /** 组装好的模型请求 */
    private AiChatRequest request;

    /** 本轮归属用户标识（记忆/落库用） */
    private String memoryUser;

    /** 人工确认恢复：待确认请求回复 ID */
    private String replyId;

    /** 人工确认恢复：各待确认工具的决策 */
    private java.util.List<com.chua.common.support.ai.agent.ConfirmationDecision> decisions;

    /** 人工确认恢复：用户修改意见（规划模式「需要修改」） */
    private String feedback;
}
