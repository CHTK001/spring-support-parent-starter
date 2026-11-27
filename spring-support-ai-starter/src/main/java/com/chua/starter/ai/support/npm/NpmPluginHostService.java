package com.chua.starter.ai.support.npm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import com.chua.common.support.utils.CollectionUtils;

/**
 * npm 插件 Node 侧车宿主服务。
 *
 * <p>懒启动一个 Node 子进程运行 {@code npm/host.js}（从 classpath 释放到
 * 安装目录 {@code .host/host.js}），通过 stdio JSON-lines 协议调用插件：
 * {@code begin} 装载某会话流的插件链，{@code event} 逐帧处理流事件，
 * {@code response} 处理完整响应体，{@code end} 清理流状态。</p>
 *
 * <p>进程死亡（崩溃/空闲自退）时清空等待中的调用并允许下次调用重新拉起；
 * 任何失败都以异常上抛，由调用方（{@link NpmMiddlewareChatClient}）降级为
 * 「跳过中间件直通」，绝不阻断对话主链路。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpmPluginHostService {

    /**
     * classpath 中的侧车脚本资源路径。
     */
    private static final String HOST_RESOURCE = "npm/host.js";

    private final NpmPluginProperties properties;
    private final NpmPluginManagerService managerService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong();

    private Process process;
    private BufferedWriter writer;
    private String hostScriptPath;

    /**
     * 为一个会话流装载插件链。
     *
     * @param streamId 会话流唯一标识
     * @param plugins  插件清单（非空）
     * @return 至少一个插件装载成功返回 true；全部失败返回 false（调用方应直通）
     */
    public boolean begin(String streamId, List<NpmPluginSpec> plugins) {
        if (CollectionUtils.isEmpty(plugins)) {
            return false;
        }
        ArrayNode arr = mapper.createArrayNode();
        for (NpmPluginSpec spec : plugins) {
            ObjectNode item = arr.addObject();
            item.put("name", spec.name());
            item.set("options", mapper.valueToTree(spec.options() == null ? Map.of() : spec.options()));
        }
        ObjectNode req = base("begin", streamId);
        req.set("plugins", arr);
        JsonNode resp = call(req);
        int loaded = resp.path("loaded").asInt(0);
        JsonNode errors = resp.path("errors");
        if (errors.isArray()) {
            for (JsonNode e : errors) {
                log.warn("[NpmPlugin] {}", e.asText());
            }
        }
        return resp.path("ok").asBoolean(false) && loaded > 0;
    }

    /**
     * 处理一个流事件帧。
     *
     * @param streamId 会话流标识
     * @param event    OpenAI 形态事件（choices[].delta）
     * @return 处理后的事件节点
     */
    public JsonNode event(String streamId, Object event) {
        ObjectNode req = base("event", streamId);
        req.set("event", mapper.valueToTree(event));
        return call(req).path("event");
    }

    /**
     * 处理完整响应体（流结束时的聚合回调）。
     *
     * @param streamId 会话流标识
     * @param body     OpenAI 形态响应体
     * @return 处理后的响应体节点
     */
    public JsonNode response(String streamId, Object body) {
        ObjectNode req = base("response", streamId);
        req.set("body", mapper.valueToTree(body));
        return call(req).path("body");
    }

    /**
     * 清理会话流状态（尽力而为，不抛异常、不阻塞）。
     *
     * @param streamId 会话流标识
     */
    public void endQuietly(String streamId) {
        try {
            if (!isRunning()) {
                return;
            }
            ObjectNode req = base("end", streamId);
            synchronized (lock) {
                ensureStarted();
                writer.write(mapper.writeValueAsString(req));
                writer.newLine();
                writer.flush();
            }
        } catch (Exception e) {
            log.debug("[NpmPlugin] 结束流状态失败: {}", e.getMessage());
        }
    }

    /**
     * 探测侧车进程是否存活且可响应（不主动拉起进程）。
     *
     * @return ping 成功返回 true
     */
    public boolean ping() {
        if (!isRunning()) {
            return false;
        }
        try {
            return call(base("ping", null)).path("pong").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 侧车进程是否存活。
     *
     * @return 存活返回 true
     */
    public boolean isRunning() {
        synchronized (lock) {
            return process != null && process.isAlive();
        }
    }

    /**
     * 汇总宿主与运行环境状态（管理端点用）。
     *
     * @return enabled/running/node 等状态键值
     */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", properties.isEnabled());
        m.put("running", isRunning());
        m.put("responsive", ping());
        m.put("nodeCommand", managerService.nodeCommand());
        m.put("nodeAvailable", managerService.nodeAvailable());
        m.put("nodeVersion", managerService.nodeVersion());
        m.put("installDir", managerService.installDir().toString());
        return m;
    }

    /**
     * 会话结束/应用停机时关闭侧车进程。
     */
    @PreDestroy
    public void shutdown() {
        synchronized (lock) {
            if (process == null) {
                return;
            }
            try {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            } finally {
                process = null;
                writer = null;
            }
        }
    }

    /**
     * 构造请求骨架。
     *
     * @param type     消息类型
     * @param streamId 会话流标识（可为 null）
     * @return 请求节点
     */
    private ObjectNode base(String type, String streamId) {
        ObjectNode req = mapper.createObjectNode();
        req.put("id", idGen.incrementAndGet());
        req.put("type", type);
        if (streamId != null) {
            req.put("streamId", streamId);
        }
        return req;
    }

    /**
     * 同步调用侧车：写入请求并等待按 id 匹配的响应。
     *
     * @param req 请求节点
     * @return 响应节点（ok=true）
     * @throws IllegalStateException 进程不可用、写入失败、超时或宿主返回 ok=false
     */
    private JsonNode call(ObjectNode req) {
        long id = req.get("id").asLong();
        long timeout = Math.max(5L, properties.getCallTimeoutSeconds());
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            String line = mapper.writeValueAsString(req);
            synchronized (lock) {
                ensureStarted();
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
            JsonNode resp = future.get(timeout, TimeUnit.SECONDS);
            if (!resp.path("ok").asBoolean(false)) {
                throw new IllegalStateException(resp.path("error").asText("npm 插件宿主返回失败"));
            }
            return resp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("npm 插件宿主调用被中断", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("npm 插件宿主调用超时(" + timeout + "s)");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("npm 插件宿主调用失败: " + e.getMessage(), e);
        } finally {
            pending.remove(id);
        }
    }

    /**
     * 确保侧车进程存活（持锁调用）。
     */
    private void ensureStarted() {
        if (process != null && process.isAlive()) {
            return;
        }
        extractHostScript();
        String node = managerService.nodeCommand();
        ProcessBuilder pb = new ProcessBuilder(List.of(node, hostScriptPath));
        pb.environment().put("NPM_PLUGIN_DIR", managerService.installDir().toString());
        pb.environment().put("NPM_HOST_IDLE_MS", String.valueOf(properties.getHostIdleSeconds() * 1000L));
        pb.redirectErrorStream(false);
        try {
            process = pb.start();
        } catch (IOException e) {
            process = null;
            writer = null;
            throw new IllegalStateException("无法启动 Node 侧车进程(" + node + "): " + e.getMessage(), e);
        }
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        startReader(process);
        startStderrPump(process);
        log.info("[NpmPlugin] Node 侧车已启动 pid={} node={} 插件目录={}", process.pid(), node, managerService.installDir());
    }

    /**
     * 把 classpath 中的 host.js 释放到安装目录（内容变化时重写）。
     */
    private void extractHostScript() {
        Path dir = managerService.installDir().resolve(".host");
        Path target = dir.resolve("host.js");
        try (InputStream in = NpmPluginHostService.class.getClassLoader().getResourceAsStream(HOST_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("classpath 中未找到 " + HOST_RESOURCE);
            }
            byte[] bytes = in.readAllBytes();
            boolean needWrite = !Files.isRegularFile(target) || !Arrays.equals(bytes, Files.readAllBytes(target));
            if (needWrite) {
                Files.createDirectories(dir);
                Files.write(target, bytes);
            }
            hostScriptPath = target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("释放 host.js 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 启动 stdout 读者线程：按行解析响应并按 id 完成等待中的 future。
     *
     * @param proc 侧车进程
     */
    private void startReader(Process proc) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        JsonNode node = mapper.readTree(line);
                        long id = node.path("id").asLong(-1);
                        CompletableFuture<JsonNode> f = id >= 0 ? pending.remove(id) : null;
                        if (f == null) {
                            continue;
                        }
                        if (node.path("ok").asBoolean(false)) {
                            f.complete(node);
                        } else {
                            f.completeExceptionally(new IllegalStateException(
                                    node.path("error").asText("npm 插件宿主返回失败")));
                        }
                    } catch (Exception e) {
                        log.debug("[NpmPlugin] 解析宿主输出失败: {}", e.getMessage());
                    }
                }
            } catch (IOException ignored) {
                // 进程退出时流自然关闭
            } finally {
                failAllPending("npm 插件宿主进程已退出");
                synchronized (lock) {
                    if (process == proc) {
                        process = null;
                        writer = null;
                    }
                }
            }
        }, "npm-plugin-host-reader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 启动 stderr 泵线程：侧车日志转发到应用日志。
     *
     * @param proc 侧车进程
     */
    private void startStderrPump(Process proc) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("[NpmPlugin][node] {}", line.length() > 300 ? line.substring(0, 300) : line);
                }
            } catch (IOException ignored) {
                // 进程退出时流自然关闭
            }
        }, "npm-plugin-host-stderr");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 进程死亡时让所有等待中的调用失败。
     *
     * @param reason 失败原因
     */
    private void failAllPending(String reason) {
        for (Map.Entry<Long, CompletableFuture<JsonNode>> e : pending.entrySet()) {
            if (pending.remove(e.getKey(), e.getValue())) {
                e.getValue().completeExceptionally(new IllegalStateException(reason));
            }
        }
    }
}
