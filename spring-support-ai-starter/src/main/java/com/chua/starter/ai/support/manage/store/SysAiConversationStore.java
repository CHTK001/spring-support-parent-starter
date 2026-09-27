package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiConversationMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiConversationMessageMapper;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;

/**
 * 会话与消息持久化存储服务。
 *
 * <p>作为前端契约的会话状态层，提供会话 CRUD、消息追加与分页查询。
 * 基于 MyBatis-Plus 持久化到 H2（或 MySQL）数据库：会话存 {@code sys_nomifun_conversation} 表，
 * 消息存 {@code sys_nomifun_message} 表。JSON 字段（模型/extra/内容）通过
 * {@link com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} 序列化存储。</p>
 *
 * <p>时间戳与主键由后端生成：主键为 UUIDv7 字符串，时间戳为 epoch 毫秒。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class SysAiConversationStore {

    /**
     * 会话表 映射器：提供会话增删改查
     */
    private final SysAiConversationMapper conversationMapper;

    /**
     * 消息表 映射器：提供消息增删改查
     */
    private final SysAiConversationMessageMapper messageMapper;

    /**
     * 分页单页最大条数，防止前端传入超大 page_大小 拖垮查询
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 列表返回最大条数，防止会话无限增长
     */
    private static final int MAX_LIST_LIMIT = 500;

    /**
     * 构造注入 MyBatis-Plus 映射器，启动时自动补列。
     *
     * @param conversationMapper 会话 映射器
     * @param messageMapper      消息 映射器
     * @param dataSource         数据源（用于启动时检查并补列）
     */
    public SysAiConversationStore(SysAiConversationMapper conversationMapper,
                                   SysAiConversationMessageMapper messageMapper,
                                   DataSource dataSource) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        ensureColumns(dataSource);
    }

    /**
     * 启动时检查 sys_AI_conversation 表是否缺少 extra 等列，自动 ALTER TABLE 补上。
     * 这样即使 MySQL 建表时遗漏了新列，重启后也能自动修复。
     *
     * <p>安全豁免：表名、列名和列类型均为本类常量，并经
     * {@link SqlName#checkSimple(String, String)} 校验；该 DDL 不接受任何用户值。</p>
     *
     * @param dataSource 数据源
     */
    private void ensureColumns(DataSource dataSource) {
        String table = SqlName.checkSimple("sys_ai_conversation", "AI 会话表名");
        String[][] neededColumns = {
            {"sys_ai_conversation_extra", "TEXT NULL COMMENT '扩展字段JSON（Map）'"},
            {"sys_ai_conversation_execution_template_id", "VARCHAR(64) NULL COMMENT '执行模板ID'"}
        };
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            log.info("[Store] 数据库 catalog={}, 开始检查列...", catalog);
            DatabaseMetaData meta = conn.getMetaData();
            for (String[] col : neededColumns) {
                boolean found = false;
                try (ResultSet rs = meta.getColumns(catalog, null, table, col[0])) {
                    found = rs.next();
                    if (found) {
                        log.info("[Store] 列 {} 已存在", col[0]);
                    }
                }
                if (!found) {
                    String safeColumn = SqlName.checkSimple(col[0], "AI 会话列名");
                    String sql = "ALTER TABLE `" + table + "` ADD COLUMN `" + safeColumn + "` " + col[1];
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql);
                        log.info("[Store] 已自动补列: {}", col[0]);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Store] 自动补列检查失败（非致命）: {}", e.getMessage());
        }
    }

    /**
     * 生成 UUID 字符串（前端要求 UUID 格式主键，uuidv7 含时间前缀）。
     *
     * @return 36 位 uuidv7 字符串
     */
    public String newId() {
        return generateUuidV7();
    }

    /**
     * 生成 uuidv7：前 48 位为毫秒时间戳，随后填充版本/变体位与随机位。
     *
     * <p>UUIDv7 格式：{@code xxxxxxxx-xxxx-7xxx-xxxx-xxxxxxxxxxxx}</p>
     * <ul>
     *   <li>前48位：毫秒级时间戳（确保时间有序）</li>
     *   <li>第13位：版本号（固定为7）</li>
     *   <li>第17位：变体（固定为10xx，即8/9/a/b）</li>
     * </ul>
     *
     * @return 36 位 uuidv7 字符串
     */
    public static String generateUuidV7() {
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
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                sb.append('-');
            }
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
        if (conversation.getUserId() == null) {
            // 身份由服务端按当前登录用户写入，不接受客户端传值；无身份不落无主数据
            conversation.setUserId(AiUserContext.requireUserId());
        }
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 获取会话（不存在抛 NOT_FOUND）。
     *
     * <p>读出口：管理员可读全量，普通用户只能读本人会话。</p>
     *
     * @param conversationId 会话 标识
     * @return 会话
     */
    public SysAiConversation get(String conversationId) {
        SysAiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "会话不存在: " + conversationId);
        }
        assertReadable(conversation);
        return conversation;
    }

    /**
     * 读出口的存在性校验：会话存在时按归属判定，不存在时放过。
     *
     * <p>记忆出口的 {@code conversation_id} 只是一个筛选维度，而前端会话主键与
     * {@code sys_ai_conversation} 不同源（如数字会话 id），未知 id 只应筛出空集而不是 404；
     * 他人会话仍然 403。</p>
     *
     * @param conversationId 会话 标识
     */
    public void assertReadableIfPresent(String conversationId) {
        SysAiConversation conversation = findIfPresent(conversationId);
        if (conversation != null) {
            assertReadable(conversation);
        }
    }

    /**
     * 按主键查会话，不存在返回 {@code null}（不抛 NOT_FOUND）。
     *
     * @param conversationId 会话 标识
     * @return 会话，不存在时为 {@code null}
     */
    public SysAiConversation findIfPresent(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return conversationMapper.selectById(conversationId);
    }

    /**
     * 写出口的存在性校验：会话存在时按归属与只读判定，不存在时放过。
     *
     * <p>用于按 {@code conversation_id} 批量清理的出口——清理本身已按当前用户收窄，
     * 未知 id 清不出任何行，不该因此拒绝请求。</p>
     *
     * @param conversationId 会话 标识
     */
    public void assertWritableIfPresent(String conversationId) {
        SysAiConversation conversation = findIfPresent(conversationId);
        if (conversation == null) {
            return;
        }
        assertReadable(conversation);
        requireWritable(conversation);
    }

    /**
     * 归属判定：管理员可读全量，普通用户只能读本人会话。
     *
     * @param conversation 会话
     */
    private void assertReadable(SysAiConversation conversation) {
        if (AiUserContext.isAdmin()) {
            return;
        }
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
            // 线程池与回复回调取不到请求身份，属机器流量而非越权
            return;
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人会话");
        }
    }

    /**
     * 写出口校验：只认归属人本身，管理员对他人数据只读，只读账号一律拒绝。
     *
     * @param conversationId 会话 标识
     * @return 校验通过的会话
     */
    public SysAiConversation requireWritable(String conversationId) {
        return requireWritable(get(conversationId));
    }

    /**
     * 写出口校验（已知会话）。
     *
     * @param conversation 会话
     * @return 校验通过的会话
     */
    private SysAiConversation requireWritable(SysAiConversation conversation) {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
            return conversation;
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
        return conversation;
    }

    /**
     * 判断会话是否存在（仅对可读会话为真）。
     *
     * @param conversationId 会话 标识
     * @return 存在返回 true
     */
    public boolean exists(String conversationId) {
        try {
            get(conversationId);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    /**
     * 删除会话及其全部消息。
     *
     * @param conversationId 会话 标识
     */
    public void delete(String conversationId) {
        requireWritable(conversationId);
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
     * @param conversationId 会话 标识
     * @param patch          部分更新内容（非 空 字段生效）
     * @return 更新后的会话
     */
    public SysAiConversation patch(String conversationId, SysAiConversation patch) {
        SysAiConversation current = requireWritable(conversationId);
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
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数（默认 50，上限 500）
     * @return 分页结果（含 total/是否包含more）
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversation> page(int page, int pageSize) {
        long current = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, MAX_LIST_LIMIT);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversation> paging =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        LambdaQueryWrapper<SysAiConversation> wrapper = new LambdaQueryWrapper<SysAiConversation>()
                .orderByDesc(SysAiConversation::getCreatedAt);
 // 管理员读全量，其余仅本人会话；无身份时条件恒不匹配（失败即关闭）
        if (true) {
            wrapper.eq(SysAiConversation::getUserId, AiUserContext.currentUserId());
        }
        return conversationMapper.selectPage(paging, wrapper);
    }

    /**
     * 追加消息并落库。
     *
     * @param conversationId 会话 标识
     * @param message        消息（已填充主键与时间戳）
     * @return 追加后的消息
     */
    public SysAiConversationMessage addMessage(String conversationId, SysAiConversationMessage message) {
        SysAiConversation conversation = requireWritable(conversationId);
 // 归属随所属会话：回复回调在异步线程执行，取不到请求身份
        message.setUserId(conversation.getUserId());
        messageMapper.insert(message);
        return message;
    }

    /**
     * 分页列出消息（按创建时间升序）。
     *
     * @param conversationId 会话 标识
     * @param page          页号（从 1 开始）
     * @param pageSize      每页条数（默认 50，上限 200）
     * @return 分页结果（含 total）
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversationMessage> pageMessages(
            String conversationId, int page, int pageSize) {
        get(conversationId);
        long current = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 50 : Math.min(pageSize, MAX_PAGE_SIZE);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversationMessage> paging =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .orderByAsc(SysAiConversationMessage::getCreatedAt);
        return messageMapper.selectPage(paging, wrapper);
    }

    /**
     * 获取单条消息（不存在抛 NOT_FOUND）。
     *
     * @param conversationId 会话 标识
     * @param messageId      消息 标识
     * @return 消息
     */
    public SysAiConversationMessage getMessage(String conversationId, String messageId) {
        get(conversationId);
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
     * @param conversationId 会话 标识
     * @return 消息列表（升序）
     */
    public List<SysAiConversationMessage> allMessages(String conversationId) {
        get(conversationId);
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .orderByAsc(SysAiConversationMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    /**
     * 更新会话扩展字段，null 表示删除。
     *
     * @param conversationId 会话标识
     * @param key           扩展键
     * @param value         扩展值
     */
    public void patchExtra(String conversationId, String key, Object value) {
        SysAiConversation current = requireWritable(conversationId);
        Map<String, Object> extra = current.getExtra();
        if (extra == null) {
            extra = new java.util.LinkedHashMap<>();
            current.setExtra(extra);
        }
        if (value == null) {
            extra.remove(key);
        } else {
            extra.put(key, value);
        }
        current.setModifiedAt(System.currentTimeMillis());
        conversationMapper.updateById(current);
    }

    /**
     * 更新会话运行时字段，null 表示删除。
     *
     * @param conversationId 会话标识
     * @param key           运行时字段
     * @param value         字段值
     */
    public void patchRuntime(String conversationId, String key, Object value) {
        SysAiConversation current = requireWritable(conversationId);
        Map<String, Object> runtime = current.getRuntime();
        if (runtime == null) {
            runtime = new java.util.LinkedHashMap<>();
            current.setRuntime(runtime);
        }
        if (value == null) {
            runtime.remove(key);
        } else {
            runtime.put(key, value);
        }
        current.setModifiedAt(System.currentTimeMillis());
        conversationMapper.updateById(current);
    }

    /**
     * 按内容关键字分页检索消息。
     *
     * @param keyword  关键字，为空时检索当前用户全部消息
     * @param page     页码
     * @param pageSize 每页条数
     * @return 消息分页
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversationMessage> searchMessages(
            String keyword, int page, int pageSize) {
        String userId = AiUserContext.requireUserId();
        long current = page <= 0 ? 1 : page;
        long size = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversationMessage> paging =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(!AiUserContext.isAdmin(), SysAiConversationMessage::getUserId, userId)
                .like(keyword != null && !keyword.isBlank(), SysAiConversationMessage::getContent, keyword)
                .orderByDesc(SysAiConversationMessage::getCreatedAt);
        return messageMapper.selectPage(paging, wrapper);
    }

    /**
     * 清空指定会话的全部消息（保留会话行，不触碰记忆库）。
     *
     * @param conversationId 会话 标识
     */
    public void clearMessages(String conversationId) {
        requireWritable(conversationId);
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);
    }

    /**
     * 删除单条消息。
     *
     * @param conversationId 会话 标识
     * @param messageId      消息 标识
     */
    public void removeMessage(String conversationId, String messageId) {
        requireWritable(conversationId);
        LambdaQueryWrapper<SysAiConversationMessage> wrapper = new LambdaQueryWrapper<SysAiConversationMessage>()
                .eq(SysAiConversationMessage::getConversationId, conversationId)
                .eq(SysAiConversationMessage::getMessageId, messageId);
        messageMapper.delete(wrapper);
    }

    /**
     * 截断指定消息及其之后的全部消息（用于编辑重提交场景）。
     *
     * @param conversationId 会话 标识
     * @param messageId      锚点消息 标识
     */
    public void truncateAfter(String conversationId, String messageId) {
        requireWritable(conversationId);
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
