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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
        request.setModel(scriptModel());
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
    /** 素材任务超过这个时长仍无终态就判死放行（毫秒） */
    private static final long STALE_TASK_MS = 120_000L;
    /** 剧本里记录「本幕失败的媒体类型」的键（跨请求持久化） */
    private static final String FAILED_TYPES_KEY = "failedMediaTypes";
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
                // 这一幕演完即接下一幕：AI 自己把故事往下续，观众不用点任何东西。
                // 这里只开一个空的下一幕并回传整行，具体书写/演出由前端连续推进，
                // 避免在同一事务里递归 advanceAct 造成状态机重入。
                out.put("nextAct", actRow(createAct(worldId)));
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
                st = MEDIA_NO_PROMPT;
            }
            mat.head().put("status", st);
            if ("SUCCESS".equals(task.getStatus())) {
                List<String> urls = GenerateService.mediaUrls(task);
                mat.head().put("url", urls == null || urls.isEmpty() ? null : urls.get(0));
                // 成功必须落成 OK：素材就绪判断、图生视频取首帧、前端放映都只认 OK。
                // 此前这里只写了 task 的 SUCCESS 字样，OK 从头到尾没人写过，
                // 结果素材永远停在非终态、图生视频永远拿不到参考图、舞台永远退回文字演出。
                mat.head().put("status", MEDIA_OK);
            }
            mat.persist();
        }

        boolean inProgress = false;
        Material target = null;
        // 关了视频出片的部署：视频通道整体放行，只出场景图。
        // 不这么做的话，每一个场景都会先建一次注定被拦掉的视频任务，素材阶段白白空转一圈。
        boolean videoEnabled = videoMediaEnabled();
        // 按已有的失败记录收敛一遍：失败类型直接放行，不再为它建任务
        for (Material mat : materials) {
            Map<String, Object> head = mat.head();
            String st = String.valueOf(head.get("status"));
            if (failedTypes.contains(String.valueOf(head.get("type")))) {
                head.remove("taskId");
                head.put("status", MEDIA_NO_PROMPT);
            } else if (!videoEnabled && mat.video()) {
                head.remove("taskId");
                head.put("status", MEDIA_NO_PROMPT);
            }
        }
        for (Material mat : materials) {
            Map<String, Object> head = mat.head();
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
                    continue;
                }
                // 状态 PENDING/RUNNING：任务还在生成中，按约束一次只允许一个任务
                inProgress = true;
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
            if (inProgress) {
                for (Material m : materials) m.persist();
                persistFailedTypes(json, failedTypes);
                storyStore.writeAct(worldId, actNo, Json.toJson(json));
                return out;
            }
            for (Material m : materials) m.persist();
            persistFailedTypes(json, failedTypes);
            storyStore.writeAct(worldId, actNo, Json.toJson(json));
            return null;
        }
        if (inProgress) {
            // 有任务还在生成中，不能叠新的，由服务端生命周期调遣
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
                target = nextTarget(materials);
                continue;
            }
            Material mat = target;
            SysAiGenerateTask task;
            try {
                task = createAssetTask(mat);
            } catch (Exception e) {
                // 没有出图/出视频能力时不能让整幕停摆：该素材按「无素材」放行，演出照常进行
                mat.head().put("status", MEDIA_NO_PROMPT);
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
                    failedTypes.add(String.valueOf(mat.head().get("type")));
                }
            }
            mat.head().put("status", st);
            if ("SUCCESS".equals(st)) {
                List<String> urls = GenerateService.mediaUrls(task);
                mat.head().put("url", urls == null || urls.isEmpty() ? null : urls.get(0));
                mat.head().put("status", MEDIA_OK);
            } else if ("FAILED".equals(st) || "CANCELED".equals(st)) {
                // 同步失败（例如模型不支持该类型）：同样放行，别把整幕卡死
                mat.head().put("status", MEDIA_NO_PROMPT);
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
        // 循环可能把 target 耗成 null（失败类型全部放行 / 本轮额度用尽）：
        // 此时素材阶段其实已经结束，必须返回 null 让状态机进入演出，
        // 否则会一直返回一个「无事可做」的进度对象，整幕永远停在 props_ok。
        if (target == null && !inProgress) {
            return null;
        }
        if (lastError != null) {
            out.put("assetError", lastError);
        }
        out.put("created", created);
        out.put("inProgress", inProgress);
        return out;
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
     * 任务是否已经僵死：非终态且发起时间超过阈值。
     *
     * <p>本地环境常常没有出图/出视频能力，受理后的任务会一直停在 PENDING/RUNNING，
     * 没有线程回写终态。这类任务若一直占着「生成中」，整幕就再也演不下去。</p>
     *
     * @param task 任务行
     * @return true 表示应当判死
     */
    private static boolean isStaleTask(SysAiGenerateTask task) {
        if (task == null) return true;
        Long createdAt = task.getCreatedAt();
        if (createdAt == null || createdAt <= 0) return false;
        return System.currentTimeMillis() - createdAt > STALE_TASK_MS;
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
                head.put("prompt", pn == null ? "" : String.valueOf(pn));
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
        if (pn == null) {
            return null;
        }
        String text = String.valueOf(pn).trim();
        if (text.isEmpty() || "null".equals(text)) {
            return null;
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
     * 舞台是否出视频。
     *
     * @return true 表示素材阶段要为场景出视频；设置里选 image 时为 false
     */
    private boolean videoMediaEnabled() {
        String mode = globalSettingReader.getStr("sim", "media_mode");
        return mode == null || mode.isBlank() || "video".equalsIgnoreCase(mode.trim());
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
                            .eq(SysAiProviderModel::getEnabled, true));            for (SysAiProviderModel row : rows) {
                if (row.getModel() == null || row.getModel().isBlank()) {
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
     * 逐场景表演叙述（一镜到底），每场景一次对话调用
     */
    private String playAct(Long worldId, Integer actNo, String script, SysAiWorldAct act) {
        Map<String, Object> json = Json.fromJson(script, Map.class);
        List<Map<String, Object>> scenes = sceneList(json.get(ActScript.SCENES));
        if (scenes.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "没有可表演的场景");
        }
        SysAiWorld world = worldMapper.selectById(worldId);
        int order = 0;
        for (Map<String, Object> scene : scenes) {
            order++;
            String name = str(scene.get(ActScript.SCENE_NAME), "场景");
            String cast = npcsLabel(worldId, scene.get(ActScript.SCENE_NPCS));
            // 演出正文里记下演员名：舞台需要标「这一幕有谁」，剧本原件里只有 npc id
            scene.put(ActScript.SCENE_CAST, cast);
            String prompt =
                "你是一位电影/话剧导演。请根据以下剧本场景生成一段不超过 300 字的表演叙述（对话 + 动作 + 神态），"
                + "风格：古典历史剧；不要输出任何 JSON 与 HTML，只输出纯文本。"
                + "\n\n【场景】" + name
                + "\n【世界】" + (world == null ? "世界" : world.getName() + (world.getEra() == null ? "" : "（" + world.getEra() + "）"))
                + "\n【画面描述】" + str(scene.get(ActScript.SCENE_DESC), "")
                + "\n【演员与关系】" + cast;
            AiChatRequest request = new AiChatRequest();
            request.setModel(scriptModel());
            request.setPrompt(prompt);
            request.setUseSkill(false);
            request.setTemperature(0.8);
            request.setMaxTokens(2000);
            AiChatResult result = aiChatFacadeService.callForResult(request);
            if (result.success() && result.text() != null && !result.text().isBlank()) {
                // 落「序 · 标题」再进正文：前端把首行当分场标题，正文不再重复标题
                scene.put(ActScript.SCENE_PLAY, seqLabel(order) + " " + name + "\n" + result.text().trim());
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
            Object url = m.get("url");
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