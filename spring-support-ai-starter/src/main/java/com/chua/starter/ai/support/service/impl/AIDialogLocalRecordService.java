package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.service.AIDialogRecordService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存的对话记录实现。
 * <p>
 * 使用 {@link ConcurrentHashMap} 存储会话与消息，适用于单实例、无需持久化的场景。
 * 重启后数据丢失。
 *
 * @author CH
 * @since 4.0.0
 */
public class AIDialogLocalRecordService implements AIDialogRecordService {

    /**
     * dialogidgenerator
    */
    private final AtomicLong dialogIdGenerator = new AtomicLong(1);
    /**
     * 消息idgenerator
    */
    private final AtomicLong messageIdGenerator = new AtomicLong(1);

    /**
     * dialogid → aidialog
    */
    private final Map<Long, AIDialog> dialogs = new ConcurrentHashMap<>();

    /**
     * dialogid → 列表&lt;对话消息&gt;
    */
    private final Map<Long, List<ChatMessage>> messages = new ConcurrentHashMap<>();

    @Override
    /**
     * 创建Dialog
     * @param name 字符串
     * @return 返回值
    */
    public AIDialog createDialog(String name) {
        Long id = dialogIdGenerator.getAndIncrement();
        AIDialog dialog = new AIDialog(id, name);
        dialogs.put(id, dialog);
        messages.put(id, Collections.synchronizedList(new ArrayList<>()));
        return dialog;
    }

    @Override
    /**
     * 删除Dialog
     * @param dialogId 长整数
     * @return 返回值
    */
    public void deleteDialog(Long dialogId) {
        dialogs.remove(dialogId);
        messages.remove(dialogId);
    }

    @Override
    /**
     * 列表dialog
     * @return 返回值
    */
    public List<AIDialog> listDialog() {
        return new ArrayList<>(dialogs.values());
    }

    @Override
    /**
     * 获取dialog消息
     * @param dialogId 长整数
     * @return 返回值
    */
    public List<ChatMessage> getDialogMessages(Long dialogId) {
        List<ChatMessage> list = messages.get(dialogId);
        if (list == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(list);
    }

    @Override
    /**
     * 添加消息
     * @param dialogId 长整数
     * @param message 参数
     * @return 返回值
    */
    public void addMessage(Long dialogId, ChatMessage message) {
        List<ChatMessage> list = messages.get(dialogId);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
            messages.put(dialogId, list);
        }
        if (message.getId() == null) {
            message.setId(messageIdGenerator.getAndIncrement());
        }
        if (message.getDialogId() == null) {
            message.setDialogId(dialogId);
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
        list.add(message);
    }
}
