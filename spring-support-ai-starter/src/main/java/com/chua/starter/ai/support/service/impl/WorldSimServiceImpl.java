// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiWorld;
import com.chua.starter.ai.support.entity.SysAiWorldAct;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import com.chua.starter.ai.support.entity.SysAiWorldMessage;
import com.chua.starter.ai.support.entity.SysAiWorldNpc;
import com.chua.starter.ai.support.entity.SysAiWorldRelationship;
import com.chua.starter.ai.support.entity.SysAiWorldScene;
import com.chua.starter.ai.support.generate.GenerateService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldActMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldEventMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldMessageMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldNpcMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldRelationshipMapper;
import com.chua.starter.ai.support.mapper.SysAiWorldSceneMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.WorldSimService;
import com.chua.starter.ai.support.service.act.ActScript;
import com.chua.starter.ai.support.service.act.StoryStoreService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.text.json.Json;
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

    /** 剧本落盘服务 */
    private final StoryStoreService storyStore;

    /** 幕次持久化映射器 */
    private final SysAiWorldActMapper actMapper;

    /** AI 对话门面（编剧 / 表演） */
    private final AiChatFacadeService aiChatFacadeService;

    /** 媒体生成服务（文生图 / 文生视频） */
    private final GenerateService generateService;

    /** 全局设置读取器（sim 分组） */
    private final GlobalSettingReader globalSettingReader;

    /** 生效对话模型解析器 */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

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
     * @param actMapper            幕次
     * @param storyStore           剧本落盘服务
     * @param aiChatFacadeService  AI 对话门面（编剧 / 表演）
     * @param generateService      媒体生成服务（文生图 / 文生视频）
     * @param globalSettingReader  全局设置读取器
     * @param effectiveChatModelResolver  生效对话模型解析器
     */
    public WorldSimServiceImpl(SysAiWorldMapper worldMapper,
                               SysAiWorldSceneMapper sceneMapper,
                               SysAiWorldNpcMapper npcMapper,
                               SysAiWorldRelationshipMapper relationshipMapper,
                               SysAiWorldEventMapper eventMapper,
                               SysAiWorldMessageMapper messageMapper,
                               SysAiRoleMapper roleMapper,
                               @Qualifier("aiCacheManager") CacheManager cacheManager,
                               SysAiWorldActMapper actMapper,
                               StoryStoreService storyStore,
                               AiChatFacadeService aiChatFacadeService,
                               GenerateService generateService,
                               GlobalSettingReader globalSettingReader,
                               EffectiveChatModelResolver effectiveChatModelResolver) {
        this.worldMapper = worldMapper;
        this.sceneMapper = sceneMapper;
        this.npcMapper = npcMapper;
        this.relationshipMapper = relationshipMapper;
        this.eventMapper = eventMapper;
        this.messageMapper = messageMapper;
        this.roleMapper = roleMapper;
        this.cacheManager = cacheManager;
        this.actMapper = actMapper;
        this.storyStore = storyStore;
        this.aiChatFacadeService = aiChatFacadeService;
        this.generateService = generateService;
        this.globalSettingReader = globalSettingReader;
        this.effectiveChatModelResolver = effectiveChatModelResolver;
    }

    /* ---------------- 世界 ---------------- */

    @Override
    public List<Map<String, Object>> listWorlds() {
        String userId = AiUserContext.requireUserId();
        // 必须写进同名列表缓存：失效端 evictWorldList()/evictActCaches() 走的是 listCache()
        // （aiWorldList）。此前这里误用 cache()（aiWorldView），导致列表条目写进视图缓存、
        // 却从不被失效 —— 新建世界不出现、推进时间后列表仍是旧时间，直到 30 分钟 TTL 到期。
        Cache cache = listCache();
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
    /**
     * delete World
     * @param id 长整数
     * @return 返回值
    */
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
    /**
     * remove Npc
     * @param id 长整数
     * @return 返回值
    */
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
    /**
     * require Owned
     * @param id 长整数
     * @return 返回值
    */
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

    /**
     * world Row
     * @param w 参数
     * @return 返回值
    */
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

    /**
     * npc Row
     * @param n 参数
     * @return 返回值
    */
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

    /**
     * npc Display Name
     * @param n 参数
     * @return 返回值
    */
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

    /**
     * npc Display Avatar
     * @param n 参数
     * @return 返回值
    */
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

    /**
     * clamp Affinity
     * @param v 整数
     * @return 返回值
    */
    private static int clampAffinity(int v) {
        return Math.max(-100, Math.min(100, v));
    }

    /**
     * cache
     * @return 返回值
    */
    private Cache cache() {
        return cacheManager.getCache(VIEW_CACHE);
    }

    /**
     * list Cache
     * @return 返回值
    */
    private Cache listCache() {
        return cacheManager.getCache(LIST_CACHE);
    }

    /**
     * evict World
     * @param worldId 长整数
     * @return 返回值
    */
    private void evictWorld(Long worldId) {
        if (worldId == null) {
            return;
        }
        String userId = AiUserContext.requireUserId();
        Cache view = cache();
        if (view != null) {
            view.evict(worldId + ":" + userId);
        }
        // 列表行的场景数 / NPC 数 / 最近事件标题 / 名称都来自同一批写接口，
        // 只失效视图会让列表卡在旧快照上（新建世界看不见、加 NPC 后人物数不变）。
        Cache list = listCache();
        if (list != null) {
            list.evict("list:" + userId);
        }
    }

    /**
     * evict World List
     * @return 返回值
    */
    private void evictWorldList() {
        if (listCache() == null) {
            return;
        }
        listCache().evict("list:" + AiUserContext.requireUserId());
    }

    /**
     * parse Int
     * @param fallback 整数
     * @param v 对象
     * @return 返回值
    */
    private static int parseInt(Object v, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    /* ---------------- 剧本幕次 ---------------- */

    /** 幕次状态：编剧书写 */
    private static final String ACT_STATUS_WRITING = "writing";
    /** 幕次状态：场景、过场、道具的素材已充实 */
    private static final String ACT_STATUS_PROPS_OK = "props_ok";
    /** 幕次状态：表演（电影/话剧） */
    private static final String ACT_STATUS_PLAYING = "playing";
    /** 幕次状态：已可重播 */
    private static final String ACT_STATUS_REPLAY = "replay";
    /** 幕次状态：已完结 */
    private static final String ACT_STATUS_FINISHED = "finished";
    /** 幕次状态：已废弃 */
    private static final String ACT_STATUS_ABORTED = "aborted";

    /** 系统提示词（AI 编剧必须输出严格 JSON 剧本） */
    private static final String SCRIPT_PROMPT_SYSTEM_SANGO =
        "你必须且只能输出严格合法的 JSON（不要输出 Markdown、不要输出代码块、不要输出额外文字）。"
        + "输出字段固定为：actNo（幕次编号，从 1 开始）、title（幕标题）、description（剧本概述）"
        + "、worldTime（世界时间标签）、weather（天气）、scenes（场景列表）和 transitions（过场列表）"
        + "与 props（道具列表）。每个场景含 sceneId/name/desc/npcs/lines/playText/mediaImage/mediaVideo；"
        + "mediaImage/mediaVideo 结构为 {taskId, url}（初始均为 null）。不要输出其他字段。";

    /** 媒体生成类型：图 */
    private static final String MEDIA_IMAGE_TYPE = "image";
    /** 媒体生成类型：视频 */
    private static final String MEDIA_VIDEO_TYPE = "video";
    /** 默认生成图片尺寸 */
    private static final String MEDIA_SIZE = "1024x1024";
    /** 单笔生成数量 */
    private static final int MEDIA_COUNT = 1;
    /** 素材状态：未开始（剧本中还没有任务号） */
    private static final String MEDIA_NOT_STARTED = "NOT_STARTED";
    /** 素材状态：无需求（没有可写进提示词的描述） */
    private static final String MEDIA_NO_PROMPT = "NO_PROMPT";
    /** 素材状态：成功（已生成） */
    private static final String MEDIA_OK = "OK";

    /* ========================================================================
     * 查询
     * ====================================================================== */

    @Override
    public List<Map<String, Object>> listActs(Long worldId) {
        requireOwned(worldId);
        List<SysAiWorldAct> acts = actMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .orderByAsc(SysAiWorldAct::getActNo));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiWorldAct a : acts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("actNo", a.getActNo());
            row.put("title", a.getTitle());
            row.put("status", a.getStatus());
            row.put("stage", a.getStage());
            row.put("progress", a.getProgress());
            row.put("ended", a.getEnded());
            row.put("hasScript", storyStore.readAct(worldId, a.getActNo()) != null);
            result.add(row);
        }
        return result;
    }

    @Override
    public SysAiWorldAct createAct(Long worldId) {
        requireOwned(worldId);
        SysAiWorldAct act = new SysAiWorldAct();
        act.setWorldId(worldId);
        act.setActNo(actNextNo(worldId));
        act.setTitle("未命名幕次");
        act.setDescription("");
        act.setStatus(ACT_STATUS_WRITING);
        act.setStage(ACT_STATUS_WRITING);
        act.setProgress(0);
        act.setEnded(0);
        act.setCreateTime(LocalDateTime.now());
        act.setUpdateTime(LocalDateTime.now());
        actMapper.insert(act);
        // 创建世界目录并落盘一个空剧本文件
        storyStore.ensureWorldDir(worldId);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("actNo", act.getActNo());
        storyStore.writeAct(worldId, act.getActNo(), Json.toJson(draft));
        evictActCaches(worldId);
        return actMapper.selectById(act.getId());
    }

    @Override
    public Map<String, Object> actDetail(Long worldId, Integer actNo) {
        requireOwned(worldId);
        SysAiWorldAct act = actMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .eq(SysAiWorldAct::getActNo, actNo));
        if (act == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("act", actRow(act));
        String script = storyStore.readAct(worldId, actNo);
        if (script != null && !script.isBlank()) {
            detail.put("script", script);
            detail.put("assets", assetProgressJson(script));
        }
        return detail;
    }

    /* ========================================================================
     * 推进（writing → props_ok → playing → replay）
     * ====================================================================== */

    @Override
    public Map<String, Object> advanceAct(Long worldId, Integer actNo) {
        requireOwned(worldId);
        SysAiWorldAct act = actMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .eq(SysAiWorldAct::getActNo, actNo));
        if (act == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次不存在");
        }
        if (ACT_STATUS_FINISHED.equals(act.getStatus())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次已完结，无法推进");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        switch (act.getStage()) {
            case ACT_STATUS_WRITING -> {
                // ── 编剧书写 ── 调用 AI 编剧，生成完整剧本并落盘文件
                String generated = writeScript(worldId, act);
                storyStore.writeAct(worldId, actNo, generated);
                act.setStage(ACT_STATUS_PROPS_OK);
                act.setUpdateTime(LocalDateTime.now());
                actMapper.updateById(act);
                out.put("act", actRow(act));
                out.put("script", generated);
            }
            case ACT_STATUS_PROPS_OK -> {
                // ── 道具就绪 ── 逐一扫生成场景/过场/道具的媒体素材（一次只跑一个任务）
                String script = storyStore.readAct(worldId, actNo);
                if (script == null || script.isBlank()) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有剧本可生成素材");
                }
                // 推进一个（或全部已完成）：null 表示 assets 已全部就位
                Map<String, Object> progress = tryAdvanceAsset(worldId, actNo, script, act);
                if (progress == null) {
                    // 素材已全部就位（全部 OK / NO_PROMPT）：直接进入出演阶段
                    act.setStage(ACT_STATUS_PLAYING);
                    act.setStatus(ACT_STATUS_PLAYING);
                    act.setProgress(0);
                }
                act.setUpdateTime(LocalDateTime.now());
                actMapper.updateById(act);
                out.put("act", actRow(act));
                if (progress != null) {
                    out.putAll(progress);
                }
            }
            case ACT_STATUS_PLAYING -> {
                // ── 表演：逐场景生成叙述（一镜到底），完成后标记可重播
                String script = storyStore.readAct(worldId, actNo);
                if (script == null || script.isBlank()) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有剧本可表演");
                }
                String played = playAct(worldId, actNo, script, act);
                storyStore.writeAct(worldId, actNo, played);
                String userId = AiUserContext.requireUserId();
                storyStore.writeStory(worldId, userId, actNo, playStory(worldId, actNo, played));
                act.setStage(ACT_STATUS_REPLAY);
                act.setStatus(ACT_STATUS_REPLAY);
                act.setProgress(scenesDone(played));
                act.setUpdateTime(LocalDateTime.now());
                actMapper.updateById(act);
                out.put("act", actRow(act));
                out.put("playedScenes", act.getProgress());
            }
            default ->
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次阶段为：" + act.getStage() + "，无法推进");
        }
        return out;
    }

    @Override
    public Map<String, Object> replayAct(Long worldId, Integer actNo) {
        requireOwned(worldId);
        SysAiWorldAct act = actMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .eq(SysAiWorldAct::getActNo, actNo));
        if (act == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次不存在");
        }
        if (!ACT_STATUS_REPLAY.equals(act.getStage()) && !ACT_STATUS_FINISHED.equals(act.getStage())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "已演过或已播的幕次才能重播");
        }
        String script = storyStore.readAct(worldId, actNo);
        if (script == null || script.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有剧本可重播");
        }
        // 表演阶段回滚到 playing，所见过的素材保留
        Map<String, Object> json = Json.fromJson(script, Map.class);
        json.put("status", ACT_STATUS_PLAYING);
        storyStore.writeAct(worldId, actNo, Json.toJson(json));
        // 用户侧故事片段回滚（已看过的不算，只删该幕）
        storyStore.deleteStory(worldId, AiUserContext.requireUserId(), actNo);
        act.setStage(ACT_STATUS_PLAYING);
        act.setStatus(ACT_STATUS_REPLAY);
        act.setProgress(0);
        act.setUpdateTime(LocalDateTime.now());
        actMapper.updateById(act);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("act", actRow(act));
        out.put("restart", true);
        return out;
    }

    @Override
    public SysAiWorldAct finishAct(Long worldId, Integer actNo) {
        requireOwned(worldId);
        SysAiWorldAct act = actMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .eq(SysAiWorldAct::getActNo, actNo));
        if (act == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次不存在");
        }
        if (!ACT_STATUS_REPLAY.equals(act.getStage()) && !ACT_STATUS_FINISHED.equals(act.getStage())) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "幕次尚未演完，无法结文");
        }
        act.setStatus(ACT_STATUS_FINISHED);
        act.setEnded(1);
        act.setUpdateTime(LocalDateTime.now());
        actMapper.updateById(act);
        // 写一条世界事件为幕次划上句号
        appendActEvent(worldId, act);
        evictActCaches(worldId);
        return actMapper.selectById(act.getId());
    }

    /* ========================================================================
     * 明价值：编剧
     * ====================================================================== */

    /**
     * 调用 AI 编剧写完整剧本，落盘幕次文件。
     *
     * @param worldId 世界ID
     * @param act     幕次行
     * @return 新剧本（JSON 文本）
     */
    private String writeScript(Long worldId, SysAiWorldAct act) {
        String prompt = composeScriptPrompt(worldId);
        AiChatRequest request = new AiChatRequest();
        request.setModel(scriptModel());
        request.setPrompt(prompt);
        request.setSystemPrompt(SCRIPT_PROMPT_SYSTEM_SANGO);
        request.setTemperature(0.85);
        request.setMaxTokens(8000);
        request.setUseSkill(false);
        AiChatResult result = aiChatFacadeService.callForResult(request);
        if (!result.success()) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "编剧生成失败：" + result.errorMessage());
        }
        String text = result.text();
        if (text == null || text.isBlank()) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "编剧返回为空");
        }
        return scriptFromText(text);
    }

    /**
     * 场景剧本提示词（世界 + NPC + 场景 + 关系摘要）
     *
     * @param worldId 世界ID
     * @return 提示词
     */
    private String composeScriptPrompt(Long worldId) {
        StringBuilder sb = new StringBuilder();
        SysAiWorld w = worldMapper.selectById(worldId);
        if (w == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "世界不存在");
        }
        sb.append("【剧本模拟世界】");
        sb.append("\n世界名：").append(w.getName());
        if (w.getEra() != null && !w.getEra().isBlank()) {
            sb.append("（时代：").append(w.getEra()).append("）");
        }
        if (w.getDescription() != null && !w.getDescription().isBlank()) {
            sb.append("\n世界背景：").append(w.getDescription());
        }
        sb.append("\n天气：").append(w.getWeather() == null ? "晴" : w.getWeather());
        sb.append("\n时间：").append(w.getTimeLabel() == null ? "第 1 天" : w.getTimeLabel());

        sb.append("\n\n【角色列表】（已登场的 NPC）");
        List<SysAiWorldNpc> npcs = npcMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldNpc>()
                        .eq(SysAiWorldNpc::getWorldId, worldId)
                        .orderByAsc(SysAiWorldNpc::getSort));
        for (SysAiWorldNpc n : npcs) {
            SysAiRole r = roleMapper.selectById(n.getRoleId());
            sb.append("\n");
            if (r != null) {
                sb.append("角色：").append(r.getName());
                sb.append("（年龄：").append(n.getAge() == null ? "-" : n.getAge()).append("）");
                sb.append("（性别：").append(n.getGender() == null ? "-" : n.getGender()).append("）");
                sb.append("（职业：").append(n.getProfession() == null ? "-" : n.getProfession()).append("）");
                sb.append("（人设：").append(n.getPersona() == null ? "-" : n.getPersona()).append("）");
            } else {
                sb.append("角色：未知（角色ID：").append(n.getRoleId()).append("）");
            }
            sb.append("；状态：").append(n.getStatus() == null ? "alive" : n.getStatus());
        }

        sb.append("\n\n【场景列表】（可在场景间自由连通）");
        List<SysAiWorldScene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldScene>()
                        .eq(SysAiWorldScene::getWorldId, worldId)
                        .orderByAsc(SysAiWorldScene::getSort)
                        .orderByAsc(SysAiWorldScene::getId));
        for (SysAiWorldScene s : scenes) {
            sb.append("\n场景：").append(s.getName());
            if (s.getDescription() != null && !s.getDescription().isBlank()) {
                sb.append("（").append(s.getDescription()).append("）");
            }
        }

        sb.append("\n\n【关系网摘要（影响角色的言行）");
        List<SysAiWorldRelationship> rels = relationshipMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldRelationship>()
                        .eq(SysAiWorldRelationship::getWorldId, worldId));
        for (SysAiWorldRelationship r : rels) {
            if (r.getAffinity() == null || r.getAffinity() == 0) continue;
            sb.append("\n").append(r.getFromNpcId()).append(" → ").append(r.getToNpcId())
                .append("（").append(r.getAffinity()).append("）");
        }

        sb.append("\n\n【约束】");
        sb.append("\n1. 剧情必须使用以上角色，不可凭空增加新角色。");
        sb.append("\n2. 每个场景的 desc（画面描述）要足够具体（色调/光影/人物动作）。");
        sb.append("\n3. 每 2~3 个场景之间要有一个过场（transitions）。");
        sb.append("\n4. 道具要有描述和关联的位置。");
        sb.append("\n5. 台词要符合角色人设与当前关系。");
        sb.append("\n6. 只输出 JSON，不要解释。");
        return sb.toString();
    }

    /** 编剧单元模型（读设置 sim.script_model，留空则用生效对话模型） */
    private String scriptModel() {
        String model = globalSettingReader.getStr("sim", "script_model");
        if (model == null || model.isBlank()) {
            return effectiveChatModelResolver.resolve(null);
        }
        return model;
    }

    /**
     * 从 AI 文本解析剧本 JSON。
     *
     * @param text AI 返回的文本
     * @return 合格的剧本 JSON 文本
     */
    private String scriptFromText(String text) {
        String trimmed = text.trim();
        // 清理 Markdown 代码块遮蔽（```json ... ```）
        if (trimmed.startsWith("```")) {
            String body = trimmed.substring(3);
            int end = body.indexOf("```");
            if (end > 0) {
                body = body.substring(0, end);
            }
            trimmed = body.trim();
        }
        try {
            Object parsed = Json.fromJson(trimmed, Map.class);
            if (parsed instanceof Map) {
                return Json.toJson(parsed);
            }
            return trimmed;
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "剧本 JSON 解析失败：" + e.getMessage());
        }
    }

    /* ========================================================================
     * 明价值：素材（场景/过场/道具的图像/视频）
     * ====================================================================== */

    /**
     * 道具就绪推进：每步只处理一个媒体素材（生成服务一次只能有一个任务在跑），
     * 返回 null 表示素材已全部就绪。
     *
     * @param worldId 世界ID
     * @param actNo   幕次编号
     * @param script  剧本 JSON 文本
     * @param act     幕次行
     * @return 推进结果（含 total/done/pending/taskId）；null 表示可进入下一阶段
     */
    private Map<String, Object> tryAdvanceAsset(Long worldId, Integer actNo, String script, SysAiWorldAct act) {
        Map<String, Object> json = Json.fromJson(script, Map.class);
        List<Material> materials = mediaItems(json);

        // 第一阶段：查封已有任务的 RETURN 状态（成功/失败/悬而未决）
        for (Material mat : materials) {
            String taskId = mat.taskId();
            if (taskId == null || taskId.isBlank()) continue;
            SysAiGenerateTask task = generateService.query(taskId);
            if (task == null) continue;
            mat.head().put("taskId", task.getTaskId());
            mat.head().put("status", task.getStatus());
            if ("SUCCESS".equals(task.getStatus())) {
                List<String> urls = GenerateService.mediaUrls(task);
                mat.head().put("url", urls == null || urls.isEmpty() ? null : urls.get(0));
            }
        }

        boolean inProgress = false;
        Material target = null;
        for (Material mat : materials) {
            Map<String, Object> head = mat.head();
            if (MEDIA_OK.equals(head.get("status"))) continue;
            String taskId = mat.taskId();
            if (taskId != null) {
                // 有任务号：由上方的 query 回填，status 为终态则一定会被标记为 OK
                if (!"FAILED".equals(head.get("status")) && !"CANCELED".equals(head.get("status"))) {
                    // 状态 PENDING/RUNNING：任务还在生成中，按约束一次只允许一个任务
                    inProgress = true;
                }
                continue;
            }
            // 没有任务号：是否有素材需求
            if (String.valueOf(head.get("prompt")).isBlank()) {
                head.put("status", MEDIA_NO_PROMPT);
                continue;
            }
            target = mat;
            break;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", materials.size());
        int done = 0;
        for (Material mat : materials) {
            if (MEDIA_OK.equals(mat.head().get("status"))) done++;
        }
        out.put("done", done);
        out.put("pending", materials.size() - done);

        // 全部落在 OK / NO_PROMPT 上：进入下一阶段（表演）
        // 注意：NO_PROMPT 的素材头此前只在内存里改了 status，没有写回剧本，
        // 前端因此拿不到素材状态（素材区一片空白）。这里先落盘再返回。
        if (target == null) {
            if (inProgress) return out;
            for (Material m : materials) m.persist();
            storyStore.writeAct(worldId, actNo, Json.toJson(json));
            return null;
        }
        if (inProgress) {
            // 有任务还在生成中，不能叠新的，由服务端生命周期调遣
            return out;
        }

        // 第一个未跑的素材，造了它
        Material mat = target;
        SysAiGenerateTask task = createAssetTask(
                String.valueOf(mat.head().get("prompt")), String.valueOf(mat.head().get("type")));
        mat.head().put("taskId", task.getTaskId());
        mat.head().put("status", task.getStatus());
        if ("SUCCESS".equals(task.getStatus())) {
            List<String> urls = GenerateService.mediaUrls(task);
            mat.head().put("url", urls == null || urls.isEmpty() ? null : urls.get(0));
        }
        // 把媒体头写回剧本节点并落盘
        for (Material m : materials) m.persist();
        storyStore.writeAct(worldId, actNo, Json.toJson(json));
        out.put("taskId", task.getTaskId());
        out.put("inProgress", true);
        return out;
    }

    /** 媒体序列 / 它落盘的剧本节点。 */
    private List<Material> mediaItems(Map<String, Object> scriptJson) {
        List<Material> items = new ArrayList<>();
        collect(ActScript.SCENES, scriptJson, items, "scene", "desc");
        collect(ActScript.TRANSITIONS, scriptJson, items, "transition", "text");
        collect(ActScript.PROPS, scriptJson, items, "prop", "name");
        return items;
    }

    /**
     * 从一组列表里拆出每个节点的媒体通道。
     *
     * <p>素材通道长这样（媒体号随走）：<pre>
     * mediaImage = {taskId?, url?, status, prompt, type, role, field}
     * </pre></p>
     *
     * @param key       剧本列表键
     * @param nameField 取提示词的字段（场景 desc / 过场 text / 道具 name）
     */
    private void collect(String key, Map<String, Object> scriptJson, List<Material> items,
                          String role, String nameField) {
        Object v = scriptJson.get(key);
        if (!(v instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) m;
            Object rn = node.get("name");
            if (rn == null) rn = node.get("order");
            String roleName = String.valueOf(rn);
            for (String mk : new String[]{ActScript.MEDIA_IMAGE_KEY, ActScript.MEDIA_VIDEO_KEY}) {
                Map<String, Object> head;
                if (node.containsKey(mk) && node.get(mk) instanceof Map) {
                    // 复用 JSON 树里活着的媒体头（修改会直接反映回剧本）
                    head = (Map<String, Object>) node.get(mk);
                } else {
                    head = new LinkedHashMap<>();
                    head.put("status", MEDIA_NOT_STARTED);
                    head.put("role", role + "/" + roleName);
                    head.put("field", mk);
                }
                Object pn = node.get(nameField);
                head.put("prompt", String.valueOf(pn));
                head.put("type", mk.equals(ActScript.MEDIA_IMAGE_KEY) ? MEDIA_IMAGE_TYPE : MEDIA_VIDEO_TYPE);
                items.add(new Material(node, mk, head));
            }
        }
    }

    /** 生成（图/视频）媒体任务。 */
    private SysAiGenerateTask createAssetTask(String prompt, String type) {
        String model = scriptModel();
        if (model == null || model.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请配置编剧模型（sim.script_model 或生效对话模型）");
        }
        GenerateService.Request request = new GenerateService.Request(
                type, model, prompt, null, MEDIA_SIZE, MEDIA_COUNT, null, null, null, null, null, null,
                AiUserContext.requireUserId());
        return generateService.create(request);
    }

    /** 剧本资产完成度 */
    private Object assetProgressJson(String script) {
        try {
            List<Material> items = mediaItems(Json.fromJson(script, Map.class));
            int total = items.size();
            int done = 0;
            for (Material mat : items) {
                if (MEDIA_OK.equals(mat.head().get("status"))) done++;
            }
            return java.util.Map.of("total", total, "done", done);
        } catch (Exception e) {
            return script;
        }
    }

    /* ========================================================================
     * 明价值：表演
     * ====================================================================== */

    /**
     * 逐场景表演叙述（一镜到底），每场景一次对话调用
     */
    private String playAct(Long worldId, Integer actNo, String script, SysAiWorldAct act) {
        Map<String, Object> json = Json.fromJson(script, Map.class);
        List<Map<String, Object>> scenes = sceneList(json.get(ActScript.SCENES));
        if (scenes.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有可表演的场景");
        }
        SysAiWorld world = worldMapper.selectById(worldId);
        for (Map<String, Object> scene : scenes) {
            String name = str(scene.get(ActScript.SCENE_NAME), "场景");
            String prompt =
                "你是一位电影/话剧导演。请根据以下剧本场景生成一段不超过 300 字的表演叙述（对话 + 动作 + 神态），"
                + "风格：古典历史剧；不要输出任何 JSON 与 HTML，只输出纯文本。"
                + "\n\n【场景】" + name
                + "\n【世界】" + (world == null ? "世界" : world.getName() + (world.getEra() == null ? "" : "（" + world.getEra() + "）"))
                + "\n【画面描述】" + str(scene.get(ActScript.SCENE_DESC), "")
                + "\n【演员与关系】" + npcsLabel(worldId, scene.get(ActScript.SCENE_NPCS));
            AiChatRequest request = new AiChatRequest();
            request.setModel(scriptModel());
            request.setPrompt(prompt);
            request.setUseSkill(false);
            request.setTemperature(0.8);
            request.setMaxTokens(2000);
            AiChatResult result = aiChatFacadeService.callForResult(request);
            if (result.success() && result.text() != null && !result.text().isBlank()) {
                scene.put(ActScript.SCENE_PLAY, result.text());
            }
        }
        json.put(ActScript.ACT_STATUS, ACT_STATUS_PLAYING);
        return Json.toJson(json);
    }

    /** 把表演后的剧本转成用户侧的故事片段 */
    private String playStory(Long worldId, Integer actNo, String script) {
        Map<String, Object> json = Json.fromJson(script, Map.class);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("actNo", actNo);
        head.put("worldId", worldId);
        head.put("title", str(json.get("title"), "未命名幕次"));
        head.put("status", ACT_STATUS_PLAYING);
        head.put("progress", scenesDone(script));
        List<Map<String, Object>> scenes = sceneList(json.get(ActScript.SCENES));
        List<Map<String, Object>> content = new ArrayList<>();
        for (Map<String, Object> s : scenes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sceneId", s.get(ActScript.SCENE_SCENE_ID));
            m.put("name", str(s.get(ActScript.SCENE_NAME), "场景"));
            m.put("playText", str(s.get(ActScript.SCENE_PLAY), ""));
            m.put("mediaImage", s.get(ActScript.SCENE_MEDIA_IMAGE));
            m.put("mediaVideo", s.get(ActScript.SCENE_MEDIA_VIDEO));
            content.add(m);
        }
        head.put("scenes", content);
        return Json.toJson(head);
    }

    /* ========================================================================
     * 私有辅助
     * ====================================================================== */

    /** 补正幕次编号（下一个幕次编号） */
    private int actNextNo(Long worldId) {
        SysAiWorldAct one = actMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .orderByDesc(SysAiWorldAct::getActNo)
                        .last("LIMIT 1"));
        return one == null ? 1 : (one.getActNo() == null ? 1 : one.getActNo() + 1);
    }

    /** 幕次行 → JSON 展示行 */
    private Map<String, Object> actRow(SysAiWorldAct act) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", act.getId());
        row.put("worldId", act.getWorldId());
        row.put("actNo", act.getActNo());
        row.put("title", act.getTitle());
        row.put("status", act.getStatus());
        row.put("stage", act.getStage());
        row.put("progress", act.getProgress());
        row.put("ended", act.getEnded() != null ? act.getEnded() : 0);
        row.put("createTime", act.getCreateTime());
        row.put("updateTime", act.getUpdateTime());
        return row;
    }

    /** 幕次列表维度的缓存失效 */
    public void evictActCaches(Long worldId) {
        if (worldId == null) return;
        String userId = AiUserContext.currentUserId();
        if (userId == null) return;
        Cache view = cache();
        if (view != null) {
            view.evict(worldId + ":" + userId);
        }
        Cache list = listCache();
        if (list != null) {
            list.evict("list:" + userId);
        }
    }

    /** 角色（世界 NPC）标签 */
    private String npcsLabel(Long worldId, Object npcsObj) {
        if (npcsObj == null || !(npcsObj instanceof List<?> l)) return "-";
        StringBuilder sb = new StringBuilder();
        for (Object id : l) {
            Long idNum = parseLong(id);
            if (idNum == null || idNum == 0L) continue;
            SysAiWorldNpc n = npcMapper.selectById(idNum);
            if (n == null) continue;
            SysAiRole r = roleMapper.selectById(n.getRoleId());
            if (r != null && sb.length() > 0) sb.append(", ");
            if (r != null) sb.append(r.getName());
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** 安全转 Long */
    private static Long parseLong(Object o) {
        try {
            Long v = Long.valueOf(String.valueOf(o));
            return v == null || v == 0L ? 0L : v;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 字符串安全补 */
    private static String str(Object o, String fallback) {
        if (o == null) return fallback;
        if (o instanceof String s) return s.trim().isEmpty() ? fallback : s.trim();
        return String.valueOf(o);
    }

    /** 场景列表（返回剧本 JSON 树里的活动节点，写回即落盘） */
    private List<Map<String, Object>> sceneList(Object o) {
        if (o == null || !(o instanceof List<?> l)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : l) {
            if (!(item instanceof Map<?, ?> m)) continue;
            boolean stringKeys = true;
            for (Object k : m.keySet()) {
                if (!(k instanceof String)) {
                    stringKeys = false;
                    break;
                }
            }
            if (stringKeys) {
                @SuppressWarnings("unchecked")
                Map<String, Object> live = (Map<String, Object>) m;
                out.add(live);
            } else {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                out.add(copy);
            }
        }
        return out;
    }

    /** 计算已完成的场景数（有 playText 的场景） */
    private int scenesDone(String script) {
        try {
            Map<String, Object> json = Json.fromJson(script, Map.class);
            List<Map<String, Object>> scenes = sceneList(json.get(ActScript.SCENES));
            int done = 0;
            for (Map<String, Object> s : scenes) {
                if (str(s.get(ActScript.SCENE_PLAY), null) != null) done++;
            }
            return done;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 写幕次 finished 消息 */
    private SysAiWorldEvent appendActEvent(Long worldId, SysAiWorldAct act) {
        SysAiWorldEvent e = new SysAiWorldEvent();
        e.setWorldId(worldId);
        e.setSceneId(null);
        e.setEventType("act_finished");
        e.setRoleIds(null);
        e.setTitle("幕次 " + act.getActNo() + " 已完结" + (act.getTitle() == null ? "" : "：" + act.getTitle()));
        e.setContent("下一次");
        e.setUserId(AiUserContext.requireUserId());
        e.setCreateTime(LocalDateTime.now());
        eventMapper.insert(e);
        evictWorld(e.getWorldId());
        return e;
    }

    /* ========================================================================
     * 素材载体
     * ====================================================================== */

    /**
     * 一段素材，与它落盘的剧本节点锁定在一起。
     *
     * <p><pre>
     * node  = JSON 树中的任意场景/过场/道具节点（活着，改起来直接反映回原 JSON 树）
     * field = "mediaImage" 或 "mediaVideo"
     * head  = 节点上在该 field 的媒体头：
     *   {taskId, url, status, prompt, type, role, field}
     *   若节点上还没有这一段通道，则 new head 之后通过 {@link #persist()} 挂上
     * </pre></p>
     *
     * @param node 所属的剧本节点
     * @param field 媒体通道
     * @param head 媒体头
     */
    private static final class Material {

        private final Map<String, Object> node;

        private final String field;

        private final Map<String, Object> head;

        private Material(Map<String, Object> node, String field, Map<String, Object> head) {
            this.node = node;
            this.field = field;
            this.head = head;
        }

        /** 媒体头 */
        public Map<String, Object> head() {
            return head;
        }

        /** 任务号 */
        public String taskId() {
            return (String) head.get("taskId");
        }

        /** 把自身写回剧本节点 */
        public void persist() {
            if (!node.containsKey(field)) {
                node.put(field, head);
            }
        }

        @Override
        public String toString() {
            return "Material{" + field + "=" + head + "}";
        }
    }
}