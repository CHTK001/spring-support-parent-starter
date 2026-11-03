// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiRole;
import com.chua.starter.ai.support.entity.SysAiTavernGroup;
import com.chua.starter.ai.support.entity.SysAiTavernMember;
import com.chua.starter.ai.support.entity.SysAiTavernMessage;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.mapper.SysAiRoleMapper;
import com.chua.starter.ai.support.mapper.SysAiTavernGroupMapper;
import com.chua.starter.ai.support.mapper.SysAiTavernMemberMapper;
import com.chua.starter.ai.support.mapper.SysAiTavernMessageMapper;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
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
 * 酒馆群聊流式控制器（SSE）。
 *
 * <p>路由规则（按优先级）：
 * <ol>
 *   <li>@某角色 → 被@的成员回复</li>
 *   <li>未@ + 群开了自动路由 → 路由模型按成员描述挑一个</li>
 *   <li>未@ + 未开自动路由 → 群默认回复成员</li>
 * </ol>
 * 群聊走纯 ChatClient（useSkill=false，不挂 MCP/工具/技能）；管理员场景仍走 Agent。
 * 主回复后按群 randomChance 概率由另一位成员补一句。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/tavern")
@ApiReturnFormatIgnore
@RequiredArgsConstructor
@Slf4j
public class TavernChatController {

