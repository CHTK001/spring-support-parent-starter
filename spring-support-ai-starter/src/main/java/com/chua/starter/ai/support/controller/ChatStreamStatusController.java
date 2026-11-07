package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.stream.ChatStreamRegistry;
import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话流式运行状态查询。
 *
 * <p>前端在打开会话时会先问一次「这个会话现在有没有正在跑的流」，据此决定是否订阅/重连。
 * 此前该接口不存在（{@code GET /v2/chat/stream/{id}/status} 直接 404），
 * 控制台每次都报「初始流状态检查失败」。</p>
 *
 * <p>单独建类而不是塞进 {@link ChatStreamController} / {@link ChatSendController}：
 * 那两个类上有 {@code @ApiReturnFormatIgnore}（为 SSE 而设），返回体会绕过统一信封，
 * 与本接口要返回的普通 JSON 不一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/chat")
@RequiredArgsConstructor
public class ChatStreamStatusController {

    /**
     * 流式注册表：按会话记录当前活跃的流
     */
    private final ChatStreamRegistry registry;

    /**
     * 查询指定会话的流式运行状态。
     *
     * <p>{@code bufferedEventCount} 恒为 0：本实现是「帧槽直接下发」，没有缓冲队列，
     * 不去编一个假数字。</p>
     *
     * @param sessionId 会话标识
     * @return isRunning / subscriberCount / bufferedEventCount
     */
    @GetMapping("/stream/{sessionId}/status")
    public ReturnResult<Map<String, Object>> status(@PathVariable("sessionId") String sessionId) {
        boolean running = sessionId != null && registry.get(sessionId) != null;
        Map<String, Object> result = new HashMap<>();
        result.put("isRunning", running);
        result.put("subscriberCount", running ? 1 : 0);
        result.put("bufferedEventCount", 0);
        return ReturnResult.ok(result);
    }
}
