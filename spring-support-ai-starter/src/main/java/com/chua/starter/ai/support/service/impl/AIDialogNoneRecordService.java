package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.service.AIDialogRecordService;

import java.util.Collections;
import java.util.List;

/**
 * 空桩实现——不持久化任何对话记录。
 * <p>
 * 当未配置持久化存储时使用此实现，所有查询返回空结果。
 *
 * @author CH
 */
public class AIDialogNoneRecordService implements AIDialogRecordService {

    @Override
    /** 创建Dialog */
    public AIDialog createDialog(String name) {
        return new AIDialog(0L, name);
    }

    @Override
    /** 删除Dialog */
    public void deleteDialog(Long dialogId) {
        // no-op
    }

    @Override
    /** ListDialog */
    public List<AIDialog> listDialog() {
        return Collections.emptyList();
    }

    @Override
    /** 获取DialogMessages */
    public List<ChatMessage> getDialogMessages(Long dialogId) {
        return Collections.emptyList();
    }

    @Override
    /** 添加Message */
    public void addMessage(Long dialogId, ChatMessage message) {
        // no-op
    }
}
