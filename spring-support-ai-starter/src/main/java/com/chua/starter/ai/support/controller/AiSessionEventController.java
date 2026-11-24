// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.service.AiSessionEventBroadcaster;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 会话事件控制器（SSE 订阅入口）。
 *
 * <p>前端标签页经 {@code GET /v2/events/sessions} 建立长连接，接收本用户范围内
 * 的会话生命周期与聊天流状态事件（创建/更新/删除、流开始/结束等）。
 * 事件产出与连接管理在 {@link AiSessionEventBroadcaster}；本类只做身份解析与订阅转发。</p>
 *
 * <p>前端契约：事件为 {@code data:} 裸帧 JSON
 * （{@code {type, userId, sessionId, timestamp, payload?, source?}}），
 * {@code X-Client-Id} 请求头带发起方标签页标识，原样回填到事件的 {@code source}
 * 供前端「忽略自身发起」判断。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/events")
@RequiredArgsConstructor
public class AiSessionEventController {

    /**
     * 前端标签页标识请求头（与前端 http 客户端全局注入的头同名同义）
     */
    private static final String HEADER_CLIENT_ID = "X-Client-Id";

    /**
     * 会话事件广播器（连接登记、心跳、按用户推送）
     */
    private final AiSessionEventBroadcaster broadcaster;

    /**
     * 建立当前用户的会话事件 SSE 连接。
     *
     * <p>心跳、超时收流、死连接回收全部由广播器负责；这里不自带定时器，
     * 避免「重连一次泄漏一个心跳任务」的旧问题。</p>
     *
     * @param clientId 前端标签页标识，可为空（仅记录，便于排查多标签页归属）
     * @return SSE 事件流
     */
    @GetMapping(value = "/sessions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sessions(@RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId) {
        // 匿名/身份解析失败也允许挂连接：落匿名桶只收心跳，不推业务事件
        String userId;
        try {
            userId = AiUserContext.currentUserId();
        } catch (Exception e) {
            log.debug("[session-events] 订阅时身份解析失败，按匿名处理: {}", e.getMessage());
            userId = null;
        }
        return broadcaster.subscribe(userId, clientId);
    }
}
