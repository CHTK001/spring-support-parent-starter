package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiDataSourceMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiRequirementMapper;
import com.chua.starter.ai.support.manage.store.datasource.IRequirementDataSource;
import com.chua.starter.ai.support.manage.store.datasource.LocalDbRequirementDataSource;
import com.chua.starter.ai.support.manage.store.datasource.RequirementDataSourceRegistry;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final SysAiDataSourceMapper dataSourceMapper;
    private final RequirementDataSourceRegistry registry;
    private final AtomicLong displaySeq = new AtomicLong(1);
    /**
     * In-memory AutoWork target → state map, keyed by "kind:targetId".
    */
    private final Map<String, Map<String, Object>> autoworkBindings = new ConcurrentHashMap<>();

    public SysAiRequirementStore(SysAiRequirementMapper requirementMapper,
                                 SysAiDataSourceMapper dataSourceMapper,
                                 RequirementDataSourceRegistry registry) {
        this.requirementMapper = requirementMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.registry = registry;
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

    /**
     * 生成新的 UUIDv7 风格 ID。
     *
     * @return 36 位 UUIDv7 字符串
     */
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
        // 管理员可跨用户查看，普通用户仅本人
        scopeRequirement(wrapper);
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
     *
     * @param requirementId 需求 ID（UUIDv7），不能为 null 或空
     * @return 需求数据 Map；不存在时返回 null
     */
    public Map<String, Object> get(String requirementId) {
        if (requirementId == null || requirementId.isEmpty()) {
            throw new IllegalArgumentException("requirementId 不能为空");
        }
        SysAiRequirement r = requirementMapper.selectById(requirementId);
        if (r == null || !readableByCurrent(r.getUserId())) {
            // 越权与不存在同返回，避免用状态差异探测他人需求是否存在
            return null;
        }
        return toMap(r);
    }

    /**
     * 创建需求。
     *
     * @param body 请求体，title/tag 缺省时使用默认值
     * @return 创建后的需求数据 Map
     */
    public Map<String, Object> create(Map<String, Object> body) {
        SysAiRequirement r = new SysAiRequirement();
        r.setRequirementId(newId());
        // 归属由服务端按当前登录用户写入，客户端传值一律忽略
        r.setUserId(AiUserContext.requireUserId());
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
        requireWritableRequirement(existing);
        String oldStatus = existing.getStatus();
        LambdaUpdateWrapper<SysAiRequirement> update = new LambdaUpdateWrapper<>();
        update.eq(SysAiRequirement::getRequirementId, requirementId);
        if (body.containsKey("title")) {
            update.set(SysAiRequirement::getTitle,        getString(body, "title"));
        }
        if (body.containsKey("content")) {
            update.set(SysAiRequirement::getContent,      getString(body, "content"));
        }
        if (body.containsKey("tag")) {
            update.set(SysAiRequirement::getTag,          getString(body, "tag"));
        }
        if (body.containsKey("order_key")) {
            update.set(SysAiRequirement::getOrderKey,     getString(body, "order_key"));
        }
        if (body.containsKey("status")) {
            update.set(SysAiRequirement::getStatus,       getString(body, "status"));
        }
        if (body.containsKey("completion_note")) {
            update.set(SysAiRequirement::getCompletionNote, getString(body, "completion_note"));
        }
        if (body.containsKey("origin")) {
            update.set(SysAiRequirement::getOrigin,       getString(body, "origin"));
        }
        if (body.containsKey("started_at")) {
            update.set(SysAiRequirement::getStartedAt,    getLong(body, "started_at"));
        }
        if (body.containsKey("completed_at")) {
            update.set(SysAiRequirement::getCompletedAt,  getLong(body, "completed_at"));
        }
        if (body.containsKey("attempt_count")) {
            update.set(SysAiRequirement::getAttemptCount, getInt(body, "attempt_count"));
        }
        if (body.containsKey("extra")) {
            update.set(SysAiRequirement::getExtra,        body.get("extra"));
        }
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
        if (requirementId == null || requirementId.isEmpty()) {
            throw new IllegalArgumentException("requirementId 不能为空");
        }
        SysAiRequirement existing = requirementMapper.selectById(requirementId);
        if (existing == null) {
            return false;
        }
        requireWritableRequirement(existing);
        return requirementMapper.deleteById(requirementId) > 0;
    }

    /**
     * 批量删除需求。
     *
     * @param requirementIds 需求 ID 列表，不能为 null；空列表返回 0
     * @return 删除条数
     */
    public int batchDelete(List<String> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return 0;
        }
        // 先整批校验归属，再执行删除：避免中途拒绝导致部分删除
        for (String requirementId : requirementIds) {
            SysAiRequirement existing = requirementMapper.selectById(requirementId);
            if (existing != null) {
                requireWritableRequirement(existing);
            }
        }
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysAiRequirement::getRequirementId, requirementIds);
        return requirementMapper.delete(wrapper);
    }

    // ======================== 标签统计 ========================

    /**
     * 标签统计：每个标签的需求计数（按状态分组）。
     *
     * @return 标签统计列表，每项含 tag/total/各状态计数
     */
    public List<Map<String, Object>> tags() {
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        scopeRequirement(wrapper);
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
     *
     * @param tag 标签，不能为 null 或空
     * @return 看板数据 Map（tag + 各状态下的需求列表）
     */
    public Map<String, Object> board(String tag) {
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("tag 不能为空");
        }
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("tag", tag);
        for (String key : new String[]{"pending", "in_progress", "done", "failed", "cancelled", "needs_review"}) {
            board.put(key, new ArrayList<>());
        }
        LambdaQueryWrapper<SysAiRequirement> wrapper = new LambdaQueryWrapper<>();
        scopeRequirement(wrapper);
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
     *
     * @param kind        执行类型（conversation/terminal）
     * @param targetId    目标会话或终端 ID
     * @param enabled     是否启用
     * @param tag         关联标签
     * @param fromAdmin   是否来自管理员入口（标签会话管理）
     * @return AutoWork 状态 Map（run_state、已办数等）
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
        // Persist binding for tag-bindings aggregation
        if (kind != null && targetId != null) {
            String key = kind + ":" + targetId;
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("kind", kind);
            binding.put("target_id", targetId);
            binding.put("tag", tag);
            binding.put("run_state", enabled ? "active" : "off");
            binding.put("enabled", enabled);
            autoworkBindings.put(key, binding);
        }
        return state;
    }

    /**
     * 恢复标签（取消暂停）。
     *
     * @param tag           标签名，不能为空
     * @param requeueFailed 是否将失败需求重新入队
     * @return 标签汇总 Map（各状态计数）
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
     * 获取所有数据源配置列表（含简要信息，不含敏感配置字段）。
     *
     * @return 数据源列表，每项包含 id/name/type/enabled/lastSyncStatus 等概要字段
     */
    public List<Map<String, Object>> listDataSources() {
        LambdaQueryWrapper<SysAiDataSource> dsWrapper = new LambdaQueryWrapper<SysAiDataSource>()
                .orderByDesc(SysAiDataSource::getCreateTime);
        scopeDataSource(dsWrapper);
        List<SysAiDataSource> all = dataSourceMapper.selectList(dsWrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiDataSource ds : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ds.getId());
            m.put("name", ds.getName());
            m.put("type", ds.getType());
            m.put("enabled", ds.getEnabled() != null ? ds.getEnabled() : true);
            m.put("last_sync_status", ds.getLastSyncStatus());
            m.put("last_sync_at", ds.getLastSyncAt());
            m.put("created_at", ds.getCreateTime());
            result.add(m);
        }
        return result;
    }

    /**
     * 创建数据源配置。
     *
     * @param body 请求体，须包含 name、type，可选 config（JSON）、enabled、syncCron
     * @return 创建后的数据源 Map；若 type 不支持返回 null
     */
    public Map<String, Object> createDataSource(Map<String, Object> body) {
        String name = getString(body, "name");
        String type = getString(body, "type");
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        type = type.toUpperCase();
        IRequirementDataSource provider = registry.getProvider(type);
        if (provider == null) {
            log.warn("[DataSource] 不支持的类型: {}", type);
            return null;
        }
        Map<String, Object> config = body.containsKey("config") ? (Map<String, Object>) body.get("config") : new LinkedHashMap<>();
        SysAiDataSource ds = new SysAiDataSource();
        ds.setId(newId());
        // 归属由服务端按当前登录用户写入：第三方凭证仅归属者可见可改
        ds.setUserId(AiUserContext.requireUserId());
        ds.setName(name);
        ds.setType(type);
        ds.setConfig(config);
        ds.setEnabled(body.containsKey("enabled") ? Boolean.parseBoolean(body.get("enabled").toString()) : true);
        ds.setSyncCron(getString(body, "sync_cron"));
        ds.setCreateTime(System.currentTimeMillis());
        ds.setUpdateTime(System.currentTimeMillis());
        dataSourceMapper.insert(ds);
        return toDataSourceMap(ds);
    }

    /**
     * 更新数据源配置（部分更新）。
     *
     * @param id   数据源 ID
     * @param body 更新字段（name/type/config/enabled/sync_cron）
     * @return 更新后的数据源 Map；若不存在返回 null
     */
    public Map<String, Object> updateDataSource(String id, Map<String, Object> body) {
        SysAiDataSource existing = dataSourceMapper.selectById(id);
        if (existing == null) {
            return null;
        }
        requireWritableDataSource(existing);
        LambdaUpdateWrapper<SysAiDataSource> update = new LambdaUpdateWrapper<>();
        update.eq(SysAiDataSource::getId, id);
        if (body.containsKey("name")) {
            update.set(SysAiDataSource::getName,         getString(body, "name"));
        }
        if (body.containsKey("enabled")) {
            update.set(SysAiDataSource::getEnabled,       body.containsKey("enabled") ? Boolean.parseBoolean(body.get("enabled").toString()) : null);
        }
        if (body.containsKey("config")) {
            update.set(SysAiDataSource::getConfig,        body.get("config"));
        }
        if (body.containsKey("sync_cron")) {
            update.set(SysAiDataSource::getSyncCron,      getString(body, "sync_cron"));
        }
        update.set(SysAiDataSource::getUpdateTime, System.currentTimeMillis());
        dataSourceMapper.update(null, update);
        return toDataSourceMap(dataSourceMapper.selectById(id));
    }

    /**
     * 删除数据源配置。
     *
     * @param id 数据源 ID
     * @return 删除成功返回 true
     */
    public boolean removeDataSource(String id) {
        SysAiDataSource existing = dataSourceMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        requireWritableDataSource(existing);
        return dataSourceMapper.deleteById(id) > 0;
    }

    /**
     * 触发单个数据源同步。
     *
     * @param id 数据源 ID
     * @return 同步结果 Map（含 synced/count/error）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> syncDataSource(String id) {
        SysAiDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            return Map.of("id", id, "error", "数据源不存在");
        }
        // 同步会消费归属者的第三方凭证与配额，仅归属者可触发
        requireWritableDataSource(ds);
        if (Boolean.FALSE.equals(ds.getEnabled())) {
            return Map.of("id", id, "error", "数据源已禁用");
        }
        String typeUpper = ds.getType() != null ? ds.getType().toUpperCase() : null;
        IRequirementDataSource provider = registry.getProvider(typeUpper);
        if (provider == null) {
            dataSourceMapper.update(null, new LambdaUpdateWrapper<SysAiDataSource>()
                    .eq(SysAiDataSource::getId, id)
                    .set(SysAiDataSource::getLastSyncStatus, "failed")
                    .set(SysAiDataSource::getLastSyncError, "未注册的数据源类型: " + ds.getType())
                    .set(SysAiDataSource::getLastSyncAt, System.currentTimeMillis()));
            return Map.of("id", id, "error", "未注册的数据源类型: " + ds.getType());
        }

        // local 类型：通过 fetchRemoteRequirements 获取完整记录并写入本地库
        if ("LOCAL".equals(typeUpper) && provider instanceof LocalDbRequirementDataSource localDs) {
            return syncLocalDb(ds, localDs);
        }

        try {
            int count = provider.sync(ds.getConfig() != null ? ds.getConfig() : new LinkedHashMap<>(),
                    ds.getLastSyncAt() != null ? ds.getLastSyncAt() : 0L);
            dataSourceMapper.update(null, new LambdaUpdateWrapper<SysAiDataSource>()
                    .eq(SysAiDataSource::getId, id)
                    .set(SysAiDataSource::getLastSyncStatus, "success")
                    .set(SysAiDataSource::getLastSyncAt, System.currentTimeMillis()));
            return Map.of("id", id, "synced", count, "status", "success");
        } catch (Exception e) {
            log.error("[DataSource] 同步失败: id={}", id, e);
            dataSourceMapper.update(null, new LambdaUpdateWrapper<SysAiDataSource>()
                    .eq(SysAiDataSource::getId, id)
                    .set(SysAiDataSource::getLastSyncStatus, "failed")
                    .set(SysAiDataSource::getLastSyncError, e.getMessage().substring(0, Math.min(512, e.getMessage().length())))
                    .set(SysAiDataSource::getLastSyncAt, System.currentTimeMillis()));
            return Map.of("id", id, "error", e.getMessage());
        }
    }

    /**
     * 同步本地/远程 MySQL 数据库的需求记录到本地库。
     *
     * @param ds      数据源实体
     * @param provider 提供方实例
     * @return 同步结果
     */
    private Map<String, Object> syncLocalDb(SysAiDataSource ds, LocalDbRequirementDataSource provider) {
        List<Map<String, Object>> remoteRecords;
        try {
            remoteRecords = provider.fetchRemoteRequirements(ds.getConfig() != null ? ds.getConfig() : new LinkedHashMap<>());
        } catch (Exception e) {
            log.error("[LocalDbDS] 读取远程需求失败: id={}", ds.getId(), e);
            dataSourceMapper.update(null, new LambdaUpdateWrapper<SysAiDataSource>()
                    .eq(SysAiDataSource::getId, ds.getId())
                    .set(SysAiDataSource::getLastSyncStatus, "failed")
                    .set(SysAiDataSource::getLastSyncError, e.getMessage())
                    .set(SysAiDataSource::getLastSyncAt, System.currentTimeMillis()));
            return Map.of("id", ds.getId(), "error", e.getMessage());
        }
        int synced = 0;
        for (Map<String, Object> row : remoteRecords) {
            String remoteId = (String) row.get("requirement_id");
            if (remoteId == null || remoteId.isEmpty()) {
                continue;
            }
            try {
                Map<String, Object> existing = get(remoteId);
                if (existing == null) {
                    // 新建
                    create(row);
                } else {
                    // 更新（只更新本地未修改的字段，避免覆盖本地变更）
                    Map<String, Object> patch = new LinkedHashMap<>();
                    patch.put("title", row.get("title"));
                    patch.put("content", row.get("content"));
                    patch.put("tag", row.get("tag"));
                    patch.put("status", row.get("status"));
                    patch.put("order_key", row.get("order_key"));
                    patch.put("completion_note", row.get("completion_note"));
                    update(remoteId, patch);
                }
                synced++;
            } catch (Exception e) {
                log.warn("[LocalDbDS] 写入本地失败: id={}", remoteId, e);
            }
        }
        dataSourceMapper.update(null, new LambdaUpdateWrapper<SysAiDataSource>()
                .eq(SysAiDataSource::getId, ds.getId())
                .set(SysAiDataSource::getLastSyncStatus, "success")
                .set(SysAiDataSource::getLastSyncAt, System.currentTimeMillis()));
        return Map.of("id", ds.getId(), "synced", synced, "total_remote", remoteRecords.size(), "status", "success");
    }

    /**
     * 测试数据源连接是否可用。
     *
     * @param id 数据源 ID，不能为空
     * @return true = 连接正常，false = 数据源不存在或连接失败
     */
    public boolean testDataSource(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id 不能为空");
        }
        SysAiDataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            return false;
        }
        // 连接测试会借用已存凭证对外发起请求，仅归属者可执行
        requireWritableDataSource(ds);
        IRequirementDataSource provider = registry.getProvider(ds.getType());
        if (provider == null) {
            return false;
        }
        return provider.testConnection(ds.getConfig() != null ? ds.getConfig() : new LinkedHashMap<>());
    }

    /**
     * 获取注册表中所有可用的数据源类型列表。
     *
     * @return 已注册类型列表
     */
    public List<String> availableTypes() {
        return registry.list();
    }

    /**
     * 需求查询按当前登录用户收窄。
     *
     * @param wrapper 查询条件
     */
    private void scopeRequirement(LambdaQueryWrapper<SysAiRequirement> wrapper) {
        if (!AiUserContext.isAdmin()) {
            wrapper.eq(SysAiRequirement::getUserId, AiUserContext.currentUserId());
        }
    }

    /**
     * 数据源查询按当前登录用户收窄。
     *
     * @param wrapper 查询条件
     */
    private void scopeDataSource(LambdaQueryWrapper<SysAiDataSource> wrapper) {
        if (!AiUserContext.isAdmin()) {
            wrapper.eq(SysAiDataSource::getUserId, AiUserContext.currentUserId());
        }
    }

    /**
     * 读归属判定：管理员可跨用户读取，其余仅本人。
     *
     * @param owner 数据归属用户标识
     * @return 当前用户是否可读
     */
    private boolean readableByCurrent(String owner) {
        return AiUserContext.isAdmin() || AiUserContext.requireUserId().equals(owner);
    }

    /**
     * 需求写归属校验：只读账号拒绝，仅归属者可改删（管理员对他人数据只读）。
     *
     * @param requirement 需求实体
     */
    private void requireWritableRequirement(SysAiRequirement requirement) {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(requirement.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
    }

    /**
     * 数据源写归属校验：第三方凭证仅归属者可改删与使用。
     *
     * @param dataSource 数据源实体
     */
    private void requireWritableDataSource(SysAiDataSource dataSource) {
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(dataSource.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "管理员对他人数据只读，不可修改或删除");
        }
    }

    /**
     * 转为数据来源映射。
     *
     * @param ds 方法入参 ds
     * @return 结果映射，无数据时为空映射
     */
    private Map<String, Object> toDataSourceMap(SysAiDataSource ds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ds.getId());
        m.put("name", ds.getName());
        m.put("type", ds.getType());
        m.put("enabled", ds.getEnabled() != null ? ds.getEnabled() : true);
        m.put("sync_cron", ds.getSyncCron());
        m.put("last_sync_at", ds.getLastSyncAt());
        m.put("last_sync_status", ds.getLastSyncStatus());
        m.put("last_sync_error", ds.getLastSyncError());
        m.put("created_at", ds.getCreateTime());
        m.put("updated_at", ds.getUpdateTime());
        return m;
    }

    // ======================== 私有工具 ========================

    /**
     * 转为映射。
     *
     * @param r 方法入参 r
     * @return 结果映射，无数据时为空映射
     */
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

    /**
     * 获取字符串。
     *
     * @param map 映射，不允许为 null
     * @param key 键，不允许为 null
     * @return 结果字符串
     */
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * 获取字符串。
     *
     * @param map 映射，不允许为 null
     * @param key 键，不允许为 null
     * @param defaultValue default值，不允许为 null
     * @return 结果字符串
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    /**
     * 获取Long。
     *
     * @param map 映射，不允许为 null
     * @param key 键，不允许为 null
     * @return Long 对象
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    /**
     * 获取Int。
     *
     * @param map 映射，不允许为 null
     * @param key 键，不允许为 null
     * @return Integer 对象
     */
    private Integer getInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    /**
     * 获取所有 AutoWork 标签绑定，按 tag 分组。
     * @return 结果列表，无数据时为空列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTagBindings() {
        Map<String, List<Map<String, Object>>> byTag = new LinkedHashMap<>();
        for (Map<String, Object> binding : autoworkBindings.values()) {
            String tag = (String) binding.get("tag");
            if (tag == null || tag.isBlank()) {
                continue;
            }
            byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(binding);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byTag.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("tag", entry.getKey());
            group.put("bindings", entry.getValue());
            result.add(group);
        }
        return result;
    }
}
