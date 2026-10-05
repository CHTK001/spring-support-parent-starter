package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiMemoryMapper;
import com.chua.starter.ai.support.model.AiMemoryDTO;
import com.chua.starter.ai.support.model.AiMemoryMaintenanceDTO;
import com.chua.starter.ai.support.model.MemoryTuningDTO;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 记忆体存储 —— 记录、管理、自动使用、维护四段能力的落库层。
 *
 * <p><b>记录</b>：{@link #record} 是唯一写入口，先按内容归一化算出去重指纹，命中已有活跃记忆时
 * 合并（重要度取大、强度回补、关键词并集、内容取最新），否则插入新行；归属用户一律由服务端
 * 按当前登录用户盖章，异步线程抽取时随会话归属写入。</p>
 *
 * <p><b>自动使用</b>：{@link #recall} 取该用户的全局记忆与指定会话的会话记忆，按
 * {@code 相关性×0.5 + 重要度×0.3 + 衰减强度×0.2}（置顶额外加权）打分取前 N，
 * 并对命中行回写召回次数、最近使用时间与强度回补。</p>
 *
 * <p><b>维护</b>：{@link #maintain} 按用户执行过期、强度衰减、低分归档与容量淘汰，
 * 置顶记忆不参与衰减与淘汰。返回各动作条数供接口与验收取证。</p>
 *
 * <p>隔离口径：全部出口以 {@code sys_ai_memory_user_id} 为准 —— 本人可读写，管理员可读他人记忆、
 * 不可修改或删除，他人一律 403。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class SysAiMemoryStore {

    /**
     * 记忆表 映射器
     */
    private final SysAiMemoryMapper memoryMapper;

    /**
     * 一天毫秒数，衰减与静默期计算用
     */
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    /**
     * 置顶记忆打分加权
     */
    private static final double PINNED_BONUS = 1.0D;

    /**
     * 命中回补的强度增量
     */
    private static final double REINFORCE_STEP = 0.05D;

    /**
     * 合并同义记忆时的强度回补增量
     */
    private static final double MERGE_REINFORCE_STEP = 0.1D;

    /**
     * 默认重要度
     */
    private static final double DEFAULT_IMPORTANCE = 0.5D;

    /**
     * 默认强度
     */
    private static final double DEFAULT_STRENGTH = 0.6D;

    /**
     * 管理列表单页上限
     */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 构造注入映射器与数据源，启动时确保记忆表存在。
     *
     * @param memoryMapper 记忆 映射器
     * @param dataSource   数据源（用于启动时幂等建表）
     */
    public SysAiMemoryStore(SysAiMemoryMapper memoryMapper, DataSource dataSource) {
        this.memoryMapper = memoryMapper;
        ensureTable(dataSource);
    }

    /**
     * 幂等建表（与 {@code db/init/V1.2__add_ai_memory.sql} 同结构）。
     *
     * <p>迁移链在「已建过表的库」上无法再交付新表：SPI Flyway 按 {@code version} 单列主键记录，
     * 与 {@code V1.0__init_all.sql} 的同版本号冲突；内置迁移器又按带表名前缀的列名读取版本表，
     * 两者互斥导致补丁脚本整体中止。故本表由存储层自证存在，与 {@link SysAiCompanionStore} 同口径。</p>
     *
     * @param dataSource 数据源
     */
    private void ensureTable(DataSource dataSource) {
        String ddl = "CREATE TABLE IF NOT EXISTS `sys_ai_memory` ("
                + "`sys_ai_memory_id` VARCHAR(64) NOT NULL,"
                + "`sys_ai_memory_user_id` VARCHAR(64) NULL,"
                + "`sys_ai_memory_scope` VARCHAR(16) NOT NULL DEFAULT 'global',"
                + "`sys_ai_memory_conversation_id` VARCHAR(64) NULL,"
                + "`sys_ai_memory_kind` VARCHAR(32) NULL,"
                + "`sys_ai_memory_title` VARCHAR(255) NULL,"
                + "`sys_ai_memory_content` TEXT NULL,"
                + "`sys_ai_memory_keywords` TEXT NULL,"
                + "`sys_ai_memory_importance` DOUBLE NULL,"
                + "`sys_ai_memory_strength` DOUBLE NULL,"
                + "`sys_ai_memory_hit_count` INT NOT NULL DEFAULT 0,"
                + "`sys_ai_memory_pinned` TINYINT NULL,"
                + "`sys_ai_memory_status` VARCHAR(16) NOT NULL DEFAULT 'active',"
                + "`sys_ai_memory_source` VARCHAR(32) NULL,"
                + "`sys_ai_memory_dedup_key` VARCHAR(64) NULL,"
                + "`sys_ai_memory_created_at` BIGINT NULL,"
                + "`sys_ai_memory_updated_at` BIGINT NULL,"
                + "`sys_ai_memory_last_used_at` BIGINT NULL,"
                + "`sys_ai_memory_expires_at` BIGINT NULL,"
                + "PRIMARY KEY (`sys_ai_memory_id`),"
                + "KEY `idx_ai_memory_user_status` (`sys_ai_memory_user_id`, `sys_ai_memory_status`),"
                + "KEY `idx_ai_memory_conversation` (`sys_ai_memory_conversation_id`),"
                + "KEY `idx_ai_memory_dedup` (`sys_ai_memory_user_id`, `sys_ai_memory_dedup_key`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (Exception e) {
            log.warn("[MemoryStore] 记忆表建表检查失败（非致命）: {}", e.getMessage());
        }
    }

    /**
     * 记录一条记忆（唯一写入口）。
     *
     * <p>内容空白直接拒绝；{@code userId} 为空时按当前登录用户盖章；命中同指纹的活跃记忆时合并而非新增。</p>
     *
     * @param draft  待落库记忆（内容必填，其余字段可空并按默认值补齐）
     * @param tuning 运行参数（决定是否去重）
     * @return 落库后的记忆（新增或合并后的行）
     */
    public SysAiMemory record(SysAiMemory draft, MemoryTuningDTO tuning) {
        String content = draft.getContent() == null ? "" : draft.getContent().replaceAll("\\s+", " ").trim();
        if (content.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "记忆内容不能为空");
        }
        long now = System.currentTimeMillis();
        draft.setContent(content.length() > 2000 ? content.substring(0, 2000) : content);
        draft.setDedupKey(sha256Hex(normalize(content)));
        if (draft.getUserId() == null || draft.getUserId().isBlank()) {
            draft.setUserId(AiUserContext.requireUserId());
        }
        if (draft.getScope() == null || draft.getScope().isBlank()) {
            draft.setScope(SysAiMemory.SCOPE_GLOBAL);
        }
        if (draft.getStatus() == null || draft.getStatus().isBlank()) {
            draft.setStatus(SysAiMemory.STATUS_ACTIVE);
        }
        if (draft.getSource() == null || draft.getSource().isBlank()) {
            draft.setSource(SysAiMemory.SOURCE_MANUAL);
        }
        if (draft.getImportance() == null) {
            draft.setImportance(defaultImportance(draft.getKind()));
        }
        boolean dedupEnabled = tuning == null || tuning.isDedup();
        SysAiMemory existing = dedupEnabled ? findActiveByDedupKey(draft.getUserId(), draft.getScope(),
                draft.getConversationId(), draft.getDedupKey()) : null;
        if (existing != null) {
            return mergeInto(existing, draft, now);
        }
        if (draft.getStrength() == null) {
            draft.setStrength(DEFAULT_STRENGTH);
        }
        if (draft.getHitCount() == null) {
            draft.setHitCount(0);
        }
        if (draft.getPinned() == null) {
            draft.setPinned(Boolean.FALSE);
        }
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            draft.setTitle(abbreviate(content, 40));
        }
        if (draft.getMemoryId() == null || draft.getMemoryId().isBlank()) {
            draft.setMemoryId(SysAiConversationStore.generateUuidV7());
        }
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        memoryMapper.insert(draft);
        return draft;
    }

    /**
     * 合并到已有记忆：内容/标题/关键词取新值，重要度取大，强度回补。
     *
     * @param existing 已有活跃记忆
     * @param draft    本次要写入的记忆
     * @param now      统一时间戳
     * @return 更新后的已有记忆
     */
    private SysAiMemory mergeInto(SysAiMemory existing, SysAiMemory draft, long now) {
        existing.setContent(draft.getContent());
        if (draft.getTitle() != null && !draft.getTitle().isBlank()) {
            existing.setTitle(draft.getTitle());
        }
        existing.setKeywords(union(existing.getKeywords(), draft.getKeywords()));
        if (draft.getKind() != null && !draft.getKind().isBlank()) {
            existing.setKind(draft.getKind());
        }
        double oldImportance = existing.getImportance() == null ? DEFAULT_IMPORTANCE : existing.getImportance();
        double newImportance = draft.getImportance() == null ? oldImportance : draft.getImportance();
        existing.setImportance(Math.min(1.0D, Math.max(oldImportance, newImportance)));
        double oldStrength = existing.getStrength() == null ? DEFAULT_STRENGTH : existing.getStrength();
        existing.setStrength(Math.min(1.0D, oldStrength + MERGE_REINFORCE_STEP));
        existing.setUpdatedAt(now);
        memoryMapper.updateById(existing);
        return existing;
    }

    /**
     * 按去重指纹查活跃记忆（同用户、同作用域、同会话）。
     *
     * @param userId         归属用户
     * @param scope          作用域
     * @param conversationId 会话 标识（可空）
     * @param dedupKey       去重指纹
     * @return 命中的记忆，无则 {@code null}
     */
    private SysAiMemory findActiveByDedupKey(String userId, String scope, String conversationId, String dedupKey) {
        LambdaQueryWrapper<SysAiMemory> wrapper = new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getStatus, SysAiMemory.STATUS_ACTIVE)
                .eq(SysAiMemory::getDedupKey, dedupKey);
        applyScopeCondition(wrapper, scope, conversationId);
        wrapper.last("LIMIT 1");
        return memoryMapper.selectOne(wrapper);
    }

    /**
     * 作用域条件：会话记忆按会话隔离，全局记忆只按用户匹配。
     *
     * @param wrapper        查询条件
     * @param scope          作用域
     * @param conversationId 会话 标识
     */
    private void applyScopeCondition(LambdaQueryWrapper<SysAiMemory> wrapper, String scope, String conversationId) {
        if (SysAiMemory.SCOPE_CONVERSATION.equals(scope)) {
            wrapper.eq(SysAiMemory::getScope, SysAiMemory.SCOPE_CONVERSATION);
            if (conversationId != null && !conversationId.isBlank()) {
                wrapper.eq(SysAiMemory::getConversationId, conversationId);
            }
        } else {
            wrapper.eq(SysAiMemory::getScope, SysAiMemory.SCOPE_GLOBAL);
        }
    }

    /**
     * 读取记忆并校验读权限（管理员可读他人记忆）。
     *
     * @param memoryId 记忆 标识
     * @return 通过校验的记忆
     */
    public SysAiMemory get(String memoryId) {
        SysAiMemory memory = memoryMapper.selectById(memoryId);
        if (memory == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "记忆不存在: " + memoryId);
        }
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
            return memory;
        }
        if (!userId.equals(memory.getUserId()) && !AiUserContext.canAccessOthersData()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人的记忆");
        }
        return memory;
    }

    /**
     * 读取记忆并校验写权限（只认归属人，只读账号一律拒绝）。
     *
     * @param memoryId 记忆 标识
     * @return 通过校验的记忆
     */
    public SysAiMemory requireWritable(String memoryId) {
        SysAiMemory memory = get(memoryId);
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        String userId = AiUserContext.currentUserId();
        if (userId != null && !userId.equals(memory.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "不可修改或删除他人的记忆");
        }
        return memory;
    }

    /**
     * 管理列表：按归属过滤，可选作用域/会话/关键词筛选，最近更新的在前。
     *
     * @param scope          作用域筛选，可空
     * @param conversationId 会话 标识筛选，可空
     * @param keyword        内容关键词筛选，可空
     * @param status         状态筛选，可空（空=仅活跃）
     * @param limit          条数上限
     * @return 记忆列表
     */
    public List<SysAiMemory> list(String scope, String conversationId, String keyword, String status, int limit) {
        int size = limit <= 0 ? 50 : Math.min(limit, MAX_PAGE_SIZE);
        LambdaQueryWrapper<SysAiMemory> wrapper = new LambdaQueryWrapper<>();
        if (true) {
            wrapper.eq(SysAiMemory::getUserId, AiUserContext.requireUserId());
        }
        if (scope != null && !scope.isBlank()) {
            wrapper.eq(SysAiMemory::getScope, scope.trim());
        }
        if (conversationId != null && !conversationId.isBlank()) {
            wrapper.and(w -> w.eq(SysAiMemory::getConversationId, conversationId.trim())
                    .or().eq(SysAiMemory::getScope, SysAiMemory.SCOPE_GLOBAL));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysAiMemory::getContent, kw).or().like(SysAiMemory::getTitle, kw));
        }
        wrapper.eq(SysAiMemory::getStatus, status == null || status.isBlank() ? SysAiMemory.STATUS_ACTIVE : status.trim());
        wrapper.orderByDesc(SysAiMemory::getPinned).orderByDesc(SysAiMemory::getUpdatedAt);
        wrapper.last("LIMIT " + size);
        return memoryMapper.selectList(wrapper);
    }

    /**
     * 部分更新记忆（PATCH 语义，仅覆盖非空字段）。
     *
     * @param memoryId 记忆 标识
     * @param patch    更新内容
     * @return 更新后的记忆
     */
    public SysAiMemory update(String memoryId, SysAiMemory patch) {
        SysAiMemory current = requireWritable(memoryId);
        boolean contentChanged = false;
        if (patch.getContent() != null && !patch.getContent().isBlank()) {
            String content = patch.getContent().replaceAll("\\s+", " ").trim();
            if (content.length() > 2000) {
                content = content.substring(0, 2000);
            }
            contentChanged = !content.equals(current.getContent());
            current.setContent(content);
        }
        if (patch.getTitle() != null && !patch.getTitle().isBlank()) {
            current.setTitle(patch.getTitle());
        }
        if (patch.getKeywords() != null) {
            current.setKeywords(patch.getKeywords());
        }
        if (patch.getKind() != null && !patch.getKind().isBlank()) {
            current.setKind(patch.getKind());
        }
        if (patch.getImportance() != null) {
            current.setImportance(clamp01(patch.getImportance()));
        }
        if (patch.getPinned() != null) {
            current.setPinned(patch.getPinned());
        }
        if (patch.getStatus() != null && !patch.getStatus().isBlank()) {
            current.setStatus(patch.getStatus().trim());
        }
        if (patch.getExpiresAt() != null) {
            current.setExpiresAt(patch.getExpiresAt());
        }
        if (patch.getScope() != null && !patch.getScope().isBlank()) {
            current.setScope(patch.getScope().trim());
            if (SysAiMemory.SCOPE_GLOBAL.equals(current.getScope())) {
                current.setConversationId(null);
            }
        }
        if (contentChanged) {
            current.setDedupKey(sha256Hex(current.getContent()));
        }
        current.setUpdatedAt(System.currentTimeMillis());
        memoryMapper.updateById(current);
        return current;
    }

    /**
     * 删除记忆（仅归属人可删）。
     *
     * @param memoryId 记忆 标识
     */
    public void delete(String memoryId) {
        requireWritable(memoryId);
        memoryMapper.deleteById(memoryId);
    }

    /**
     * 删除某会话名下的全部会话级记忆（会话被删除时连带清理，全局记忆不动）。
     *
     * @param conversationId 会话 标识
     * @return 删除条数
     */
    public int deleteByConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        String userId = AiUserContext.requireUserId();
        LambdaQueryWrapper<SysAiMemory> wrapper = new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getScope, SysAiMemory.SCOPE_CONVERSATION)
                .eq(SysAiMemory::getConversationId, conversationId);
        return memoryMapper.delete(wrapper);
    }

    /**
     * 统计某用户当前活跃记忆条数（容量上限判定用）。
     *
     * @param userId 归属用户
     * @return 活跃条数
     */
    public int countActive(String userId) {
        Long count = memoryMapper.selectCount(new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getStatus, SysAiMemory.STATUS_ACTIVE));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 召回：取该用户可见的活跃记忆并按分数选出前 N，同时回写召回次数与强度。
     *
     * @param userId         归属用户
     * @param conversationId 当前会话 标识（可空，空则只召回全局记忆）
     * @param query          本轮用户输入，用于相关性打分（可空）
     * @param tuning         运行参数
     * @param reinforceHeld  是否回写召回次数与强度（试算时传 {@code false}）
     * @return 按分数倒序的记忆（{@code score} 已回填）
     */
    public List<AiMemoryDTO> recall(String userId, String conversationId, String query,
                                    MemoryTuningDTO tuning, boolean reinforceHeld) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<SysAiMemory> candidates = selectRecallable(userId, conversationId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        Set<String> queryGrams = bigrams(normalize(query));
        List<AiMemoryDTO> scored = new ArrayList<>(candidates.size());
        for (SysAiMemory memory : candidates) {
            double score = score(memory, queryGrams, query, tuning, now);
            if (score < tuning.getMinScore() && !Boolean.TRUE.equals(memory.getPinned())) {
                continue;
            }
            AiMemoryDTO dto = toDTO(memory);
            dto.setScore(round4(score));
            scored.add(dto);
        }
        scored.sort(Comparator.comparingDouble((AiMemoryDTO d) -> d.getScore() == null ? 0D : d.getScore()).reversed());
        int limit = Math.max(1, tuning.getRecallLimit());
        List<AiMemoryDTO> chosen = scored.size() > limit ? new ArrayList<>(scored.subList(0, limit)) : scored;
        if (reinforceHeld) {
            reinforce(chosen);
        }
        return chosen;
    }

    /**
     * 取可召回记忆：该用户的全部活跃全局记忆 + 指定会话的活跃会话记忆。
     *
     * @param userId         归属用户
     * @param conversationId 会话 标识（可空）
     * @return 候选记忆
     */
    private List<SysAiMemory> selectRecallable(String userId, String conversationId) {
        LambdaQueryWrapper<SysAiMemory> wrapper = new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getStatus, SysAiMemory.STATUS_ACTIVE)
                .and(w -> {
                    w.eq(SysAiMemory::getScope, SysAiMemory.SCOPE_GLOBAL);
                    if (conversationId != null && !conversationId.isBlank()) {
                        w.or(o -> o.eq(SysAiMemory::getScope, SysAiMemory.SCOPE_CONVERSATION)
                                .eq(SysAiMemory::getConversationId, conversationId));
                    }
                });
        return memoryMapper.selectList(wrapper);
    }

    /**
     * 统计可召回的活跃记忆条数（与 {@link #selectRecallable} 同口径，不受调用方角色影响）。
     *
     * @param userId         归属用户
     * @param conversationId 会话 标识（可空）
     * @return 候选池条数
     */
    public int countRecallable(String userId, String conversationId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        LambdaQueryWrapper<SysAiMemory> wrapper = new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getStatus, SysAiMemory.STATUS_ACTIVE)
                .and(w -> {
                    w.eq(SysAiMemory::getScope, SysAiMemory.SCOPE_GLOBAL);
                    if (conversationId != null && !conversationId.isBlank()) {
                        w.or(o -> o.eq(SysAiMemory::getScope, SysAiMemory.SCOPE_CONVERSATION)
                                .eq(SysAiMemory::getConversationId, conversationId));
                    }
                });
        Long count = memoryMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 打分：{@code 相关性×0.5 + 重要度×0.3 + 衰减强度×0.2}，置顶额外加权。
     *
     * @param memory     记忆
     * @param queryGrams 查询字符二元组集合（预计算）
     * @param query      查询原文（关键词命中判定用）
     * @param tuning     运行参数
     * @param now        统一时间戳
     * @return 分数
     */
    private double score(SysAiMemory memory, Set<String> queryGrams, String query, MemoryTuningDTO tuning, long now) {
        double relevance = relevance(memory, queryGrams, query);
        double importance = memory.getImportance() == null ? DEFAULT_IMPORTANCE : clamp01(memory.getImportance());
        double strength = decayedStrength(memory, tuning, now);
        double score = relevance * 0.5D + importance * 0.3D + strength * 0.2D;
        double hits = memory.getHitCount() == null ? 0D : memory.getHitCount().doubleValue();
        score += Math.min(0.1D, Math.log1p(hits) * 0.02D);
        if (Boolean.TRUE.equals(memory.getPinned())) {
            score += PINNED_BONUS;
        }
        return score;
    }

    /**
     * 相关性：字符二元组重合度（对中英文 alike 有效），关键词字面命中额外加权。
     *
     * @param memory     记忆
     * @param queryGrams 查询字符二元组集合
     * @param query      查询原文
     * @return 0-1 的相关性，无查询时返回中性值 0.5
     */
    private double relevance(SysAiMemory memory, Set<String> queryGrams, String query) {
        if (queryGrams.isEmpty()) {
            return 0.5D;
        }
        String text = normalize(memory.getContent() + " " + nullToEmpty(memory.getTitle()) + " "
                + nullToEmpty(memory.getKeywords()));
        Set<String> textGrams = bigrams(text);
        int shared = 0;
        for (String gram : queryGrams) {
            if (textGrams.contains(gram)) {
                shared++;
            }
        }
        double overlap = (double) shared / queryGrams.size();
        double bonus = 0D;
        String haystack = memory.getContent() + " " + nullToEmpty(memory.getKeywords());
        for (String keyword : splitKeywords(memory.getKeywords())) {
            if (!keyword.isBlank() && query != null && query.toLowerCase(Locale.ROOT).contains(keyword)) {
                bonus += 0.15D;
            }
        }
        return clamp01(overlap + Math.min(0.3D, bonus));
    }

    /**
     * 衰减后的强度：{@code strength × 0.5^(距最近使用的天数 / 半衰期)}，置顶不衰减。
     *
     * @param memory 记忆
     * @param tuning 运行参数
     * @param now    统一时间戳
     * @return 0-1 的有效强度
     */
    private double decayedStrength(SysAiMemory memory, MemoryTuningDTO tuning, long now) {
        double strength = memory.getStrength() == null ? DEFAULT_STRENGTH : clamp01(memory.getStrength());
        if (Boolean.TRUE.equals(memory.getPinned()) || tuning.getHalfLifeDays() <= 0D) {
            return strength;
        }
        Long anchor = memory.getLastUsedAt() != null ? memory.getLastUsedAt() : memory.getCreatedAt();
        if (anchor == null || anchor <= 0L || anchor >= now) {
            return strength;
        }
        double days = (now - anchor) / (double) DAY_MILLIS;
        return clamp01(strength * Math.pow(0.5D, days / tuning.getHalfLifeDays()));
    }

    /**
     * 召回命中后的强化：次数 +1、刷新最近使用时间、强度小幅回补。
     *
     * @param chosen 本轮实际注入的记忆
     */
    private void reinforce(List<AiMemoryDTO> chosen) {
        long now = System.currentTimeMillis();
        for (AiMemoryDTO dto : chosen) {
            try {
                SysAiMemory memory = memoryMapper.selectById(dto.getMemoryId());
                if (memory == null) {
                    continue;
                }
                int hits = memory.getHitCount() == null ? 0 : memory.getHitCount();
                memory.setHitCount(hits + 1);
                memory.setLastUsedAt(now);
                double strength = memory.getStrength() == null ? DEFAULT_STRENGTH : memory.getStrength();
                memory.setStrength(Math.min(1.0D, strength + REINFORCE_STEP));
                memoryMapper.updateById(memory);
            } catch (Exception e) {
                // 强化失败只影响统计，不影响本轮注入
                log.warn("[Memory] 召回强化失败 memoryId={}: {}", dto.getMemoryId(), e.getMessage());
            }
        }
    }

    /**
     * 维护单个用户的记忆库：过期 → 衰减落库 → 去重合并 → 低分归档 → 容量淘汰。
     *
     * @param userId 归属用户
     * @param tuning 运行参数
     * @return 各动作条数
     */
    public AiMemoryMaintenanceDTO maintain(String userId, MemoryTuningDTO tuning) {
        AiMemoryMaintenanceDTO report = new AiMemoryMaintenanceDTO();
        report.setExecutedAt(System.currentTimeMillis());
        if (userId == null || userId.isBlank()) {
            return report;
        }
        long now = System.currentTimeMillis();
        List<SysAiMemory> all = memoryMapper.selectList(new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId));
        report.setScanned(all.size());
        int expired = 0;
        int decayed = 0;
        int archived = 0;
        List<SysAiMemory> active = new ArrayList<>();
        for (SysAiMemory memory : all) {
            if (SysAiMemory.STATUS_ACTIVE.equals(memory.getStatus())) {
                active.add(memory);
            }
        }
        for (SysAiMemory memory : active) {
            if (memory.getExpiresAt() != null && memory.getExpiresAt() > 0 && memory.getExpiresAt() < now) {
                memory.setStatus(SysAiMemory.STATUS_EXPIRED);
                memory.setUpdatedAt(now);
                memoryMapper.updateById(memory);
                expired++;
                continue;
            }
            if (Boolean.TRUE.equals(memory.getPinned())) {
                continue;
            }
            double decayedStrength = decayedStrength(memory, tuning, now);
            double stored = memory.getStrength() == null ? DEFAULT_STRENGTH : memory.getStrength();
            if (Math.abs(stored - decayedStrength) > 0.0001D) {
                memory.setStrength(round4(decayedStrength));
                memoryMapper.updateById(memory);
                decayed++;
            }
            Long anchor = memory.getLastUsedAt() != null ? memory.getLastUsedAt() : memory.getCreatedAt();
            boolean quiet = anchor == null || (now - anchor) / (double) DAY_MILLIS >= tuning.getQuietDays();
            if (decayedStrength < tuning.getArchiveThreshold() && quiet) {
                memory.setStatus(SysAiMemory.STATUS_ARCHIVED);
                memory.setUpdatedAt(now);
                memoryMapper.updateById(memory);
                archived++;
            }
        }
        report.setExpired(expired);
        report.setDecayed(decayed);
        report.setArchived(archived);
        report.setMerged(tuning.isDedup() ? mergeDuplicates(userId) : 0);
        report.setTrimmed(trimToCapacity(userId, tuning.getMaxPerUser()));
        return report;
    }

    /**
     * 去重合并：同指纹的活跃记忆只留分数最高的一条，其余归档。
     *
     * @param userId 归属用户
     * @return 被合并（归档）的条数
     */
    private int mergeDuplicates(String userId) {
        List<SysAiMemory> active = memoryMapper.selectList(new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getStatus, SysAiMemory.STATUS_ACTIVE)
                .isNotNull(SysAiMemory::getDedupKey));
        Set<String> seen = new HashSet<>();
        int merged = 0;
        long now = System.currentTimeMillis();
        for (SysAiMemory memory : active) {
            String key = memory.getDedupKey() + "#" + memory.getScope() + "#" + nullToEmpty(memory.getConversationId());
            if (!seen.add(key)) {
                memory.setStatus(SysAiMemory.STATUS_ARCHIVED);
                memory.setUpdatedAt(now);
                memoryMapper.updateById(memory);
                merged++;
            }
        }
        return merged;
    }

    /**
     * 容量淘汰：活跃记忆超出上限时，按分数从低到高淘汰非置顶记忆。
     *
     * @param userId     归属用户
     * @param maxPerUser 上限，{@code <=0} 表示不限制
     * @return 归档条数
     */
    private int trimToCapacity(String userId, int maxPerUser) {
        if (maxPerUser <= 0) {
            return 0;
        }
        MemoryTuningDTO tuning = new MemoryTuningDTO();
        List<SysAiMemory> active = memoryMapper.selectList(new LambdaQueryWrapper<SysAiMemory>()
                .eq(SysAiMemory::getUserId, userId)
                .eq(SysAiMemory::getStatus, SysAiMemory.STATUS_ACTIVE));
        List<SysAiMemory> droppable = new ArrayList<>();
        for (SysAiMemory memory : active) {
            if (!Boolean.TRUE.equals(memory.getPinned())) {
                droppable.add(memory);
            }
        }
        int overflow = active.size() - maxPerUser;
        if (overflow <= 0 || droppable.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        droppable.sort(Comparator.comparingDouble(m -> score(m, Set.of(), null, tuning, now)));
        int trimmed = Math.min(overflow, droppable.size());
        for (int i = 0; i < trimmed; i++) {
            SysAiMemory memory = droppable.get(i);
            memory.setStatus(SysAiMemory.STATUS_ARCHIVED);
            memory.setUpdatedAt(now);
            memoryMapper.updateById(memory);
        }
        log.info("[Memory] 容量淘汰: user={} 活跃={} 上限={} 归档={}", userId, active.size(), maxPerUser, trimmed);
        return trimmed;
    }

    /**
     * 列出全部有记忆的用户（后台维护任务遍历用）。
     *
     * @return 用户标识列表
     */
    public List<String> listOwners() {
        List<SysAiMemory> rows = memoryMapper.selectList(new LambdaQueryWrapper<SysAiMemory>()
                .select(SysAiMemory::getUserId)
                .groupBy(SysAiMemory::getUserId));
        List<String> owners = new ArrayList<>(rows.size());
        for (SysAiMemory row : rows) {
            if (row.getUserId() != null && !row.getUserId().isBlank()) {
                owners.add(row.getUserId());
            }
        }
        return owners;
    }

    /**
     * 实体转出参。
     *
     * @param memory 记忆实体
     * @return 记忆 记忆体条目
     */
    public AiMemoryDTO toDTO(SysAiMemory memory) {
        AiMemoryDTO dto = new AiMemoryDTO();
        dto.setMemoryId(memory.getMemoryId());
        dto.setUserId(memory.getUserId());
        dto.setScope(memory.getScope());
        dto.setConversationId(memory.getConversationId());
        dto.setKind(memory.getKind());
        dto.setTitle(memory.getTitle());
        dto.setContent(memory.getContent());
        dto.setKeywords(splitKeywords(memory.getKeywords()));
        dto.setImportance(memory.getImportance());
        dto.setStrength(memory.getStrength());
        dto.setHitCount(memory.getHitCount());
        dto.setPinned(memory.getPinned());
        dto.setStatus(memory.getStatus());
        dto.setSource(memory.getSource());
        dto.setCreatedAt(memory.getCreatedAt());
        dto.setUpdatedAt(memory.getUpdatedAt());
        dto.setLastUsedAt(memory.getLastUsedAt());
        dto.setExpiresAt(memory.getExpiresAt());
        return dto;
    }

    /**
     * 关键词并集（去重、忽略空值）。
     *
     * @param left  已有关键词串
     * @param right 新增关键词串
     * @return 合并后的关键词串
     */
    private String union(String left, String right) {
        Set<String> merged = new LinkedHashSet<>(splitKeywords(left));
        merged.addAll(splitKeywords(right));
        return joinKeywords(new ArrayList<>(merged));
    }

    /**
     * 关键词串转列表。
     *
     * @param keywords 逗号分隔关键词串，可空
     * @return 关键词列表
     */
    public static List<String> splitKeywords(String keywords) {
        List<String> list = new ArrayList<>();
        if (keywords == null || keywords.isBlank()) {
            return list;
        }
        for (String part : keywords.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * 关键词列表转存储串。
     *
     * @param keywords 关键词列表，可空
     * @return 逗号分隔串，空列表返回 {@code null}
     */
    public static String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()) {
                unique.add(keyword.trim().toLowerCase(Locale.ROOT));
            }
        }
        return unique.isEmpty() ? null : String.join(",", unique);
    }

    /**
     * 归一化文本：折叠空白、去首尾空格、转小写，供指纹与相关性计算共用。
     *
     * @param text 原文，可空
     * @return 归一化文本
     */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 字符二元组集合：单字符文本退化为单字符集合。
     *
     * @param normalized 归一化文本
     * @return 二元组集合
     */
    private static Set<String> bigrams(String normalized) {
        Set<String> grams = new LinkedHashSet<>();
        if (normalized == null || normalized.isEmpty()) {
            return grams;
        }
        if (normalized.length() == 1) {
            grams.add(normalized);
            return grams;
        }
        for (int i = 0; i + 1 < normalized.length(); i++) {
            grams.add(normalized.substring(i, i + 2));
        }
        return grams;
    }

    /**
     * SHA-256 十六进制前 32 位，作为内容去重指纹。
     *
     * @param normalized 归一化后的内容
     * @return 指纹，算法不可用时退化为内容哈希串
     */
    private static String sha256Hex(String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16 && i < hash.length; i++) {
                sb.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    /**
     * 种类默认重要度：指令与偏好更值得长期保留。
     *
     * @param kind 种类，可空
     * @return 默认重要度
     */
    private static double defaultImportance(String kind) {
        if ("instruction".equals(kind)) {
            return 0.9D;
        }
        if ("preference".equals(kind)) {
            return 0.75D;
        }
        if ("fact".equals(kind)) {
            return 0.65D;
        }
        return DEFAULT_IMPORTANCE;
    }

    /**
     * 截断为摘要标题。
     *
     * @param text      原文
     * @param maxLength 最大长度
     * @return 摘要标题
     */
    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }

    /**
     * 限制到 0-1 区间。
     *
     * @param value 输入值
     * @return 0-1 区间内的值
     */
    private static double clamp01(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    /**
     * 保留 4 位小数。
     *
     * @param value 输入值
     * @return 四舍五入后的值
     */
    private static double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    /**
     * 空串兜底。
     *
     * @param text 原文
     * @return 非空文本
     */
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
