package com.chua.starter.sse.support.socket;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE Socket 连接控制器
 * 提供 SSE 协议的连接端点
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-24
 */
@Slf4j
@RestController
@RequestMapping("/sse")
@ConditionalOnBean(SseSocketSessionTemplate.class)
public class SseSocketController {

    private static final Logger log = LoggerFactory.getLogger(SseSocketController.class);

    @Autowired
    private SseSocketSessionTemplate sseSocketSessionTemplate;

    /**
     * 建立 SSE 连接
     *
     * @param clientId 客户端ID（可选）
     * @return SseEmitter
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
     * @param userId   用户ID
     * @param clientId 客户端ID
     */
    @PostMapping("/bind")
    public void bindUser(@RequestParam String userId, @RequestParam String clientId) {
        sseSocketSessionTemplate.bindUser(userId, clientId);
        log.info("[SSE] 用户绑定: userId={}, clientId={}", userId, clientId);
    }

    /**
     * 断开连接
     *
     * @param clientId 客户端ID
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
