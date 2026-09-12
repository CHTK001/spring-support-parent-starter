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
* @since 4.0.0
 */
public class AIDialogNoneRecordService implements AIDialogRecordService {

    @Override
    /** 创建Dialog */
    public AIDialog createDialog(String name) {
        /**
        * aidialog。
        * @param 0L 0L
        * @param name 名称
        * @return AIdialog的结果
         */
        return new AIDialog(0L, name);
    }

    @Override
    /** 删除Dialog */
    public void deleteDialog(Long dialogId) {
        // no-op
    }

    @Override
    /** 列表dialog */
    public List<AIDialog> listDialog() {
        return Collections.emptyList();
    }

    @Override
    /** 获取dialog消息 */
    public List<ChatMessage> getDialogMessages(Long dialogId) {
        return Collections.emptyList();
    }

    @Override
    /** 添加消息 */
    public void addMessage(Long dialogId, ChatMessage message) {
        // no-op
    }
}
