// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

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
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 模拟-世界沙盒流式控制器（场景对话 SSE + 时间推进）。
 *
 * <p>与酒馆的区别在于上下文更厚：每位 NPC 回复时，除了角色自己的人设，
 * 还会注入<b>世界状态</b>（世界观/时代/天气/当前时间）、<b>场景描述</b>与
 * <b>关系网摘要</b>（该 NPC 与其他在场 NPC 的好感度），让 NPC 的言行受
 * 世界设定与关系演化支配，而不是单纯的群聊人设。</p>
 *
 * <p>路由规则（按优先级）：
 * <ol>
 *   <li>@某 NPC → 被@的 NPC 回复</li>
 *   <li>未@ + 场景内多 NPC → 路由模型按场景上下文挑一位</li>
 *   <li>未@ + 单 NPC → 该 NPC 回复</li>
 * </ol>
 * 主回复后按每人 isActive 与随机概率（固定 25%）由另一位在场 NPC 补一句。
 * 一轮结束后，用模型解析本段互动对各 NPC 关系的好感度增减并落库。</p>
 *
 * <p>时间推进（advance）：调用模型生成「世界动态」事件（标题 + 正文 + 可选天气 +
 * 好感度增减），更新世界时间标签并写入事件时间线。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/world-sim")
@ApiReturnFormatIgnore
@RequiredArgsConstructor
@Slf4j
public class WorldSimChatController {