    /**
     * 酒馆群聊回复线程池。
     *
     * <p>主回复与触发了随机发言的成员在这条 SSE 里并行流式输出，
     * 不能让线程池线程阻止 JVM 退出。</p>
     */
    private static final ExecutorService TAVERN_CHAT_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tavern-chat-reply");
        t.setDaemon(true);
        return t;
    });

    /**
     * 生效对话模型解析器（连接器级 → Bot 默认 → 配置中心默认）
     */
    private final EffectiveChatModelResolver effectiveChatModelResolver;

    private final SysAiTavernGroupMapper groupMapper;
    private final SysAiTavernMemberMapper memberMapper;
    private final SysAiTavernMessageMapper messageMapper;
    private final SysAiRoleMapper roleMapper;
    private final AiChatFacadeService aiChatFacadeService;
    private final SysAiGlobalSettingService globalSettingService;
    private final ObjectMapper objectMapper;

    /** 供应商查询：把成员模型覆盖里的 providerId 补成可读的供应商名/平台/baseUrl */
    private final com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper providerMapper;

    /**
     * 群聊 SSE。
     * @param body 请求体 groupId/content/atRoleId
     * @param response HTTP 响应
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void chat(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        Long groupId = body.get("groupId") == null ? null : Long.valueOf(String.valueOf(body.get("groupId")));
        String content = str(body.get("content"));
        Integer atRoleId = body.get("atRoleId") == null || "null".equals(String.valueOf(body.get("atRoleId")))
                ? null : Integer.valueOf(String.valueOf(body.get("atRoleId")));
        if (groupId == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少群ID");
        }
        if (content == null || content.isBlank()) {
            content = "";
        }

        SysAiTavernGroup group = requireOwned(groupId);
        List<SysAiTavernMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<SysAiTavernMember>()
                        .eq(SysAiTavernMember::getGroupId, groupId)
                        .orderByAsc(SysAiTavernMember::getSort));
        if (members.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "群里还没有成员，请先添加角色");
        }

        // 1. 解析本轮回复成员
        SysAiTavernMember speaker = resolveSpeaker(group, members, atRoleId, content);

        // 2. 落库用户消息
        String userId = AiUserContext.requireUserId();
        SysAiTavernMessage userMsg = new SysAiTavernMessage();
        userMsg.setGroupId(groupId);
        userMsg.setSpeakerType("user");
        userMsg.setSpeakerId(userId);
        userMsg.setSpeakerName("我");
        userMsg.setContent(content);
        userMsg.setAtRoleId(atRoleId);
        userMsg.setUserId(userId);
        userMsg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // 3. 准备 SSE（上下文条数由每位成员自己的 randomContextCount 决定）
        try {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            OutputStream out = response.getOutputStream();

            sendRaw(out, Map.of("type", "user_message", "content", content,
                    "atRoleId", atRoleId == null ? "" : atRoleId));

            // 4. 组装本轮回复成员：主回复在前，触发了随机发言的成员随后。
            //    并行发起后，各自独立流式输出到同一条 SSE——不会像老的串行实现
            //    那样让前面的角色阻塞后面的角色（前端感受就是「一个接一个回复」）。
            List<SysAiTavernMember> speakers = new ArrayList<>();
            speakers.add(speaker);
            for (SysAiTavernMember m : members) {
                if (m.getId().equals(speaker.getId())) {
                    continue;
                }
                if (!Boolean.TRUE.equals(m.getRandomReplyEnabled())) {
                    continue;
                }
                double p = m.getRandomProbability() == null ? 0 : m.getRandomProbability();
                if (p <= 0) {
                    continue;
                }
                if (Math.random() * 100.0 >= p) {
                    continue;
                }
                speakers.add(m);
            }

            // content 在方法开头可能被置空，lambda 内引用的必须是 effectively final 变量
            final String userPrompt = content;

            // 5. 主回复与随机发言并行执行；请求线程只等全部完成再发 turn_end。
            CountDownLatch replyLatch = new CountDownLatch(speakers.size());
            for (SysAiTavernMember replyMember : speakers) {
                boolean isMain = replyMember.getId().equals(speaker.getId());
                TAVERN_CHAT_POOL.submit(() -> {
                    try {
                        String prompt = isMain ? userPrompt
                                : "（群成员刚说完，用你自己的人设简短接一句，一两句话即可）";
                        Streamed reply = streamOne(out, groupId, group, replyMember, prompt);
                        saveAiMessage(groupId, replyMember, reply);
                    } catch (Exception e) {
                        log.error("[TavernChat] 并行回复失败 groupId={} memberId={}",
                                groupId, replyMember.getId(), e);
                        sendRaw(out, Map.of("type", "finish", "finishReason", "error",
                                "error", e.getMessage()));
                    } finally {
                        replyLatch.countDown();
                    }
                });
            }
            replyLatch.await(5, TimeUnit.MINUTES);

            sendRaw(out, Map.of("type", "turn_end", "finishReason", "stop"));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[TavernChat] 流式失败 groupId={}", groupId, e);
        }
    }

    /**
     * 一位成员流式回复的结果：使用的模型 + 完整正文。
     *
     * <p>由 {@link #streamOne(OutputStream, Long, SysAiTavernGroup, SysAiTavernMember, String)}
     * 在 SSE 已全部下发完毕后产出，唯一用途是把这轮回复落库
     * （见 {@link #saveAiMessage(Long, SysAiTavernMember, String, String)}），
     * 因此它不参与任何对外响应体。</p>
     *
     * @param model 本轮实际使用的模型标识（不含 {@code providerId:} 前缀），
     *              由 {@link #resolveModel(SysAiTavernMember, SysAiRole)} 决定：
     *              优先该成员的「模型覆盖」配置，其次角色的模型配置，
     *              最后取全局默认模型（依次读 {@code runtime.default_model}、
     *              {@code models.defaultChatModelId}、{@code runtime.default_chat_model}，
     *              均未配置时由 {@code EffectiveChatModelResolver} 报错）。
     *              不允许为 {@code null}——它在流式回调触发前就已确定
     * @param providerId 本轮实际应答的供应商标识（可空）。
     *              <b>为什么必须带</b>：成员的模型覆盖在库里是
     *              {@code <providerId>:<model>} 限定写法，若拆掉前缀只留裸模型名，
     *              下游按名反查供应商时，同名模型（多家供应商都叫 dpv4）
     *              命中哪一家由数据库决定 —— 表现为「群里选的是 A 家模型，
     *              回复却来自 B 家接口」，B 家的配额/订阅报错（周卡、套餐过期）
     *              就冒进了酒馆会话。解析不出供应商时为 {@code null}，
     *              下游退回按名反查并打同名告警
     *              （见 {@code AiChatFacadeServiceImpl#resolveDbProviderByModel}）
     * @param text  本轮回复的完整正文，即 SSE 逐块下发的增量 chunk 按到达顺序拼接的结果。
     *              <b>不含</b>「{@code [角色名] }」前缀（该前缀只加在写回聊天历史的副本上），
     *              也不含「{@code [群成员刚说完…]}」这类拼接用的提示语。
     *              中途报错而中断时为已收到的部分，可能是空串，不允许为 {@code null}
     */
    private record Streamed(String model, String providerId, String text) {}

    /**
     * 流式回复一位成员。
     */
    private Streamed streamOne(OutputStream out, Long groupId, SysAiTavernGroup group,
                             SysAiTavernMember member, String prompt) {
        int ctxLimit = member.getRandomContextCount() == null || member.getRandomContextCount() <= 0 ? 20 : member.getRandomContextCount();
        List<Map<String, Object>> history = buildHistory(groupId, ctxLimit);
        SysAiRole role = roleMapper.selectById(member.getRoleId());
        String roleName = role != null && role.getName() != null ? role.getName() : "AI";
        // 拆出供应商一侧。EffectiveChatModelResolver 只回裸模型名，
        // 这里从原始限定写法里把 providerId 单独提出来带下去（见 Streamed 注释）
        String[] resolved = resolveModelQualified(member, role);
        String model = resolved[0];
        String providerId = resolved[1];

        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        request.setProviderId(providerId);
        request.setStream(true);
        request.setUseSkill(false); // 纯 ChatClient，不挂 MCP/工具/技能
        request.setPrompt(prompt);
        request.setMessages(history);
        if (role != null && role.getSystemPrompt() != null && !role.getSystemPrompt().isBlank()) {
            request.setSystemPrompt(role.getSystemPrompt());
        }
        if (role != null && role.getTemperature() != null) {
            request.setTemperature(role.getTemperature());
        }

        Map<String, Object> createEv = new LinkedHashMap<>();
        createEv.put("type", "create");
        createEv.put("speakerType", "ai");
        createEv.put("speakerId", String.valueOf(member.getRoleId()));
        createEv.put("speakerName", roleName);
        createEv.put("modelName", model);
        // 本轮实际应答的供应商：前端据此在酒馆消息旁显示提示，
        // 与主对话链路的 route 提示同口径
        createEv.put("route", buildRoute(providerId, model));
        sendRaw(out, createEv);
        log.info("[TavernChat] 路由选定 memberId={} model={} providerId={}",
                member.getId(), model, providerId);

        StringBuilder full = new StringBuilder();
        // streamWithUsage 为异步触发：回调在后台线程 fire。请求线程必须阻塞在此 latch 上，
        // 否则方法一返回 Spring 会关闭响应，后续 chunk 全部丢失（表现为只收到第一个字符）。
        CountDownLatch latch = new CountDownLatch(1);
        try {
            aiChatFacadeService.streamWithUsage(request,
                    chunk -> {
                        if (chunk != null && !chunk.isEmpty()) {
                            full.append(chunk);
                            sendRaw(out, Map.of(
                                    "type", "text",
                                    "speakerId", String.valueOf(member.getRoleId()),
                                    "content", chunk));
                        }
                    },
                    (reply, usage) -> {
                        String text = reply != null ? reply : full.toString();
                        // 把这轮 AI 回复追加进历史，供下一位成员看到
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("role", "assistant");
                        m.put("content", "[" + roleName + "] " + text);
                        history.add(m);
                        latch.countDown();
                    },
                    err -> {
                        sendRaw(out, Map.of("type", "finish", "finishReason", "error",
                                "speakerId", String.valueOf(member.getRoleId()),
                                "speakerName", roleName,
                                "error", err != null ? err.getMessage() : "生成失败"));
                        latch.countDown();
                    },
                    null);
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[TavernChat] streamOne 失败 memberId={}: {}", member.getId(), e.getMessage());
            sendRaw(out, Map.of("type", "finish", "finishReason", "error",
                    "speakerId", String.valueOf(member.getRoleId()),
                    "speakerName", roleName,
                    "error", e.getMessage()));
        }
        sendRaw(out, Map.of("type", "finish", "finishReason", "stop",
                "speakerId", String.valueOf(member.getRoleId()), "speakerName", roleName));
        return new Streamed(model, providerId, full.toString());
    }

    /** 解析回复成员：@ > 默认 > 首位；自动路由开启时由路由模型挑选 */
    private SysAiTavernMember resolveSpeaker(SysAiTavernGroup group, List<SysAiTavernMember> members,
                                             Integer atRoleId, String content) {
        if (atRoleId != null) {
            for (SysAiTavernMember m : members) {
                if (atRoleId.equals(m.getRoleId())) {
                    return m;
                }
            }
        }
        if (group.getDefaultMemberId() != null) {
            for (SysAiTavernMember m : members) {
                if (group.getDefaultMemberId().equals(m.getId())) {
                    return m;
                }
            }
        }
        // 自动路由
        if (group.getAutoRoute() != null && group.getAutoRoute() == 1 && members.size() > 1) {
            SysAiTavernMember routed = autoRoute(members, content);
            if (routed != null) {
                return routed;
            }
        }
        return members.get(0);
    }

    /**
     * auto Route
     * @param content 字符串
     * @param members 参数
     * @return 返回值
    */
    private SysAiTavernMember autoRoute(List<SysAiTavernMember> members, String content) {
        try {
            StringBuilder roster = new StringBuilder();
            for (int i = 0; i < members.size(); i++) {
                SysAiRole r = roleMapper.selectById(members.get(i).getRoleId());
                roster.append(i).append("=").append(r != null ? r.getName() : members.get(i).getRoleId());
                if (r != null && r.getDescription() != null && !r.getDescription().isBlank()) {
                    roster.append("(").append(r.getDescription()).append(")");
                }
                roster.append("\n");
            }
            String prompt = "群成员列表：\n" + roster + "\n用户说：" + content
                    + "\n请只回复最应该接话的成员编号（0~" + (members.size() - 1) + "），只输出一个数字，不要解释。";
            AiChatRequest req = new AiChatRequest();
            // 自动路由也要带供应商限定：默认模型同样可能是
            // <providerId>:<model>，裸名在同名模型下会走错家
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
                    if (idx >= 0 && idx < members.size()) {
                        log.info("[TavernChat] 自动路由 -> memberIndex={}", idx);
                        return members.get(idx);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[TavernChat] 自动路由失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * build History
     * @param groupId 长整数
     * @param limit 整数
     * @return 返回值
    */
    private List<Map<String, Object>> buildHistory(Long groupId, int limit) {
        LambdaQueryWrapper<SysAiTavernMessage> w = new LambdaQueryWrapper<>();
        w.eq(SysAiTavernMessage::getGroupId, groupId).orderByDesc(SysAiTavernMessage::getId).last("LIMIT " + limit);
        List<SysAiTavernMessage> rows = messageMapper.selectList(w);
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            SysAiTavernMessage m = rows.get(i);
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
     * @param groupId 长整数
     * @param member 参数
     * @param streamed 参数
     * @return 返回值
    */
    private void saveAiMessage(Long groupId, SysAiTavernMember member, Streamed streamed) {
        SysAiRole role = roleMapper.selectById(member.getRoleId());
        SysAiTavernMessage m = new SysAiTavernMessage();
        m.setGroupId(groupId);
        m.setSpeakerType("ai");
        m.setSpeakerId(String.valueOf(member.getRoleId()));
        m.setSpeakerName(role != null ? role.getName() : "AI");
        // 落库存限定名 <providerId>:<model>：sys_ai_tavern_message
        // 只有 model_name 一个文本列、没有 metadata 列，这是把「本轮由哪家供应商
        // 应答」带进历史的唯一办法（前端 displayModel 会剥掉前缀显示）。
        // 这样刷新后仍能对上一轮到底是不是走对了家。
        m.setModelName(qualify(streamed.providerId(), streamed.model()));
        m.setContent(streamed.text());
        m.setUserId(AiUserContext.currentUserId());
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /**
     * 拼回限定名。providerId 为空时退回裸模型名，保持与旧数据一致。
     *
     * @param providerId 供应商标识（可空）
     * @param model      裸模型名
     * @return {@code <providerId>:<model>} 或裸模型名
     */
    private static String qualify(String providerId, String model) {
        if (providerId == null || providerId.isBlank() || model == null) {
            return model;
        }
        return providerId + ":" + model;
    }

    /**
     * pick Random Other
     * @param exclude 参数
     * @param members 参数
     * @return 返回值
    */
    private SysAiTavernMember pickRandomOther(List<SysAiTavernMember> members, SysAiTavernMember exclude) {
        List<SysAiTavernMember> others = new ArrayList<>();
        for (SysAiTavernMember m : members) {
            if (!m.getId().equals(exclude.getId())) {
                others.add(m);
            }
        }
        if (others.isEmpty()) {
            return null;
        }
        return others.get((int) (Math.random() * others.size()));
    }

    /**
     * 解析成员应答模型，返回「裸模型名 + 供应商标识」。
     *
     * <p>成员的模型覆盖在库里是 {@code <providerId>:<model>} 限定写法。
     * 这里把两侧拆开：裸模型名交给 {@code EffectiveChatModelResolver}
     * 归一（命中已启用模型就用它，否则逐级回落默认模型），
     * 供应商标识原样保留并带进 {@code AiChatRequest.providerId}。</p>
     *
     * <p>为什么不能像旧实现那样只回裸模型名：多家供应商允许同名模型，
     * 下游按名反查供应商时命中哪一家由数据库决定 ——「群里选的是 A 家
     * 模型，回复却来自 B 家接口」，B 家的配额/订阅报错（周卡、套餐过期）
     * 就冒进了酒馆会话。</p>
     *
     * @param member 成员
     * @param role   成员角色（可空）
     * @return {@code [裸模型名, 供应商标识]}；解析不出供应商时后者为 {@code null}
     */
    private String[] resolveModelQualified(SysAiTavernMember member, SysAiRole role) {
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
        // 成员/角色没配（或配了但解析不出已启用模型）：
        // 回落全局默认模型。默认模型同样可能是「providerId:模型名」
        // 限定写法（实测 runtime/default_model 就是），必须保留
        // 供应商一侧，否则裸名在同名模型下会走错家
        return resolveDefaultQualified();
    }

    /**
     * 从限定写法里提取供应商标识。
     *
     * <p>冒号前是纯数字（雪花 id）或 {@code onnx} 这类平台标识时，
     * 才是真实的供应商主键；前缀是供应商名（如
     * {@code agnes:agnes-3.0-flash}）时按名称查出该供应商、
     * 改用它的主键 id。三种写法都认不出时才返回 {@code null}，
     * 让下游退回按名反查并打同名告警。</p>
     *
     * @param raw 原始取值，可空
     * @return 供应商标识；非限定写法或前缀认不出时返回 {@code null}
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
                return prefix;
            }
            // onnx 等平台标识：能查到启用的供应商才认
            com.chua.starter.ai.support.manage.store.SysAiProvider p =
                    providerMapper.selectById(prefix);
            if (p != null && Boolean.TRUE.equals(p.getEnabled())) {
                return prefix;
            }
            // 供应商名前缀（如「agnes:agnes-3.0-flash」）：
            // 按名称查出启用的供应商后，改用它的主键 id，
            // 这样角色模型配的是供应商名也能精确路由。
            // 名称匹配忽略大小写（库里是「Agnes」，配置常写「agnes」）；
            // 供应商表只有几家，全量加载在应用层匹配即可
            for (com.chua.starter.ai.support.manage.store.SysAiProvider cand
                    : providerMapper.selectList(null)) {
                if (cand.getName() != null && cand.getName().equalsIgnoreCase(prefix)
                        && Boolean.TRUE.equals(cand.getEnabled())
                        && cand.getProviderId() != null && !cand.getProviderId().isBlank()) {
                    return cand.getProviderId();
                }
            }
        } catch (Exception e) {
            log.debug("[TavernChat] 提取供应商标识失败 raw={}: {}", raw, e.getMessage());
        }
        return null;
    }

    /**
     * 组装酒馆 create 事件携带的路由信息。
     *
     * @param providerId 供应商标识（可空）
     * @param model      裸模型名
     * @return 路由信息 Map
     */
    private Map<String, Object> buildRoute(String providerId, String model) {
        Map<String, Object> route = new LinkedHashMap<>(8);
        route.put("model", model);
        route.put("providerId", providerId);
        route.put("providerResolved", providerId != null && !providerId.isBlank());
        if (providerId != null && !providerId.isBlank()) {
            try {
                com.chua.starter.ai.support.manage.store.SysAiProvider p =
                        providerMapper.selectById(providerId);
                if (p != null) {
                    route.put("providerName", p.getName());
                    route.put("platform", p.getPlatform());
                    route.put("baseUrl", p.getBaseUrl());
                }
            } catch (Exception e) {
                log.debug("[TavernChat] 查询供应商 {} 失败: {}", providerId, e.getMessage());
            }
        }
        return route;
    }

    /**
     * resolve Model
     * @param member 参数
     * @param role 参数
     * @return 返回值
    */
    private String resolveModel(SysAiTavernMember member, SysAiRole role) {
        return resolveModelQualified(member, role)[0];
    }

    /**
     * 解析全局默认模型，返回「裸模型名 + 供应商标识」。
     *
     * @return {@code [裸模型名, 供应商标识]}；解析不出供应商时后者为 {@code null}
     */
    private String[] resolveDefaultQualified() {
        String[][] keys = {{"runtime", "default_model"}, {"models", "defaultChatModelId"}, {"runtime", "default_chat_model"}};
        for (String[] k : keys) {
            String v = globalSettingService.getValue(k[0], k[1])
                    .map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
            if (v != null) {
                String providerId = extractProviderId(v);
                // getDefaultModel 会把前缀剥掉；这里自己剥，同时留下供应商一侧
                int idx = v.indexOf(':');
                String bare = idx >= 0 ? v.substring(idx + 1).trim() : v;
                if (!bare.isEmpty()) {
                    return new String[]{bare, providerId};
                }
            }
        }
        String resolved = effectiveChatModelResolver.resolve(null);
        return new String[]{resolved, null};
    }

    /**
     * require Owned
     * @param id 长整数
     * @return 返回值
    */
    private SysAiTavernGroup requireOwned(Long id) {
        SysAiTavernGroup g = groupMapper.selectById(id);
        if (g == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "群不存在");
        }
        if (!AiUserContext.requireUserId().equals(g.getUserId())) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "不可操作他人的群");
        }
        return g;
    }

    /**
     * send Raw
     * @param Map<String 参数
     * @param event 参数
     * @param out 输出流
     * @return 返回值
    */
    private void sendRaw(OutputStream out, Map<String, Object> event) {
        // 并行回复会从多个线程写同一条 SSE，必须串行写完整帧，否则两个角色
        // 的 text 事件可能交错，前端 JSON.parse 会失败（表现为流式突然中断）。
        synchronized (out) {
            try {
                String json = objectMapper.writeValueAsString(event);
                out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                log.debug("[TavernChat] sendRaw 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * str
     * @param v 对象
     * @return 返回值
    */
    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
