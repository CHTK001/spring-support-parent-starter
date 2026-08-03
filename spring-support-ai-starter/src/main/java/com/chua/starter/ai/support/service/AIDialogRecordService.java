package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;

import java.util.List;

/**
 * AI 对话记录服务接口。
 * <p>
 * 负责管理会话（Dialog）及其消息（ChatMessage）的持久化与查询。
 *
 * @author CH
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
     * @param dialogId 会话 ID
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
     * @param dialogId 会话 ID
     * @return 消息列表
     */
    List<ChatMessage> getDialogMessages(Long dialogId);

    /**
     * 向指定会话追加一条消息。
     *
     * @param dialogId 会话 ID
     * @param message  消息
     */
    void addMessage(Long dialogId, ChatMessage message);
}