    /** 世界模拟回复线程池（守护线程，不阻止 JVM 退出） */
// [P3C C1 豁免] 场景化创建线程，需文档化
    private static final ExecutorService WORLD_CHAT_POOL = Executors.newCachedThreadPool(r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
        Thread t = new Thread(r, "world-sim-chat-reply");
        t.setDaemon(true);
        return t;
    });

    /** 每轮对话注入的上下文消息条数 */
    private static final int CONTEXT_LIMIT = 20;

    /** 主动补话概率（%） */
    private static final double INTERJECT_PROBABILITY = 25.0;

    /** 关系好感度钳制区间 */
    private static final int AFFINITY_MIN = -100;
    private static final int AFFINITY_MAX = 100;

    private final SysAiWorldMapper worldMapper;
    private final SysAiWorldSceneMapper sceneMapper;
    private final SysAiWorldNpcMapper npcMapper;
    private final SysAiWorldRelationshipMapper relationshipMapper;
    private final SysAiWorldEventMapper eventMapper;
    private final SysAiWorldMessageMapper messageMapper;
    private final SysAiRoleMapper roleMapper;
    private final AiChatFacadeService aiChatFacadeService;
    private final SysAiGlobalSettingService globalSettingService;
    private final EffectiveChatModelResolver effectiveChatModelResolver;
    private final ObjectMapper objectMapper;
    private final com.chua.starter.ai.support.service.WorldSimService worldSimService;

    /** 供应商查询：把模型限定名里的 providerId 补成可读供应商信息 */
    private final com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper providerMapper;

    /* ============================================================================
     * 场景对话 SSE
     * ==========================================================================*/

    /**
     * 场景对话流式接口。
     *
     * @param body worldId/sceneId/content/atRoleId
     * @param response HTTP 响应
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chat(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        Long worldId = body.get("worldId") == null ? null : Long.valueOf(String.valueOf(body.get("worldId")));
        Long sceneId = body.get("sceneId") == null ? null : Long.valueOf(String.valueOf(body.get("sceneId")));
        String content = str(body.get("content"));
        Integer atRoleId = body.get("atRoleId") == null || "null".equals(String.valueOf(body.get("atRoleId")))
                ? null : Integer.valueOf(String.valueOf(body.get("atRoleId")));
        if (worldId == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少世界ID");
        }
        if (content == null || content.isBlank()) {
            content = "";
        }

        SysAiWorld world = requireOwned(worldId);
        SysAiWorldScene scene = null;
        if (sceneId != null) {
            scene = sceneMapper.selectById(sceneId);
            if (scene == null || !worldId.equals(scene.getWorldId())) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "场景不存在或不属于该世界");
            }
        }

        // 场景内 NPC 才是"在场"的回复候选
        List<SysAiWorldNpc> sceneNpcs = listSceneNpcs(worldId, sceneId);
        if (sceneNpcs.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "这个场景还没有角色，先去添加 NPC 或布置到场景");
        }
        List<SysAiWorldNpc> allNpcs = listAllNpcs(worldId);

        // 1. 解析本轮回复 NPC
        SysAiWorldNpc speaker = resolveSpeaker(world, scene, allNpcs, sceneNpcs, atRoleId, content);
        // lambda 捕获需要 effectively final，复制一份最终场景引用
        final SysAiWorldScene fScene = scene;

        // 2. 落库用户消息
        String userId = AiUserContext.requireUserId();
        SysAiWorldMessage userMsg = new SysAiWorldMessage();
        userMsg.setWorldId(worldId);
        userMsg.setSceneId(sceneId);
        userMsg.setSpeakerType("user");
        userMsg.setSpeakerId(userId);
        userMsg.setSpeakerName("我");
        userMsg.setContent(content);
        userMsg.setAtRoleId(atRoleId);
        userMsg.setUserId(userId);
        userMsg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // 3. 准备 SSE
        try {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            OutputStream out = response.getOutputStream();

            sendRaw(out, Map.of("type", "user_message", "content", content,
                    "atRoleId", atRoleId == null ? "" : atRoleId));

            // 4. 组装本轮回复 NPC：主回复在前，触发了补话的 NPC 随后
            final String userPrompt = content;
            List<SysAiWorldNpc> speakers = new ArrayList<>();
            speakers.add(speaker);
            for (SysAiWorldNpc n : sceneNpcs) {
                if (n.getId().equals(speaker.getId())) {
                    continue;
                }
                if (!Boolean.TRUE.equals(n.getIsActive())) {
                    continue;
                }
                if (Math.random() * 100.0 >= INTERJECT_PROBABILITY) {
                    continue;
                }
                speakers.add(n);
            }

            // 5. 并行流式回复
            CountDownLatch replyLatch = new CountDownLatch(speakers.size());
            for (SysAiWorldNpc replyNpc : speakers) {
                boolean isMain = replyNpc.getId().equals(speaker.getId());
                WORLD_CHAT_POOL.submit(() -> {
                    try {
                        String prompt = isMain ? userPrompt
                                : "（场景里另一位角色刚说完话，用你自己的人设简短接一句，一两句话即可）";
                        Streamed reply = streamOne(out, world, fScene, sceneNpcs, replyNpc, prompt);
                        saveAiMessage(worldId, sceneId, replyNpc, reply);
                        // 补话者的回应也计入关系演化（对主回复者好感 +0~2）
                        if (!isMain) {
                            applyDropAffinity(worldId, replyNpc.getId(), speaker.getId(), 0, 2);
                        }
                    } catch (Exception e) {
                        log.error("[WorldSim] 并行回复失败 worldId={} npcId={}",
                                worldId, replyNpc.getId(), e);
                        sendRaw(out, Map.of("type", "finish", "finishReason", "error",
                                "error", e.getMessage()));
                    } finally {
                        replyLatch.countDown();
                    }
                });
            }
            replyLatch.await(5, TimeUnit.MINUTES);

            // 6. 本轮主回复者对 @/对象的情绪：好感度微调（异步、失败不影响主流程）
            adjustAffinityAfterTurn(world, scene, sceneNpcs, speaker, userPrompt);

            sendRaw(out, Map.of("type", "turn_end", "finishReason", "stop"));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[WorldSim] 流式失败 worldId={}", worldId, e);
        }
    }

    /** 一位 NPC 流式回复的结果 */
    private record Streamed(String model, String providerId, String text) {
    }

    /**
     * 流式回复一位 NPC。
     *
     * <p>与酒馆的关键差异：系统提示词 = 角色人设 + 世界状态 + 场景描述 + 关系网摘要，
     * 让 NPC 的回答带着世界与关系的"味道"。</p>
     */
    private Streamed streamOne(OutputStream out, SysAiWorld world, SysAiWorldScene scene,
                               List<SysAiWorldNpc> present, SysAiWorldNpc member, String prompt) {
        List<Map<String, Object>> history = buildHistory(world.getId(), scene == null ? null : scene.getId());
        SysAiRole role = roleMapper.selectById(member.getRoleId());
        String displayName = displayName(member, role);
        String[] resolved = resolveModelQualified(member, role);
        String model = resolved[0];
        String providerId = resolved[1];
        if (model == null || model.isBlank()) {
            // 没有可用模型时不能把 null 一路带进 ChatClient 解析：那条路径最终会把
            // null 模型交给 AgentScope，报成「未配置主 ChatClient」，与真实原因
            //（模型管理里没有任何启用模型）相差十万八千里。这里直接给出可操作提示。
            throw new ApiException(ApiErrorCode.INVALID_PARAM,
                    "未配置可用的对话模型：请在「设置中心 → 模型管理」启用一个模型，"
                            + "或在「系统设置 → 对话路由」选择默认模型");
        }

        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        request.setProviderId(providerId);
        request.setStream(true);
        request.setUseSkill(false); // 纯 ChatClient，不挂 MCP/工具/技能
        request.setPrompt(prompt);
        request.setMessages(history);
        request.setSystemPrompt(buildSystemPrompt(world, scene, member, role, present));
        if (role != null && role.getTemperature() != null) {
            request.setTemperature(role.getTemperature());
        }

        Map<String, Object> createEv = new LinkedHashMap<>();
        createEv.put("type", "create");
        createEv.put("speakerType", "ai");
        createEv.put("speakerId", String.valueOf(member.getId()));
        createEv.put("speakerName", displayName);
        createEv.put("modelName", model);
        createEv.put("route", buildRoute(providerId, model));
        sendRaw(out, createEv);
        log.info("[WorldSim] 路由选定 npcId={} model={} providerId={}",
                member.getId(), model, providerId);

        StringBuilder full = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        try {
            aiChatFacadeService.streamWithUsage(request,
                    chunk -> {
                        if (chunk != null && !chunk.isEmpty()) {
                            full.append(chunk);
                            sendRaw(out, Map.of(
                                    "type", "text",
                                    "speakerId", String.valueOf(member.getId()),
                                    "content", chunk));
                        }
                    },
                    (reply, usage) -> {
                        String text = reply != null ? reply : full.toString();
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("role", "assistant");
                        m.put("content", "[" + displayName + "] " + text);
                        history.add(m);
                        latch.countDown();
                    },
                    err -> {
                        sendRaw(out, Map.of("type", "finish", "finishReason", "error",
                                "speakerId", String.valueOf(member.getId()),
                                "speakerName", displayName,
                                "error", err != null ? err.getMessage() : "生成失败"));
                        latch.countDown();
                    },
                    null);
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[WorldSim] streamOne 失败 npcId={}: {}", member.getId(), e.getMessage());
            sendRaw(out, Map.of("type", "finish", "finishReason", "error",
                    "speakerId", String.valueOf(member.getId()),
                    "speakerName", displayName,
                    "error", e.getMessage()));
        }
        sendRaw(out, Map.of("type", "finish", "finishReason", "stop",
                "speakerId", String.valueOf(member.getId()), "speakerName", displayName));
        return new Streamed(model, providerId, full.toString());
    }

    /**
     * 组装 NPC 的系统提示词：人设 + 世界状态 + 场景 + 关系网。
     */
    private String buildSystemPrompt(SysAiWorld world, SysAiWorldScene scene,
                                     SysAiWorldNpc member, SysAiRole role,
                                     List<SysAiWorldNpc> present) {
        StringBuilder sb = new StringBuilder();
        // 角色人设（世界内 persona 覆盖优先）
        if (member.getPersona() != null && !member.getPersona().isBlank()) {
            sb.append("你是「").append(displayName(member, role)).append("」。").append(member.getPersona()).append("\n");
        } else if (role != null && role.getSystemPrompt() != null && !role.getSystemPrompt().isBlank()) {
            sb.append("你是「").append(displayName(member, role)).append("」。").append(role.getSystemPrompt()).append("\n");
        } else {
            sb.append("你是「").append(displayName(member, role)).append("」。\n");
        }
        // 世界设定
        if (world != null) {
            sb.append("\n【世界背景】").append(world.getName()).append("｜").append(world.getEra() == null ? "现代" : world.getEra());
            if (world.getDescription() != null && !world.getDescription().isBlank()) {
                sb.append("\n").append(world.getDescription());
            }
            sb.append("\n当前时间：").append(world.getTimeLabel() == null ? "第 1 天" : world.getTimeLabel());
            sb.append("；天气：").append(world.getWeather() == null ? "晴" : world.getWeather());
        }
        // 场景
        if (scene != null && scene.getName() != null) {
            sb.append("\n\n你现在在【").append(scene.getName()).append("】");
            if (scene.getDescription() != null && !scene.getDescription().isBlank()) {
                sb.append("：").append(scene.getDescription());
            }
            sb.append("。");
        }
        // 关系网（与在场其他 NPC 的好感度），让言行受关系演化支配
        List<String> relLines = new ArrayList<>();
        for (SysAiWorldNpc other : present) {
            if (other.getId().equals(member.getId())) {
                continue;
            }
            SysAiWorldRelationship r = relationshipMapper.selectOne(
                    new LambdaQueryWrapper<SysAiWorldRelationship>()
                            .eq(SysAiWorldRelationship::getWorldId, world.getId())
                            .eq(SysAiWorldRelationship::getFromNpcId, member.getId())
                            .eq(SysAiWorldRelationship::getToNpcId, other.getId()));
            if (r != null) {
                relLines.add(displayName(other, roleMapper.selectById(other.getRoleId()))
                        + "（好感度 " + (r.getAffinity() == null ? 0 : r.getAffinity())
                        + (r.getRelationTag() != null ? "，关系：" + r.getRelationTag() : "") + "）");
            }
        }
        if (!relLines.isEmpty()) {
            sb.append("\n\n【你对其他在场角色的态度】\n").append(String.join("\n", relLines));
            sb.append("\n保持角色一致：说话和行动要符合这些关系，好感度越高越亲近，越低越疏远。");
        }
        sb.append("\n\n请用第一人称扮演，像真正生活在这个世界里一样回应，不要提及你是 AI。");
        return sb.toString();
    }

    /** 解析本轮回复 NPC：@ > 自动路由 > 首位 */
    private SysAiWorldNpc resolveSpeaker(SysAiWorld world, SysAiWorldScene scene,
                                         List<SysAiWorldNpc> allNpcs, List<SysAiWorldNpc> sceneNpcs,
                                         Integer atRoleId, String content) {
        if (atRoleId != null) {
            // @ 的目标可以是世界内任意 NPC（不止场景内的）
            for (SysAiWorldNpc n : allNpcs) {
                if (atRoleId.equals(n.getId())) {
                    return n;
                }
            }
        }
        if (sceneNpcs.size() > 1) {
            SysAiWorldNpc routed = autoRoute(world, scene, sceneNpcs, content);
            if (routed != null) {
                return routed;
            }
        }
        return sceneNpcs.get(0);
    }

    /** 路由模型：按场景上下文挑一位最该接话的 NPC */
    private SysAiWorldNpc autoRoute(SysAiWorld world, SysAiWorldScene scene,
                                    List<SysAiWorldNpc> npcs, String content) {
        try {
            StringBuilder roster = new StringBuilder();
            for (int i = 0; i < npcs.size(); i++) {
                SysAiRole r = roleMapper.selectById(npcs.get(i).getRoleId());
                roster.append(i).append("=").append(displayName(npcs.get(i), r));
                if (r != null && r.getDescription() != null && !r.getDescription().isBlank()) {
                    roster.append("(").append(r.getDescription()).append(")");
                }
                roster.append("\n");
            }
            StringBuilder ctx = new StringBuilder("世界：").append(world.getName())
                    .append("｜").append(world.getEra()).append("｜").append(world.getTimeLabel());
            if (scene != null) {
                ctx.append("\n场景：").append(scene.getName());
                if (scene.getDescription() != null && !scene.getDescription().isBlank()) {
                    ctx.append("：").append(scene.getDescription());
                }
            }
            String prompt = "当前上下文：\n" + ctx + "\n在场角色：\n" + roster
                    + "\n用户说：" + content
                    + "\n请只回复最应该接话的角色编号（0~" + (npcs.size() - 1) + "），只输出一个数字，不要解释。";
            AiChatRequest req = new AiChatRequest();
            String[] autoModel = resolveDefaultQualified();
            req.setModel(autoModel[0]);
            req.setProviderId(autoModel[1]);
            req.setStream(false);
            req.setUseSkill(false);
            req.setPrompt(prompt);
            AiChatResult res = aiChatFacadeService.callForResult(req);
            if (res != null && res.success() && res.text() != null) {
                String s = res.text().replaceAll("[^0-9]", "");
                if (!s.isEmpty()) {
                    int idx = Integer.parseInt(s.trim());
                    if (idx >= 0 && idx < npcs.size()) {
                        log.info("[WorldSim] 自动路由 -> npcIndex={}", idx);
                        return npcs.get(idx);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WorldSim] 自动路由失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * build History
     * @param sceneId 长整数
     * @param worldId 长整数
     * @return 返回值
    */
    private List<Map<String, Object>> buildHistory(Long worldId, Long sceneId) {
        LambdaQueryWrapper<SysAiWorldMessage> w = new LambdaQueryWrapper<SysAiWorldMessage>()
                .eq(SysAiWorldMessage::getWorldId, worldId)
                .orderByDesc(SysAiWorldMessage::getId)
                .last("LIMIT " + CONTEXT_LIMIT);
        if (sceneId != null) {
            w.eq(SysAiWorldMessage::getSceneId, sceneId);
        }
        List<SysAiWorldMessage> rows = messageMapper.selectList(w);
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            SysAiWorldMessage m = rows.get(i);
            Map<String, Object> mm = new LinkedHashMap<>();
            if ("user".equals(m.getSpeakerType())) {
                mm.put("role", "user");
                mm.put("content", m.getContent());
            } else {
                mm.put("role", "assistant");
                mm.put("content", "[" + (m.getSpeakerName() != null ? m.getSpeakerName() : "AI") + "] " + m.getContent());
            }
            history.add(mm);
        }
        return history;
    }

    /**
     * save Ai Message
     * @param member 参数
     * @param sceneId 长整数
     * @param streamed 参数
     * @param worldId 长整数
     * @return 返回值
    */
    private void saveAiMessage(Long worldId, Long sceneId, SysAiWorldNpc member, Streamed streamed) {
        // 模型调用失败/被限流时 text 为空。这种「空 AI 消息」一旦落库，会以一条
        // 空白气泡留在历史里，并进入下一轮的上下文（CONTEXT_LIMIT 条），
        // 让模型以为角色说过一句空话。宁可少一条记录，也不要写脏数据。
        if (streamed == null || streamed.text() == null || streamed.text().isBlank()) {
            log.warn("[WorldSim] 跳过落库：本轮 AI 输出为空 worldId={} npcId={} model={}",
                    worldId, member == null ? null : member.getId(),
                    streamed == null ? null : qualify(streamed.providerId(), streamed.model()));
            return;
        }
        SysAiRole role = roleMapper.selectById(member.getRoleId());
        SysAiWorldMessage m = new SysAiWorldMessage();
        m.setWorldId(worldId);
        m.setSceneId(sceneId);
        m.setSpeakerType("ai");
        m.setSpeakerId(String.valueOf(member.getId()));
        m.setSpeakerName(displayName(member, role));
        m.setModelName(qualify(streamed.providerId(), streamed.model()));
        m.setContent(streamed.text());
        m.setUserId(AiUserContext.currentUserId());
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /**
     * 本轮互动后的关系演化：让模型读一遍主回复，判断主回复者对在场其他 NPC
     * 的态度是友好还是刻薄，把好感度增量解析出来落库。失败静默（不影响主流程）。
     */
    private void adjustAffinityAfterTurn(SysAiWorld world, SysAiWorldScene scene,
                                         List<SysAiWorldNpc> present,
                                         SysAiWorldNpc speaker, String userPrompt) {
        try {
            StringBuilder roster = new StringBuilder();
            List<SysAiWorldNpc> others = new ArrayList<>();
            for (SysAiWorldNpc n : present) {
                if (n.getId().equals(speaker.getId())) {
                    continue;
                }
                others.add(n);
                SysAiRole r = roleMapper.selectById(n.getRoleId());
                roster.append(n.getId()).append("=").append(displayName(n, r)).append("\n");
            }
            if (others.isEmpty()) {
                return;
            }
            StringBuilder ctx = new StringBuilder("世界：").append(world.getName());
            if (scene != null) {
                ctx.append("\n场景：").append(scene.getName());
            }
            String prompt = "以下是一段发生在" + ctx + "的互动。\n"
                    + "用户说：" + userPrompt + "\n"
                    + "「" + displayName(speaker, roleMapper.selectById(speaker.getRoleId())) + "」回应了。\n\n"
                    + "请判断这位发言人对他人的态度，输出 JSON：\n"
                    + roster.toString()
                    + "格式：{\"deltas\":[{\"npc_id\":数字,\"delta\":整数}]}\n"
                    + "规则：delta ∈ [-10,10]；明显友善+3~+10，轻微友善+1~+2，中立0，稍冷淡-1~-2，敌对-3~-10。"
                    + "只输出 JSON，不要解释。";
            AiChatRequest req = new AiChatRequest();
            String[] m = resolveDefaultQualified();
            req.setModel(m[0]);
            req.setProviderId(m[1]);
            req.setStream(false);
            req.setUseSkill(false);
            req.setPrompt(prompt);
            AiChatResult res = aiChatFacadeService.callForResult(req);
            if (res == null || !res.success() || res.text() == null) {
                return;
            }
            List<Map<String, Object>> deltas = parseDeltas(res.text());
            for (Map<String, Object> d : deltas) {
                Object idObj = d.get("npc_id");
                Object deltaObj = d.get("delta");
                if (idObj == null || deltaObj == null) {
                    continue;
                }
                try {
                    long toNpcId = Long.parseLong(String.valueOf(idObj).trim());
                    int delta = Integer.parseInt(String.valueOf(deltaObj).trim());
                    applyAffinityQuiet(world.getId(), speaker.getId(), toNpcId, delta);
                } catch (NumberFormatException ignore) {
                    // 跳过解析失败项
                }
            }
        } catch (Exception e) {
            log.debug("[WorldSim] 关系演化解析失败（忽略）: {}", e.getMessage());
        }
    }

    /**
     * parse Deltas
     * @param text 字符串
     * @return 返回值
    */
    private List<Map<String, Object>> parseDeltas(String text) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return result;
            }
            JsonNode arr = objectMapper.readTree(text.substring(start, end + 1));
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (node.has("npc_id")) {
                        row.put("npc_id", node.get("npc_id").asText());
                    }
                    if (node.has("delta")) {
                        row.put("delta", node.get("delta").asText());
                    }
                    if (!row.isEmpty()) {
                        result.add(row);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[WorldSim] deltas JSON 解析失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * apply Drop Affinity
     * @param fromNpcId 长整数
     * @param max 整数
     * @param min 整数
     * @param toNpcId 长整数
     * @param worldId 长整数
     * @return 返回值
    */
    private void applyDropAffinity(Long worldId, Long fromNpcId, Long toNpcId, int min, int max) {
        try {
            applyAffinityQuiet(worldId, fromNpcId, toNpcId, min + (int) (Math.random() * (max - min + 1)));
        } catch (Exception ignore) {
            // 静默
        }
    }

    /**
     * apply Affinity Quiet
     * @param delta 整数
     * @param fromNpcId 长整数
     * @param toNpcId 长整数
     * @param worldId 长整数
     * @return 返回值
    */
    private void applyAffinityQuiet(Long worldId, Long fromNpcId, Long toNpcId, int delta) {
        if (fromNpcId == null || toNpcId == null || fromNpcId.equals(toNpcId) || delta == 0) {
            return;
        }
        SysAiWorldRelationship r = relationshipMapper.selectOne(
                new LambdaQueryWrapper<SysAiWorldRelationship>()
                        .eq(SysAiWorldRelationship::getWorldId, worldId)
                        .eq(SysAiWorldRelationship::getFromNpcId, fromNpcId)
                        .eq(SysAiWorldRelationship::getToNpcId, toNpcId));
        int base = (r != null && r.getAffinity() != null) ? r.getAffinity() : 0;
        int next = Math.max(AFFINITY_MIN, Math.min(AFFINITY_MAX, base + delta));
        if (r == null) {
            r = new SysAiWorldRelationship();
            r.setWorldId(worldId);
            r.setFromNpcId(fromNpcId);
            r.setToNpcId(toNpcId);
            r.setAffinity(next);
            r.setUserId(AiUserContext.currentUserId());
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
            relationshipMapper.insert(r);
        } else if (next != base) {
            r.setAffinity(next);
            r.setUpdateTime(LocalDateTime.now());
            relationshipMapper.updateById(r);
        }
    }

    /* ============================================================================
     * 时间推进（世界动态）
    /**
     * 时间推进（世界动态）
     * @param Map<String 参数
     * @param body 参数
     * @return 返回值
    */
    @PostMapping("/advance")
    public Map<String, Object> advance(@RequestBody Map<String, Object> body) {
        Long worldId = body.get("worldId") == null ? null : Long.valueOf(String.valueOf(body.get("worldId")));
        if (worldId == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少世界ID");
        }
        SysAiWorld world = requireOwned(worldId);
        List<SysAiWorldNpc> actives = listActives(worldId);
        if (actives.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "世界还没有可参与推进的 NPC，请先添加");
        }

        // 1. 挑 1~3 位活跃 NPC 参与本段
        List<SysAiWorldNpc> participants = pickParticipants(actives, world.getTickCount() == null ? 0 : world.getTickCount());

        // 2. 生成世界动态
        WorldAdvanceResult result = generateAdvance(world, participants);

        // 3. 推进时间标签
        int tick = (world.getTickCount() == null ? 0 : world.getTickCount()) + 1;
        world.setTickCount(tick);
        world.setTimeLabel(nextTimeLabel(world.getTimeLabel(), tick));
        if (result.weather() != null && !result.weather().isBlank() && !"null".equals(result.weather())) {
            world.setWeather(result.weather());
        }
        world.setUpdateTime(LocalDateTime.now());
        worldMapper.updateById(world);

        // 4. 落事件时间线
        String roleIds = participants.stream()
                .map(n -> String.valueOf(n.getId()))
                .reduce((a, b) -> a + "," + b).orElse(null);
        SysAiWorldEvent event = new SysAiWorldEvent();
        event.setWorldId(worldId);
        event.setSceneId(participants.get(0).getSceneId());
        event.setEventType("advance");
        event.setRoleIds(roleIds);
        event.setTitle(result.title());
        event.setContent(result.content());
        event.setUserId(AiUserContext.requireUserId());
        event.setCreateTime(LocalDateTime.now());
        eventMapper.insert(event);

        // 5. 好感度增减
        if (result.deltas() != null) {
            for (Map<String, Object> d : result.deltas()) {
                Object idObj = d.get("npc_id");
                Object deltaObj = d.get("delta");
                if (idObj == null || deltaObj == null) {
                    continue;
                }
                try {
                    long toNpcId = Long.parseLong(String.valueOf(idObj).trim());
                    int delta = Integer.parseInt(String.valueOf(deltaObj).trim());
                    applyAffinityQuiet(worldId, participants.get(0).getId(), toNpcId, delta);
                } catch (NumberFormatException ignore) {
                    // 跳过
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world);
        out.put("event", event);
        out.put("participants", participants.stream()
                .map(n -> displayName(n, roleMapper.selectById(n.getRoleId()))).toList());
        // 世界视图缓存失效（updateWorld/appendEvent 会失效，这里显式再失效一次）
        evictWorldView(worldId);
        return out;
    }

    /**
     * pick Participants
     * @param actives 参数
     * @param tick 整数
     * @return 返回值
    */
    private List<SysAiWorldNpc> pickParticipants(List<SysAiWorldNpc> actives, int tick) {
        List<SysAiWorldNpc> pool = new ArrayList<>(actives);
        List<SysAiWorldNpc> chosen = new ArrayList<>();
        SysAiWorldNpc first = pool.remove((int) (Math.random() * pool.size()));
        chosen.add(first);
        // 优先找与 first 同场景的
        List<SysAiWorldNpc> sameScene = pool.stream()
                .filter(n -> n.getSceneId() != null && n.getSceneId().equals(first.getSceneId()))
                .toList();
        int extra = tick > 6 ? 2 : (tick > 2 ? 1 : 0);
        for (int i = 0; i < Math.min(extra, Math.min(sameScene.size(), 2)); i++) {
            chosen.add(sameScene.get(i));
            pool.remove(sameScene.get(i));
        }
        // 仍不足时从其余活跃 NPC 补齐
        while (chosen.size() < 3 && !pool.isEmpty()) {
            int idx = (int) (Math.random() * pool.size());
            chosen.add(pool.remove(idx));
        }
        return chosen;
    }

    /**
     * generate Advance
     * @param participants 参数
     * @param world 参数
     * @return 返回值
    */
    private WorldAdvanceResult generateAdvance(SysAiWorld world, List<SysAiWorldNpc> participants) {
        StringBuilder roster = new StringBuilder();
        for (SysAiWorldNpc n : participants) {
            SysAiRole r = roleMapper.selectById(n.getRoleId());
            roster.append("- ").append(displayName(n, r));
            SysAiWorldScene s = n.getSceneId() == null ? null : sceneMapper.selectById(n.getSceneId());
            if (s != null) {
                roster.append("（在").append(s.getName()).append("）");
            }
            if (r != null && r.getDescription() != null && !r.getDescription().isBlank()) {
                roster.append("：").append(r.getDescription());
            }
            roster.append("\n");
        }
        String prompt = "你是一个沉浸式世界模拟的世界引擎。世界设定如下：\n"
                + "世界：" + world.getName() + "\n"
                + "时代：" + (world.getEra() == null ? "现代" : world.getEra()) + "\n"
                + (world.getDescription() != null && !world.getDescription().isBlank()
                        ? "世界观：" + world.getDescription() + "\n" : "")
                + "当前时间：" + (world.getTimeLabel() == null ? "第 1 天 上午" : world.getTimeLabel())
                + "；天气：" + (world.getWeather() == null ? "晴" : world.getWeather()) + "\n\n"
                + "接下来这段时间里，以下角色会出现在故事中：\n" + roster + "\n"
                + "请推进时间，生成一小段" + (world.getTickCount() == null || world.getTickCount() == 0 ? "世界刚开始" : "新的")
                + "情节：角色们之间发生的事、说的话、环境变化。要有画面感，像小说片段（200字以内）。\n"
                + "同时给出：天气是否变化（不变则原样返回）、这几人之间好感度的增减。\n"
                + "只输出 JSON：{\"title\":\"一句话标题\",\"content\":\"正文\",\"weather\":\"天气\",\"deltas\":[{\"npc_id\":数字,\"delta\":整数}]}\n"
                + "deltas 里 npc_id 从 " + participants.get(0).getId() + " 的视角，对每位参与者的好感度变化 [-10,10]。"
                + "只输出 JSON 对象，不要输出其他文字。";

        AiChatRequest req = new AiChatRequest();
        String[] m = resolveDefaultQualified();
        req.setModel(m[0]);
        req.setProviderId(m[1]);
        req.setStream(false);
        req.setUseSkill(false);
        req.setPrompt(prompt);
        // 回落文案必须能一眼看出「这不是 AI 生成的情节」。
        // 此前回落用的是「世界继续运转 / 世界在继续运转，一切如常。」，与真实生成的
        // 事件在库里长得一模一样，验收时以为链路是通的，实际模型一次都没成功调用。
        String fallbackTitle = "（AI 未生成）世界继续运转";
        String fallbackContent = "本轮未能取得模型输出，世界按原样继续运转。请检查模型配置后再推进一次。";
        String fallbackWeather = world.getWeather();
        try {
            AiChatResult res = aiChatFacadeService.callForResult(req);
            if (res == null || !res.success() || res.text() == null) {
                log.warn("[WorldSim] advance 未取得模型输出 model={} providerId={}，本轮写入回落事件",
                        m[0], m[1]);
                return new WorldAdvanceResult(fallbackTitle, fallbackContent, fallbackWeather, List.of());
            }
            JsonNode root = parseJsonObject(res.text());
            if (root == null) {
                log.warn("[WorldSim] advance 模型输出不是 JSON model={}，本轮写入回落事件", m[0]);
                return new WorldAdvanceResult(fallbackTitle, fallbackContent, fallbackWeather, List.of());
            }
            String title = root.has("title") && root.get("title").isTextual()
                    ? root.get("title").asText() : fallbackTitle;
            String content = root.has("content") && root.get("content").isTextual()
                    ? root.get("content").asText() : fallbackContent;
            String weather = root.has("weather") && root.get("weather").isTextual()
                    ? root.get("weather").asText() : fallbackWeather;
            List<Map<String, Object>> deltas = new ArrayList<>();
            JsonNode dArr = root.get("deltas");
            if (dArr != null && dArr.isArray()) {
                for (JsonNode node : dArr) {
                    if (!node.isObject()) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    if (node.has("npc_id")) {
                        row.put("npc_id", node.get("npc_id").asText());
                    }
                    if (node.has("delta")) {
                        row.put("delta", node.get("delta").asText());
                    }
                    if (!row.isEmpty()) {
                        deltas.add(row);
                    }
                }
            }
            return new WorldAdvanceResult(title, content, weather, deltas);
        } catch (Exception e) {
            log.warn("[WorldSim] advance 生成失败（回落占位）: {}", e.getMessage());
            return new WorldAdvanceResult(fallbackTitle, fallbackContent, fallbackWeather, List.of());
        }
    }

    /**
     * parse Json Object
     * @param text 字符串
     * @return 返回值
    */
    private JsonNode parseJsonObject(String text) {
        try {
            int open = text.indexOf('{');
            int close = text.lastIndexOf('}');
            if (open < 0 || close <= open) {
                return null;
            }
            return objectMapper.readTree(text.substring(open, close + 1));
        } catch (Exception e) {
            log.debug("[WorldSim] JSON 对象解析失败: {}", e.getMessage());
            return null;
        }
    }

    private record WorldAdvanceResult(String title, String content, String weather, List<Map<String, Object>> deltas) {
    }

    /**
     * next Time Label
     * @param current 字符串
     * @param tick 整数
     * @return 返回值
    */
    private String nextTimeLabel(String current, int tick) {
        String[] parts = {"上午", "下午", "傍晚", "深夜"};
        // 初始（tick=0）为「第 1 天 上午」，每推进一次前进一个时段，每 4 个时段一天
        int day = tick / 4 + 1;
        String part = parts[tick % 4];
        return "第 " + day + " 天 " + part;
    }

    /* ============================================================================
    /**
     * 内部工具
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
     * list All Npcs
     * @param worldId 长整数
     * @return 返回值
    */
    private List<SysAiWorldNpc> listAllNpcs(Long worldId) {
        return npcMapper.selectList(new LambdaQueryWrapper<SysAiWorldNpc>()
                .eq(SysAiWorldNpc::getWorldId, worldId)
                .orderByAsc(SysAiWorldNpc::getSort)
                .orderByAsc(SysAiWorldNpc::getId));
    }

    /**
     * list Scene Npcs
     * @param sceneId 长整数
     * @param worldId 长整数
     * @return 返回值
    */
    private List<SysAiWorldNpc> listSceneNpcs(Long worldId, Long sceneId) {
        LambdaQueryWrapper<SysAiWorldNpc> w = new LambdaQueryWrapper<SysAiWorldNpc>()
                .eq(SysAiWorldNpc::getWorldId, worldId);
        if (sceneId == null) {
            w.isNull(SysAiWorldNpc::getSceneId);
        } else {
            w.eq(SysAiWorldNpc::getSceneId, sceneId);
        }
        return npcMapper.selectList(w.orderByAsc(SysAiWorldNpc::getSort).orderByAsc(SysAiWorldNpc::getId));
    }

    /**
     * list Actives
     * @param worldId 长整数
     * @return 返回值
    */
    private List<SysAiWorldNpc> listActives(Long worldId) {
        return npcMapper.selectList(new LambdaQueryWrapper<SysAiWorldNpc>()
                .eq(SysAiWorldNpc::getWorldId, worldId)
                .eq(SysAiWorldNpc::getIsActive, true)
                .orderByAsc(SysAiWorldNpc::getSort)
                .orderByAsc(SysAiWorldNpc::getId));
    }

    /**
     * display Name
     * @param n 参数
     * @param role 参数
     * @return 返回值
    */
    private String displayName(SysAiWorldNpc n, SysAiRole role) {
        if (n == null) {
            return "NPC";
        }
        if (n.getNickname() != null && !n.getNickname().isBlank()) {
            return n.getNickname();
        }
        return role != null && role.getName() != null ? role.getName() : "NPC";
    }

    /**
     * evict World View
     * @param worldId 长整数
     * @return 返回值
    */
    private void evictWorldView(Long worldId) {
        worldSimService.evictWorldCaches(worldId);
    }

    /**
     * resolve Model Qualified
     * @param member 参数
     * @param role 参数
     * @return 返回值
    */
    private String[] resolveModelQualified(SysAiWorldNpc member, SysAiRole role) {
        String providerId = null;
        String raw = null;
        if (member.getModelOverride() != null && !member.getModelOverride().isBlank()) {
            raw = member.getModelOverride();
        } else if (role != null && role.getModel() != null && !role.getModel().isBlank()) {
            raw = role.getModel();
        }
        if (raw != null) {
            providerId = extractProviderId(raw);
            String model = effectiveChatModelResolver.resolve(raw);
            if (model != null && !model.isBlank()) {
                return new String[]{model, providerId};
            }
        }
        return resolveDefaultQualified();
    }

    /**
     * extract Provider Id
     * @param raw 字符串
     * @return 返回值
    */
    private String extractProviderId(String raw) {
        if (raw == null) {
            return null;
        }
        int sep = raw.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        String prefix = raw.substring(0, sep).trim();
        if (prefix.isEmpty()) {
            return null;
        }
        try {
            boolean numeric = prefix.chars().allMatch(Character::isDigit);
            if (numeric) {
                // 数字前缀也必须在供应商表里真实存在且启用，才认作「已解析」。
                // 此前直接 return prefix，于是一个早已被删掉的供应商 ID 也能让
                // route.providerResolved=true，把「配置过期」伪装成「路由成功」。
                com.chua.starter.ai.support.manage.store.SysAiProvider numericProvider =
                        providerMapper.selectById(prefix);
                if (numericProvider != null && Boolean.TRUE.equals(numericProvider.getEnabled())) {
                    return prefix;
                }
                log.warn("[WorldSim] 模型限定名前缀 {} 不是已启用的供应商，改为按模型名重新解析", prefix);
            } else {
                com.chua.starter.ai.support.manage.store.SysAiProvider p = providerMapper.selectById(prefix);
                if (p != null && Boolean.TRUE.equals(p.getEnabled())) {
                    return prefix;
                }
            }
            for (com.chua.starter.ai.support.manage.store.SysAiProvider cand : providerMapper.selectList(null)) {
                if (cand.getName() != null && cand.getName().equalsIgnoreCase(prefix)
                        && Boolean.TRUE.equals(cand.getEnabled())
                        && cand.getProviderId() != null && !cand.getProviderId().isBlank()) {
                    return cand.getProviderId();
                }
            }
        } catch (Exception e) {
            log.debug("[WorldSim] 提取供应商标识失败 raw={}: {}", raw, e.getMessage());
        }
        return null;
    }

    /**
     * build Route
     * @param model 字符串
     * @param providerId 字符串
     * @return 返回值
    */
    private Map<String, Object> buildRoute(String providerId, String model) {
        Map<String, Object> route = new LinkedHashMap<>(8);
        route.put("model", model);
        route.put("providerId", providerId);
        route.put("providerResolved", providerId != null && !providerId.isBlank());
        if (providerId != null && !providerId.isBlank()) {
            try {
                com.chua.starter.ai.support.manage.store.SysAiProvider p = providerMapper.selectById(providerId);
                if (p != null) {
                    route.put("providerName", p.getName());
                    route.put("platform", p.getPlatform());
                    route.put("baseUrl", p.getBaseUrl());
                }
            } catch (Exception e) {
                log.debug("[WorldSim] 查询供应商 {} 失败: {}", providerId, e.getMessage());
            }
        }
        return route;
    }

    /**
     * resolve Default Qualified
     * @return 返回值
    */
    private String[] resolveDefaultQualified() {
        // 取值顺序与 EffectiveChatModelResolver 一致，但**保留原始的「供应商:模型」限定名**。
        //
        // 为什么不直接取 resolver.candidates() 的返回值：它把候选归一化成「模型管理里
        // 命中的那一种写法」，而模型表的 model 列只存裸模型名，于是限定名
        // pollinations-local:openai-fast 会被归一成 openai-fast，供应商前缀丢失，
        // SSE 的 route.providerResolved 变成 false（明明配了供应商却报未解析）。
        //
        // 这里改为：按同一优先级逐层读原始配置值，先校验「模型管理里存在且已启用」
        // （isAvailable 会试原样与去前缀两种写法），校验通过才采用，并保留前缀。
        //
        // 校验这一步是之前缺失的关键：配置行被删掉后，永久 Redis 缓存仍会返回旧限定名
        // 1789547286889:agnes-3.0-flash，供应商早已不存在，世界模拟每次对话都把它当默认
        // 模型发出去，错误还被包装成「未配置主 ChatClient」，完全看不出真因。
        String[][] keys = {
                {"bot", "default_model"},
                {"runtime", "default_model"},
                {"models", "defaultChatModelId"},
                {"runtime", "default_chat_model"}
        };
        for (String[] k : keys) {
            String v = globalSettingService.getValue(k[0], k[1])
                    .map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
            if (v == null) {
                continue;
            }
            if (!effectiveChatModelResolver.isAvailable(v)) {
                log.warn("[WorldSim] 默认模型配置 {}/{}={} 未在模型管理中启用，跳过",
                        k[0], k[1], v);
                continue;
            }
            int idx = v.indexOf(':');
            String bare = idx >= 0 ? v.substring(idx + 1).trim() : v;
            if (bare.isEmpty()) {
                continue;
            }
            return new String[]{bare, extractProviderId(v)};
        }
        // 配置层没有可用值时，交给统一解析器在候选链里挑（可能来自显式模型或 yaml）
        for (String candidate : effectiveChatModelResolver.candidates(null)) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String value = candidate.trim();
            int idx = value.indexOf(':');
            String bare = idx >= 0 ? value.substring(idx + 1).trim() : value;
            if (bare.isEmpty()) {
                continue;
            }
            return new String[]{bare, extractProviderId(value)};
        }
        return new String[]{null, null};
    }

    /**
     * qualify
     * @param model 字符串
     * @param providerId 字符串
     * @return 返回值
    */
    private static String qualify(String providerId, String model) {
        if (providerId == null || providerId.isBlank() || model == null) {
            return model;
        }
        return providerId + ":" + model;
    }

    /**
     * send Raw
     * @param Map<String 参数
     * @param event 参数
     * @param out 输出流
     * @return 返回值
    */
    private void sendRaw(OutputStream out, Map<String, Object> event) {
        synchronized (out) {
            try {
                String json = objectMapper.writeValueAsString(event);
                out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                log.debug("[WorldSim] sendRaw 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * str
     * @param v 对象
     * @return 返回值
    */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);    }}