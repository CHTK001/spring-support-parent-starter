package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.settings.AiModelService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 通用命令生成控制器。
 *
 * <p>提供两类能力：</p>
 * <ol>
 *   <li>{@code GET /v2/command/models}：普通用户可用的「启用中」对话模型列表（可按能力过滤），
 *       不含 admin 限制，供命令组件的模型下拉使用；</li>
 *   <li>{@code POST /v2/command/generate}：SSE 流式生成 shell / PowerShell 命令，
 *       复用 {@link AiChatFacadeService}，但<b>不落 AI 会话库</b>，避免工具性生成污染对话列表。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CommandGeneratorController {

    /** SSE 帧专用紧凑序列化器（不继承全局美化配置，保证单行 JSON） */
    private static final ObjectMapper SSE_MAPPER = new ObjectMapper();

    private final AiModelService aiModelService;
    private final AiChatFacadeService aiChatFacadeService;

    /**
     * 查询启用中的可用对话模型（普通用户可调）。
     *
     * @param capability 能力过滤关键字（如 chat / think / vision），为空返回全部启用模型
     * @return 精简模型信息列表
     */
    @GetMapping("/v2/command/models")
    public ReturnResult<List<Map<String, Object>>> models(
            @RequestParam(required = false) String capability) {
        List<AiModelDTO> all;
        try {
            all = aiModelService.list();
        } catch (Exception e) {
            // 模型服务对当前用户不可用时返回空列表，前端据此隐藏 AI 入口
            return ReturnResult.ok(new ArrayList<>());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiModelDTO m : all) {
            if (Boolean.FALSE.equals(m.getEnabled())) {
                continue;
            }
            if (capability != null && !capability.isBlank()) {
                List<String> caps = m.getCapabilities();
                boolean match = caps != null && caps.stream()
                        .anyMatch(c -> c.toLowerCase().contains(capability.toLowerCase()));
                if (!match) {
                    continue;
                }
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getName());
            map.put("code", m.getCode());
            map.put("providerName", m.getProviderName());
            map.put("capabilities", m.getCapabilities());
            list.add(map);
        }
        return ReturnResult.ok(list);
    }

    /**
     * 流式生成命令（SSE），不写入 AI 会话库。
     *
     * <p>SSE 帧：{@code start} → 多个 {@code chunk}（增量）→ {@code done}（含 full）
     * 或 {@code error}。为避免全局消息转换器对 SSE 数据做美化拆行，这里手动管理
     * {@link AsyncContext} 并直接写入 {@code data: 单行JSON} 帧。</p>
     *
     * @param body     生成上下文（os / projectType / mainEntry / projectPath / logPath /
     *                 appName / requirement / model / multi）
     * @param request  Servlet 请求（用于开启异步）
     * @param response Servlet 响应（直接写 SSE 字节）
     */
    @PostMapping(value = "/v2/command/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiReturnFormatIgnore
    public void generate(@RequestBody Map<String, Object> body,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        final AsyncContext async = request.startAsync();
        async.setTimeout(0);
        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                response.setContentType("text/event-stream;charset=UTF-8");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setHeader("Cache-Control", "no-cache");
                final ServletOutputStream out = response.getOutputStream();

                String os = str(body.get("os"));
                boolean windows = "windows".equalsIgnoreCase(os);
                String shell = windows ? "PowerShell" : "bash";
                String projectType = str(body.get("projectType"));
                String mainEntry = str(body.get("mainEntry"));
                String projectPath = str(body.get("projectPath"));
                String logPath = str(body.get("logPath"));
                String appName = str(body.get("appName"));
                String requirement = str(body.get("requirement"));
                String model = str(body.get("model"));
                boolean multi = Boolean.TRUE.equals(body.get("multi"));

                StringBuilder sys = new StringBuilder();
                sys.append("你是资深运维与部署脚本生成器。\n");
                sys.append("目标操作系统：").append(has(os) ? os : "linux")
                        .append("，命令 Shell：").append(shell).append("。\n");
                if (has(projectType)) {
                    sys.append("项目类型：").append(projectType).append("。\n");
                }
                if (has(mainEntry)) {
                    sys.append("主入口（jar / 二进制 / 脚本名）：").append(mainEntry).append("。\n");
                }
                if (has(projectPath)) {
                    sys.append("项目部署路径：").append(projectPath).append("。\n");
                }
                if (has(logPath)) {
                    sys.append("日志路径：").append(logPath).append("。\n");
                }
                if (has(appName)) {
                    sys.append("应用名：").append(appName).append("。\n");
                }
                sys.append("\n输出要求：\n");
                sys.append("1. 只输出可直接执行的 ").append(shell).append(" 命令；\n");
                sys.append("2. 不要解释、不要 Markdown 代码围栏；\n");
                if (multi) {
                    sys.append("3. 同时生成启动、停止、重启三段配套命令（进程 / pid / 服务名保持一致），");
                    sys.append("严格只输出如下 JSON，命令内换行用 \\n 表示：\n");
                    sys.append("{\"start\":\"\",\"stop\":\"\",\"restart\":\"\"}\n");
                } else {
                    sys.append("3. 命令需可重复执行，带必要的进程 / pid 管理与日志重定向。\n");
                }

                List<Map<String, Object>> messages = new ArrayList<>();
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("role", "system");
                sm.put("content", sys.toString());
                messages.add(sm);
                Map<String, Object> um = new LinkedHashMap<>();
                um.put("role", "user");
                um.put("content", has(requirement) ? requirement
                        : (multi ? "请生成启动、停止、重启三段配套命令。" : "请生成该命令。"));
                messages.add(um);

                AiChatRequest req = new AiChatRequest();
                if (has(model)) {
                    req.setModel(model);
                }
                req.setStream(true);
                req.setMessages(messages);

                Map<String, Object> startEv = new LinkedHashMap<>();
                startEv.put("type", "start");
                startEv.put("shell", shell);
                writeFrame(out, startEv);

                StringBuilder full = new StringBuilder();
                aiChatFacadeService.stream(req,
                        chunk -> {
                            if (chunk != null && !chunk.isEmpty()) {
                                full.append(chunk);
                                Map<String, Object> ev = new LinkedHashMap<>();
                                ev.put("type", "chunk");
                                ev.put("content", chunk);
                                writeFrame(out, ev);
                            }
                        },
                        reply -> {
                            String f = reply != null ? reply : full.toString();
                            Map<String, Object> ev = new LinkedHashMap<>();
                            ev.put("type", "done");
                            ev.put("full", f);
                            writeFrame(out, ev);
                            async.complete();
                        },
                        error -> {
                            Map<String, Object> ev = new LinkedHashMap<>();
                            ev.put("type", "error");
                            ev.put("message", error != null && error.getMessage() != null
                                    ? error.getMessage() : "命令生成失败");
                            writeFrame(out, ev);
                            async.complete();
                        });
            } catch (Exception e) {
                log.error("[Command] 生成失败", e);
                try {
                    Map<String, Object> ev = new LinkedHashMap<>();
                    ev.put("type", "error");
                    ev.put("message", e.getMessage() != null ? e.getMessage() : "生成失败");
                    writeFrame(response.getOutputStream(), ev);
                } catch (Exception ignored) {
                    // 连接已关闭，忽略
                }
                async.complete();
            }
        }));
    }

    /**
     * 直接写入一帧 SSE：{@code data: 紧凑单行JSON} + 空行，并立即刷新。
     *
     * @param out     响应输出流
     * @param payload 帧数据
     */
    private static void writeFrame(ServletOutputStream out, Map<String, Object> payload) {
        try {
            String json = SSE_MAPPER.writeValueAsString(payload);
            out.write(("data:" + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("SSE 帧写入失败", e);
        }
    }

    /**
     * 字符串化，null 返回 null。
     *
     * @param o 值
     * @return 字符串
     */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 判断字符串是否有内容。
     *
     * @param s 字符串
     * @return 有内容返回 true
     */
    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }
}
