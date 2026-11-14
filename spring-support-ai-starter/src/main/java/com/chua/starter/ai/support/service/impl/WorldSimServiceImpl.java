// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.controller.GenerateMediaController;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiWorld;
import com.chua.starter.ai.support.entity.SysAiWorldAct;
import com.chua.starter.ai.support.entity.SysAiWorldEvent;
import com.chua.starter.ai.support.entity.SysAiWorldMessage;
import com.chua.starter.ai.support.entity.SysAiWorldNpc;
import com.chua.starter.ai.support.entity.SysAiWorldRelationship;
import com.chua.starter.ai.support.entity.SysAiWorldScene;
import com.chua.starter.ai.support.generate.GenerateClientResolver;
import com.chua.starter.ai.support.generate.GenerateConfigSupport;
import com.chua.starter.ai.support.generate.GenerateMediaStore;
import com.chua.starter.ai.support.generate.GenerateService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
@Slf4j
@Service
public class WorldSimServiceImpl implements WorldSimService {

    /** 世界视图缓存名（Caffeine 本地，30 分钟 TTL，变更后主动失效） */
    private static final String VIEW_CACHE = "aiWorldView";

    /** 世界列表缓存名 */
    private static final String LIST_CACHE = "aiWorldList";

    /** 造角色的系统提示词：只吐 JSON，字段固定，便于服务端落库 */
    private static final String NPC_WRITER_SYSTEM = String.join("\n",
            "你是一位角色设计师，为模拟世界起草新角色。",
            "只输出一个 JSON 对象，结构如下：",
            "{\"characters\":[{\"name\":\"名字\",\"persona\":\"身份与性格（60~120 字）\","
                    + "\"gender\":\"男/女\",\"profession\":\"职业\",\"scene\":\"所属场景名\",\"seed\":1}],"
                    + "\"relations\":[{\"from\":1,\"to\":2,\"affinity\":45,\"tag\":\"旧识\",\"memo\":\"关系由来\"}]}",
            "要求：",
            "1. characters 的个数与用户要求一致，seed 从 1 递增；",
            "2. scene 必须是给出的场景名之一；",
            "3. relations 只写新角色之间、或新角色与现有角色（seed 用负数，如 -1 表示现有第 1 个）的关系；",
            "4. affinity 取 -100~100；",
            "5. 不要输出 JSON 以外的任何内容。");

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

    /** 生成实现解析器：按媒体类型挑模型、读尺寸声明 */
    private final GenerateClientResolver generateClientResolver;

    /** 生成结果落盘存储：图生视频要把已生成的场景图读回字节 */
    private final GenerateMediaStore generateMediaStore;

