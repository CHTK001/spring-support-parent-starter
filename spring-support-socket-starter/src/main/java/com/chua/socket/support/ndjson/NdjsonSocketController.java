package com.chua.socket.support.ndjson;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;

/**
 * NDJSON Socket 连接控制器
 * 提供 NDJSON（别名 jsonl）协议的连接端点。
 *
 * <p>端点形状与 {@code SseSocketController} 一致：
 * {@code POST /ndjson/connect} 建立长连接，另含 {@code /status}、
 * {@code /bind}、{@code /disconnect}。响应体为
 * {@code application/x-ndjson}：每行一个 JSON 帧
 * （{@code {"event":"..","data":..}}），帧间以 {@code \n} 分隔。</p>
 *
 * @since 2026-11-25
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/ndjson")
public class NdjsonSocketController {

    /**
     * ndjsonSocket会话模板
     */
    private final NdjsonSocketSessionTemplate ndjsonSocketSessionTemplate;

    /**
     * 构造器注入：必须和业务广播用的是同一个模板实例。
     *
     * @param ndjsonSocketSessionTemplate NDJSON 会话模板
     */
    public NdjsonSocketController(NdjsonSocketSessionTemplate ndjsonSocketSessionTemplate) {
        this.ndjsonSocketSessionTemplate = ndjsonSocketSessionTemplate;
    }

    /**
     * 建立 NDJSON 连接（POST）
     *
     * <p>用 POST 而不是 GET：token 可以放在 {@code x-oauth-token} / Authorization
     * 请求头里，不会出现在 URL、浏览器历史和网关访问日志中。</p>
     *
     * @param clientId 客户端标识（可选）
     * @param response HTTP 响应，用于关闭代理缓冲
     * @return ResponseBodyEmitter
     */
    @PostMapping(value = "/connect", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public ResponseBodyEmitter connect(@RequestParam(required = false) String clientId,
                                       HttpServletResponse response) {
        // nginx 等代理默认会缓冲响应；不关掉会“攒一批才吐”，看起来像不推送。
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        log.info("[NDJSON] 连接请求: clientId={}", clientId);
        return ndjsonSocketSessionTemplate.createConnection(clientId);
    }

    /**
     * 获取连接状态
     *
     * @return 连接状态信息
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return ndjsonSocketSessionTemplate.getConnectionStatus();
    }

    /**
     * 绑定用户
     *
     * @param userId   用户标识
     * @param clientId 客户端标识
     */
    @PostMapping("/bind")
    public void bindUser(@RequestParam String userId, @RequestParam String clientId) {
        ndjsonSocketSessionTemplate.bindUser(userId, clientId);
        log.info("[NDJSON] 用户绑定: userId={}, clientId={}", userId, clientId);
    }

    /**
     * 断开连接
     *
     * @param clientId 客户端标识
     */
    @PostMapping("/disconnect")
    public void disconnect(@RequestParam String clientId) {
        var session = ndjsonSocketSessionTemplate.getSession(clientId);
        if (session != null) {
            ndjsonSocketSessionTemplate.remove(clientId, session);
            log.info("[NDJSON] 主动断开连接: clientId={}", clientId);
        }
    }
}
