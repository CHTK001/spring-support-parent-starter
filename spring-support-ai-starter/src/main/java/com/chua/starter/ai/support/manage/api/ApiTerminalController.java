package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.ssh.SshTerminalSessionManager;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.manage.store.SysAiSshHostStore;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.common.support.utils.IdUtils;
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
 * （MINA SSHD 伪终端），输出经 {@code terminal.output} WS 事件推给前端 xtermview；</li>
 *   <li>未携带时登记为本地占位终端（真实 伪终端 由宿主进程管理）。</li>
 * </ul>
 *
 * <p>终端是登录用户手上的一条实时 Shell，因此本控制器全部端点都要带会话，
 * 且按 {@link SshTerminalSessionManager} 记录的归属分流：本人可读写；
 * 管理员可列出与查看他人会话，但输入、调尺寸、关闭一律 403；他人一律 403。
 * 创建时使用的 {@code ssh_host_id} 也须经主机簿归属校验，避免借他人主机凭据开终端。</p>
 *
 * @author CH
 * @since 4.0.0
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

    @GetMapping
    /**
     * 列表。
     * @return 列表的结果
     */
    public Map<String, Object> list() {
        return Map.of("success", true, "data", sshTerminalManager.listVisible().stream()
                .map(this::toSummary).toList());
    }

    @GetMapping("/list-ssh")
    /**
     * 列表ssh。
     * @return 列表ssh的结果
     */
    public Map<String, Object> listSsh() {
        return Map.of("success", true, "data", List.of());
    }

    @PostMapping
    /**
     * 创建。
     * @param body 主体
     * @return 创建的结果
     */
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
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[Terminal] SSH 终端创建失败: {}", e.getMessage());
                return Map.of("success", false, "error", "SSH 终端创建失败: " + sanitize(e.getMessage()));
            }
        }
 // 无 ssh_主机_标识：登记为本人占位终端
        String terminalId = IdUtils.uuidv7();
        SshTerminalSessionManager.SshTerminalSession session = sshTerminalManager.registerLocalPlaceholder(terminalId, "terminal");
        return Map.of("success", true, "data", toSshResponse(session));
    }

    @GetMapping("/{terminalId}")
    /**
     * 获取。
     * @param terminalId terminalid
     * @return 获取的结果
     */
    public Map<String, Object> get(@PathVariable String terminalId) {
        SshTerminalSessionManager.SshTerminalSession session = sshTerminalManager.requireOwned(terminalId, false);
        return Map.of("success", true, "data", toSshResponse(session));
    }

    @PostMapping("/{terminalId}/input")
    /**
     * 输入。
     * @param terminalId terminalid
     * @param body 主体
     * @return 输入的结果
     */
    public Map<String, Object> input(@PathVariable String terminalId, @RequestBody Map<String, String> body) {
        String dataB64 = body.get("data_b64");
        sshTerminalManager.requireOwned(terminalId, true);
        boolean ok = dataB64 != null && sshTerminalManager.input(terminalId, dataB64);
        return Map.of("success", true, "data", ok);
    }

    @PostMapping("/{terminalId}/resize")
    /**
     * resize。
     * @param terminalId terminalid
     * @param body 主体
     * @return resize的结果
     */
    public Map<String, Object> resize(@PathVariable String terminalId, @RequestBody Map<String, Object> body) {
        int cols = body.get("cols") instanceof Number n ? n.intValue() : 80;
        int rows = body.get("rows") instanceof Number m ? m.intValue() : 24;
        sshTerminalManager.requireOwned(terminalId, true);
        boolean ok = sshTerminalManager.resize(terminalId, cols, rows);
        return Map.of("success", true, "data", ok);
    }

    @PostMapping("/{terminalId}/kill")
    /**
     * kill。
     * @param terminalId terminalid
     * @return kill的结果
     */
    public Map<String, Object> kill(@PathVariable String terminalId) {
        sshTerminalManager.requireOwned(terminalId, true);
        sshTerminalManager.close(terminalId);
        return Map.of("success", true, "data", true);
    }

    @PostMapping("/{terminalId}/relaunch")
    /**
     * relaunch。
     * @param terminalId terminalid
     * @return relaunch的结果
     */
    public Map<String, Object> relaunch(@PathVariable String terminalId) {
        sshTerminalManager.requireOwned(terminalId, true);
        return Map.of("success", true, "data", true);
    }

    @PostMapping("/{terminalId}/relaunch-shell")
    /**
     * relaunchshell。
     * @param terminalId terminalid
     * @return relaunchShell的结果
     */
    public Map<String, Object> relaunchShell(@PathVariable String terminalId) {
        sshTerminalManager.requireOwned(terminalId, true);
        return Map.of("success", true, "data", true);
    }

    @DeleteMapping("/{terminalId}")
    /**
     * 删除。
     * @param terminalId terminalid
     * @return 删除的结果
     */
    public Map<String, Object> delete(@PathVariable String terminalId) {
        sshTerminalManager.requireOwned(terminalId, true);
        sshTerminalManager.close(terminalId);
        return Map.of("success", true, "data", true);
    }

    @GetMapping("/mcp-register-template")
    /**
     * mcp注册template。
     * @return mcp注册template的结果
     */
    public Map<String, Object> mcpRegisterTemplate() {
        return Map.of("success", true, "data", Map.of());
    }

    @PostMapping("/register-knowledge")
    /**
     * 注册知识。
     * @param body 主体
     * @return 注册知识的结果
     */
    public Map<String, Object> registerKnowledge(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @Permission("admin")
    @PostMapping("/register-knowledge-global")
    /**
     * 注册知识全局。
     * @param body 主体
     * @return 注册知识全局的结果
     */
    public Map<String, Object> registerKnowledgeGlobal(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @Permission("admin")
    @PostMapping("/unregister-knowledge-global")
    /**
     * 注销知识全局。
     * @param body 主体
     * @return 注销知识全局的结果
     */
    public Map<String, Object> unregisterKnowledgeGlobal(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @GetMapping("/knowledge-global-status")
    /**
     * 知识全局状态。
     * @return 知识全局状态的结果
     */
    public Map<String, Object> knowledgeGlobalStatus() {
        return Map.of("success", true, "data", Map.of("registered", false));
    }

    @GetMapping("/{terminalId}/workspace")
    public Map<String, Object> workspace(@PathVariable String terminalId,
                                          @RequestParam(defaultValue = ".") String path,
                                          @RequestParam(required = false) String search) {
        sshTerminalManager.requireOwned(terminalId, false);
        return Map.of("success", true, "data", List.of());
    }

    // ==================== 响应构建 ====================

    /**
     * 会话摘要（列表用，不带 scrollback）。
     * @param session 会话
     * @return toSummary的结果
     */
    private Map<String, Object> toSummary(SshTerminalSessionManager.SshTerminalSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("terminal_id", session.terminalId);
        m.put("name", session.name);
        m.put("cwd", "");
        m.put("command", session.sshHostId == null ? "local" : "ssh");
        m.put("args", List.of());
        m.put("cols", 80);
        m.put("rows", 24);
        m.put("created_at", session.updatedAt);
        m.put("updated_at", session.updatedAt);
        m.put("last_status", session.lastStatus);
        m.put("ssh_host_id", session.sshHostId);
        return m;
    }

    /**
     * 将 SSH 会话包装为前端 iterminal会话 契约的响应 映射。
     * @param session 会话
     * @return 转为ssh响应的结果
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
     * @param message 消息
     * @return sanitize的结果
     */
    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) + "..." : compact;
    }
}
