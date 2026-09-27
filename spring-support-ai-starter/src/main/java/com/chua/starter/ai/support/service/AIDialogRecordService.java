package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;

import java.util.List;

/**
 * AI 对话记录服务接口。
 * <p>
 * 负责管理会话（Dialog）及其消息（对话消息）的持久化与查询。
 *
 * @author CH
 * @since 4.0.0
 */
public interface AIDialogRecordService {

    /**
     * 创建新会话。
     *
     * @param name 会话名称
     * @return 创建的会话
     */
    AIDialog createDialog(String name);

    /**
     * 删除指定会话及其所有消息。
     *
     * @param dialogId 会话 标识
     */
    void deleteDialog(Long dialogId);

    /**
     * 获取所有会话列表。
     *
     * @return 会话列表
     */
    List<AIDialog> listDialog();

    /**
     * 获取指定会话的所有消息。
     *
     * @param dialogId 会话 标识
     * @return 消息列表
     */
    List<ChatMessage> getDialogMessages(Long dialogId);

    /**
     * 向指定会话追加一条消息。
     *
     * @param dialogId 会话 标识
     * @param message  消息
     */
    void addMessage(Long dialogId, ChatMessage message);

    /**
     * 将指定会话中、携带给定 replyId 的待确认请求标记为已解决（approved/rejected）。
     *
     * <p>用于人工确认恢复：用户在卡片上批准/拒绝后，把旧助手消息上的 pending 确认置为终态。
     * 默认空实现，仅持久化（DB）实现需要覆盖。</p>
     *
     * @param dialogId 会话 标识
     * @param replyId  待确认请求的 replyId
     * @param decision 决定结果（approved/rejected）
     */
    default void resolveConfirmation(Long dialogId, String replyId, String decision) {
    }

    /**
     * 查找携带给定 replyId 确认请求的助手消息所属的用户消息标识（parent_id）。
     *
     * <p>用于人工确认恢复：恢复轮新建的助手消息需要挂到同一用户消息下，
     * 否则前端按 parentId 分组时该消息不会渲染。默认空实现返回 {@code null}。</p>
     *
     * @param dialogId 会话 标识
     * @param replyId  待确认请求的 replyId
     * @return 用户消息标识；未找到时返回 {@code null}
     */
    default String findParentMessageIdByReplyId(Long dialogId, String replyId) {
        return null;
    }
}
