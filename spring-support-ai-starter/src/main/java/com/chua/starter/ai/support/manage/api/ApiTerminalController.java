package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.ssh.SshTerminalSessionManager;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.manage.store.SysAiSshHostStore;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端管理端点。
 *
 * <p>桌面模式下终端由 Electron 管理；WebUI 模式：
 * <ul>
 *   <li>创建请求携带 {@code ssh_host_id} 时，开启真实 SSH 交互式终端
 *       （MINA SSHD PTY），输出经 {@code terminal.output} WS 事件推给前端 XtermView；</li>
 *   <li>未携带时保持占位（本地 PTY 由宿主进程管理，此端点仅回执）。</li>
 * </ul>
 */
@ApiReturnFormatIgnore
@Slf4j
@RestController
@RequestMapping("/api/terminals")
@RequiredArgsConstructor
public class ApiTerminalController {

    /**
     * SSH 交互终端会话管理
     */
    private final SshTerminalSessionManager sshTerminalManager;

    /**
     * SSH 主机存储
     */
    private final SysAiSshHostStore sshHostStore;

    @TokenForIgnore
    @GetMapping
    public Map<String, Object> list() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/list-ssh")
    public Map<String, Object> listSsh() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Object sshHostId = body.get("ssh_host_id");
        if (sshHostId != null && !String.valueOf(sshHostId).isBlank()) {
            try {
                SysAiSshHost host = sshHostStore.get(String.valueOf(sshHostId));
                if (host == null) {
                    return Map.of("success", false, "error", "主机不存在: " + sshHostId);
                }
                String terminalId = IdUtils.uuidv7();
                int cols = body.get("cols") instanceof Number n ? n.intValue() : 80;
                int rows = body.get("rows") instanceof Number m ? m.intValue() : 24;
                SshTerminalSessionManager.SshTerminalSession session = sshTerminalManager.open(host, terminalId, cols, rows);
                return Map.of("success", true, "data", toSshResponse(session));
            } catch (Exception e) {
                log.warn("[Terminal] SSH 终端创建失败: {}", e.getMessage());
                return Map.of("success", false, "error", "SSH 终端创建失败: " + sanitize(e.getMessage()));
            }
        }
        // 无 ssh_host_id：本地 PTY 占位回执（与旧行为一致）
        return Map.of("success", true, "data", Map.of(
                "terminal_id", "term-" + System.currentTimeMillis(),
                "status", "running"
        ));
    }

    @TokenForIgnore
    @GetMapping("/{terminalId}")
    public Map<String, Object> get(@PathVariable String terminalId) {
        SshTerminalSessionManager.SshTerminalSession session = sshTerminalManager.get(terminalId);
        if (session == null) {
            return Map.of("success", true, "data", Map.of(
                    "terminal_id", terminalId,
                    "status", "running"
            ));
        }
        return Map.of("success", true, "data", toSshResponse(session));
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/input")
    public Map<String, Object> input(@PathVariable String terminalId, @RequestBody Map<String, String> body) {
        String dataB64 = body.get("data_b64");
        boolean ok = dataB64 != null && sshTerminalManager.input(terminalId, dataB64);
        return Map.of("success", true, "data", ok);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/resize")
    public Map<String, Object> resize(@PathVariable String terminalId, @RequestBody Map<String, Object> body) {
        int cols = body.get("cols") instanceof Number n ? n.intValue() : 80;
        int rows = body.get("rows") instanceof Number m ? m.intValue() : 24;
        boolean ok = sshTerminalManager.resize(terminalId, cols, rows);
        return Map.of("success", true, "data", ok);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/kill")
    public Map<String, Object> kill(@PathVariable String terminalId) {
        sshTerminalManager.close(terminalId);
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/relaunch")
    public Map<String, Object> relaunch(@PathVariable String terminalId) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/relaunch-shell")
    public Map<String, Object> relaunchShell(@PathVariable String terminalId) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @DeleteMapping("/{terminalId}")
    public Map<String, Object> delete(@PathVariable String terminalId) {
        sshTerminalManager.close(terminalId);
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @GetMapping("/mcp-register-template")
    public Map<String, Object> mcpRegisterTemplate() {
        return Map.of("success", true, "data", Map.of());
    }

    @TokenForIgnore
    @PostMapping("/register-knowledge")
    public Map<String, Object> registerKnowledge(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/register-knowledge-global")
    public Map<String, Object> registerKnowledgeGlobal(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/unregister-knowledge-global")
    public Map<String, Object> unregisterKnowledgeGlobal(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @GetMapping("/knowledge-global-status")
    public Map<String, Object> knowledgeGlobalStatus() {
        return Map.of("success", true, "data", Map.of("registered", false));
    }

    @TokenForIgnore
    @GetMapping("/{terminalId}/workspace")
    public Map<String, Object> workspace(@PathVariable String terminalId,
                                          @RequestParam(defaultValue = ".") String path,
                                          @RequestParam(required = false) String search) {
        return Map.of("success", true, "data", List.of());
    }

    // ==================== 响应构建 ====================

    /**
     * 将 SSH 会话包装为前端 ITerminalSession 契约的响应 Map。
     */
    private Map<String, Object> toSshResponse(SshTerminalSessionManager.SshTerminalSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("terminal_id", session.terminalId);
        m.put("name", session.name);
        m.put("cwd", "");
        m.put("command", "ssh");
        m.put("args", List.of());
        m.put("cols", 80);
        m.put("rows", 24);
        m.put("created_at", session.updatedAt);
        m.put("updated_at", session.updatedAt);
        m.put("last_status", session.lastStatus);
        m.put("ssh_host_id", session.sshHostId);
        String scrollback = session.scrollbackB64();
        if (scrollback != null) {
            m.put("scrollback_b64", scrollback);
        }
        return m;
    }

    /**
     * 脱敏错误信息（截断 + 压缩空白）。
     */
    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) + "..." : compact;
    }
}