package com.chua.starter.ai.support.nomifun.service;

import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nomifun 会话契约业务服务。
 *
 * <p>nomifun 前端以 UUIDv7 作为会话 id，但现有 AI 中台 (AIDialog) 使用自增 Long id。
 * 本服务在网关层维护 UUIDv7 ↔ Long 的映射，委托 AIDialogRecordService 做存储。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NomifunConversationService {

    /** 现有对话记录服务 */
    private final AIDialogRecordService dialogRecordService;

    /** UUIDv7 -> DialogId(Long) 映射 */
    private final Map<String, Long> uuidToDialog = new ConcurrentHashMap<>();
    /** DialogId(Long) -> UUIDv7 映射 */
    private final Map<Long, String> dialogToUuid = new ConcurrentHashMap<>();

    /**
     * 生成 canonical lowercase UUIDv7（RFC 9562）。
     */
    private static String uuidV7() {
        var bytes = new byte[16];
        long millis = System.currentTimeMillis();
        bytes[0] = (byte) (millis >>> 40);
        bytes[1] = (byte) (millis >>> 32);
        bytes[2] = (byte) (millis >>> 24);
        bytes[3] = (byte) (millis >>> 16);
        bytes[4] = (byte) (millis >>> 8);
        bytes[5] = (byte) millis;
        ThreadLocalRandom.current().nextBytes(bytes);
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70); // version 7
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // RFC 9562 variant
        char[] hex = "0123456789abcdef".toCharArray();
        var sb = new StringBuilder(36);
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
        uuidToDialog.put(uuid, dialog.getId());
        dialogToUuid.put(dialog.getId(), uuid);
        return toApi(dialog, uuid);
    }

    /**
     * 列出会话（nomifun GET /api/conversations）。
     */
    public List<NomifunConversation> listConversations() {
        var result = new ArrayList<NomifunConversation>();
        for (var e : uuidToDialog.entrySet()) {
            var dialog = dialogRecordService.listDialog().stream()
                    .filter(d -> Objects.equals(d.getId(), e.getValue())).findFirst().orElse(null);
            if (dialog != null) result.add(toApi(dialog, e.getKey()));
        }
        result.sort(Comparator.comparingLong((NomifunConversation c) -> c.updated_at).reversed());
        return result;
    }

    /**
     * 获取会话（nomifun GET /api/conversations/{id}）。
     */
    public NomifunConversation getConversation(String conversationId) {
        Long dialogId = uuidToDialog.get(conversationId);
        if (dialogId == null) return null;
        return dialogRecordService.listDialog().stream()
                .filter(d -> Objects.equals(d.getId(), dialogId))
                .findFirst().map(d -> toApi(d, conversationId)).orElse(null);
    }

    /**
     * 删除会话。
     */
    public void deleteConversation(String conversationId) {
        Long dialogId = uuidToDialog.remove(conversationId);
        if (dialogId != null) {
            dialogToUuid.remove(dialogId);
            dialogRecordService.deleteDialog(dialogId);
        }
    }
}
