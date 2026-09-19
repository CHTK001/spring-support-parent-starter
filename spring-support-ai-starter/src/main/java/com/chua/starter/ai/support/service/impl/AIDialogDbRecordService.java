package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于数据库的对话记录实现。
 *
 * <p>会话写入 {@code sys_ai_session}，消息写入 {@code sys_ai_message}，
 * 重启后历史记录不丢失。取代 {@link AIDialogLocalRecordService} 的内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIDialogDbRecordService implements AIDialogRecordService {

    /**
     * 会话 映射器
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 消息 映射器
     */
    private final SysAiMessageMapper messageMapper;

    @Override
    /** 创建Dialog */
    public AIDialog createDialog(String name) {
        SysAiSession session = new SysAiSession();
        session.setTitle(name == null || name.isBlank() ? "新对话" : name);
        session.setType("CHAT");
        session.setSysUserId("anonymous");
        session.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        log.debug("[AIDialogDb] 创建会话: id={}, title={}", session.getId(), name);
        return toDialog(session);
    }

    @Override
    /** 删除Dialog */
    public void deleteDialog(Long dialogId) {
        if (dialogId == null) {
            return;
        }
        // 先删除消息，再删除会话
        messageMapper.delete(new LambdaQueryWrapper<SysAiMessage>()
                .eq(SysAiMessage::getSysAiSessionId, dialogId));
        sessionMapper.deleteById(dialogId);
    }

    @Override
    /** 列表dialog */
    public List<AIDialog> listDialog() {
        return sessionMapper.selectList(new LambdaQueryWrapper<SysAiSession>()
                        .eq(SysAiSession::getStatus, 1)
                        .orderByDesc(SysAiSession::getCreateTime))
                .stream()
                .map(this::toDialog)
                .collect(Collectors.toList());
    }

    @Override
    /** 获取dialog消息 */
    public List<ChatMessage> getDialogMessages(Long dialogId) {
        return messageMapper.selectList(new LambdaQueryWrapper<SysAiMessage>()
                        .eq(SysAiMessage::getSysAiSessionId, dialogId)
                        .orderByAsc(SysAiMessage::getId))
                .stream()
                .map(this::toChatMessage)
                .collect(Collectors.toList());
    }

    @Override
    /** 添加消息 */
    public void addMessage(Long dialogId, ChatMessage message) {
        if (dialogId == null || message == null) {
            return;
        }
        SysAiMessage entity = new SysAiMessage();
        entity.setSysAiSessionId(dialogId);
        entity.setRole(message.getRole() != null ? message.getRole() : "user");
        entity.setType("TEXT");
        entity.setContent(message.getContent());
        entity.setParentId(message.getParentId());
        entity.setModelName(message.getModelName());
        entity.setPromptTokens(message.getPromptTokens());
        entity.setCompletionTokens(message.getCompletionTokens());
        entity.setTotalTokens(message.getTotalTokens());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        messageMapper.insert(entity);
 // 同步 标识 与会话 标识，保持接口语义
        message.setId(entity.getId());
        message.setDialogId(dialogId);
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
    }

    /**
     * 数据库会话实体转业务会话。
     *
     * @param session 数据库会话
     * @return 业务会话
     */
    private AIDialog toDialog(SysAiSession session) {
        AIDialog dialog = new AIDialog();
        dialog.setId(session.getId());
        dialog.setName(session.getTitle());
        dialog.setTimestamp(session.getCreateTime() != null
                ? session.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis());
        return dialog;
    }

    /**
     * 数据库消息实体转业务消息。
     *
     * @param entity 数据库消息
     * @return 业务消息
     */
    private ChatMessage toChatMessage(SysAiMessage entity) {
        ChatMessage message = new ChatMessage();
        message.setId(entity.getId());
        message.setDialogId(entity.getSysAiSessionId());
        message.setRole(entity.getRole());
        message.setContent(entity.getContent());
        message.setModelName(entity.getModelName());
        message.setPromptTokens(entity.getPromptTokens());
        message.setCompletionTokens(entity.getCompletionTokens());
        message.setTotalTokens(entity.getTotalTokens());
        message.setTimestamp(entity.getCreateTime() != null
                ? entity.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis());
        return message;
    }
}
