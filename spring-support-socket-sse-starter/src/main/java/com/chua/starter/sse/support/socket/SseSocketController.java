package com.chua.starter.sse.support.socket;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE Socket 连接控制器
 * 提供 SSE 协议的连接端点
 *
 * @since 2024-12-24
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/sse")
public class SseSocketController {

    /**
     * sseSocket会话模板
     */
    private final SseSocketSessionTemplate sseSocketSessionTemplate;

    /**
     * 构造器注入：必须和业务广播用的是同一个模板实例。
     *
     * @param sseSocketSessionTemplate SSE 会话模板
     */
    public SseSocketController(SseSocketSessionTemplate sseSocketSessionTemplate) {
        this.sseSocketSessionTemplate = sseSocketSessionTemplate;
    }

    /**
     * 建立 SSE 连接（POST）
     *
     * <p>用 POST 而不是 GET：token 可以放在 {@code x-oauth-token} / Authorization
     * 请求头里，不会出现在 URL、浏览器历史和网关访问日志中。</p>
     *
     * @param clientId 客户端标识（可选）
     * @param response HTTP 响应，用于关闭代理缓冲
     * @return SseEmitter
     */
    @PostMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(required = false) String clientId,
                              HttpServletResponse response) {
        // nginx 默认会缓冲 text/event-stream；不关掉会“攒一批才吐”，看起来像不推送。
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        log.info("[SSE] 连接请求: clientId={}", clientId);
        return sseSocketSessionTemplate.createConnection(clientId);
    }

    /**
     * 获取连接状态
     *
     * @return 连接状态信息
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return sseSocketSessionTemplate.getConnectionStatus();
    }

    /**
     * 绑定用户
     *
     * @param userId   用户标识
     * @param clientId 客户端标识
     */
    @PostMapping("/bind")
    public void bindUser(@RequestParam String userId, @RequestParam String clientId) {
        sseSocketSessionTemplate.bindUser(userId, clientId);
        log.info("[SSE] 用户绑定: userId={}, clientId={}", userId, clientId);
    }

    /**
     * 断开连接
     *
     * @param clientId 客户端标识
     */
    @PostMapping("/disconnect")
    public void disconnect(@RequestParam String clientId) {
        var session = sseSocketSessionTemplate.getSession(clientId);
        if (session != null) {
            sseSocketSessionTemplate.remove(clientId, session);
            log.info("[SSE] 主动断开连接: clientId={}", clientId);
        }
    }
}
