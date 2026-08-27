package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiConversationMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 会话与消息持久化存储服务。
 *
 * <p>作为前端契约的会话状态层，提供会话 CRUD、消息追加与分页查询。
 * 基于 MyBatis-Plus 持久化到 H2（或 MySQL）数据库：会话存 {@code sys_nomifun_conversation} 表，
 * 消息存 {@code sys_nomifun_message} 表。JSON 字段（model/extra/content）通过
 * {@link com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} 序列化存储。</p>
 *
 * <p>时间戳与主键由后端生成：主键为 UUIDv7 字符串，时间戳为 epoch 毫秒。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class SysAiConversationStore {

    /**
     * 会话表 Mapper：提供会话增删改查
     */
    private final SysAiConversationMapper conversationMapper;

    /**
     * 消息表 Mapper：提供消息增删改查
     */
    private final SysAiConversationMessageMapper messageMapper;

    /**
     * 分页单页最大条数，防止前端传入超大 page_size 拖垮查询
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 列表返回最大条数，防止会话无限增长
     */
    private static final int MAX_LIST_LIMIT = 500;

    /**
     * 构造注入 MyBatis-Plus Mapper。
     *
     * @param conversationMapper 会话 Mapper
     * @param messageMapper      消息 Mapper
     */
    public SysAiConversationStore(SysAiConversationMapper conversationMapper, SysAiConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 生成 UUID 字符串（前端要求 UUID 格式主键，UUIDv7 含时间前缀）。
     *
     * @return 36 位 UUIDv7 字符串
     */
    public String newId() {
        return generateUuidV7();
    }

    /**
     * 生成 UUIDv7：前 48 位为毫秒时间戳，随后填充版本/变体位与随机位。
     *
     * <p>UUIDv7 格式：{@code xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx}</p>
     * <ul>
     *   <li>前48位：毫秒级时间戳（确保时间有序）</li>
     *   <li>第13位：版本号（固定为7）</li>
     *   <li>第17位：变体（固定为10xx，即8/9/a/b）</li>
     * </ul>
     *
     * @return 36 位 UUIDv7 字符串
     */
    private String generateUuidV7() {
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
     * 创建会话并落库。
     *
     * @param conversation 会话（须已填充主键与时间戳）
     * @return 创建的会话
     */
    public SysAiConversation create(SysAiConversation conversation) {
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 获取会话（不存在抛 NOT_FOUND）。
     *
     * @param conversationId 会话 ID
     * @return 会话
     */
    public SysAiConversation get(String conversationId) {
        SysAiConversation conversation = conversationMapper.selectById(conversationId);
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
        return conversationMapper.selectById(conversationId) != null;
    }

    /**
     * 删除会话及其全部消息。
     *
     * @param conversationId 会话 ID
     */
    public void delete(String conversationId) {
        conversationMapper.deleteById(conversationId);
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);
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
    public SysAiConversation patch(String conversationId, SysAiConversation patch) {
        SysAiConversation current = get(conversationId);
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
                extra = new java.util.HashMap<>();
                current.setExtra(extra);
            }
            extra.putAll(patch.getExtra());
        }
        current.setModifiedAt(System.currentTimeMillis());
        conversationMapper.updateById(current);
        return current;
    }

    /**
     * 分页列出会话（按创建时间倒序，最近在前）。
     *
     * @param limit 返回条数（默认 50，上限 500）
     * @return 会话列表
     */
    public List<SysAiConversation> list(int limit) {
        int size = limit <= 0 ? 50 : Math.min(limit, MAX_LIST_LIMIT);
        LambdaQueryWrapper<SysAiConversation> wrapper = new LambdaQueryWrapper<SysAiConversation>()
                .orderByDesc(SysAiConversation::getCreatedAt)
                .last("LIMIT " + size);
        return conversationMapper.selectList(wrapper);
    }

    /**
     * 追加消息并落库。
     *
     * @param conversationId 会话 ID
     * @param message        消息（已填充主键与时间戳）
     * @return 追加后的消息
     */
    public SysAiConversationMessage addMessage(String conversationId, SysAiConversationMessage message) {
        messageMapper.insert(message);
        return message;
    }

    /**
     * 分页列出消息（按创建时间升序）。
     *
     * @param conversationId 会话 ID
     * @param page          页号（从 1 开始）
     * @param pageSize      每页条数（默认 50，上限 200）
     * @return 消息列表
     */
    public List<SysAiConversationMessage> listMessages(String conversationId, int page, int pageSize) {
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, MAX_PAGE_SIZE);
        int from = Math.max(page, 0);
        int offset = (from <= 0 ? 1 : from - 1) * size;
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .orderByAsc(SysAiConversationMessage::getCreatedAt)
                .last("LIMIT " + offset + "," + size);
        return messageMapper.selectList(wrapper);
    }

    /**
     * 获取单条消息（不存在抛 NOT_FOUND）。
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     * @return 消息
     */
    public SysAiConversationMessage getMessage(String conversationId, String messageId) {
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .eq(SysAiConversationMessage::getMessageId, messageId)
                .last("LIMIT 1");
        SysAiConversationMessage message = messageMapper.selectOne(wrapper);
        if (message == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "消息不存在: " + messageId);
        }
        return message;
    }

    /**
     * 获取会话全部消息（供对话历史拼装）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表（升序）
     */
    public List<SysAiConversationMessage> allMessages(String conversationId) {
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .orderByAsc(SysAiConversationMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    /**
     * 清空指定会话的全部消息（保留会话行，不触碰记忆库）。
     *
     * @param conversationId 会话 ID
     */
    public void clearMessages(String conversationId) {
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);
    }

    /**
     * 删除单条消息。
     *
     * @param conversationId 会话 ID
     * @param messageId      消息 ID
     */
    public void removeMessage(String conversationId, String messageId) {
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .eq(SysAiConversationMessage::getMessageId, messageId);
        messageMapper.delete(wrapper);
    }

    /**
     * 截断指定消息及其之后的全部消息（用于编辑重提交场景）。
     *
     * @param conversationId 会话 ID
     * @param messageId      锚点消息 ID
     */
    public void truncateAfter(String conversationId, String messageId) {
        List<SysAiConversationMessage> all = allMessages(conversationId);
        if (all.isEmpty()) {
            return;
        }
        // 找到锚点索引；锚点（含）之后的所有消息一并删除
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (messageId.equals(all.get(i).getMessageId())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            List<String> idsToDelete = new ArrayList<>();
            for (int i = idx; i < all.size(); i++) {
                idsToDelete.add(all.get(i).getMessageId());
            }
            LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                    .eq(SysAiConversationMessage::getConversationId, conversationId)
                    .in(SysAiConversationMessage::getMessageId, idsToDelete);
            messageMapper.delete(wrapper);
        }
    }
}
