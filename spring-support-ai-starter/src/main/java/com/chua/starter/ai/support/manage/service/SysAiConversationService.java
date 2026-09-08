package com.chua.starter.ai.support.manage.service;

import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.entity.SysAiConversationUuid;
import com.chua.starter.ai.support.mapper.SysAiConversationUuidMapper;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nomifun 会话契约业务服务。
 *
 * <p>nomifun 前端以 UUIDv7 作为会话 id，但现有 AI 中台 (AIDialog) 使用自增 Long id。
 * 本服务在网关层维护 UUIDv7 ↔ Long 的映射，委托 AIDialogRecordService 做存储。</p>
 *
 * <p>映射关系持久化到 {@code sys_ai_conversation_uuid} 表，确保重启后会话可恢复。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiConversationService {

    /** 现有对话记录服务 */
    private final AIDialogRecordService dialogRecordService;

    /** UUID 映射持久化 */
    private final SysAiConversationUuidMapper uuidMapper;

    /**
     * 生成 canonical lowercase UUIDv7（RFC 9562）。
     *
     * <p>UUIDv7 格式：{@code xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx}</p>
     * <ul>
     *   <li>前48位：毫秒级时间戳（确保时间有序）</li>
     *   <li>第13位：版本号（固定为7）</li>
     *   <li>第17位：变体（固定为10xx，即8/9/a/b）</li>
     * </ul>
     */
    private static String uuidV7() {
        long millis = System.currentTimeMillis();
        byte[] bytes = new byte[16];

        // 先生成随机数填充
        ThreadLocalRandom.current().nextBytes(bytes);

        // 前6字节：时间戳（大端序）
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;

        // 设置版本号为7（第6字节高4位）
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);

        // 设置变体为10xx（第8字节高2位）
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        // 格式化为字符串
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(hex[(bytes[i] >> 4) & 0xF]).append(hex[bytes[i] & 0xF]);
        }
        return sb.toString();
    }

    /**
     * nomifun 聊天会话响应视图（record 简化）。
     */
    public record NomifunConversation(String conversation_id, String type, String name,
            Map<String, Object> extra, Object model, boolean pinned, Long pinned_at,
            long created_at, long updated_at, int message_count) {}

    private NomifunConversation toApi(AIDialog dialog, String uuid) {
        return new NomifunConversation(
                uuid, "nomi", dialog.getName(), Map.of(), null,
                false, null, dialog.getTimestamp(), dialog.getTimestamp(), 0);
    }

    /**
     * 创建会话（nomifun POST /api/conversations）。
     */
    public NomifunConversation createConversation(String name) {
        var dialog = dialogRecordService.createDialog(name == null || name.isBlank() ? "新建会话" : name);
        String uuid = uuidV7();

        // 持久化映射到数据库
        SysAiConversationUuid uuidEntity = new SysAiConversationUuid();
        uuidEntity.setUuid(uuid);
        uuidEntity.setDialogId(dialog.getId());
        uuidEntity.setCreateTime(LocalDateTime.now());
        uuidMapper.insert(uuidEntity);

        return toApi(dialog, uuid);
    }

    /**
     * 列出会话（nomifun GET /api/conversations）。
     */
    public List<NomifunConversation> listConversations() {
        var result = new ArrayList<NomifunConversation>();

        // 从数据库加载所有映射
        var allMappings = uuidMapper.selectList(null);
        for (var mapping : allMappings) {
            var dialog = dialogRecordService.listDialog().stream()
                    .filter(d -> Objects.equals(d.getId(), mapping.getDialogId()))
                    .findFirst().orElse(null);
            if (dialog != null) result.add(toApi(dialog, mapping.getUuid()));
        }

        result.sort(Comparator.comparingLong((NomifunConversation c) -> c.updated_at).reversed());
        return result;
    }

    /**
     * 获取会话（nomifun GET /api/conversations/{id}）。
     */
    public NomifunConversation getConversation(String conversationId) {
        Long dialogId = uuidMapper.findDialogIdByUuid(conversationId);
        if (dialogId == null) return null;
        return dialogRecordService.listDialog().stream()
                .filter(d -> Objects.equals(d.getId(), dialogId))
                .findFirst().map(d -> toApi(d, conversationId)).orElse(null);
    }

    /**
     * 删除会话。
     */
    public void deleteConversation(String conversationId) {
        Long dialogId = uuidMapper.findDialogIdByUuid(conversationId);
        if (dialogId != null) {
            // 删除映射记录
            uuidMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<
                    SysAiConversationUuid>().eq("uuid", conversationId));
            dialogRecordService.deleteDialog(dialogId);
        }
    }

    /**
     * nomifun 消息视图（record）。
     */
    public record NomifunSendMessageResult(String msg_id, boolean replayed, boolean completed,
            Boolean result_ok, String result_text, String result_error) {}

    /**
     * 发送消息（nomifun POST /api/conversations/{id}/messages）。
     *
     * <p>当前为同步完成：持久化 user 角色消息后，直接回 completed=true。后续接入
     * 自有 Agent 流式编排后，该方法将转为投递任务并经 WS 广播 message.stream / turn。</p>
     *
     * @param conversationId 会话 id（UUIDv7）
     * @param content        输入内容
     * @return send 结果
     */
    public NomifunSendMessageResult sendMessage(String conversationId, String content) {
        Long dialogId = uuidMapper.findDialogIdByUuid(conversationId);
        if (dialogId == null) throw new IllegalArgumentException("conversation not found: " + conversationId);
        var msg = new com.chua.starter.ai.support.chat.ChatMessage("user", content == null ? "" : content);
        dialogRecordService.addMessage(dialogId, msg);
        String msgId = uuidV7();
        return new NomifunSendMessageResult(msgId, false, true, true, "已接收", null);
    }

    /**
     * 获取会话消息列表（nomifun GET /api/conversations/{id}/messages）。
     */
    public List<Map<String, Object>> getMessages(String conversationId) {
        Long dialogId = uuidMapper.findDialogIdByUuid(conversationId);
        if (dialogId == null) return List.of();
        var msgs = dialogRecordService.getDialogMessages(dialogId);
        if (msgs == null) return List.of();
        var result = new ArrayList<Map<String, Object>>();
        for (var m : msgs) {
            result.add(Map.of(
                    "msg_id", uuidV7(),
                    "role", m.getRole() == null ? "user" : m.getRole(),
                    "content", m.getContent() == null ? "" : m.getContent(),
                    "created_at", m.getTimestamp() == null ? System.currentTimeMillis() : m.getTimestamp()));
        }
        return result;
    }
}
