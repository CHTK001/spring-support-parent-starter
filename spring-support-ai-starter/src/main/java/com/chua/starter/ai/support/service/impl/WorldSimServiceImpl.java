package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiWorld;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import com.chua.starter.ai.support.entity.SysAiWorldMessage;
import com.chua.starter.ai.support.entity.SysAiWorldNpc;
import com.chua.starter.ai.support.entity.SysAiWorldRelationship;
import com.chua.starter.ai.support.entity.SysAiWorldScene;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldEventMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldNpcMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldRelationshipMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldSceneMapper;
import com.chua.starter.ai.support.service.WorldSimService;
import com.chua.starter.ai.support.util.AiUserContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟（世界沙盒）服务实现。
 *
 * <p>世界视图（世界配置 + 场景 + NPC + 关系网 + 最近事件）是只读聚合，走本地
 * Caffeine 缓存（{@code aiCacheManager} 下 {@code aiWorldView} 缓存），任何写操作
 * 后按 {@code worldId:userId} 失效，避免脏读又减轻 DB 压力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class WorldSimServiceImpl implements WorldSimService {

    /** 世界视图缓存名（Caffeine 本地，30 分钟 TTL，变更后主动失效） */
    private static final String VIEW_CACHE = "aiWorldView";

    /** 世界列表缓存名 */
    private static final String LIST_CACHE = "aiWorldList";

    private final SysAiWorldMapper worldMapper;
    private final SysAiWorldSceneMapper sceneMapper;
    private final SysAiWorldNpcMapper npcMapper;
    private final SysAiWorldRelationshipMapper relationshipMapper;
    private final SysAiWorldEventMapper eventMapper;
    private final SysAiWorldMessageMapper messageMapper;
    private final SysAiRoleMapper roleMapper;

    /**
     * 本地 Caffeine 缓存管理器。
     *
     * <p>必须按 bean 名注入：宿主（如 eduai）里同时存在多个 {@code @Primary}
     * 的 {@code CacheManager}（{@code defaultCacheManager}、{@code cacheWithRedis}）
     * 与本模块的 {@code aiCacheManager}，按类型注入会因「多个 primary」直接让上下文
     * 启动失败。本服务只写 {@code aiWorldView} / {@code aiWorldList} 这两个本地缓存，
     * 与 Redis 无关。</p>
     */
    private final CacheManager cacheManager;

    /**
     * 构造注入。
     *
     * <p>不用 {@code @RequiredArgsConstructor}：Lombok 不会把字段上的 {@link Qualifier}
     * 复制到构造参数，Spring 仍按类型注入，撞上多个 {@code @Primary} 的
     * {@code CacheManager} 时上下文直接起不来。这里显式声明构造器，把限定符写在参数上。</p>
     *
     * @param worldMapper          世界
     * @param sceneMapper          场景
     * @param npcMapper            NPC
     * @param relationshipMapper   关系
     * @param eventMapper          事件
     * @param messageMapper        消息
     * @param roleMapper           角色
     * @param cacheManager         本地 Caffeine 缓存管理器（aiCacheManager）
     */
    public WorldSimServiceImpl(SysAiWorldMapper worldMapper,
                               SysAiWorldSceneMapper sceneMapper,
                               SysAiWorldNpcMapper npcMapper,
                               SysAiWorldRelationshipMapper relationshipMapper,
                               SysAiWorldEventMapper eventMapper,
                               SysAiWorldMessageMapper messageMapper,
                               SysAiRoleMapper roleMapper,
                               @Qualifier("aiCacheManager") CacheManager cacheManager) {
        this.worldMapper = worldMapper;
        this.sceneMapper = sceneMapper;
        this.npcMapper = npcMapper;
        this.relationshipMapper = relationshipMapper;
        this.eventMapper = eventMapper;
        this.messageMapper = messageMapper;
        this.roleMapper = roleMapper;
        this.cacheManager = cacheManager;
    }

    /* ---------------- 世界 ---------------- */

    @Override
    public List<Map<String, Object>> listWorlds() {
        String userId = AiUserContext.requireUserId();
        Cache cache = cache();
        String key = "list:" + userId;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cached = cache.get(key, List.class);
        if (cached != null) {
            return cached;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        List<SysAiWorld> worlds = worldMapper.selectList(
                new LambdaQueryWrapper<SysAiWorld>()
                        .eq(SysAiWorld::getUserId, userId)
                        .orderByAsc(SysAiWorld::getId));
        for (SysAiWorld w : worlds) {
            result.add(worldRow(w));
        }
        cache.put(key, result);
        return result;
    }

    @Override
    public Map<String, Object> worldView(Long worldId) {
        String userId = AiUserContext.requireUserId();
        SysAiWorld world = requireOwned(worldId);
        Cache cache = cache();
        String key = worldId + ":" + userId;
        Map<String, Object> cached = cache.get(key, Map.class);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("world", worldRow(world));
        view.put("scenes", listScenes(worldId));
        view.put("npcs", listNpcs(worldId));
        view.put("relationships", listRelationships(worldId));
        view.put("events", listEvents(worldId, null, 30));
        cache.put(key, view);
        return view;
    }

    @Override
    public SysAiWorld createWorld(String name, String description, String era) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "世界名称不能为空");
        }
        String userId = AiUserContext.requireUserId();
        SysAiWorld w = new SysAiWorld();
        w.setName(name.trim());
        w.setDescription(description == null || description.isBlank() ? null : description.trim());
        w.setEra(era == null || era.isBlank() ? "现代" : era.trim());
        w.setWeather("晴");
        w.setTimeLabel("第 1 天 上午");
        w.setTickCount(0);
        w.setStatus(1);
        w.setUserId(userId);
        w.setCreateTime(LocalDateTime.now());
        w.setUpdateTime(LocalDateTime.now());
        worldMapper.insert(w);
        // 建世界即送一个「起始之地」场景，保证立刻可玩
        SysAiWorldScene s = new SysAiWorldScene();
        s.setWorldId(w.getId());
        s.setName("起始之地");
        s.setDescription("这个世界的起点，一切故事将要发生的地方。");
        s.setSort(0);
        s.setUserId(userId);
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        sceneMapper.insert(s);
        evictWorldList();
        return w;
    }

    @Override
    public SysAiWorld updateWorld(Long id, Map<String, Object> body) {
        SysAiWorld w = requireOwned(id);
        if (body.get("name") != null) {
            w.setName(String.valueOf(body.get("name")).trim());
        }
        if (body.containsKey("description")) {
            Object v = body.get("description");
            w.setDescription(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v).trim());
        }
        if (body.get("era") != null) {
            w.setEra(String.valueOf(body.get("era")).trim());
        }
        if (body.get("weather") != null) {
            w.setWeather(String.valueOf(body.get("weather")).trim());
        }
        if (body.containsKey("timeLabel")) {
            w.setTimeLabel(String.valueOf(body.get("timeLabel")));
        }
        if (body.get("status") != null) {
            w.setStatus(parseInt(body.get("status"), w.getStatus()));
        }
        w.setUpdateTime(LocalDateTime.now());
        worldMapper.updateById(w);
        evictWorld(id);
        return w;
    }

    @Override
    @Transactional
    public void deleteWorld(Long id) {
        SysAiWorld w = requireOwned(id);
        List<SysAiWorldNpc> npcs = npcMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldNpc>().eq(SysAiWorldNpc::getWorldId, id));
        List<Long> npcIds = npcs.stream().map(SysAiWorldNpc::getId).toList();
        if (!npcIds.isEmpty()) {
            relationshipMapper.delete(new LambdaQueryWrapper<SysAiWorldRelationship>()
                    .eq(SysAiWorldRelationship::getWorldId, id));
        }
        npcMapper.delete(new LambdaQueryWrapper<SysAiWorldNpc>().eq(SysAiWorldNpc::getWorldId, id));
        sceneMapper.delete(new LambdaQueryWrapper<SysAiWorldScene>().eq(SysAiWorldScene::getWorldId, id));
        eventMapper.delete(new LambdaQueryWrapper<SysAiWorldEvent>().eq(SysAiWorldEvent::getWorldId, id));
        messageMapper.delete(new LambdaQueryWrapper<SysAiWorldMessage>().eq(SysAiWorldMessage::getWorldId, id));
        worldMapper.deleteById(id);
        evictWorld(id);
    }

    /* ---------------- 场景 ---------------- */

    @Override
    public List<SysAiWorldScene> listScenes(Long worldId) {
        requireOwned(worldId);
        return sceneMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldScene>()
                        .eq(SysAiWorldScene::getWorldId, worldId)
                        .orderByAsc(SysAiWorldScene::getSort)
                        .orderByAsc(SysAiWorldScene::getId));
    }

    @Override
    public SysAiWorldScene createScene(Long worldId, Map<String, Object> body) {
        requireOwned(worldId);
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "场景名称不能为空");
        }
        SysAiWorldScene s = new SysAiWorldScene();
        s.setWorldId(worldId);
        s.setName(name.trim());
        Object desc = body.get("description");
        s.setDescription(desc == null || "null".equals(String.valueOf(desc)) ? null : String.valueOf(desc).trim());
        s.setSort(parseInt(body.get("sort"), Math.toIntExact(sceneMapper.selectCount(
                new LambdaQueryWrapper<SysAiWorldScene>().eq(SysAiWorldScene::getWorldId, worldId)))));
        s.setUserId(AiUserContext.requireUserId());
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        sceneMapper.insert(s);
        evictWorld(worldId);
        return s;
    }

    @Override
    public SysAiWorldScene updateScene(Long id, Map<String, Object> body) {
        SysAiWorldScene s = sceneMapper.selectById(id);
        if (s == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "场景不存在");
        }
        requireOwned(s.getWorldId());
        if (body.get("name") != null) {
            s.setName(String.valueOf(body.get("name")).trim());
        }
        if (body.containsKey("description")) {
            Object v = body.get("description");
            s.setDescription(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v).trim());
        }
        if (body.get("sort") != null) {
            s.setSort(parseInt(body.get("sort"), s.getSort()));
        }
        s.setUpdateTime(LocalDateTime.now());
        sceneMapper.updateById(s);
        evictWorld(s.getWorldId());
        return s;
    }

    @Override
    public void deleteScene(Long id) {
        SysAiWorldScene s = sceneMapper.selectById(id);
        if (s == null) {
            return;
        }
        requireOwned(s.getWorldId());
        // 把该场景下的 NPC 挪回"无场景"
        npcMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysAiWorldNpc>()
                .eq(SysAiWorldNpc::getSceneId, id)
                .set(SysAiWorldNpc::getSceneId, null));
        sceneMapper.deleteById(id);
        evictWorld(s.getWorldId());
    }

    /* ---------------- 世界 NPC ---------------- */

    @Override
    public List<Map<String, Object>> listNpcs(Long worldId) {
        requireOwned(worldId);
        List<SysAiWorldNpc> npcs = npcMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldNpc>()
                        .eq(SysAiWorldNpc::getWorldId, worldId)
                        .orderByAsc(SysAiWorldNpc::getSort)
                        .orderByAsc(SysAiWorldNpc::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiWorldNpc n : npcs) {
            result.add(npcRow(n));
        }
        return result;
    }

    @Override
    public SysAiWorldNpc addNpc(Long worldId, Map<String, Object> body) {
        requireOwned(worldId);
        Integer roleId = body.get("roleId") == null ? null
                : Integer.valueOf(String.valueOf(body.get("roleId")));
        if (roleId == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请选择要加入的角色");
        }
        SysAiRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "角色不存在");
        }
        Long dup = npcMapper.selectCount(new LambdaQueryWrapper<SysAiWorldNpc>()
                .eq(SysAiWorldNpc::getWorldId, worldId)
                .eq(SysAiWorldNpc::getRoleId, roleId));
        if (dup != null && dup > 0) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "该角色已在这个世界");
        }
        SysAiWorldNpc n = new SysAiWorldNpc();
        n.setWorldId(worldId);
        n.setRoleId(roleId);
        Object sceneId = body.get("sceneId");
        n.setSceneId(sceneId == null || "null".equals(String.valueOf(sceneId))
                ? null : Long.valueOf(String.valueOf(sceneId)));
        Object nickname = body.get("nickname");
        n.setNickname(nickname == null || "null".equals(String.valueOf(nickname))
                ? null : String.valueOf(nickname).trim());
        Object avatar = body.get("avatar");
        n.setAvatar(avatar == null || "null".equals(String.valueOf(avatar))
                ? null : String.valueOf(avatar));
        Object model = body.get("modelOverride");
        n.setModelOverride(model == null || "null".equals(String.valueOf(model))
                ? null : String.valueOf(model));
        Object temperature = body.get("temperature");
        n.setTemperature(temperature == null || "null".equals(String.valueOf(temperature))
                ? null : Double.valueOf(String.valueOf(temperature)));
        Object persona = body.get("persona");
        n.setPersona(persona == null || "null".equals(String.valueOf(persona))
                ? null : String.valueOf(persona));
        if (body.get("isActive") != null) {
            n.setIsActive(Boolean.parseBoolean(String.valueOf(body.get("isActive"))));
        } else {
            n.setIsActive(Boolean.TRUE);
        }
        n.setSort(Math.toIntExact(npcMapper.selectCount(
                new LambdaQueryWrapper<SysAiWorldNpc>().eq(SysAiWorldNpc::getWorldId, worldId))));
        n.setUserId(AiUserContext.requireUserId());
        n.setCreateTime(LocalDateTime.now());
        n.setUpdateTime(LocalDateTime.now());
        npcMapper.insert(n);
        evictWorld(worldId);
        return n;
    }

    @Override
    public SysAiWorldNpc updateNpc(Long id, Map<String, Object> body) {
        SysAiWorldNpc n = npcMapper.selectById(id);
        if (n == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "世界NPC不存在");
        }
        requireOwned(n.getWorldId());
        if (body.containsKey("sceneId")) {
            Object v = body.get("sceneId");
            n.setSceneId(v == null || "null".equals(String.valueOf(v))
                    ? null : Long.valueOf(String.valueOf(v)));
        }
        if (body.containsKey("nickname")) {
            Object v = body.get("nickname");
            n.setNickname(v == null || "null".equals(String.valueOf(v))
                    ? null : String.valueOf(v).trim());
        }
        if (body.containsKey("avatar")) {
            Object v = body.get("avatar");
            n.setAvatar(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v));
        }
        if (body.containsKey("modelOverride")) {
            Object v = body.get("modelOverride");
            n.setModelOverride(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v));
        }
        if (body.containsKey("temperature")) {
            Object v = body.get("temperature");
            n.setTemperature(v == null || "null".equals(String.valueOf(v))
                    ? null : Double.valueOf(String.valueOf(v)));
        }
        if (body.containsKey("persona")) {
            Object v = body.get("persona");
            n.setPersona(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v));
        }
        if (body.containsKey("isActive")) {
            n.setIsActive(Boolean.parseBoolean(String.valueOf(body.get("isActive"))));
        }
        if (body.get("sort") != null) {
            n.setSort(parseInt(body.get("sort"), n.getSort()));
        }
        n.setUpdateTime(LocalDateTime.now());
        npcMapper.updateById(n);
        evictWorld(n.getWorldId());
        return npcMapper.selectById(id);
    }

    @Override
    @Transactional
    public void removeNpc(Long id) {
        SysAiWorldNpc n = npcMapper.selectById(id);
        if (n == null) {
            return;
        }
        requireOwned(n.getWorldId());
        relationshipMapper.delete(new LambdaQueryWrapper<SysAiWorldRelationship>()
                .and(w -> w.eq(SysAiWorldRelationship::getFromNpcId, id)
                        .or().eq(SysAiWorldRelationship::getToNpcId, id)));
        npcMapper.deleteById(id);
        evictWorld(n.getWorldId());
    }

    /* ---------------- 关系 ---------------- */

    @Override
    public List<Map<String, Object>> listRelationships(Long worldId) {
        requireOwned(worldId);
        List<SysAiWorldRelationship> rows = relationshipMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldRelationship>()
                        .eq(SysAiWorldRelationship::getWorldId, worldId)
                        .orderByAsc(SysAiWorldRelationship::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiWorldRelationship r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("fromNpcId", r.getFromNpcId());
            row.put("toNpcId", r.getToNpcId());
            row.put("affinity", r.getAffinity());
            row.put("relationTag", r.getRelationTag());
            row.put("memo", r.getMemo());
            SysAiWorldNpc from = npcMapper.selectById(r.getFromNpcId());
            SysAiWorldNpc to = npcMapper.selectById(r.getToNpcId());
            row.put("fromName", npcDisplayName(from));
            row.put("toName", npcDisplayName(to));
            row.put("fromAvatar", npcDisplayAvatar(from));
            row.put("toAvatar", npcDisplayAvatar(to));
            result.add(row);
        }
        return result;
    }

    @Override
    public SysAiWorldRelationship updateRelationship(Long id, Map<String, Object> body) {
        SysAiWorldRelationship r = relationshipMapper.selectById(id);
        if (r == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "关系不存在");
        }
        requireOwned(r.getWorldId());
        if (body.containsKey("affinity")) {
            r.setAffinity(clampAffinity(parseInt(body.get("affinity"), r.getAffinity())));
        }
        if (body.containsKey("relationTag")) {
            Object v = body.get("relationTag");
            r.setRelationTag(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v).trim());
        }
        if (body.containsKey("memo")) {
            Object v = body.get("memo");
            r.setMemo(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v).trim());
        }
        r.setUpdateTime(LocalDateTime.now());
        relationshipMapper.updateById(r);
        evictWorld(r.getWorldId());
        return relationshipMapper.selectById(id);
    }

    @Override
    public int applyAffinity(Long worldId, Long fromNpcId, Long toNpcId, int delta) {
        requireOwned(worldId);
        if (fromNpcId == null || toNpcId == null || fromNpcId.equals(toNpcId)) {
            return 0;
        }
        SysAiWorldRelationship r = relationshipMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldRelationship>()
                        .eq(SysAiWorldRelationship::getWorldId, worldId)
                        .eq(SysAiWorldRelationship::getFromNpcId, fromNpcId)
                        .eq(SysAiWorldRelationship::getToNpcId, toNpcId));
        if (r == null) {
            r = new SysAiWorldRelationship();
            r.setWorldId(worldId);
            r.setFromNpcId(fromNpcId);
            r.setToNpcId(toNpcId);
            r.setAffinity(clampAffinity(delta));
            r.setUserId(AiUserContext.requireUserId());
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
            relationshipMapper.insert(r);
        } else {
            r.setAffinity(clampAffinity(r.getAffinity() + delta));
            r.setUpdateTime(LocalDateTime.now());
            relationshipMapper.updateById(r);
        }
        evictWorld(worldId);
        return r.getAffinity();
    }

    /* ---------------- 事件时间线 ---------------- */

    @Override
    public List<SysAiWorldEvent> listEvents(Long worldId, Long sceneId, Integer limit) {
        requireOwned(worldId);
        LambdaQueryWrapper<SysAiWorldEvent> w = new LambdaQueryWrapper<SysAiWorldEvent>()
                .eq(SysAiWorldEvent::getWorldId, worldId)
                .orderByDesc(SysAiWorldEvent::getId);
        if (sceneId != null) {
            w.eq(SysAiWorldEvent::getSceneId, sceneId);
        }
        if (limit != null && limit > 0) {
            w.last("LIMIT " + Math.min(limit, 200));
        }
        return eventMapper.selectList(w);
    }

    @Override
    public SysAiWorldEvent appendEvent(Long worldId, Long sceneId, String eventType,
                                       String roleIds, String title, String content) {
        SysAiWorldEvent e = new SysAiWorldEvent();
        e.setWorldId(worldId);
        e.setSceneId(sceneId);
        e.setEventType(eventType);
        e.setRoleIds(roleIds);
        e.setTitle(title);
        e.setContent(content);
        e.setUserId(AiUserContext.requireUserId());
        e.setCreateTime(LocalDateTime.now());
        eventMapper.insert(e);
        evictWorld(worldId);
        return e;
    }

    /* ---------------- 场景对话 ---------------- */

    @Override
    public List<SysAiWorldMessage> listMessages(Long worldId, Long sceneId) {
        requireOwned(worldId);
        return messageMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldMessage>()
                        .eq(SysAiWorldMessage::getWorldId, worldId)
                        .eq(SysAiWorldMessage::getSceneId, sceneId)
                        .orderByAsc(SysAiWorldMessage::getId));
    }

    @Override
    public SysAiWorldMessage saveMessage(SysAiWorldMessage message) {
        messageMapper.insert(message);
        evictWorld(message.getWorldId());
        return message;
    }

    @Override
    public void evictWorldCaches(Long worldId) {
        if (worldId == null) {
            return;
        }
        String userId = AiUserContext.currentUserId();
        Cache view = cache();
        if (view != null) {
            view.evict(worldId + ":" + userId);
        }
        Cache list = listCache();
        if (list != null) {
            list.evict("list:" + userId);
        }
    }

    /* ---------------- 内部 ---------------- */

    private SysAiWorld requireOwned(Long id) {
        SysAiWorld w = worldMapper.selectById(id);
        if (w == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "世界不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        if (!AiUserContext.requireUserId().equals(w.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "不可操作他人的世界");
        }
        return w;
    }

    private Map<String, Object> worldRow(SysAiWorld w) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", w.getId());
        row.put("name", w.getName());
        row.put("description", w.getDescription());
        row.put("era", w.getEra());
        row.put("weather", w.getWeather());
        row.put("timeLabel", w.getTimeLabel());
        row.put("tickCount", w.getTickCount());
        row.put("status", w.getStatus());
        Long sceneCount = sceneMapper.selectCount(
                new LambdaQueryWrapper<SysAiWorldScene>().eq(SysAiWorldScene::getWorldId, w.getId()));
        Long npcCount = npcMapper.selectCount(
                new LambdaQueryWrapper<SysAiWorldNpc>().eq(SysAiWorldNpc::getWorldId, w.getId()));
        row.put("sceneCount", sceneCount == null ? 0 : sceneCount);
        row.put("npcCount", npcCount == null ? 0 : npcCount);
        SysAiWorldEvent last = eventMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldEvent>()
                        .eq(SysAiWorldEvent::getWorldId, w.getId())
                        .orderByDesc(SysAiWorldEvent::getId)
                        .last("LIMIT 1"));
        row.put("lastEventTitle", last == null ? null : last.getTitle());
        row.put("createTime", w.getCreateTime());
        return row;
    }

    private Map<String, Object> npcRow(SysAiWorldNpc n) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", n.getId());
        row.put("worldId", n.getWorldId());
        row.put("roleId", n.getRoleId());
        row.put("sceneId", n.getSceneId());
        row.put("nickname", n.getNickname());
        row.put("avatar", n.getAvatar());
        row.put("modelOverride", n.getModelOverride());
        row.put("temperature", n.getTemperature());
        row.put("persona", n.getPersona());
        row.put("isActive", Boolean.TRUE.equals(n.getIsActive()));
        SysAiRole role = roleMapper.selectById(n.getRoleId());
        if (role != null) {
            row.put("roleName", role.getName());
            row.put("sysPrompt", role.getSystemPrompt());
            row.put("roleModel", role.getModel());
        }
        row.put("displayName", npcDisplayName(n));
        row.put("displayAvatar", npcDisplayAvatar(n));
        return row;
    }

    /** NPC 展示名：世界内昵称优先，其次角色名 */
    private String npcDisplayName(SysAiWorldNpc n) {
        if (n == null) {
            return "未知";
        }
        if (n.getNickname() != null && !n.getNickname().isBlank()) {
            return n.getNickname();
        }
        SysAiRole role = roleMapper.selectById(n.getRoleId());
        return role != null && role.getName() != null ? role.getName() : "NPC";
    }

    /** NPC 展示头像：世界内头像覆盖优先，其次角色头像 */
    private String npcDisplayAvatar(SysAiWorldNpc n) {
        if (n == null) {
            return null;
        }
        if (n.getAvatar() != null && !n.getAvatar().isBlank()) {
            return n.getAvatar();
        }
        SysAiRole role = roleMapper.selectById(n.getRoleId());
        return role != null ? role.getAvatar() : null;
    }

    private static int clampAffinity(int v) {
        return Math.max(-100, Math.min(100, v));
    }

    private Cache cache() {
        return cacheManager.getCache(VIEW_CACHE);
    }

    private Cache listCache() {
        return cacheManager.getCache(LIST_CACHE);
    }

    private void evictWorld(Long worldId) {
        if (worldId == null || cache() == null) {
            return;
        }
        cache().evict(worldId + ":" + AiUserContext.requireUserId());
    }

    private void evictWorldList() {
        if (listCache() == null) {
            return;
        }
        listCache().evict("list:" + AiUserContext.requireUserId());
    }

    private static int parseInt(Object v, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }
}