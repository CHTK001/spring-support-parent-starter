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

    private static final String DEFAULT_MODEL_FALLBACK = "agnes-2.5-flash";

    private final SysAiTavernGroupMapper groupMapper;
    private final SysAiTavernMemberMapper memberMapper;
    private final SysAiTavernMessageMapper messageMapper;
    private final SysAiRoleMapper roleMapper;
    private final AiChatFacadeService aiChatFacadeService;
    private final SysAiGlobalSettingService globalSettingService;
    private final ObjectMapper objectMapper;

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

            // 5. 主回复（主回复者用自己的上下文条数）
            Streamed main = streamOne(out, groupId, group, speaker, content);
            saveAiMessage(groupId, speaker, main.model(), main.text());

            // 6. 随机发言：其他开启了随机发言的成员，按各自概率依次接话（参考连接器）
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
                Streamed otherStream = streamOne(out, groupId, group, m,
                        "（群成员刚说完，用你自己的人设简短接一句，一两句话即可）");
                saveAiMessage(groupId, m, otherStream.model(), otherStream.text());
            }

            sendRaw(out, Map.of("type", "turn_end", "finishReason", "stop"));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (ApiException e) {
            throw e;
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
     * @param model 本轮实际使用的模型标识（不含 {@code provider:} 前缀），
     *              由 {@link #resolveModel(SysAiTavernMember, SysAiRole)} 决定：
     *              优先该成员的「模型覆盖」配置，其次角色的模型配置，
     *              最后取全局默认模型（依次读 {@code runtime.default_model}、
     *              {@code models.defaultChatModelId}、{@code runtime.default_chat_model}，
     *              均未配置时回落 {@code agnes-2.5-flash}）。
     *              不允许为 {@code null}——它在流式回调触发前就已确定
     * @param text  本轮回复的完整正文，即 SSE 逐块下发的增量 chunk 按到达顺序拼接的结果。
     *              <b>不含</b>「{@code [角色名] }」前缀（该前缀只加在写回聊天历史的副本上），
     *              也不含「{@code [群成员刚说完…]}」这类拼接用的提示语。
     *              中途报错而中断时为已收到的部分，可能是空串，不允许为 {@code null}
     */
    private record Streamed(String model, String text) {}

    /**
     * 流式回复一位成员。
     */
    private Streamed streamOne(OutputStream out, Long groupId, SysAiTavernGroup group,
                             SysAiTavernMember member, String prompt) {
        int ctxLimit = member.getRandomContextCount() == null || member.getRandomContextCount() <= 0 ? 20 : member.getRandomContextCount();
        List<Map<String, Object>> history = buildHistory(groupId, ctxLimit);
        SysAiRole role = roleMapper.selectById(member.getRoleId());
        String roleName = role != null && role.getName() != null ? role.getName() : "AI";
        String model = resolveModel(member, role);

        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
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
        sendRaw(out, createEv);

        StringBuilder full = new StringBuilder();
        // streamWithUsage 为异步触发：回调在后台线程 fire。请求线程必须阻塞在此 latch 上，
        // 否则方法一返回 Spring 会关闭响应，后续 chunk 全部丢失（表现为只收到第一个字符）。
        CountDownLatch latch = new CountDownLatch(1);
        try {
            aiChatFacadeService.streamWithUsage(request,
                    chunk -> {
                        if (chunk != null && !chunk.isEmpty()) {
                            full.append(chunk);
                            sendRaw(out, Map.of("type", "text", "content", chunk));
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
                                "error", err != null ? err.getMessage() : "生成失败"));
                        latch.countDown();
                    },
                    null);
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[TavernChat] streamOne 失败 memberId={}: {}", member.getId(), e.getMessage());
            sendRaw(out, Map.of("type", "finish", "finishReason", "error", "error", e.getMessage()));
        }
        sendRaw(out, Map.of("type", "finish", "finishReason", "stop",
                "speakerId", String.valueOf(member.getRoleId()), "speakerName", roleName));
        return new Streamed(model, full.toString());
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

    /** 路由模型：让默认模型按成员描述挑一位最合适的 */
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
            req.setModel(getDefaultModel());
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

    /** 拼群历史：user=群主发言，ai=[角色名] 发言 */
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

    private void saveAiMessage(Long groupId, SysAiTavernMember member, String model, String content) {
        SysAiRole role = roleMapper.selectById(member.getRoleId());
        SysAiTavernMessage m = new SysAiTavernMessage();
        m.setGroupId(groupId);
        m.setSpeakerType("ai");
        m.setSpeakerId(String.valueOf(member.getRoleId()));
        m.setSpeakerName(role != null ? role.getName() : "AI");
        m.setModelName(model);
        m.setContent(content);
        m.setUserId(AiUserContext.currentUserId());
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

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

    private String resolveModel(SysAiTavernMember member, SysAiRole role) {
        if (member.getModelOverride() != null && !member.getModelOverride().isBlank()) {
            return member.getModelOverride();
        }
        if (role != null && role.getModel() != null && !role.getModel().isBlank()) {
            return role.getModel();
        }
        return getDefaultModel();
    }

    private String getDefaultModel() {
        String[][] keys = {{"runtime", "default_model"}, {"models", "defaultChatModelId"}, {"runtime", "default_chat_model"}};
        for (String[] k : keys) {
            String v = globalSettingService.getValue(k[0], k[1]).map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
            if (v != null) {
                int idx = v.indexOf(':');
                return idx >= 0 ? v.substring(idx + 1) : v;
            }
        }
        return DEFAULT_MODEL_FALLBACK;
    }

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

    private void sendRaw(OutputStream out, Map<String, Object> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            log.debug("[TavernChat] sendRaw 失败: {}", e.getMessage());
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
