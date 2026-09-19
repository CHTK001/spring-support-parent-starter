package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.entity.SysAiMessage;
import com.chua.starter.ai.support.entity.SysAiSession;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiSessionMapper;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.util.AiUserContext;
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
     * 无身份流量（定时任务、异步线程、机器调用）的归属占位
     */
    private static final String ANONYMOUS_USER = "anonymous";

    /**
     * 会话 映射器
     */
    private final SysAiSessionMapper sessionMapper;

    /**
     * 消息 映射器
     */
    private final SysAiMessageMapper messageMapper;

    @Override
    /**
     * 创建Dialog
    */
    public AIDialog createDialog(String name) {
        SysAiSession session = new SysAiSession();
        session.setTitle(name == null || name.isBlank() ? "新对话" : name);
        session.setType("CHAT");
 // 请求线程内取 oauth 写入的身份；异步线程与机器流量无身份，回落 anonymous
        String userId = AiUserContext.currentUserId();
        session.setSysUserId(userId != null ? userId : ANONYMOUS_USER);
        session.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        log.debug("[AIDialogDb] 创建会话: id={}, title={}", session.getId(), name);
        return toDialog(session);
    }

    @Override
    /**
     * 删除Dialog
    */
    public void deleteDialog(Long dialogId) {
        if (dialogId == null) {
            return;
        }
        requireWritable(dialogId);
        // 先删除消息，再删除会话
        messageMapper.delete(new LambdaQueryWrapper<SysAiMessage>()
                .eq(SysAiMessage::getSysAiSessionId, dialogId));
        sessionMapper.deleteById(dialogId);
    }

    @Override
    /**
     * 列表dialog
    */
    public List<AIDialog> listDialog() {
        LambdaQueryWrapper<SysAiSession> wrapper = new LambdaQueryWrapper<SysAiSession>()
                .eq(SysAiSession::getStatus, 1);
 // 管理员只读全量，普通用户只见本人会话；无身份时条件恒不匹配（失败即关闭）
        if (!AiUserContext.isAdmin()) {
            wrapper.eq(SysAiSession::getSysUserId, AiUserContext.currentUserId());
        }
        return sessionMapper.selectList(wrapper.orderByDesc(SysAiSession::getCreateTime))
                .stream()
                .map(this::toDialog)
                .collect(Collectors.toList());
    }

    @Override
    /**
     * 获取dialog消息
    */
    public List<ChatMessage> getDialogMessages(Long dialogId) {
        requireReadable(dialogId);
        return messageMapper.selectList(new LambdaQueryWrapper<SysAiMessage>()
                        .eq(SysAiMessage::getSysAiSessionId, dialogId)
                        .orderByAsc(SysAiMessage::getId))
                .stream()
                .map(this::toChatMessage)
                .collect(Collectors.toList());
    }

    @Override
    /**
     * 添加消息
    */
    public void addMessage(Long dialogId, ChatMessage message) {
        if (dialogId == null || message == null) {
            return;
        }
 // 写出口校验：请求线程与经 AiUserContext.capturing 传递身份的异步回调都按归属拦截，仅无上下文的后台任务放行
        SysAiSession session = requireWritable(dialogId);
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
 // 归属随所属会话：回复回调在异步线程执行，取不到请求身份
        entity.setUserId(session.getSysUserId());
        messageMapper.insert(entity);
 // 同步 标识 与会话 标识，保持接口语义
        message.setId(entity.getId());
        message.setDialogId(dialogId);
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
    }

    /**
     * 读出口归属校验。
     *
     * <p>管理员可读全量；无请求身份（线程池、回复回调、机器流量）按机器信任放行；
     * 其余仅允许归属人。</p>
     *
     * @param dialogId 会话 标识
     * @return 校验通过的会话
     */
    private SysAiSession requireReadable(Long dialogId) {
        SysAiSession session = sessionMapper.selectById(dialogId);
        if (session == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在");
        }
        if (AiUserContext.isAdmin()) {
            return session;
        }
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
 // 只有未绑定请求上下文的后台任务才会走到这里，属机器流量而非越权
            return session;
        }
        if (!userId.equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人会话");
        }
        return session;
    }

    /**
     * 写出口归属校验。
     *
     * <p>管理员对他人数据只读，故写操作只认归属人本身；只读账号一律拒绝。</p>
     *
     * @param dialogId 会话 标识
     * @return 校验通过的会话
     */
    private SysAiSession requireWritable(Long dialogId) {
        SysAiSession session = requireReadable(dialogId);
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
            return session;
        }
        if (!userId.equals(session.getSysUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return session;
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
