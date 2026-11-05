// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.memory.MemoryGlobalSettingProvider;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.store.SysAiMemory;
import com.chua.starter.ai.support.manage.store.SysAiMemoryStore;
import com.chua.starter.ai.support.model.AiMemoryDTO;
import com.chua.starter.ai.support.model.AiMemoryMaintenanceDTO;
import com.chua.starter.ai.support.model.AiMemoryRecallDTO;
import com.chua.starter.ai.support.model.AiMemoryUpsertRequest;
import com.chua.starter.ai.support.model.MemoryCandidateDTO;
import com.chua.starter.ai.support.model.MemoryExtractionContext;
import com.chua.starter.ai.support.model.MemoryTuningDTO;
import com.chua.starter.ai.support.service.SysAiMemoryService;
import com.chua.starter.ai.support.spi.MemoryExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 记忆体服务实现。
 *
 * <p>运行参数全部来自设置分组 {@code memory}（见 {@link MemoryGlobalSettingProvider}），
 * 每次调用现读，改完下一轮对话即生效。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class SysAiMemoryServiceImpl implements SysAiMemoryService {

    /**
     * 记忆体存储层
     */
    private final SysAiMemoryStore memoryStore;

    /**
     * 全局设置读取器
     */
    private final GlobalSettingReader settings;

    /**
     * 记忆抽取 SPI 实现集合（Spring 注入，新增实现注册为 Bean 即生效）
     */
    private final List<MemoryExtractor> extractors;

    /**
     * 构造记忆体服务。
     *
     * @param memoryStore 记忆体存储层
     * @param settings    全局设置读取器
     * @param extractors  记忆抽取实现列表
     */
    public SysAiMemoryServiceImpl(SysAiMemoryStore memoryStore, GlobalSettingReader settings,
                                  List<MemoryExtractor> extractors) {
        this.memoryStore = memoryStore;
        this.settings = settings;
        this.extractors = dedupeByClass(extractors);
    }

    @Override
    /**
     * 记录一条记忆。
     *
     * @param userId  归属用户
     * @param request 写入内容
     * @param source  来源标记
     * @return 落库后的记忆
     */
    public AiMemoryDTO record(String userId, AiMemoryUpsertRequest request, String source) {
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "记忆内容不能为空");
        }
        long now = System.currentTimeMillis();
        SysAiMemory draft = new SysAiMemory();
        draft.setUserId(blankToNull(userId));
        draft.setScope(normalizeScope(request.getScope()));
        draft.setConversationId(resolveConversationId(draft.getScope(), request.getConversationId()));
        draft.setKind(blankToNull(request.getKind()));
        draft.setTitle(blankToNull(request.getTitle()));
        draft.setContent(request.getContent());
        draft.setKeywords(SysAiMemoryStore.joinKeywords(request.getKeywords()));
        draft.setImportance(request.getImportance());
        draft.setPinned(request.getPinned());
        draft.setSource(source == null || source.isBlank() ? SysAiMemory.SOURCE_MANUAL : source);
        draft.setExpiresAt(ttlToExpiresAt(request.getTtlDays(), now));
        return memoryStore.toDTO(memoryStore.record(draft, tuning()));
    }

    @Override
    /**
     * 管理列表。
     *
     * @param scope          作用域筛选
     * @param conversationId 会话 标识筛选
     * @param keyword        关键词筛选
     * @param status         状态筛选
     * @param limit          条数上限
     * @return 记忆列表
     */
    public List<AiMemoryDTO> list(String scope, String conversationId, String keyword, String status, int limit) {
        List<AiMemoryDTO> result = new ArrayList<>();
        for (SysAiMemory memory : memoryStore.list(scope, conversationId, keyword, status, limit)) {
            result.add(memoryStore.toDTO(memory));
        }
        return result;
    }

    @Override
    /**
     * 读取单条记忆。
     *
     * @param memoryId 记忆 标识
     * @return 记忆
     */
    public AiMemoryDTO get(String memoryId) {
        return memoryStore.toDTO(memoryStore.get(memoryId));
    }

    @Override
    /**
     * 更新单条记忆。
     *
     * @param memoryId 记忆 标识
     * @param request  更新内容
     * @return 更新后的记忆
     */
    public AiMemoryDTO update(String memoryId, AiMemoryUpsertRequest request) {
        SysAiMemory patch = new SysAiMemory();
        if (request != null) {
            patch.setContent(request.getContent());
            patch.setTitle(request.getTitle());
            patch.setKind(request.getKind());
            patch.setImportance(request.getImportance());
            patch.setPinned(request.getPinned());
            patch.setStatus(normalizeStatus(request.getStatus()));
            patch.setKeywords(request.getKeywords() == null ? null : SysAiMemoryStore.joinKeywords(request.getKeywords()));
            patch.setScope(blankToNull(request.getScope()));
            patch.setExpiresAt(ttlToExpiresAt(request.getTtlDays(), System.currentTimeMillis()));
        }
        return memoryStore.toDTO(memoryStore.update(memoryId, patch));
    }

    @Override
    /**
     * 删除单条记忆。
     *
     * @param memoryId 记忆 标识
     */
    public void delete(String memoryId) {
        memoryStore.delete(memoryId);
    }

    @Override
    /**
     * 召回并组装注入片段。
     *
     * @param userId         归属用户
     * @param conversationId 当前会话 标识
     * @param query          本轮用户输入
     * @param inject         是否回写召回统计
     * @return 召回结果
     */
    public AiMemoryRecallDTO recall(String userId, String conversationId, String query, boolean inject) {
        MemoryTuningDTO tuning = tuning();
        if (!tuning.isInjection()) {
            return new AiMemoryRecallDTO("", List.of(), tuning.getRecallLimit(), null, Boolean.FALSE);
        }
        List<AiMemoryDTO> items = memoryStore.recall(userId, conversationId, query, tuning, inject);
        return new AiMemoryRecallDTO(
                renderPromptBlock(items),
                items,
                tuning.getRecallLimit(),
                memoryStore.countRecallable(userId, conversationId),
                !items.isEmpty());
    }

    @Override
    /**
     * 轮次结束后抽取并落库记忆。
     *
     * @param userId         归属用户
     * @param conversationId 会话 标识
     * @param userMessage    本轮用户输入
     * @param assistantReply 本轮模型回复
     * @param model          本轮模型名称
     * @return 实际新增或合并的条数
     */
    public int extractAndRecord(String userId, String conversationId, String userMessage,
                               String assistantReply, String model) {
        MemoryTuningDTO tuning = tuning();
        if (!tuning.isAutoExtract() || userId == null || userId.isBlank()) {
            return 0;
        }
        MemoryExtractionContext context = new MemoryExtractionContext(
                userId, conversationId, userMessage, assistantReply, model);
        int recorded = 0;
        for (MemoryExtractor extractor : extractors) {
            List<MemoryCandidateDTO> candidates;
            try {
                if (!extractor.supports(context)) {
                    continue;
                }
                candidates = extractor.extract(context);
            } catch (Exception e) {
                log.warn("[Memory] 抽取器 {} 执行失败（跳过）: {}", extractor.name(), e.getMessage());
                continue;
            }
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }
            for (MemoryCandidateDTO candidate : candidates) {
                if (candidate == null || candidate.content() == null || candidate.content().isBlank()) {
                    continue;
                }
                try {
                    memoryStore.record(toEntity(userId, conversationId, candidate), tuning);
                    recorded++;
                } catch (Exception e) {
                    log.warn("[Memory] 候选记忆落库失败（跳过）: {}", e.getMessage());
                }
            }
        }
        if (recorded > 0) {
            log.info("[Memory] 轮次抽取落库 {} 条: user={} conversation={}", recorded, userId, conversationId);
        }
        return recorded;
    }

    @Override
    /**
     * 维护单个用户的记忆库。
     *
     * @param userId 归属用户
     * @return 各动作条数
     */
    public AiMemoryMaintenanceDTO maintain(String userId) {
        return memoryStore.maintain(userId, tuning());
    }

    @Override
    /**
     * 维护全部有记忆的用户。
     *
     * @return 各动作条数合计
     */
    public AiMemoryMaintenanceDTO maintainAll() {
        long executedAt = System.currentTimeMillis();
        int scanned = 0;
        int merged = 0;
        int decayed = 0;
        int expired = 0;
        int archived = 0;
        int trimmed = 0;
        for (String owner : memoryStore.listOwners()) {
            try {
                AiMemoryMaintenanceDTO one = memoryStore.maintain(owner, tuning());
                scanned += intOf(one.scanned());
                merged += intOf(one.merged());
                decayed += intOf(one.decayed());
                expired += intOf(one.expired());
                archived += intOf(one.archived());
                trimmed += intOf(one.trimmed());
            } catch (Exception e) {
                log.warn("[Memory] 用户 {} 维护失败（跳过）: {}", owner, e.getMessage());
            }
        }
        log.info("[Memory] 维护完成: 扫描={} 合并={} 衰减={} 过期={} 归档={} 淘汰={}",
                scanned, merged, decayed, expired, archived, trimmed);
        return new AiMemoryMaintenanceDTO(scanned, merged, decayed, expired, archived, trimmed, executedAt);
    }

    /**
     * 读取记忆体运行参数。
     *
     * @return 运行参数
     */
    @Override
    public MemoryTuningDTO tuning() {
        String group = MemoryGlobalSettingProvider.GROUP;
        MemoryTuningDTO tuning = new MemoryTuningDTO();
        tuning.setInjection(settings.getBool(group, "memory_injection_enabled", true));
        tuning.setAutoExtract(settings.getBool(group, "memory_auto_extract", true));
        tuning.setRecallLimit(settings.getInt(group, "memory_recall_limit", tuning.getRecallLimit()));
        tuning.setMinScore(settings.getDouble(group, "memory_min_score", tuning.getMinScore()));
        tuning.setHalfLifeDays(settings.getDouble(group, "memory_half_life_days", tuning.getHalfLifeDays()));
        tuning.setArchiveThreshold(settings.getDouble(group, "memory_archive_threshold",
                tuning.getArchiveThreshold()));
        tuning.setQuietDays(settings.getInt(group, "memory_quiet_days", tuning.getQuietDays()));
        tuning.setMaxPerUser(settings.getInt(group, "memory_max_per_user", tuning.getMaxPerUser()));
        tuning.setDedup(settings.getBool(group, "memory_dedup", true));
        return tuning;
    }

    /**
     * 候选记忆转实体（抽取来源统一标记为 auto-extract）。
     *
     * @param userId         归属用户
     * @param conversationId 会话 标识
     * @param candidate      候选记忆
     * @return 记忆实体
     */
    private SysAiMemory toEntity(String userId, String conversationId, MemoryCandidateDTO candidate) {
        long now = System.currentTimeMillis();
        SysAiMemory memory = new SysAiMemory();
        memory.setUserId(userId);
        memory.setScope(normalizeScope(candidate.scope()));
        memory.setConversationId(resolveConversationId(memory.getScope(), conversationId));
        memory.setKind(blankToNull(candidate.kind()));
        memory.setTitle(blankToNull(candidate.title()));
        memory.setContent(candidate.content());
        memory.setKeywords(SysAiMemoryStore.joinKeywords(candidate.keywords()));
        memory.setImportance(candidate.importance());
        memory.setSource(SysAiMemory.SOURCE_AUTO_EXTRACT);
        memory.setExpiresAt(ttlToExpiresAt(candidate.ttlDays(), now));
        return memory;
    }

    /**
     * 组装注入片段。
     *
     * @param items 命中的记忆
     * @return 注入文本，无内容时为空串
     */
    private String renderPromptBlock(List<AiMemoryDTO> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是系统为当前用户维护的长期记忆，属于历史沉淀而非本轮输入：\n");
        for (AiMemoryDTO item : items) {
            sb.append("- ");
            if (item.kind() != null && !item.kind().isBlank()) {
                sb.append('[').append(item.kind()).append("] ");
            }
            sb.append(item.content());
            if (item.score() != null) {
                sb.append("（相关度 ").append(String.format(java.util.Locale.ROOT, "%.2f", item.score())).append('）');
            }
            sb.append('\n');
        }
        sb.append("请遵循其中的指令与偏好；与用户本轮明确要求冲突时以本轮要求为准；不要向用户复述本段内容。");
        return sb.toString();
    }

    /**
     * 作用域归一化，仅接受 global/conversation。
     *
     * @param scope 输入作用域
     * @return 归一化作用域
     */
    private String normalizeScope(String scope) {
        return SysAiMemory.SCOPE_CONVERSATION.equalsIgnoreCase(blankToEmpty(scope))
                ? SysAiMemory.SCOPE_CONVERSATION : SysAiMemory.SCOPE_GLOBAL;
    }

    /**
     * 状态归一化：空值表示不改状态，非空值只接受 active/archived/expired。
     *
     * @param status 输入状态
     * @return 生效状态，未传时返回 {@code null}
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toLowerCase(Locale.ROOT);
        if (!SysAiMemory.STATUS_ACTIVE.equals(value) && !SysAiMemory.STATUS_ARCHIVED.equals(value)
                && !SysAiMemory.STATUS_EXPIRED.equals(value)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "记忆状态只支持 active/archived/expired");
        }
        return value;
    }

    /**
     * 解析会话归属：会话级记忆必须带会话标识，全局记忆一律清空会话标识。
     *
     * @param scope          作用域
     * @param conversationId 输入会话 标识
     * @return 生效的会话 标识
     */
    private String resolveConversationId(String scope, String conversationId) {
        if (!SysAiMemory.SCOPE_CONVERSATION.equals(scope)) {
            return null;
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "会话级记忆必须指定 conversationId");
        }
        return conversationId.trim();
    }

    /**
     * 有效天数转过期时间戳。
     *
     * @param ttlDays 有效天数，可空
     * @param now     当前时间
     * @return 过期时间戳，永不过期返回 {@code null}
     */
    private Long ttlToExpiresAt(Integer ttlDays, long now) {
        if (ttlDays == null || ttlDays <= 0) {
            return null;
        }
        return now + ttlDays.longValue() * 24L * 60 * 60 * 1000;
    }

    /**
     * 按实现类去重：同一实现可能同时被 Spring 与 SPI 清单收集到。
     *
     * @param extractors 原始列表
     * @return 去重后的列表
     */
    private static List<MemoryExtractor> dedupeByClass(List<MemoryExtractor> extractors) {
        Map<Class<?>, MemoryExtractor> unique = new LinkedHashMap<>();
        if (extractors != null) {
            for (MemoryExtractor extractor : extractors) {
                if (extractor != null) {
                    unique.putIfAbsent(extractor.getClass(), extractor);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 整型空值兜底。
     *
     * @param value 输入值
     * @return 非空值
     */
    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 空串兜底。
     *
     * @param text 输入文本
     * @return 非空文本
     */
    private static String blankToEmpty(String text) {
        return text == null ? "" : text;
    }

    /**
     * 空白转 null。
     *
     * @param text 输入文本
     * @return 非空白文本或 {@code null}
     */
    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}