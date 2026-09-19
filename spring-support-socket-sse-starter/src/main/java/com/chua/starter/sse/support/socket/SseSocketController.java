package com.chua.starter.sse.support.socket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * @版本 1.0.0
 * @since 2024-12-24
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/sse")
@ConditionalOnBean(SseSocketSessionTemplate.class)
public class SseSocketController {

    @Autowired
    /** sseSocket会话模板 */
    private SseSocketSessionTemplate sseSocketSessionTemplate;

    /**
     * 建立 SSE 连接
     *
     * @param clientId 客户端标识（可选）
     * @return SseEmitter
     * @param false false
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(required = false) String clientId) {
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