    /** 模型行查询：设置里没点名模型时，按「有对应 SPI 实现」挑一个可用模型 */
    private final SysAiProviderModelMapper providerModelMapper;

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
     * @param generateClientResolver 生成实现解析器
     * @param generateMediaStore   生成结果落盘存储
     * @param providerModelMapper  生成模型行查询
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
                               GenerateClientResolver generateClientResolver,
                               GenerateMediaStore generateMediaStore,
                               SysAiProviderModelMapper providerModelMapper,
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
        this.generateClientResolver = generateClientResolver;
        this.generateMediaStore = generateMediaStore;
        this.providerModelMapper = providerModelMapper;
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
        SysAiWorld world = requireVisible(worldId);
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
    public SysAiWorld createWorld(String name, String description, String era, String directorModel, String actorRoles) {
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "世界名称不能为空");
        }
        String userId = AiUserContext.requireUserId();
        SysAiWorld w = new SysAiWorld();
        w.setName(name.trim());
        w.setDescription(description == null || description.isBlank() ? null : description.trim());
        w.setEra(era == null || era.isBlank() ? "现代" : era.trim());
        w.setWeather("晴");
        w.setDirectorModel(directorModel);
        w.setActorRoles(actorRoles);
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
        if (body.containsKey("director_model")) {
            Object v = body.get("director_model");
            w.setDirectorModel(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v));
        }
        if (body.containsKey("actor_roles")) {
            Object v = body.get("actor_roles");
            w.setActorRoles(v == null || "null".equals(String.valueOf(v)) ? null : String.valueOf(v));
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
        requireVisible(worldId);
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
        requireVisible(worldId);
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
    @Transactional
    public List<Map<String, Object>> createNpcsByAi(Long worldId, Integer count, String hint) {
        SysAiWorld world = requireOwned(worldId);
        int n = count == null ? 2 : Math.max(1, Math.min(3, count));
        String userId = AiUserContext.requireUserId();
        List<SysAiWorldScene> scenes = sceneMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldScene>()
                        .eq(SysAiWorldScene::getWorldId, worldId)
                        .orderByAsc(SysAiWorldScene::getSort)
                        .orderByAsc(SysAiWorldScene::getId));
        if (scenes.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "世界还没有场景，先给世界摆一个舞台");
        }
        List<SysAiWorldNpc> existing = npcMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldNpc>()
                        .eq(SysAiWorldNpc::getWorldId, worldId)
                        .orderByAsc(SysAiWorldNpc::getSort));

        AiChatRequest request = new AiChatRequest();
        request.setModel(scriptModel(world));
        request.setSystemPrompt(NPC_WRITER_SYSTEM);
        request.setPrompt(composeNpcPrompt(world, scenes, existing, n, hint));
        request.setTemperature(0.95);
        request.setMaxTokens(2000);
        request.setUseSkill(false);
        AiChatResult result = aiChatFacadeService.callForResult(request);
        if (!result.success()) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "AI 造角色失败：" + result.errorMessage());
        }
        List<Map<String, Object>> drafted = parseNpcDrafts(result.text(), n);
        if (drafted.isEmpty()) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "AI 没有返回可用的角色");
        }

        int sort = existing.size();
        List<Map<String, Object>> created = new ArrayList<>();
        Map<Long, Long> npcBySeed = new LinkedHashMap<>();
        // 现有角色也登记进去：AI 写关系时可以用负数 seed 指向它们
        int seedIdx = 0;
        for (SysAiWorldNpc npc : existing) {
            seedIdx++;
            npcBySeed.put((long) -seedIdx, npc.getId());
        }
        for (Map<String, Object> d : drafted) {
            String name = str(d.get("name"), "无名氏");
            String persona = str(d.get("persona"), "");
            SysAiRole role = new SysAiRole();
            role.setUserId(userId);
            // 标记为世界沙盒模拟 NPC：角色管理列表按 sys_ai_role_source 过滤，
            // 世界沙盒内部按 role_id 直查人设不受影响。
            role.setSource(SysAiRole.SOURCE_WORLD_SIM);
            role.setName(name);
            role.setCode("ws-" + worldId + "-" + System.currentTimeMillis() + "-" + sort);
            role.setDescription(persona.length() > 120 ? persona.substring(0, 120) : persona);
            role.setSystemPrompt(persona);
            role.setTemperature(0.8);
            role.setStatus(1);
            role.setDeleted(0);
            role.setSort(sort);
            role.setCreateName(AiUserContext.currentUsername());
            role.setCreateTime(LocalDateTime.now());
            role.setUpdateTime(LocalDateTime.now());
            roleMapper.insert(role);

            SysAiWorldNpc npc = new SysAiWorldNpc();
            npc.setWorldId(worldId);
            npc.setRoleId(role.getId());
            npc.setSceneId(resolveSceneId(scenes, d.get("scene")));
            npc.setPersona(persona);
            npc.setGender(str(d.get("gender"), null));
            npc.setProfession(str(d.get("profession"), null));
            npc.setAgeEnabled(Boolean.FALSE);
            npc.setStatus("alive");
            npc.setIsActive(Boolean.TRUE);
            npc.setSort(sort++);
            npc.setUserId(userId);
            npc.setCreateTime(LocalDateTime.now());
            npc.setUpdateTime(LocalDateTime.now());
            npcMapper.insert(npc);

            Object seed = d.get("seed");
            if (seed != null) {
                npcBySeed.put(parseLong(seed), npc.getId());
            }
            created.add(npcRow(npc));
        }

        // 关系：AI 写的 from/to 用临时序号，落库时换成真实 NPC ID；找不到就静默丢弃
        Object rawRels = drafted.isEmpty() ? null : drafted.get(0).get("relations");
        if (rawRels instanceof List<?> rl) {
            for (Object o : rl) {
                if (!(o instanceof Map<?, ?> rm)) {
                    continue;
                }
                Long from = npcBySeed.get(parseLong(rm.get("from")));
                Long to = npcBySeed.get(parseLong(rm.get("to")));
                if (from == null || to == null || from.equals(to)) {
                    continue;
                }
                SysAiWorldRelationship rel = new SysAiWorldRelationship();
                rel.setWorldId(worldId);
                rel.setFromNpcId(from);
                rel.setToNpcId(to);
                rel.setAffinity(clampAffinity(rm.get("affinity")));
                rel.setRelationTag(str(rm.get("tag"), null));
                rel.setMemo(str(rm.get("memo"), null));
                rel.setUserId(userId);
                rel.setCreateTime(LocalDateTime.now());
                rel.setUpdateTime(LocalDateTime.now());
                relationshipMapper.insert(rel);
            }
        }

        evictWorld(worldId);
        return created;
    }

    /**
     * 造角色的提示词：世界现状 + 已有角色 + 场景，作为 AI 起草的依据。
     *
     * @param world    世界行
     * @param scenes   场景列表
     * @param existing 已有 NPC
     * @param count    要造几个
     * @param hint     观众给的倾向
     * @return 提示词
     */
    private String composeNpcPrompt(SysAiWorld world, List<SysAiWorldScene> scenes,
                                    List<SysAiWorldNpc> existing, int count, String hint) {
        StringBuilder sb = new StringBuilder();
        sb.append("世界名：").append(world.getName());
        if (world.getEra() != null && !world.getEra().isBlank()) {
            sb.append("（时代：").append(world.getEra()).append("）");
        }
        if (world.getDescription() != null && !world.getDescription().isBlank()) {
            sb.append("\n世界背景：").append(world.getDescription());
        }
        sb.append("\n\n【现有角色】");
        if (existing.isEmpty()) {
            sb.append("（暂无）");
        }
        int idx = 0;
        for (SysAiWorldNpc npc : existing) {
            idx++;
            sb.append("\n- seed=").append(-idx).append(" ").append(npcDisplayName(npc));
            if (npc.getPersona() != null && !npc.getPersona().isBlank()) {
                sb.append("：").append(npc.getPersona());
            }
        }
        sb.append("\n\n【场景】");
        for (SysAiWorldScene s : scenes) {
            sb.append("\n- ").append(s.getName());
        }
        sb.append("\n\n【要求】");
        sb.append("\n请新造 ").append(count).append(" 个角色，编号 1~").append(count);
        sb.append("。人设要具体（身份、性格、与现有角色的利害关系），不要套话。");
        if (hint != null && !hint.isBlank()) {
            sb.append("\n观众的倾向：").append(hint.trim());
        }
        sb.append("\n只输出 JSON，不要解释。");
        return sb.toString();
    }

    /**
     * 解析 AI 返回的角色草稿。
     *
     * <p>期望结构：<code>{"characters":[{name, persona, gender, profession, scene, seed}],
     * "relations":[{from, to, affinity, tag, memo}]}</code>；解析不出数组时抛 AI_ERROR。</p>
     *
     * @param text  AI 文本
     * @param count 期望个数
     * @return 草稿列表（第一条额外带 relations）
     */
    private List<Map<String, Object>> parseNpcDrafts(String text, int count) {
        String trimmed = text == null ? "" : text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "AI 没有按要求返回 JSON");
        }
        Map<String, Object> json;
        try {
            json = Json.fromJson(trimmed.substring(start, end + 1), Map.class);
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.AI_ERROR, "角色 JSON 解析失败：" + e.getMessage());
        }
        Object raw = json.get("characters");
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) m;
                    if (str(item.get("name"), null) != null && out.size() < count) {
                        out.add(item);
                    }
                }
            }
        }
        if (!out.isEmpty() && json.get("relations") != null) {
            out.get(0).put("relations", json.get("relations"));
        }
        return out;
    }

    /** 场景名或序号 → 场景 ID；对不上时落到第一个场景，保证新角色至少有个位置 */
    private static Long resolveSceneId(List<SysAiWorldScene> scenes, Object ref) {
        String key = str(ref, null);
        if (key != null) {
            for (SysAiWorldScene s : scenes) {
                if (key.equals(s.getName())) {
                    return s.getId();
                }
            }
            try {
                int idx = Integer.parseInt(key.trim());
                if (idx >= 1 && idx <= scenes.size()) {
                    return scenes.get(idx - 1).getId();
                }
            } catch (NumberFormatException ignore) {
                // 不是序号，按名字没对上，落到默认场景
            }
        }
        return scenes.get(0).getId();
    }

    /** 好感度钳制到 [-100,100]，非法值按 0 */
    private static int clampAffinity(Object v) {
        int n = 0;
        if (v != null) {
            try {
                n = (int) Math.round(Double.parseDouble(String.valueOf(v)));
            } catch (NumberFormatException e) {
                n = 0;
            }
        }
        return Math.max(-100, Math.min(100, n));
    }

    @Override
    public List<Map<String, Object>> listRelationships(Long worldId) {
        requireVisible(worldId);
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
        requireVisible(worldId);
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
        requireVisible(worldId);
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
     * require Visible：只读写路径用。主人可见自己的世界；他人世界只要已经开演（status=1）
     * 也可旁观，但只能看不能改（写路径仍走 {@link #requireOwned}）。
     *
     * @param id 世界ID
     * @return 世界行
     */
    private SysAiWorld requireVisible(Long id) {
        SysAiWorld w = worldMapper.selectById(id);
        if (w == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "世界不存在");
        }
        String userId = AiUserContext.requireUserId();
        if (!userId.equals(w.getUserId()) && !Integer.valueOf(1).equals(w.getStatus())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "这个世界还没开演，不能旁观");
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
        row.put("directorModel", w.getDirectorModel());
        row.put("actorRoles", w.getActorRoles());
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

    /** 单个世界允许的最大幕数缺省值（设置中心 sim.story_max_acts 未配置时使用） */
    private static final int DEFAULT_STORY_MAX_ACTS = 64;

    /** 系统提示词（AI 编剧必须输出严格 JSON 剧本） */
    private static final String SCRIPT_PROMPT_SYSTEM_SANGO =
        "你必须且只能输出严格合法的 JSON（不要输出 Markdown、不要输出代码块、不要输出额外文字）。"
        + "输出字段固定为：actNo（幕次编号，从 1 开始）、title（幕标题）、description（剧本概述）"
        + "、worldTime（世界时间标签）、weather（天气）、scenes（场景列表）和 transitions（过场列表）"
        + "与 props（道具列表）。每个场景含 sceneId/name/desc/npcs/lines/mediaImage/mediaVideo；"
        + "不要输出 playText（演出正文由演出阶段逐场生成）；"
        + "mediaImage/mediaVideo 结构为 {taskId, url}（初始均为 null）。不要输出其他字段。";

    /** 媒体生成类型：图 */
    private static final String MEDIA_IMAGE_TYPE = "image";
    /** 媒体生成类型：视频 */
    private static final String MEDIA_VIDEO_TYPE = "video";
    /** 舞台形态：纯文本（不调用任何生成接口） */
    private static final String SIM_MODE_TEXT = "text";
    /** 舞台形态：每个场景出一张图 */
    private static final String SIM_MODE_IMAGE = "image";
    /** 舞台形态：整幕出一条视频 */
    private static final String SIM_MODE_VIDEO = "video";
    /** 默认生成图片尺寸 */
    private static final String MEDIA_SIZE = "1024x1024";
    /** 默认视频尺寸（模型没声明尺寸时用它：竖屏短剧，舞台上占满一栏） */
    private static final String MEDIA_VIDEO_SIZE = "720x1280";
    /** 单个分镜视频时长（秒）：太长上游慢且贵，短片连播更像一幕戏 */
    private static final int VIDEO_DURATION = 5;
    /** 单笔生成数量 */
    private static final int MEDIA_COUNT = 1;
    /** 素材状态：未开始（剧本中还没有任务号） */
    private static final String MEDIA_NOT_STARTED = "NOT_STARTED";
    /** 素材状态：无需求（没有可写进提示词的描述） */
    private static final String MEDIA_NO_PROMPT = "NO_PROMPT";
    /** 素材状态：成功（已生成） */
    private static final String MEDIA_OK = "OK";
    /** 单轮推进最多提交几个素材任务（防止一轮打爆上游） */
    private static final int MAX_ASSETS_PER_TICK = 8;
    /**
     * 素材任务超过这个时长仍无终态就判死放行（毫秒）。
     *
     * <p>必须覆盖真实出图 / 出视频的耗时：真实模型单张出图 70–90 秒，
     * 加上排队与上游转存，一个任务跑到 3–5 分钟很常见。阈值定 120 秒时，
     * 还在正常生成的任务会被误判成僵死 → 该类型记进 failedMediaTypes →
     * 同一幕剩下的场景全部不再出图，舞台就变成「有的场景有图、有的没图」。</p>
     *
     * <p>取 10 分钟：足够覆盖正常出片，又能让真正卡死的任务最终被放行，
     * 不至于把整幕永久挂在素材阶段。</p>
     */
    private static final long STALE_TASK_MS = 600_000L;
    /** 剧本里记录「本幕失败的媒体类型」的键（跨请求持久化） */
    private static final String FAILED_TYPES_KEY = "failedMediaTypes";
    /**
     * 按幕的推进互斥锁（键 {@code worldId#actNo}）。
     *
     * <p>素材阶段是「读剧本 → 改 → 写剧本」的复合操作，没有事务也没有文件锁；
     * 并发推进会让后写的旧快照覆盖掉新状态，导致同一幕里图片状态参差。</p>
     */
    private static final ConcurrentHashMap<String, Object> ACT_LOCKS = new ConcurrentHashMap<>();
    /** 节点种类：过场 */
    private static final String MEDIA_TRANSITION_TYPE = "transition";
    /** 节点种类：道具 */
    private static final String MEDIA_PROP_TYPE = "prop";

    /* ========================================================================
     * 查询
     * ====================================================================== */

    @Override
    public List<Map<String, Object>> listActs(Long worldId) {
        requireVisible(worldId);
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

    /**
     * 取「下一幕」：已有空的待演幕就复用它，没有才新建。
     *
     * <p>每演完一幕都无条件 {@code createAct} 会让幕次成倍膨胀、堆满同名的空幕——
     * 本机实测演 4 幕就冒出 4 个空「未命名幕次」。而观众真正要的是「往下演」，
     * 那一个空幕就是它。复用它既不再膨胀，也让「演完接下一幕」始终只多一幕。</p>
     *
     * @param worldId 世界 id
     * @return 下一幕的行（新建的或复用的空幕）
     */
    private SysAiWorldAct ensureNextAct(Long worldId) {
        SysAiWorldAct latest = actMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldAct>()
                        .eq(SysAiWorldAct::getWorldId, worldId)
                        .orderByDesc(SysAiWorldAct::getActNo)
                        .last("limit 1"));
        if (latest != null && isBlankAct(latest)) {
            return latest;
        }
        // 幕次上限（sim.story_max_acts）：到顶后不再新建。这里不能抛异常 ——
        // 调用方是「演完一幕顺带给出下一幕」，抛错会把已经成功的播放结果一起打掉；
        // 也不能返回 null —— actRow(null) 会 NPE。回退成最新的那一幕，
        // 前端看到的是「没有下一幕」，与 limit 到的语义一致。
        if (latest != null && latest.getActNo() != null && latest.getActNo() + 1 > storyMaxActs()) {
            log.info("[WorldSim] 世界 {} 已达幕次上限 {}，不再新建下一幕", worldId, storyMaxActs());
            return latest;
        }
        return createAct(worldId);
    }

    /**
     * 一个世界允许的最大幕数（设置中心 {@code sim.story_max_acts}，默认 64）。
     *
     * <p>这项此前只声明、全仓零读取：幕次可以无限新增，剧本目录与库表跟着无限膨胀。</p>
     *
     * @return 上限；配置非法（非正数）时回落 64
     */
    public int storyMaxActs() {
        int configured = globalSettingReader.getInt("sim", "story_max_acts", DEFAULT_STORY_MAX_ACTS);
        return configured > 0 ? configured : DEFAULT_STORY_MAX_ACTS;
    }

    /**
     * 这一幕是不是一个「还没写过任何东西」的空幕。
     *
     * <p>判据是阶段仍停在 writing 且进度为 0。剧本文件在 {@code createAct} 里只落了一个
     * {@code {"actNo":N}} 的空壳，不能拿「文件存在」当有内容。</p>
     *
     * @param act 幕次行
     * @return true 表示可以直接拿来写下一幕
     */
    private static boolean isBlankAct(SysAiWorldAct act) {
        if (!ACT_STATUS_WRITING.equals(act.getStage())) {
            return false;
        }
        return act.getProgress() == null || act.getProgress() == 0;
    }

    @Override
    public SysAiWorldAct createAct(Long worldId) {
        requireOwned(worldId);
        int nextNo = actNextNo(worldId);
        int maxActs = storyMaxActs();
        if (nextNo > maxActs) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "该世界已达到幕次上限 " + maxActs + "（可在设置中心 sim.story_max_acts 调整）");
        }
        SysAiWorldAct act = new SysAiWorldAct();
        act.setWorldId(worldId);
        act.setActNo(nextNo);
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
        requireVisible(worldId);
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
        // 同一幕的推进必须串行：素材阶段是「读剧本 → 改素材头 → 写剧本」的非原子过程，
        // 前端定时推进与用户手动点「继续」很容易叠在一起。并发时后写的一方会用旧快照
        // 覆盖先写的（taskId/status 被抹回 NOT_STARTED），结果是整幕带着
        // 「有的场景有图、有的还在生成」的参差进入表演——正是要杜绝的混合。
        synchronized (advanceLock(worldId, actNo)) {
            requireOwned(worldId);
            return advanceActLocked(worldId, actNo);
        }
    }

    /**
     * 按幕取一把进程内互斥锁。
     *
     * <p>键用 worldId + actNo，不同幕互不阻塞；锁对象常驻，避免每次 new 出来互不相干。</p>
     *
     * @param worldId 世界
     * @param actNo   幕次
     * @return 该幕的锁对象
     */
    private Object advanceLock(Long worldId, Integer actNo) {
        return ACT_LOCKS.computeIfAbsent(worldId + "#" + actNo, k -> new Object());
    }

    /**
     * 推进一幕的实际逻辑（由 {@link #advanceAct} 持锁调用）。
     *
     * @param worldId 世界
     * @param actNo   幕次
     * @return 结果
     */
    private Map<String, Object> advanceActLocked(Long worldId, Integer actNo) {
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
                // 纯文本形态没有素材可等：不进素材循环，写完剧本直接开演。
                // 走 tryAdvanceAsset 也能收敛（全部 NO_PROMPT → 返回 null），
                // 但那要多一轮请求，且会在剧本里留下「无需求」的素材头，纯文本用户看着像出错。
                String mode = mediaMode();
                boolean needMedia = imageMediaEnabled() || videoMediaEnabled();
                log.info("[WorldSim] 素材阶段 world={} act={} mode={} needMedia={}", worldId, actNo, mode, needMedia);
                Map<String, Object> progress = needMedia
                        ? tryAdvanceAsset(worldId, actNo, script, act)
                        : null;
                log.info("[WorldSim] 素材结果 world={} act={} progressNull={}", worldId, actNo, progress == null);
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
                // ── 表演：一次只演一场 ──
                // 早先整幕一次演完（每场一次模型调用）才落稿，观众对着「正在演…」干等
                // 几十秒，正文一个字都看不到。现在每推进一次只演下一场，演完立刻把剧本
                // 与用户侧演出稿各落一次盘，前端下一次推进前就能把已演的场次放上舞台。
                String script = storyStore.readAct(worldId, actNo);
                if (script == null || script.isBlank()) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有剧本可表演");
                }
                Map<String, Object> json = Json.fromJson(script, Map.class);
                List<Map<String, Object>> scenes = sceneList(json.get(ActScript.SCENES));
                if (scenes.isEmpty()) {
                    throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有可表演的场景");
                }
                int total = scenes.size();
                int idx = nextUnplayedScene(scenes);
                if (idx >= 0) {
                    playScene(worldId, scenes.get(idx), idx + 1);
                    json.put(ActScript.ACT_STATUS, ACT_STATUS_PLAYING);
                    script = Json.toJson(json);
                    storyStore.writeAct(worldId, actNo, script);
                    // 一场一落稿：舞台上的正文随场次增长，不必等整幕结束
                    storyStore.writeStory(worldId, AiUserContext.requireUserId(), actNo,
                            playStory(worldId, actNo, script));
                }
                int done = scenesDone(script);
                act.setProgress(done);
                if (done >= total) {
                    // 全部场次演完：标记可重播，并开一个空的下一幕交给前端接着推
                    act.setStage(ACT_STATUS_REPLAY);
                    act.setStatus(ACT_STATUS_REPLAY);
                    out.put("nextAct", actRow(ensureNextAct(worldId)));
                }
                act.setUpdateTime(LocalDateTime.now());
                actMapper.updateById(act);
                out.put("act", actRow(act));
                out.put("playedScenes", done);
                out.put("totalScenes", total);
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
        // 只看阶段，不看状态：回退期间 stage=playing 而 status 仍留着 replay，
        // 早先按 status 判可重播，同一次重播里第二次点就会被拒——「重播」变成一次性按钮。
        // 还要接住旧数据：早期版本的重播把 stage 置成 playing 却没留下任何待演内容，
        // 这种幕点「演出」会重跑一遍生成（多花一次图），点「重播」才是它真正需要的。
        if (!ACT_STATUS_REPLAY.equals(act.getStage())
                && !ACT_STATUS_FINISHED.equals(act.getStage())
                && !isBlankAct(act)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "已演过或已播的幕次才能重播");
        }
        String script = storyStore.readAct(worldId, actNo);
        if (script == null || script.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有剧本可重播");
        }
        // 重播的有无演出稿，看的是用户侧故事片段——舞台放的正是它。
        // 没有它，按下「重播」舞台上还是空白，不如直说原因。
        String story = storyStore.readStory(worldId, AiUserContext.requireUserId(), actNo);
        if (story == null || story.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "这一幕还没演过，先点「演出」");
        }
        // 重播就是「从头再放一遍」，不改任何演出数据：
        // 既不能删用户侧故事片段（那正是舞台要重放的稿子，删了重播只剩空白），
        // 也不能把舞台形态回退成 playing（那样素材任务会从头再跑一轮，
        // 观众点十次重播就烧十次图）。这里只把阶段摆到可重播的位置。
        act.setStage(ACT_STATUS_REPLAY);
        act.setStatus(ACT_STATUS_REPLAY);
        act.setUpdateTime(LocalDateTime.now());
        actMapper.updateById(act);
        evictActCaches(worldId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("act", actRow(act));
        out.put("restart", true);
        // 重播之后 AI 自己往下接：开一个空的下一幕，观众不用再回头找按钮。
        // 只开空幕、不预写剧本——重播本身不该悄悄烧掉一整幕的模型与出图额度。
        out.put("nextAct", actRow(ensureNextAct(worldId)));
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
        // 阶段也必须落成 finished：前端标签与按钮可用性都读 stage，
        // 只改 status 会让结文后仍显示「可重播」且重播/结文按钮保持可点。
        act.setStage(ACT_STATUS_FINISHED);
        act.setEnded(1);
        act.setUpdateTime(LocalDateTime.now());
        actMapper.updateById(act);
        // 写一条世界事件为幕次划上句号
        appendActEvent(worldId, act);
        evictActCaches(worldId);
        return actMapper.selectById(act.getId());
    }

    /**
     * 演出正文（只读）：取用户侧故事片段，不触发任何生成。
     *
     * <p>故事片段在 {@link #advanceAct} 的表演阶段由 {@link #playStory} 写入，结构为
     * <code>{actNo, worldId, title, status, progress, scenes:[{sceneId,name,playText,mediaImage,mediaVideo}]}</code>。
     * 这里只做读取与补字段（舞台名、幕次阶段），保证前端一次拿到可直接排版的正文。</p>
     */
    @Override
    public List<Map<String, Object>> listStories(Long worldId, Integer actNo) {
        requireVisible(worldId);
        String userId = AiUserContext.requireUserId();
        List<Integer> nos = actNo != null
                ? List.of(actNo)
                : storyStore.storyActNumbers(worldId, userId);
        Map<Integer, SysAiWorldAct> actByNo = new LinkedHashMap<>();
        for (SysAiWorldAct a : actMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldAct>().eq(SysAiWorldAct::getWorldId, worldId))) {
            if (a.getActNo() != null) {
                actByNo.put(a.getActNo(), a);
            }
        }
        Map<Long, String> sceneNames = new LinkedHashMap<>();
        for (SysAiWorldScene s : sceneMapper.selectList(
                new LambdaQueryWrapper<SysAiWorldScene>().eq(SysAiWorldScene::getWorldId, worldId))) {
            sceneNames.put(s.getId(), s.getName());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Integer no : nos) {
            if (no == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("actNo", no);
            SysAiWorldAct act = actByNo.get(no);
            row.put("stage", act == null ? null : act.getStage());
            String raw = storyStore.readStory(worldId, userId, no);
            if (raw == null || raw.isBlank()) {
                row.put("played", false);
                out.add(row);
                continue;
            }
            Map<String, Object> json;
            try {
                json = Json.fromJson(raw, Map.class);
            } catch (Exception e) {
                // 文件被手改坏时不让整页 500：按「未演出」处理，正文可等下一次演出覆盖
                row.put("played", false);
                row.put("broken", true);
                out.add(row);
                continue;
            }
            row.put("played", true);
            row.put("title", str(json.get("title"), "未命名幕次"));
            row.put("description", str(json.get("description"), ""));
            row.put("progress", json.get("progress"));
            List<Map<String, Object>> scenes = new ArrayList<>();
            for (Map<String, Object> s : sceneList(json.get("scenes"))) {
                Map<String, Object> m = new LinkedHashMap<>();
                Object sid = s.get(ActScript.SCENE_SCENE_ID);
                m.put("sceneId", sid);
                m.put("name", str(s.get(ActScript.SCENE_NAME), "场景"));
                m.put("stageName", sceneNameById(sceneNames, sid));
                m.put("desc", str(s.get(ActScript.SCENE_DESC), ""));
                m.put("playText", str(s.get(ActScript.SCENE_PLAY), ""));
                // 台词是演出正文里最重要的一层：没有它前端只能显示旁白，
                // 观众看不到「谁说了什么」，整幕看起来像一段没有对白的流水账。
                m.put("lines", sceneLines(s.get(ActScript.SCENE_LINES)));
                m.put("mediaImage", s.get(ActScript.SCENE_MEDIA_IMAGE));
                m.put("mediaVideo", s.get(ActScript.SCENE_MEDIA_VIDEO));
                scenes.add(m);
            }
            row.put("scenes", scenes);
            out.add(row);
        }
        return out;
    }

    /**
     * 把剧本里的台词数组整理成演出可用的结构。
     *
     * <p>编剧有时把台词写成纯字符串，有时写成 {@code {speaker, text}}。演出正文要逐句排版，
     * 两种都要接住：字符串按旁白处理，说话人为空。</p>
     *
     * @param raw 台词原值
     * @return 台词列表（说话人 + 文本），永不为 null
     */
    private static List<Map<String, Object>> sceneLines(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (o == null) continue;
            Map<String, Object> line = new LinkedHashMap<>();
            if (o instanceof Map<?, ?> m) {
                Object speaker = m.get("speaker");
                if (speaker == null) speaker = m.get("role");
                Object text = m.get("text");
                if (text == null) text = m.get("content");
                line.put("speaker", speaker == null ? "" : String.valueOf(speaker));
                line.put("text", text == null ? "" : String.valueOf(text));
            } else {
                line.put("speaker", "");
                line.put("text", String.valueOf(o));
            }
            out.add(line);
        }
        return out;
    }

    /** 场景 ID（可能是 Integer/Long/String 的 JSON 值）→ 场景名，找不到返回 null */    private static String sceneNameById(Map<Long, String> names, Object rawId) {
        if (rawId == null) {
            return null;
        }
        try {
            return names.get(Long.valueOf(String.valueOf(rawId).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
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
        request.setModel(scriptModel(worldId));
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

        // 世界级「演员」配置：导演指定的可用角色，编剧应优先在这些角色里排戏
        String actorRoles = w.getActorRoles();
        if (actorRoles != null && !actorRoles.isBlank()) {
            sb.append("\n\n【导演指定演员】（优先在这些角色里安排戏份）");
            sb.append("\n").append(actorRoles.trim());
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
        sb.append("\n2. 每个场景的 desc（画面描述）必填且要足够具体（色调/光影/人物动作/环境）。")
          .append("不能留空、不能只写「（无）」「（此处省略）」之类的占位——")
          .append("舞台形态为图片/视频时每个场景都要据此出图，缺一个就会整幕缺图。");
        sb.append("\n3. 每 2~3 个场景之间要有一个过场（transitions）。");
        sb.append("\n4. 道具要有描述和关联的位置。");
        sb.append("\n5. 台词要符合角色人设与当前关系。");
        sb.append("\n6. 只输出 JSON，不要解释。");
        return sb.toString();
    }

    /** 编剧单元模型：世界级「导演模型」优先，其次设置 sim.script_model，最后用生效对话模型 */
    private String scriptModel() {
        String model = globalSettingReader.getStr("sim", "script_model");
        if (model == null || model.isBlank()) {
            return effectiveChatModelResolver.resolve(null);
        }
        return model;
    }

    /** 按世界取编剧模型：世界配置了导演模型就用它，否则回落全局 */
    private String scriptModel(SysAiWorld world) {
        if (world != null) {
            String director = world.getDirectorModel();
            if (director != null && !director.isBlank()) {
                return director.trim();
            }
        }
        return scriptModel();
    }

    /** 按世界 ID 取编剧模型（内部会加载世界；世界不存在时回落全局） */
    private String scriptModel(Long worldId) {
        return scriptModel(worldId == null ? null : worldMapper.selectById(worldId));
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
            // 模型常写成 ```json / ```JSON / ``` javascript：去掉语言标记再解析，
            // 否则首字符是 "json" 这类裸 token，JSON 解析必失败（已实测）。
            body = body.replaceFirst("^[A-Za-z][A-Za-z0-9_-]*\\s*", "");
            int end = body.indexOf("```");
            if (end > 0) {
                body = body.substring(0, end);
            }
            trimmed = body.trim();
        }
        try {
            Object parsed = Json.fromJson(trimmed, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                // 演出阶段靠「场景有没有 playText」判断这一场演没演（见 scenesDone）。
                // 部分模型会照着提示词把 playText 直接写进剧本，结果整幕一写完就被
                // 判定为全部演完：既不逐场演出，也不落用户侧故事文件。编剧只负责写
                // 剧本，playText 必须由 playScene 逐场生成，所以这里一律剥掉。
                Object scenes = map.get(ActScript.SCENES);
                if (scenes instanceof List<?> sceneList) {
                    for (Object item : sceneList) {
                        if (item instanceof Map<?, ?> scene) {
                            scene.remove(ActScript.SCENE_PLAY);
                        }
                    }
                }
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
     * 把生成结果里的媒体值换成浏览器可访问的地址。
     *
     * <p>生成侧落盘后给的是 {@code media:} 句柄（或改造前的内联 base64），
     * 直接塞进 {@code <img src>} / {@code <video src>} 会被浏览器当成未知协议，
     * 表现为缩略图 404、视频报「播不出来」。句柄必须换成按任务取字节的媒体接口地址，
     * 判断口径与生成页一致（{@link GenerateMediaStore#isStored}）。</p>
     *
     * @param task  任务行
     * @param value 结果里的第 0 个地址
     * @return 可播放地址
     */
    private static String playableUrl(SysAiGenerateTask task, String value) {
        if (!GenerateMediaStore.isStored(value)) {
            return value;
        }
        return GenerateMediaController.INLINE_PATH_PREFIX + task.getTaskId() + "/media/0";
    }

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

        // 本幕已经失败过的媒体类型（图/视频）。环境没有该能力时不要让同类素材反复建任务空转，
        // 记在剧本里而不是只记在内存里——否则每轮请求都是一次新的方法调用，记忆全丢。
        Set<String> failedTypes = new HashSet<>();
        Object ft = json.get(FAILED_TYPES_KEY);
        if (ft instanceof List<?> ftList) {
            for (Object o : ftList) {
                if (o != null) failedTypes.add(String.valueOf(o));
            }
        }

        // 第一阶段：查封已有任务的 RETURN 状态（成功/失败/悬而未决）
        for (Material mat : materials) {
            String taskId = mat.taskId();
            if (taskId == null || taskId.isBlank()) continue;
            SysAiGenerateTask task = generateService.query(taskId);
            if (task == null) {
                // 任务记录已不存在（历史数据被清理 / 库里从未写入）：
                // 留着 taskId 会让下面的循环永远判「生成中」，整幕永远卡在素材阶段。
                // 这里直接判死，交给下面的失败分支放行。
                mat.head().put("status", "FAILED");
                continue;
            }
            mat.head().put("taskId", task.getTaskId());
            String st = task.getStatus();
            if (st == null || st.isBlank()) {
                // 状态缺失当作生成中，别把未知状态当成终态放行
                st = "RUNNING";
            }
            if (!"SUCCESS".equals(st) && isStaleTask(task)) {
                // 排队/生成中超过阈值仍无终态：判死放行，避免整幕被一个卡住的任务无限期拖住
                st = "FAILED";
            }
            if ("FAILED".equals(st) || "CANCELED".equals(st)) {
                // 失败要当场放行：留到下一轮会让整幕多空转一次
                mat.head().remove("taskId");
                failedTypes.add(String.valueOf(mat.head().get("type")));
                // failed 标记用于整幕一致性收口：该出却没出的素材会让整幕退回纯文字
                mat.head().put("failed", Boolean.TRUE);
                st = MEDIA_NO_PROMPT;
            }
            mat.head().put("status", st);
            if ("SUCCESS".equals(task.getStatus())) {
                List<String> urls = GenerateService.mediaUrls(task);
                String raw = urls == null || urls.isEmpty() ? null : urls.get(0);
                // raw 是后端自用的原始值（media: 句柄），供图生视频取首帧；
                // url 是给浏览器用的可播放地址
                mat.head().put("raw", raw);
                mat.head().put("url", raw == null ? null : playableUrl(task, raw));
                // 成功必须落成 OK：素材就绪判断、图生视频取首帧、前端放映都只认 OK。
                // 此前这里只写了 task 的 SUCCESS 字样，OK 从头到尾没人写过，
                // 结果素材永远停在非终态、图生视频永远拿不到参考图、舞台永远退回文字演出。
                mat.head().put("status", MEDIA_OK);
            }
            mat.persist();
        }

        boolean inProgress = false;
        Material target = null;
        // 三种形态互斥：视频 → 场景图 + 分镜视频；图片 → 只出场景图；文本 → 什么任务都不建。
        //
        // 文本形态必须在这里直接放行，而不是「建了任务再拦」：一个场景一张图要 70–90 秒，
        // 只想看文字演出的用户会白白等上几分钟、还会照常产生费用。
        boolean videoEnabled = videoMediaEnabled();
        boolean imageEnabled = imageMediaEnabled();
        // 按已有的失败记录收敛一遍
        for (Material mat : materials) {
            Map<String, Object> head = mat.head();
            // 形态关闭导致的不出图不算「失败」：整幕本来就不要这类素材
            if (!enabledForMode(mat)) {
                head.remove("taskId");
                head.remove("url");
                head.remove("raw");
                head.put("status", MEDIA_NO_PROMPT);
                head.remove("failed");
                // 必须立刻写回节点：不落盘的话下一轮 collect 会把它当 NOT_STARTED 重建，
                // 于是「关掉的视频通道」又活过来去建任务、再失败、再进 failedTypes，
                // 最后把整幕的图片一致性一起带崩。
                mat.persist();
                continue;
            }
            // 该类型本幕已经确认出不了（上游报错 / 没有能力）：
            // 已经在跑的任务不能在这里抹掉——抹掉只是让它的图白生成，
            // 它自己会在下面的任务循环里落成 FAILED 并被记 failed。
            // 这里只做一件事：不再为同一类型新建任务，避免空转刷错误。
            if (failedTypes.contains(String.valueOf(head.get("type"))) && mat.taskId() == null) {
                head.put("status", MEDIA_NO_PROMPT);
                // 该出却没出（上游失败）：打标记，整幕要做一致性收口
                head.put("failed", Boolean.TRUE);
            }
        }
        for (Material mat : materials) {
            Map<String, Object> head = mat.head();
            if (!enabledForMode(mat)) continue;
            if (MEDIA_OK.equals(head.get("status"))) continue;
            String taskId = mat.taskId();
            if (taskId != null) {
                // 有任务号：由上方的 query 回填，status 为终态则一定会被标记为 OK
                String st = String.valueOf(head.get("status"));
                if ("FAILED".equals(st) || "CANCELED".equals(st)) {
                    // 出图失败不能让整幕永远卡在素材阶段：记一笔失败并当作「无素材」放行。
                    // 必须把 taskId 一并清掉，否则下一轮又落回任务分支，永远等不到终态。
                    head.remove("taskId");
                    head.put("status", MEDIA_NO_PROMPT);
                    head.put("failed", Boolean.TRUE);
                    continue;
                }
                // 状态 PENDING/RUNNING：任务还在生成中，按约束一次只允许一个任务
                inProgress = true;
                continue;
            }
            // 没有任务号：是否有素材需求
            if (String.valueOf(head.get("prompt")).isBlank()) {
                // 这个节点本来就没有画面描述（如无名道具）：不是失败，只是不需要图
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
        // 只要还有任务在生成中，本幕就没收敛：这一刻放行，观众会先看到「几场有图」，
        // 等后台补完才算齐，中间那段就是混合态。所以 inProgress 时不进收口分支。
        if (target == null && !inProgress) {
            // 整幕一致性收口：一场戏要么全有图、要么全没有，不做半张半张的混合。
            // 与提交循环之后的收口是同一段逻辑。
            return closeActIfConverged(worldId, actNo, materials, json, failedTypes);
        }
        if (inProgress || target == null) {
            // 还有任务在生成中（或还有素材没轮到）：本幕没收敛，先只回进度、不放行。
            // 素材阶段每轮只提交一个任务，MAX_ASSETS_PER_TICK 只在这一轮内生效，
            // 所以 target != null 时同样要返回进度让调用方发起下一轮。
            for (Material m : materials) m.persist();
            persistFailedTypes(json, failedTypes);
            storyStore.writeAct(worldId, actNo, Json.toJson(json));
            return out;
        }

        int created = 0;
        String lastError = null;
        while (target != null && created < MAX_ASSETS_PER_TICK) {
            if (failedTypes.contains(String.valueOf(target.head().get("type")))) {
                target.head().remove("taskId");
                target.head().put("status", MEDIA_NO_PROMPT);
                target.head().put("failed", Boolean.TRUE);
                target = nextTarget(materials);
                continue;
            }
            Material mat = target;
            SysAiGenerateTask task;
            try {
                task = createAssetTask(mat);
            } catch (Exception e) {
                // 没有出图/出视频能力时不能让整幕停摆：该素材按「无素材」放行，演出照常进行。
                // 打 failed 标记是给整幕一致性收口用的：能力缺失时整幕统一退回纯文字，
                // 不要出现「前一场有图、后一场没有」。
                mat.head().put("status", MEDIA_NO_PROMPT);
                mat.head().put("failed", Boolean.TRUE);
                mat.head().remove("taskId");
                failedTypes.add(String.valueOf(mat.head().get("type")));
                lastError = e.getMessage() == null ? "媒体服务不可用" : e.getMessage();
                target = nextTarget(materials);
                continue;
            }
            created++;
            mat.head().put("taskId", task.getTaskId());
            String st = task.getStatus() == null || task.getStatus().isBlank() ? "RUNNING" : task.getStatus();
            // 本地同步任务常常「受理即失败」（例如环境没有该类型的生成能力）：
            // 建完立刻回查一次，失败就当场放行，别让整幕按轮次空转。
            if ("RUNNING".equals(st) || "PENDING".equals(st)) {
                SysAiGenerateTask now = null;
                try {
                    now = generateService.query(task.getTaskId());
                } catch (Exception ignore) {
                    // 回查失败按仍在跑处理
                }
                String nowSt = now == null ? null : now.getStatus();
                if (nowSt != null && !nowSt.isBlank()) {
                    st = nowSt;
                }
                if ("FAILED".equals(st) || "CANCELED".equals(st)) {
                    // 受理即失败：必须同时清掉 taskId 并落成「该出没出」，
                    // 否则下一轮还是「有任务号 → 判定生成中」，整幕永远等一个已经死掉的任务，
                    // 而状态又停在 PENDING，最终以半收敛的混合形态被放行。
                    mat.head().remove("taskId");
                    mat.head().put("status", MEDIA_NO_PROMPT);
                    mat.head().put("failed", Boolean.TRUE);
                    failedTypes.add(String.valueOf(mat.head().get("type")));
                    continue;
                }
            }
            mat.head().put("status", st);
            if ("SUCCESS".equals(st)) {
                List<String> urls = GenerateService.mediaUrls(task);
                String raw = urls == null || urls.isEmpty() ? null : urls.get(0);
                mat.head().put("raw", raw);
                mat.head().put("url", raw == null ? null : playableUrl(task, raw));
                mat.head().put("status", MEDIA_OK);
            } else if ("FAILED".equals(st) || "CANCELED".equals(st)) {
                // 同步失败（例如模型不支持该类型）：同样放行，别把整幕卡死。
                // 打 failed 标记，让整幕一致性收口统一退回纯文字，不留半张图
                mat.head().put("status", MEDIA_NO_PROMPT);
                mat.head().put("failed", Boolean.TRUE);
                mat.head().remove("taskId");
            } else {
                // 真在跑：按约束一次只允许一个生成中任务，交回给下一轮轮询。
                // 这里不能把类型记进失败集合：本地同步任务建出来就是 PENDING/RUNNING，
                // 大多会在下一轮变成 SUCCESS。此前一建任务就记账，等于「凡是建了任务的类型
                // 都判死」——场景图与视频永远生不出来，舞台只能退回文字演出。
                // 真正的失败由上面 FAILED/CANCELED 分支与 catch 分支记账。
                inProgress = true;
            }
            target = inProgress ? null : nextTarget(materials);
        }
        for (Material m : materials) m.persist();
        persistFailedTypes(json, failedTypes);
        storyStore.writeAct(worldId, actNo, Json.toJson(json));
        // 形态关闭（text）时本轮不该跑素材循环；真正「全部收敛」的判定只放在上面
        // 唯一的收口处（target == null && !inProgress）。
        //
        // 这里曾经有一句 `if (target == null && !inProgress) return null;` 的捷径，
        // 它绕过了整幕一致性收口：只要循环里把目标耗光（失败类型全部放行），
        // 就直接以 null 放行进表演，于是「有些场景有图、有些还在生成」的混合态
        // 就这么漏到舞台上。收敛只能有一个出口。
        if (inProgress || target != null) {
            // 还在跑 / 还有没轮到的素材：回进度，让它下一轮再来
            if (lastError != null) {
                out.put("assetError", lastError);
            }
            out.put("created", created);
            out.put("inProgress", inProgress);
            return out;
        }
        // 循环把目标全部耗光且没有在跑的任务：和第一段收敛走同一套收口判定。
        return closeActIfConverged(worldId, actNo, materials, json, failedTypes);
    }

    /**
     * 整幕素材收口（唯一出口）。
     *
     * <p>素材阶段有两条路会走到「没有待办、也没有在跑的任务」：首轮判定就是收敛的，
     * 和提交循环把目标全部耗光之后。两条路必须做同一件事——按一致性判定要么放行演出，
     * 要么整幕统一退回纯文字。分成两处写就出过事：循环那条路曾经直接返回，绕过判定。</p>
     *
     * @param worldId     世界 id
     * @param actNo       幕号
     * @param materials   本幕全部素材
     * @param json        剧本 JSON
     * @param failedTypes 本幕失败类型
     * @return 收敛时返回 null（调用方进入演出），否则返回给调用方的进度的空壳
     */
    private Map<String, Object> closeActIfConverged(Long worldId, Integer actNo, List<Material> materials,
                                     Map<String, Object> json, Set<String> failedTypes) {
        if (MEDIA_OK.equals(enforceActMediaConsistency(materials))) {
            // 收敛后的分布：场景要么全 OK、要么全无需求。留一条日志，
            // 混合问题一旦回潮，能直接从这一行看出当时各通道的真实状态。
            int ok = 0;
            int none = 0;
            for (Material m : materials) {
                if (!enabledForMode(m)) continue;
                if (MEDIA_OK.equals(m.head().get("status"))) ok++;
                else none++;
            }
            log.info("[WorldSim] 素材收敛 world={} act={} mode={} 有素材={} 无需求={} failedTypes={}",
                    worldId, actNo, mediaMode(), ok, none, failedTypes);
            for (Material m : materials) m.persist();
            persistFailedTypes(json, failedTypes);
            storyStore.writeAct(worldId, actNo, Json.toJson(json));
            return null;
        }
        // 有素材该出却没出：整幕统一退回纯文字，已出好的图也不用，
        // 免得同一幕里图片状态自相矛盾（舞台上最刺眼的就是「有几场有图」）。
        StringBuilder why = new StringBuilder();
        for (Material m : materials) {
            if (!enabledForMode(m)) continue;
            Map<String, Object> h = m.head();
            if (MEDIA_NO_PROMPT.equals(h.get("status")) && Boolean.TRUE.equals(h.get("failed"))) {
                why.append(h.get("role")).append(' ');
            }
        }
        log.warn("[WorldSim] 素材退化 world={} act={} mode={} 该出没出={} failedTypes={}",
                worldId, actNo, mediaMode(), why.toString().trim(), failedTypes);
        for (Material m : materials) {
            m.head().remove("url");
            m.head().remove("raw");
            m.head().remove("taskId");
            m.head().put("status", MEDIA_NO_PROMPT);
        }
        for (Material m : materials) m.persist();
        persistFailedTypes(json, failedTypes);
        storyStore.writeAct(worldId, actNo, Json.toJson(json));
        return null;
    }

    /**
     * 把「本幕失败的媒体类型」写回剧本节点。
     *
     * <p>剧本文件是唯一跨请求存活的载体：只记在内存 Set 里，下一轮推进又会当它没失败过，
     * 于是同一类型的素材被反复提交、反复失败，整幕永远停在素材阶段。</p>
     *
     * @param json        剧本 JSON
     * @param failedTypes 失败类型集合
     */
    private static void persistFailedTypes(Map<String, Object> json, Set<String> failedTypes) {
        if (failedTypes == null || failedTypes.isEmpty()) {
            json.remove(FAILED_TYPES_KEY);
            return;
        }
        json.put(FAILED_TYPES_KEY, new ArrayList<>(failedTypes));
    }

    /**
     * 这个素材通道在「图片形态」下是否要出图。
     *
     * <p>图片形态只给<b>场景</b>出图。用户看到的舞台是「每一场一张定场画面」，
     * 过场与道具在演出里不出现，也就没有画面位置放它们的图。</p>
     *
     * <p>更重要的是一致性：整幕的图要么全有、要么全没有。若把过场和道具也算进来，
     * 一次十来个素材里只要有一张失败，整幕的好图就全被作废——
     * 本机实测 8 个图片任务里恰好坏在道具上，整幕退成了纯文字。
     * 收窄到场景后，判定范围就是观众真正会看到的那几张。</p>
     *
     * @param mat 素材
     * @return true 表示图片形态下要给它出图
     */
    private static boolean imageWanted(Material mat) {
        return !mat.video() && !MEDIA_PROP_TYPE.equals(String.valueOf(mat.head().get("nodeType")))
                && !MEDIA_TRANSITION_TYPE.equals(String.valueOf(mat.head().get("nodeType")));
    }

    /**
     * 这个素材通道在当前形态下是否启用。
     *
     * <p>三形态互斥：图片形态只有场景图；视频形态两者都要（图生视频要有首帧）；
     * 文本形态两个都关。判断统一收在这里，避免各处重复写形态判断而漏掉一处。</p>
     *
     * @param mat 素材
     * @return true 表示本幕要为它出素材
     */
    private boolean enabledForMode(Material mat) {
        if (mat.video()) {
            return videoMediaEnabled();
        }
        return imageMediaEnabled() && imageWanted(mat);
    }

    /**
     * 任务是否已经僵死：非终态、并且长时间没有任何进展。
     *
     * <p>本地环境常常没有出图/出视频能力，受理后的任务会一直停在 PENDING/RUNNING，
     * 没有线程回写终态。这类任务若一直占着「生成中」，整幕就再也演不下去。</p>
     *
     * <p>判死要看<b>最后一次推进的时间</b>，不能只看发起时间：真实出图本机实测 53–120 秒，
     * 排队靠后的任务发起后就一直等，按发起时间判死会在它还没轮到就被判失败。
     * 同时只能用服务端时钟（{@code currentTimeMillis}），任务行的时间来自另一台机器的库，
     * 两套钟差几秒就能把刚建好的任务误判成僵死——本机实测两个任务 SUCCESS 写库同一毫秒，
     * 就是被这条误判打断的。</p>
     *
     * @param task 任务行
     * @return true 表示应当判死
     */
    private static boolean isStaleTask(SysAiGenerateTask task) {
        if (task == null) return true;
        long now = System.currentTimeMillis();
        Long updatedAt = task.getUpdatedAt();
        if (updatedAt == null || updatedAt <= 0) {
            updatedAt = task.getCreatedAt();
        }
        if (updatedAt == null || updatedAt <= 0) return false;
        long idle = now - updatedAt;
        // 数据库与进程存在钟差时 idle 可能为负（任务比现在还「新」），那是活跃而不是僵死
        return idle > STALE_TASK_MS;
    }

    /**
     * 整幕素材一致性收口：一场戏要么全有图，要么全没有。
     *
     * <p>舞台形态是<b>整幕</b>的选择，不是逐节点的。真实环境里会出现两种参差：</p>
     * <ul>
     *   <li>某类素材真的失败（上游报错 / 额度用尽 / 没有该能力），
     *       于是前几场有图、后面几场没图；</li>
     *   <li>部分节点本来就没有画面描述（例如没有名字的道具），
     *       被判成 NO_PROMPT 而跳过。</li>
     * </ul>
     *
     * <p>前者会让观众看到「一会儿有图一会儿没图」，比整幕统一不出图更像是坏了。
     * 后者是节点本来就不该配图，不算破坏一致性。</p>
     *
     * <p>判定办法：只要有素材 <b>建过任务却最终没拿到图</b>（{@code failed} 标记），
     * 就认为本幕不满足一致性，由调用方整幕退回纯文字。</p>
     *
     * <p><b>只看当前形态启用的通道</b>：图片形态下视频通道是关掉的，
     * 它上面的历史 {@code failed}（比如当初试过图生视频被拒）与本幕要不要出图无关。
     * 把它算进来会把一整幕本来完全正常的图片全部作废，回归成纯文字。</p>
     *
     * @param materials 本幕全部素材
     * @return 一致返回 {@link #MEDIA_OK}；出现缺失返回 {@code null}
     */
    private String enforceActMediaConsistency(List<Material> materials) {
        for (Material mat : materials) {
            if (!enabledForMode(mat)) {
                continue;
            }
            Map<String, Object> head = mat.head();
            String st = String.valueOf(head.get("status"));
            if (MEDIA_OK.equals(st) || MEDIA_NO_PROMPT.equals(st)) {
                continue;
            }
            // 还有悬而未决的素材（正常流程走不到这里）
            return null;
        }
        for (Material mat : materials) {
            if (!enabledForMode(mat)) {
                continue;
            }
            Map<String, Object> head = mat.head();
            if (MEDIA_NO_PROMPT.equals(head.get("status"))
                    && Boolean.TRUE.equals(head.get("failed"))) {
                // 该出却没出：整幕不能半张半张
                return null;
            }
        }
        return MEDIA_OK;
    }

    /** 在素材序列里找下一个「还没建任务、有提示词」的目标；无提示词的直接标成放行 */
    private Material nextTarget(List<Material> materials) {
        for (Material mat : materials) {
            Map<String, Object> head = mat.head();
            String st = String.valueOf(head.get("status"));
            if (MEDIA_OK.equals(st) || MEDIA_NO_PROMPT.equals(st)) continue;
            if (mat.taskId() != null) continue;
            if (String.valueOf(head.get("prompt")).isBlank()) {
                head.put("status", MEDIA_NO_PROMPT);
                continue;
            }
            return mat;
        }
        return null;
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
                // String.valueOf(null) 会得到字面量 "null"，会被误当成有效提示词去建任务。
                String prompt = pn == null ? "" : String.valueOf(pn).trim();
                if ((prompt.isEmpty() || "null".equals(prompt)) && !MEDIA_PROP_TYPE.equals(role)) {
                    // 剧本节点缺画面描述（场景 desc 为空 / 过场没写 text）：
                    // 这里就把兜底提示词算出来写进素材头。
                    // 不能留到 createAssetTask 再补——nextTarget 用 head.prompt 判「有没有需求」，
                    // 为空就直接判 NO_PROMPT 跳过，兜底永远轮不到，
                    // 于是同一幕里有的场景有图、有的没图，正是要杜绝的混合。
                    // 道具不套兜底：道具名本身就是要生成的画面主体，mediaPrompt 会补成「道具特写」。
                    String fallback = fallbackPrompt(node);
                    if (fallback != null) {
                        prompt = fallback;
                    }
                }
                head.put("prompt", prompt);
                String type = mk.equals(ActScript.MEDIA_IMAGE_KEY) ? MEDIA_IMAGE_TYPE : MEDIA_VIDEO_TYPE;
                head.put("type", type);
                // 节点种类（scene/transition/prop）与媒体种类（image/video）分开记：
                // 提示词的写法按节点种类补，任务类型按媒体种类分，混用一个键会互相覆盖。
                head.put("nodeType", role);
                items.add(new Material(node, mk, nameField, head));
            }
        }
    }

    /**
     * 这一个素材的提示词是否值得去生成。
     *
     * <p>场景 desc 是「这一场发生了什么」的完整语句，本体就是画面描述，可以直接交给生成模型；
     * 道具只有名字（如「旧书」），单喂一个词生成出来的东西往往和剧情无关——
     * 给它补一句画面语，让模型知道是在什么戏里出现的。</p>
     *
     * @param mat 素材
     * @return 可以直接提交的提示词；不值得生成时为 null
     */
    private static String mediaPrompt(Material mat) {
        Object pn = mat.node().get(mat.promptField());
        String text = pn == null ? "" : String.valueOf(pn).trim();
        if ("null".equals(text)) {
            text = "";
        }
        if (text.isEmpty()) {
            // 场景 desc / 过场 text 缺了：collect 已经把兜底提示词算进素材头，
            // 这里直接用它，保证「每个场景都能出图」这个口径不被缺字段破坏。
            Object headPrompt = mat.head().get("prompt");
            text = headPrompt == null ? "" : String.valueOf(headPrompt).trim();
            if (text.isEmpty() || "null".equals(text)) {
                return null;
            }
        }
        if (mat.video()) {
            // 视频要比静态图多交代「怎么动」：不说清楚时长内几乎没有可看的变化
            if (MEDIA_TRANSITION_TYPE.equals(String.valueOf(mat.head().get("nodeType")))) {
                return text + "。镜头平稳推进，光线与氛围自然流动。";
            }
            return text + "。电影感运镜，人物有细微动作与表情变化，画面连续，不要字幕。";
        }
        if (MEDIA_PROP_TYPE.equals(String.valueOf(mat.head().get("nodeType")))) {
            return "道具特写：" + text + "，置于场景中，光线自然。";
        }
        return text;
    }

    /**
     * 剧本节点缺画面描述时的兜底提示词。
     *
     * <p>用场景名与第一句台词拼一句可用的画面语，保证这一幕的每个场景都能出图，
     * 不至于因为编剧漏写 desc 就变成「几场有图几场没图」。</p>
     *
     * @param node 剧本节点
     * @return 兜底提示词；连场景名都没有时返回 null
     */
    private static String fallbackPrompt(Map<String, Object> node) {
        Object nameObj = node.get(ActScript.SCENE_NAME);
        String name = nameObj == null ? "" : String.valueOf(nameObj).trim();
        if (name.isEmpty() || "null".equals(name)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(name);
        Object lines = node.get(ActScript.SCENE_LINES);
        if (lines instanceof List<?> lineList && !lineList.isEmpty()) {
            Object first = lineList.get(0);
            String speech = "";
            if (first instanceof Map<?, ?> lineMap) {
                Object t = lineMap.get("text");
                if (t == null) t = lineMap.get("content");
                speech = t == null ? "" : String.valueOf(t).trim();
            }
            if (!speech.isEmpty() && !"null".equals(speech)) {
                sb.append("：").append(speech.length() > 40 ? speech.substring(0, 40) : speech);
            }
        }
        return sb.append("。电影感画面，光线与氛围自然，人物神态清晰。").toString();
    }

    /**
     * 舞台播什么：文本 / 图 / 视频。
     *
     * <p>三选一是<b>互斥</b>的，选哪种就只产出哪种，不做「都生成、前端挑一个显示」——
     * 那样纯文本形态也会为每幕跑一遍出图（一张 70–90 秒），
     * 用户只想看文字演出，却要为一次都没看过的图等待与付费。</p>
     *
     * <p>取值：{@code text} / {@code image} / {@code video}；留空按 {@code video} 处理
     * （与历史行为一致，老数据不改也能继续跑）。</p>
     *
     * @return 规范化后的形态
     */
    private String mediaMode() {
        String mode = globalSettingReader.getStr("sim", "media_mode");
        if (mode == null || mode.isBlank()) {
            return SIM_MODE_VIDEO;
        }
        String v = mode.trim().toLowerCase(Locale.ROOT);
        if (SIM_MODE_TEXT.equals(v) || SIM_MODE_IMAGE.equals(v)) {
            return v;
        }
        return SIM_MODE_VIDEO;
    }

    /**
     * 舞台是否出视频。
     *
     * @return true 表示素材阶段要为场景出视频；设置里选 image / text 时为 false
     */
    private boolean videoMediaEnabled() {
        return SIM_MODE_VIDEO.equals(mediaMode());
    }

    /**
     * 舞台是否出图。
     *
     * <p>纯文本形态返回 false：整幕不建任何生成任务，直接进表演。</p>
     *
     * @return true 表示要为场景出图
     */
    private boolean imageMediaEnabled() {
        return !SIM_MODE_TEXT.equals(mediaMode());
    }

    /**
     * 生成（图 / 视频）媒体任务。
     *
     * <p>图与视频走的是两套模型：编剧模型只会「写字」，拿它去建视频任务上游必然
     * 报「模型不支持」，本地又没有真实出图 / 出视频能力，于是素材阶段永远只能降级成纯文字。
     * 因此这里按媒体类型各取各的模型（{@code sim.image_model} / {@code sim.video_model}），
     * 留空时才退回生成侧该类型的默认模型。</p>
     *
     * <p>视频优先走<b>图生视频</b>：同一节点的场景图已经生成好，就以它为参考图，
     * 让画面与舞台上的布景、人物一致；没有可用的图（未开图生成 / 图生成失败）时退回文生视频。</p>
     *
     * @param mat 待生成的素材
     * @return 已提交的生成任务
     */
    private SysAiGenerateTask createAssetTask(Material mat) {
        String type = String.valueOf(mat.head().get("type"));
        String rawPrompt = String.valueOf(mat.head().get("prompt"));
        boolean video = MEDIA_VIDEO_TYPE.equals(type);

        // 画面提示词按节点种类补全（场景直接用 desc，道具补成特写，视频补上运镜与动作）
        String prompt = mediaPrompt(mat);
        if (prompt == null) {
            prompt = rawPrompt;
        }

        StringBuilder model = new StringBuilder(video ? mediaModel(MEDIA_VIDEO_TYPE) : mediaModel(MEDIA_IMAGE_TYPE));
        if (model.toString().isBlank()) {
            // 设置里没配：退回生成侧对同类型已经可用（有 SPI 实现）的模型
            String found = defaultGenerateModel(type);
            model.setLength(0);
            model.append(found == null ? "" : found);
        }
        if (model.toString().isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, video
                    ? "请配置视频生成模型（sim.video_model 或任一视频模型）"
                    : "请配置图片生成模型（sim.image_model 或任一图片模型）");
        }

        byte[] reference = null;
        if (video) {
            reference = referenceImageOf(mat);
        }

        GenerateService.Request request = new GenerateService.Request(
                type, model.toString(), prompt, null,
                video ? videoSize(model.toString()) : MEDIA_SIZE,
                MEDIA_COUNT,
                video ? VIDEO_DURATION : null,
                null, null, null, null,
                reference == null ? null : Base64.getEncoder().encodeToString(reference),
                AiUserContext.requireUserId());
        try {
            return generateService.create(request);
        } catch (IllegalStateException e) {
            // 图生视频被渠道拒绝（参考图只收公网地址、存储不可用，或该模型不支持参考图）：
            // 观众等的是这一幕的画面，而不是一条「不支持」的错误。退回文生视频重试一次，
            // 并把原因记在素材头上，前端能看到「这一条是退回来的」。
            if (reference == null) {
                throw e;
            }
            String reason = e.getMessage() == null ? "图生视频不可用" : e.getMessage();
            log.info("[WorldSim] 图生视频不可用，退回文生视频 type={} reason={}", type, reason);
            mat.head().put("videoMode", "text2video");
            mat.head().put("videoFallbackReason", reason);
            GenerateService.Request fallback = new GenerateService.Request(
                    type, model.toString(), prompt, null, videoSize(model.toString()), MEDIA_COUNT,
                    VIDEO_DURATION, null, null, null, null, null, AiUserContext.requireUserId());
            return generateService.create(fallback);
        }
    }

    /**
     * 取图生视频的参考图（字节）。
     *
     * <p>场景图由生成侧落地：可能是外链，也可能是落盘的 base64 / data URI。
     * 两种都要能读成字节；本机读不到（外链无网、文件已清理）就返回 null，退回文生视频。</p>
     *
     * @param mat 视频素材
     * @return 参考图字节，取不到为 null
     */
    private byte[] referenceImageOf(Material mat) {
        String url = mat.referenceImageUrl();
        if (url == null) {
            return null;
        }
        mat.head().put("videoMode", "image2video");
        mat.head().put("referenceImage", url.length() > 200 ? url.substring(0, 200) + '…' : url);
        byte[] bytes = loadImageBytes(url);
        if (bytes == null) {
            mat.head().put("videoMode", "text2video");
            mat.head().put("videoFallbackReason", "场景图读不到（外链不可达或文件已清理）");
        }
        return bytes;
    }

    /**
     * 把生成结果地址读成字节。
     *
     * @param url data URI / base64 / http(s) 地址
     * @return 图片字节，读不到为 null
     */
    private byte[] loadImageBytes(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            if (url.startsWith("data:")) {
                int comma = url.indexOf(',');
                return comma < 0 ? null : Base64.getDecoder().decode(url.substring(comma + 1));
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                java.net.URI uri = java.net.URI.create(url);
                try (java.io.InputStream in = uri.toURL().openStream()) {
                    return in.readAllBytes();
                }
            }
            // 落盘结果给的是 media:xxx 句柄，交生成侧的存储按同口径读回字节
            if (GenerateMediaStore.isHandle(url)) {
                GenerateMediaStore.Stored stored = generateMediaStore.load(url);
                if (stored != null && stored.bytes() != null && stored.bytes().length > 0) {
                    return stored.bytes();
                }
            }
            String bare = url.trim();
            if (bare.length() > 64 && bare.matches("[A-Za-z0-9+/=\\s]+")) {
                // 裸 base64（无前缀）：生成侧部分实现直接把字节编码成字符串落库
                return Base64.getDecoder().decode(bare.replaceAll("\\s", ""));
            }
            return null;
        } catch (Exception e) {
            log.warn("[WorldSim] 读取场景图失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 素材模型的设置值。
     *
     * @param type image / video
     * @return 设置里配置的模型名；未配置为空串
     */
    private String mediaModel(String type) {
        String key = MEDIA_VIDEO_TYPE.equals(type) ? "video_model" : "image_model";
        String model = globalSettingReader.getStr("sim", key);
        return model == null ? "" : model.trim();
    }

    /**
     * 同类型可用的默认生成模型。
     *
     * <p>只挑「该供应商确实有对应 SPI 实现」的模型：否则建了任务也只是再去失败一次，
     * 还会被记进失败类型，连别的可用模型都轮不上。</p>
     *
     * @param type image / video
     * @return 模型名；找不到为 null
     */
    private String defaultGenerateModel(String type) {
        boolean video = MEDIA_VIDEO_TYPE.equals(type);
        try {
            var rows = providerModelMapper.selectList(
                    new LambdaQueryWrapper<SysAiProviderModel>()
                            .eq(SysAiProviderModel::getEnabled, true));
            for (SysAiProviderModel row : rows) {
                if (row.getModel() == null || row.getModel().isBlank()) {
                    continue;
                }
                // 能力位里声明的是何种任务：出图模型绝不能拿去建视频任务。
                // 只靠 resolveVideo「供应商支持视频」会把图片模型也选中，
                // 建出来必然被上游拒（本机实测 agnes-image-2.1-flash 反复提交失败）。
                if (!declaresTask(row.getCapabilities(), type)) {
                    continue;
                }
                var resolved = video
                        ? generateClientResolver.resolveVideo(row.getModel())
                        : generateClientResolver.resolveImage(row.getModel());
                if (resolved.ok()) {
                    return row.getModel();
                }
            }
        } catch (Exception e) {
            log.warn("[WorldSim] 查找默认生成模型失败 type={}: {}", type, e.getMessage());
        }
        return null;
    }

    /**
     * 模型行声明的能力里是否包含指定任务类型。
     *
     * <p>能力位是 JSON 数组（如 {@code [{"task":"image_generation"}]}）。
     * 没写能力位的模型一律不认——宁可让上层报「请配置模型」，
     * 也不要拿一个不确定的模型去跑，跑出来的失败还会污染整幕的素材一致性。</p>
     *
     * @param capabilities 能力位 JSON
     * @param type         image / video
     * @return true 表示该模型能承担这类任务
     */
    private static boolean declaresTask(Object capabilities, String type) {
        if (capabilities == null) {
            return false;
        }
        String want = MEDIA_VIDEO_TYPE.equals(type) ? "video_generation" : "image_generation";
        if (capabilities instanceof List<?> caps) {
            for (Object cap : caps) {
                if (cap instanceof Map<?, ?> m && want.equals(String.valueOf(m.get("task")))) {
                    return true;
                }
            }
            return false;
        }
        // 兼容以 JSON 字符串存能力位的旧行
        String text = String.valueOf(capabilities);
        return !text.isBlank() && text.contains("\"" + want + "\"");
    }

    /**
     * 视频分辨率：按模型的尺寸声明取第一条（竖屏短剧就是 9:16），未声明时用默认横屏。
     *
     * @param model 视频模型名
     * @return WxH
     */
    private String videoSize(String model) {
        try {
            var resolved = generateClientResolver.resolveVideo(model);
            if (resolved != null && resolved.modelCfg() != null) {
                var view = GenerateConfigSupport.read(resolved.modelCfg(), MEDIA_VIDEO_TYPE);
                if (view.sizes() != null && !view.sizes().isEmpty()
                        && view.sizes().get(0) != null && !view.sizes().get(0).isBlank()) {
                    return view.sizes().get(0);
                }
            }
        } catch (Exception ignore) {
            // 读不到尺寸声明就用默认值
        }
        return MEDIA_VIDEO_SIZE;
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
     * 整幕表演（保留给一次性演完全部的场景）。
     *
     * <p>演出已改成分场增量，正常路径不再走这里，留作整幕重演的兜底实现。</p>
     */
    private String playAct(Long worldId, Integer actNo, String script, SysAiWorldAct act) {
        Map<String, Object> json = Json.fromJson(script, Map.class);
        List<Map<String, Object>> scenes = sceneList(json.get(ActScript.SCENES));
        if (scenes.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有可表演的场景");
        }
        int order = 0;
        for (Map<String, Object> scene : scenes) {
            playScene(worldId, scene, ++order);
        }
        json.put(ActScript.ACT_STATUS, ACT_STATUS_PLAYING);
        return Json.toJson(json);
    }

    /**
     * 下一场还没演的场次下标（剧本里 playText 为空的第一个场景）。
     *
     * @param scenes 场景列表
     * @return 下标；全部演完返回 -1
     */
    private static int nextUnplayedScene(List<Map<String, Object>> scenes) {
        for (int i = 0; i < scenes.size(); i++) {
            if (str(scenes.get(i).get(ActScript.SCENE_PLAY), null) == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 演一场：给这个场景生成一段表演叙述，写进场景节点的 playText（就地修改）。
     *
     * @param worldId 世界
     * @param scene   场景节点（剧本 JSON 树里的活动对象）
     * @param order   第几场（从 1 起，用于序号小标题）
     */
    private void playScene(Long worldId, Map<String, Object> scene, int order) {
        String name = str(scene.get(ActScript.SCENE_NAME), "场景");
        String cast = npcsLabel(worldId, scene.get(ActScript.SCENE_NPCS));
        // 演出正文里记下演员名：舞台需要标「这一幕有谁」，剧本原件里只有 npc id
        scene.put(ActScript.SCENE_CAST, cast);
        SysAiWorld world = worldMapper.selectById(worldId);
        String prompt =
            "你是一位电影/话剧导演。请根据以下剧本场景生成一段不超过 300 字的表演叙述（对话 + 动作 + 神态），"
            + "风格：古典历史剧；不要输出任何 JSON 与 HTML，只输出纯文本。"
            + "\n\n【场景】" + name
            + "\n【世界】" + (world == null ? "世界" : world.getName() + (world.getEra() == null ? "" : "（" + world.getEra() + "）"))
            + "\n【画面描述】" + str(scene.get(ActScript.SCENE_DESC), "")
            + "\n【演员与关系】" + cast;
        AiChatRequest request = new AiChatRequest();
        request.setModel(scriptModel(world));
        request.setPrompt(prompt);
        request.setUseSkill(false);
        request.setTemperature(0.8);
        request.setMaxTokens(2000);
        AiChatResult result = aiChatFacadeService.callForResult(request);
        if (result == null || !result.success() || result.text() == null || result.text().isBlank()) {
            // 不能静默留在 playing：前端会一直推进，观众等到的只有空白舞台。
            String reason = str(result.errorMessage(), "模型未返回内容");
            throw new ApiException(ApiErrorCode.AI_ERROR, "第 " + order + " 场演出失败：" + reason);
        }
        // 落「序 · 标题」再进正文：前端把首行当分场标题，正文不再重复标题
        scene.put(ActScript.SCENE_PLAY, seqLabel(order) + " " + name + "\n" + result.text().trim());
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
            m.put("cast", s.get(ActScript.SCENE_CAST));
            // 剧情概览用：画面描述与台词（观众在演出前先看得到这一幕要讲什么）
            m.put("desc", s.get(ActScript.SCENE_DESC));
            m.put("lines", s.get(ActScript.SCENE_LINES));
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

    /** 分场序号：1 → 一，11 → 十一（正文小标题用它，观众一眼看出场次递增） */
    private static String seqLabel(int n) {
        String[] digits = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n <= 0) {
            return "一";
        }
        if (n < 10) {
            return digits[n - 1];
        }
        if (n < 20) {
            return "十" + (n == 10 ? "" : digits[n - 11]);
        }
        return String.valueOf(n);
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

        /** 该节点取提示词用的字段名（场景 desc / 过场 text / 道具 name） */
        private final String promptField;

        private final Map<String, Object> head;

        private Material(Map<String, Object> node, String field, String promptField, Map<String, Object> head) {
            this.node = node;
            this.field = field;
            this.promptField = promptField;
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

        /** 所属节点（场景 / 过场 / 道具） */
        public Map<String, Object> node() {
            return node;
        }

        /** 是否视频通道 */
        public boolean video() {
            return ActScript.MEDIA_VIDEO_KEY.equals(field);
        }

        /** 取提示词用的字段名（场景 desc / 过场 text / 道具 name） */
        public String promptField() {
            return promptField;
        }

        /**
         * 同一节点下已生成好的画面地址（图生视频的参考图来源）。
         *
         * <p>素材顺序是先图后视频：视频任务轮到建的时候，场景图通常已经 OK，
         * 把它当首帧喂给视频模型，画面才和这一幕的布景、人物对得上，
         * 而不是另外凭空生成一段与舞台无关的视频。</p>
         *
         * @return 画面地址；没有就直接返回 null，调用方退回文生视频
         */
        public String referenceImageUrl() {
            Object img = node.get(ActScript.MEDIA_IMAGE_KEY);
            if (!(img instanceof Map<?, ?> m)) {
                return null;
            }
            if (!MEDIA_OK.equals(String.valueOf(m.get("status")))) {
                return null;
            }
            // 优先后端自用原始值（可能是 media: 句柄），它才是能读出字节的那个；
            // url 给前端用的是 /v2/... 相对地址，后端取不到字节。
            Object raw = m.get("raw");
            Object url = raw != null && !String.valueOf(raw).isBlank() ? raw : m.get("url");
            return url == null || String.valueOf(url).isBlank() ? null : String.valueOf(url);
        }

        /** 把自身写回剧本节点 */
        public void persist() {
            // 注意：剧本里 mediaImage/mediaVideo 的初值常常是显式的 null（AI 编剧按约定这么写），
            // 只判断 containsKey 会让媒体头永远写不回去，素材阶段每轮都被重置成 NOT_STARTED。
            if (!node.containsKey(field) || node.get(field) == null || node.get(field) == head) {
                node.put(field, head);
            }
        }

        @Override
        public String toString() {
            return "Material{" + field + "=" + head + "}";
        }
    }
}