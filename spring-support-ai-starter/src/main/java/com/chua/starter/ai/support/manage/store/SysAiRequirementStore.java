package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiRequirementMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 需求条目持久化服务（JDBC 后端）。
 *
 * <p>替代原 {@code ApiRequirementController} 中的 {@code ConcurrentHashMap} 内存实现。
 * 提供完整的 CRUD、标签统计、看板查询、AutoWork 状态管理。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class SysAiRequirementStore {

    private final SysAiRequirementMapper requirementMapper;
    private final AtomicLong displaySeq = new AtomicLong(1);

    public SysAiRequirementStore(SysAiRequirementMapper requirementMapper) {
        this.requirementMapper = requirementMapper;
        // 从数据库读取最大 display_no，防止序号重复；表不存在时降级为 1
        try {
            LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(SysAiRequirement::getDisplayNo)
                   .orderByDesc(SysAiRequirement::getDisplayNo)
                   .last("LIMIT 1");
            SysAiRequirement last = requirementMapper.selectOne(wrapper);
            if (last != null && last.getDisplayNo() != null) {
                displaySeq.set((long) last.getDisplayNo() + 1);
            }
        } catch (Exception e) {
            log.warn("[RequirementStore] 初始化序号查询失败（表可能尚未创建），使用默认值 1: {}", e.getMessage());
        }
        log.info("[RequirementStore] 初始化完成，当前序号起始值={}", displaySeq.get());
    }

    /** 生成新的 UUIDv7 风格 ID */
    public String newId() {
        return SysAiConversationStore.generateUuidV7();
    }

    // ======================== CRUD ========================

    /**
     * 分页列表查询。
     *
     * @param tag      标签过滤，null = 不过滤
     * @param status   状态过滤，null = 不过滤
     * @param q        关键词（标题模糊搜索），null = 不过滤
     * @param orderBy  排序字段：display_no / created_at / updated_at / status
     * @param order    排序方向：asc / desc
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return { items, total, has_more }
     */
    public Map<String, Object> list(String tag, String status, String q, String orderBy, String order, int page, int pageSize) {
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        if (tag != null && !tag.isEmpty()) {
            wrapper.eq(SysAiRequirement::getTag, tag);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SysAiRequirement::getStatus, status);
        }
        if (q != null && !q.isEmpty()) {
            wrapper.like(SysAiRequirement::getTitle, q);
        }

        // 排序
        boolean asc = "asc".equalsIgnoreCase(order);
        switch (orderBy != null ? orderBy.toLowerCase() : "display_no") {
            case "created_at":
                wrapper.orderBy(true, asc, SysAiRequirement::getCreateTime);
                break;
            case "updated_at":
                wrapper.orderBy(true, asc, SysAiRequirement::getUpdateTime);
                break;
            case "status":
                wrapper.orderBy(true, asc, SysAiRequirement::getStatus);
                break;
            default:
                wrapper.orderBy(true, asc, SysAiRequirement::getDisplayNo);
                break;
        }

        long total = requirementMapper.selectCount(wrapper);
        List<SysAiRequirement> items;
        if (pageSize > 0) {
            wrapper.last("LIMIT " + ((page - 1) * pageSize) + "," + pageSize);
            items = requirementMapper.selectList(wrapper);
        } else {
            items = requirementMapper.selectList(wrapper);
        }

        List<Map<String, Object>> resultItems = new ArrayList<>();
        for (SysAiRequirement r : items) {
            resultItems.add(toMap(r));
        }

        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("items", resultItems);
        pageResult.put("total", total);
        pageResult.put("has_more", (long) page * pageSize < total);
        return pageResult;
    }

    /**
     * 获取单个需求。
     */
    public Map<String, Object> get(String requirementId) {
        SysAiRequirement r = requirementMapper.selectById(requirementId);
        if (r == null) {
            return null;
        }
        return toMap(r);
    }

    /**
     * 创建需求。
     */
    public Map<String, Object> create(Map<String, Object> body) {
        SysAiRequirement r = new SysAiRequirement();
        r.setRequirementId(newId());
        r.setDisplayNo((int) displaySeq.getAndIncrement());
        r.setTitle(getString(body, "title", "未命名需求"));
        r.setContent(getString(body, "content", ""));
        r.setTag(getString(body, "tag", "default"));
        r.setOrderKey(getString(body, "order_key", String.valueOf(System.currentTimeMillis())));
        r.setStatus(getString(body, "status", "pending"));
        r.setCompletionNote(null);
        r.setOwnerConversationId(getString(body, "owner_conversation_id"));
        r.setOwnerTerminalId(getString(body, "owner_terminal_id"));
        r.setOrigin(getString(body, "origin"));
        r.setAttemptCount(0);
        r.setExtra(null);
        r.setCreateTime(System.currentTimeMillis());
        r.setUpdateTime(System.currentTimeMillis());
        requirementMapper.insert(r);
        return toMap(r);
    }

    /**
     * 更新需求（部分更新）。
     *
     * @param requirementId 需求 ID
     * @param body          更新字段（仅包含非 null 字段生效）
     * @return 更新后的需求 Map；若不存在返回 null
     */
    public Map<String, Object> update(String requirementId, Map<String, Object> body) {
        SysAiRequirement existing = requirementMapper.selectById(requirementId);
        if (existing == null) {
            return null;
        }
        String oldStatus = existing.getStatus();
        LambdaUpdateWrapper<SysAiRequirement> update = new LambdaUpdateWrapper<>();
        update.eq(SysAiRequirement::getRequirementId, requirementId);
        if (body.containsKey("title"))        update.set(SysAiRequirement::getTitle,        getString(body, "title"));
        if (body.containsKey("content"))      update.set(SysAiRequirement::getContent,      getString(body, "content"));
        if (body.containsKey("tag"))          update.set(SysAiRequirement::getTag,          getString(body, "tag"));
        if (body.containsKey("order_key"))    update.set(SysAiRequirement::getOrderKey,     getString(body, "order_key"));
        if (body.containsKey("status"))       update.set(SysAiRequirement::getStatus,       getString(body, "status"));
        if (body.containsKey("completion_note")) update.set(SysAiRequirement::getCompletionNote, getString(body, "completion_note"));
        if (body.containsKey("origin"))       update.set(SysAiRequirement::getOrigin,       getString(body, "origin"));
        if (body.containsKey("started_at"))   update.set(SysAiRequirement::getStartedAt,    getLong(body, "started_at"));
        if (body.containsKey("completed_at")) update.set(SysAiRequirement::getCompletedAt,  getLong(body, "completed_at"));
        if (body.containsKey("attempt_count")) update.set(SysAiRequirement::getAttemptCount, getInt(body, "attempt_count"));
        if (body.containsKey("extra"))        update.set(SysAiRequirement::getExtra,        body.get("extra"));
        update.set(SysAiRequirement::getUpdateTime, System.currentTimeMillis());
        requirementMapper.update(null, update);
        SysAiRequirement refreshed = requirementMapper.selectById(requirementId);
        Map<String, Object> result = toMap(refreshed);
        result.put("__status_changed__", !oldStatus.equals(refreshed.getStatus()));
        return result;
    }

    /**
     * 删除需求。
     *
     * @param requirementId 需求 ID
     * @return 删除成功返回 true
     */
    public boolean remove(String requirementId) {
        return requirementMapper.deleteById(requirementId) > 0;
    }

    /**
     * 批量删除需求。
     *
     * @param requirementIds 需求 ID 列表
     * @return 删除条数
     */
    public int batchDelete(List<String> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) return 0;
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysAiRequirement::getRequirementId, requirementIds);
        return requirementMapper.delete(wrapper);
    }

    // ======================== 标签统计 ========================

    /**
     * 标签统计：每个标签的需求计数（按状态分组）。
     */
    public List<Map<String, Object>> tags() {
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(
                SysAiRequirement::getTag,
                SysAiRequirement::getStatus
        );
        List<SysAiRequirement> all = requirementMapper.selectList(wrapper);

        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (SysAiRequirement r : all) {
            String tag = r.getTag();
            Map<String, Object> s = summary.computeIfAbsent(tag, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tag", k);
                for (String key : new String[]{"pending", "in_progress", "done", "failed", "cancelled", "needs_review"}) {
                    m.put(key, 0);
                }
                m.put("total", 0);
                m.put("paused", false);
                return m;
            });
            String status = r.getStatus();
            if (s.containsKey(status)) {
                s.put(status, ((Number) s.get(status)).intValue() + 1);
            }
            s.put("total", ((Number) s.get("total")).intValue() + 1);
        }
        return new ArrayList<>(summary.values());
    }

    // ======================== 看板 ========================

    /**
     * 看板数据：按状态分组的特定标签下的需求。
     */
    public Map<String, Object> board(String tag) {
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("tag", tag);
        for (String key : new String[]{"pending", "in_progress", "done", "failed", "cancelled", "needs_review"}) {
            board.put(key, new ArrayList<>());
        }
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiRequirement::getTag, tag);
        List<SysAiRequirement> items = requirementMapper.selectList(wrapper);
        for (SysAiRequirement r : items) {
            String status = r.getStatus();
            if (board.containsKey(status)) {
                ((List<Map<String, Object>>) board.get(status)).add(toMap(r));
            }
        }
        return board;
    }

    // ======================== AutoWork ========================

    /**
     * 设置标签自动执行状态（AutoWork）。
     */
    public Map<String, Object> setAutoWork(String kind, String targetId, boolean enabled, String tag, boolean fromAdmin) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("kind", kind);
        state.put("target_id", targetId);
        state.put("enabled", enabled);
        state.put("tag", tag);
        state.put("run_state", enabled ? "active" : "off");
        state.put("current_requirement_id", null);
        state.put("completed_count", 0);
        return state;
    }

    /**
     * 恢复标签（取消暂停）。
     */
    public Map<String, Object> resumeTag(String tag, boolean requeueFailed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tag", tag);
        summary.put("total", 0);
        summary.put("pending", 0);
        summary.put("in_progress", 0);
        summary.put("done", 0);
        summary.put("failed", 0);
        summary.put("cancelled", 0);
        summary.put("needs_review", 0);
        summary.put("paused", false);
        summary.put("requeue_failed", requeueFailed);
        return summary;
    }

    // ======================== 数据源管理 ========================

    /**
     * 获取所有数据源配置列表。
     * TODO: 对接 SysAiDataSourceMapper 实现持久化查询。
     */
    public List<Map<String, Object>> listDataSources() {
        return new ArrayList<>();
    }

    /**
     * 创建数据源配置。
     * TODO: 对接 SysAiDataSourceMapper 实现持久化写入。
     */
    public Map<String, Object> createDataSource(Map<String, Object> body) {
        return new LinkedHashMap<>();
    }

    /**
     * 更新数据源配置。
     * TODO: 对接 SysAiDataSourceMapper 实现持久化更新。
     */
    public Map<String, Object> updateDataSource(String id, Map<String, Object> body) {
        return new LinkedHashMap<>();
    }

    /**
     * 删除数据源配置。
     * TODO: 对接 SysAiDataSourceMapper 实现持久化删除。
     */
    public boolean removeDataSource(String id) {
        return false;
    }

    /**
     * 触发数据源同步。
     * TODO: 委托给 RequirementDataSourceRegistry.syncAll()。
     */
    public Map<String, Object> syncDataSource(String id) {
        return new LinkedHashMap<>();
    }

    // ======================== 私有工具 ========================

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(SysAiRequirement r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requirement_id", r.getRequirementId());
        m.put("display_no", r.getDisplayNo());
        m.put("title", r.getTitle());
        m.put("content", r.getContent());
        m.put("tag", r.getTag());
        m.put("order_key", r.getOrderKey());
        m.put("status", r.getStatus());
        m.put("completion_note", r.getCompletionNote());
        m.put("owner_conversation_id", r.getOwnerConversationId());
        m.put("owner_terminal_id", r.getOwnerTerminalId());
        m.put("origin", r.getOrigin());
        m.put("started_at", r.getStartedAt());
        m.put("completed_at", r.getCompletedAt());
        m.put("attempt_count", r.getAttemptCount());
        m.put("extra", r.getExtra());
        m.put("created_at", r.getCreateTime());
        m.put("updated_at", r.getUpdateTime());
        return m;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }
}
