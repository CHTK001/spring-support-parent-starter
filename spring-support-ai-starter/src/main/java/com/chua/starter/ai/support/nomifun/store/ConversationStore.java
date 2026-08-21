package com.chua.starter.ai.support.nomifun.store;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话与消息内存存储服务。
 *
 * <p>作为前端契约的会话状态层，提供会话 CRUD、消息追加与分页查询。
 * 当前为进程内内存实现（重启丢失），满足前端对接与联调；后续可替换为持久化存储。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class ConversationStore {

    /**
     * 会话表：conversationId → 会话
     */
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    /**
     * 消息表：conversationId → 消息列表
     */
    private final Map<String, List<StoredMessage>> messages = new ConcurrentHashMap<>();

    /**
     * 生成 UUID 字符串（前端要求 UUID 格式主键）。
     *
     * @return UUID 字符串
     */
    public String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 创建会话。
     *
     * @param conversation 会话（已填充主键与时间戳）
     * @return 创建的会话
     */
    public Conversation create(Conversation conversation) {
        conversations.put(conversation.getConversationId(), conversation);
        messages.putIfAbsent(conversation.getConversationId(), new CopyOnWriteArrayList<>());
        return conversation;
    }

    /**
     * 获取会话（不存在抛 NOT_FOUND）。
     *
     * @param conversationId 会话 ID
     * @return 会话
     */
    public Conversation get(String conversationId) {
        Conversation conversation = conversations.get(conversationId);
        if (conversation == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在: " + conversationId);
        }
        return conversation;
    }

    /**
     * 判断会话是否存在。
     *
     * @param conversationId 会话 ID
     * @return 存在返回 true
     */
    public boolean exists(String conversationId) {
        return conversations.containsKey(conversationId);
    }

    /**
     * 删除会话及其消息。
     *
     * @param conversationId 会话 ID
     */
    public void delete(String conversationId) {
        conversations.remove(conversationId);
        messages.remove(conversationId);
    }

    /**
     * 部分更新会话（PATCH 语义）。
     *
     * <p>仅覆盖 {@code patch} 中非 null 的顶层字段；{@code extra} 采用合并语义（追加/覆盖同名键，
     * 而非整体替换）。</p>
     *
     * @param conversationId 会话 ID
     * @param patch          部分更新内容（非 null 字段生效）
     * @return 更新后的会话
     */
    public Conversation patch(String conversationId, Conversation patch) {
        Conversation current = get(conversationId);
        if (patch.getName() != null) {
            current.setName(patch.getName());
        }
        if (patch.getModel() != null) {
            current.setModel(patch.getModel());
        }
        if (patch.getExecutionTemplateId() != null) {
            current.setExecutionTemplateId(patch.getExecutionTemplateId());
        }
        if (patch.getPinnedAt() != null || patch.isPinned()) {
            current.setPinned(patch.isPinned());
            current.setPinnedAt(patch.getPinnedAt() != null ? patch.getPinnedAt() : (patch.isPinned() ? System.currentTimeMillis() : null));
        }
        if (patch.getExtra() != null) {
            Map<String, Object> extra = current.getExtra();
            if (extra == null) {
                extra = new ConcurrentHashMap<>();
                current.setExtra(extra);
            }
            extra.putAll(patch.getExtra());
        }
        current.setModifiedAt(System.currentTimeMillis());
        return current;
    }

    /**
     * 分页列出会话（按创建时间倒序，最近在前）。
     *
     * @param limit 返回条数（默认 50）
     * @return 会话列表
     */
    public List<Conversation> list(int limit) {
        int size = limit <= 0 ? 50 : limit;
        List<Conversation> all = new ArrayList<>(conversations.values());
        all.sort(Comparator.comparing(Conversation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return all.stream().limit(size).toList();
    }

    /**
     * 追加消息到会话。
     *
     * @param conversationId 会话 ID
     * @param message        消息（已填充主键与时间戳）
     * @return 追加后的消息
     */
    public StoredMessage addMessage(String conversationId, StoredMessage message) {
        List<StoredMessage> list = messages.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        list.add(message);
        return message;
    }

    /**
     * 分页列出消息（按创建时间升序）。
     *
     * @param conversationId 会话 ID
     * @param page          页号（从 1 开始）
     * @param pageSize      每页条数
     * @return 消息列表
     */
    public List<StoredMessage> listMessages(String conversationId, int page, int pageSize) {
        List<StoredMessage> all = messages.getOrDefault(conversationId, new CopyOnWriteArrayList<>());
        all.sort(Comparator.comparing(StoredMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        int size = pageSize <= 0 ? 50 : pageSize;
        int from = (page - 1) * size;
        if (from >= all.size()) {
            return new ArrayList<>();
        }
        int to = Math.min(from + size, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    /**
     * 获取单条消息（不存在抛 NOT_FOUND）。
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     * @return 消息
     */
    public StoredMessage getMessage(String conversationId, String messageId) {
        List<StoredMessage> list = messages.getOrDefault(conversationId, new CopyOnWriteArrayList<>());
        return list.stream()
                .filter(m -> messageId.equals(m.getMessageId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "消息不存在: " + messageId));
    }

    /**
     * 获取会话全部消息（供对话历史拼装）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表（升序）
     */
    public List<StoredMessage> allMessages(String conversationId) {
        return listMessages(conversationId, 1, Integer.MAX_VALUE);
    }

    /**
     * 清空指定会话的全部消息（保留会话行，不触碰记忆库）。
     *
     * @param conversationId 会话 ID
     */
    public void clearMessages(String conversationId) {
        messages.remove(conversationId);
    }

    /**
     * 删除单条消息。
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     */
    public void removeMessage(String conversationId, String messageId) {
        List<StoredMessage> list = messages.get(conversationId);
        if (list != null) {
            list.removeIf(m -> messageId.equals(m.getMessageId()));
        }
    }

    /**
     * 截断指定消息及其之后的全部消息（用于编辑重提交场景）。
     *
     * @param conversationId 会话 ID
     * @param messageId      锚点消息 ID
     */
    public void truncateAfter(String conversationId, String messageId) {
        List<StoredMessage> list = messages.get(conversationId);
        if (list == null || list.isEmpty()) {
            return;
        }
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (messageId.equals(list.get(i).getMessageId())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            list.subList(idx, list.size()).clear();
        }
    }
}
