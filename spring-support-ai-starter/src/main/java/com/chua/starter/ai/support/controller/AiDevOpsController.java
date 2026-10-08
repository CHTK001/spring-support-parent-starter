package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DevOps 快捷能力：Git 拉取/提交 + 公网隧道（cloudflared Quick Tunnel）。
 *
 * <p>用户需求：会话里从 GitHub 拉项目 → 让 AI 改 → 提交到服务器；并支持一键把本地端口
 * 用**免费开源**的 cloudflared quick tunnel 暴露到公网。</p>
 *
 * <p>安全：全部动作限定在**当前账号的工作目录**内执行（同 workspaceTree 口径），
 * 且要求管理员；命令不经过 shell 拼接（ProcessBuilder 参数数组），避免注入。</p>
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/devops")
@RequiredArgsConstructor
public class AiDevOpsController {

    private final SysAiGlobalSettingService globalSettingService;

    /** 正在运行的 cloudflared 进程（单例，够用） */
    private static volatile Process tunnelProcess;
    private static volatile String tunnelUrl;
    private static volatile int tunnelPort;

    private static final Pattern TRY_URL = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    /**
     * 环境自检：git / cloudflared 是否可用 + 隧道状态。
     *
     * @return {git, cloudflared, tunnel:{running,url,port}}
     */
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("git", version("git", "--version"));
        data.put("cloudflared", version("cloudflared", "--version"));
        Map<String, Object> tunnel = new LinkedHashMap<>();
        tunnel.put("running", tunnelProcess != null && tunnelProcess.isAlive());
        tunnel.put("url", tunnelUrl);
        tunnel.put("port", tunnelPort);
        data.put("tunnel", tunnel);
        data.put("workspace", String.valueOf(resolveAgentWorkspace()));
        return ReturnResult.ok(data);
    }

    /**
     * 从 GitHub（或任意 git 地址）克隆到工作目录。
     *
     * @param body {url: 仓库地址, dir: 目标子目录（可空，默认取仓库名）}
     * @return {dir, output}
     */
    @PostMapping("/git/clone")
    public ReturnResult<Map<String, Object>> gitClone(@RequestBody Map<String, Object> body) {
        Path ws = requireWorkspace();
        String url = str(body.get("url"));
        if (url == null || url.isBlank()) {
            return ReturnResult.error("url 不能为空");
        }
        String dir = str(body.get("dir"));
        if (dir == null || dir.isBlank()) {
            dir = url.replaceAll("\\.git$", "").replaceAll(".*/", "");
        }
        Path target = safeResolve(ws, dir);
        if (target == null) {
            return ReturnResult.error("目标目录越界");
        }
        if (Files.exists(target)) {
            return ReturnResult.error("目录已存在: " + dir + "（先改名或删除）");
        }
        List<String> out = exec(ws, 180, "git", "clone", "--depth=1", url, target.toString());
        // git 失败时必须如实报错：此前无论成败都回 success:true（实测网络抖动时误导用户）
        if (!Files.isDirectory(target.resolve(".git"))) {
            return ReturnResult.error("克隆失败（git 输出）：" + String.join(" | ", out));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dir", dir);
        data.put("path", target.toString());
        data.put("output", out);
        return ReturnResult.ok(data);
    }

    /**
     * 查看仓库状态（短格式）。
     *
     * @param body {dir}
     * @return {output}
     */
    @PostMapping("/git/status")
    public ReturnResult<Map<String, Object>> gitStatus(@RequestBody Map<String, Object> body) {
        Path repo = requireRepo(body);
        return ReturnResult.ok(Map.of("output", exec(repo, 60, "git", "status", "--short", "--branch")));
    }

    /**
     * 提交当前改动（git add -A + commit）。
     *
     * @param body {dir, message}
     * @return {output}
     */
    @PostMapping("/git/commit")
    public ReturnResult<Map<String, Object>> gitCommit(@RequestBody Map<String, Object> body) {
        Path repo = requireRepo(body);
        String message = str(body.get("message"));
        if (message == null || message.isBlank()) {
            message = "chore: update by ai-guada";
        }
        List<String> out = new ArrayList<>();
        out.addAll(exec(repo, 60, "git", "add", "-A"));
        out.addAll(exec(repo, 60, "git", "-c", "user.name=ai-guada", "-c", "user.email=ai-guada@local",
                "commit", "-m", message));
        return ReturnResult.ok(Map.of("output", out));
    }

    /**
     * 推送到远端（凭据沿用仓库/服务器已配置的方式）。
     *
     * @param body {dir}
     * @return {output}
     */
    @PostMapping("/git/push")
    public ReturnResult<Map<String, Object>> gitPush(@RequestBody Map<String, Object> body) {
        Path repo = requireRepo(body);
        return ReturnResult.ok(Map.of("output", exec(repo, 180, "git", "push")));
    }

    /**
     * 启动 cloudflared Quick Tunnel（免费、开源、免账号），把本地端口暴露到公网。
     *
     * @param body {port: 本地端口}
     * @return {url, port}
     */
    @PostMapping("/tunnel/start")
    public ReturnResult<Map<String, Object>> tunnelStart(@RequestBody Map<String, Object> body) {
        Object p = body.get("port");
        int port = p == null ? 8787 : Integer.parseInt(String.valueOf(p));
        if (tunnelProcess != null && tunnelProcess.isAlive()) {
            return ReturnResult.ok(Map.of("url", String.valueOf(tunnelUrl), "port", tunnelPort));
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("cloudflared", "tunnel", "--url", "http://127.0.0.1:" + port,
                    "--no-autoupdate");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            tunnelProcess = proc;
            tunnelPort = port;
            tunnelUrl = null;
            // 【坑】不能"读到 URL 就 break 并关闭 reader"：关掉子进程 stdout 会让 cloudflared 退出，
            // 隧道随即变 530（实测 2026-10-08）。这里单线程持续消费输出，读到 URL 只置位+通知。
            final Object lock = new Object();
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (tunnelUrl == null) {
                            Matcher m = TRY_URL.matcher(line);
                            if (m.find()) {
                                tunnelUrl = m.group();
                                synchronized (lock) {
                                    lock.notifyAll();
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 进程结束即退出
                }
            }, "cloudflared-log");
            reader.setDaemon(true);
            reader.start();

            long deadline = System.currentTimeMillis() + 40_000L;
            synchronized (lock) {
                while (tunnelUrl == null && proc.isAlive() && System.currentTimeMillis() < deadline) {
                    lock.wait(500L);
                }
            }
            if (tunnelUrl == null) {
                proc.destroyForcibly();
                tunnelProcess = null;
                return ReturnResult.error("cloudflared 启动超时或未输出公网地址（检查 /usr/local/bin/cloudflared 与出网）");
            }
            return ReturnResult.ok(Map.of("url", tunnelUrl, "port", port));
        } catch (Exception e) {
            log.warn("[DevOps] 启动 tunnel 失败: {}", e.getMessage());
            return ReturnResult.error("启动隧道失败: " + e.getMessage());
        }
    }

    /**
     * 关闭隧道。
     *
     * @return {stopped}
     */
    @PostMapping("/tunnel/stop")
    public ReturnResult<Map<String, Object>> tunnelStop() {
        Process proc = tunnelProcess;
        boolean stopped = false;
        if (proc != null && proc.isAlive()) {
            proc.destroy();
            try {
                if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                    proc.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopped = true;
        }
        tunnelProcess = null;
        tunnelUrl = null;
        return ReturnResult.ok(Map.of("stopped", stopped));
    }

    // ==================== 内部工具 ====================

    private String version(String cmd, String arg) {
        try {
            Process p = new ProcessBuilder(cmd, arg).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String first = r.readLine();
                p.waitFor(5, TimeUnit.SECONDS);
                return first == null ? "unknown" : first.trim();
            }
        } catch (Exception e) {
            return "(未安装)";
        }
    }

    private List<String> exec(Path cwd, int timeoutSec, String... cmd) {
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    lines.add(line);
                    if (lines.size() >= 400) {
                        break;
                    }
                }
            }
            if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                lines.add("(超时 " + timeoutSec + "s 已终止)");
            }
        } catch (Exception e) {
            lines.add("(执行失败: " + e.getMessage() + ")");
        }
        return lines;
    }

    private Path requireRepo(Map<String, Object> body) {
        Path ws = requireWorkspace();
        String dir = str(body.get("dir"));
        Path repo = safeResolve(ws, dir);
        if (repo == null || !Files.isDirectory(repo) || !Files.isDirectory(repo.resolve(".git"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "不是 git 仓库或目录不存在: " + dir);
        }
        return repo;
    }

    private Path requireWorkspace() {
        Path ws = resolveAgentWorkspace();
        if (ws == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "工作目录未配置");
        }
        return ws;
    }

    private Path safeResolve(Path ws, String dir) {
        if (dir == null || dir.isBlank()) {
            return null;
        }
        Path p = ws.resolve(dir).normalize();
        return p.startsWith(ws) ? p : null;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    /** 与 workspaceTree 同口径：<基路径>/<账号>/agent */
    private Path resolveAgentWorkspace() {
        String base;
        try {
            base = globalSettingService.getValue("system", "workspaceBaseDir").orElse(null);
        } catch (Exception e) {
            return null;
        }
        if (base == null || base.isBlank()) {
            return null;
        }
        try {
            Path basePath = Paths.get(base.trim()).toAbsolutePath().normalize();
            String account = Objects.toString(AiUserContext.currentUserId(), "public")
                    .replaceAll("[^A-Za-z0-9_-]", "_");
            Path workspace = basePath.resolve(account).resolve("agent").normalize();
            return workspace.startsWith(basePath) ? workspace : null;
        } catch (Exception e) {
            return null;
        }
    }
}
