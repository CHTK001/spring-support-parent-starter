package com.chua.starter.server.support.controller;

import com.chua.starter.server.support.sse.ReleaseSseHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 发布管理全局 SSE 连接。
 *
 * <p>发布管理只保留这一条 SSE 长连接，与主机资产的 SSE 完全独立。
 * 发布/项目页面挂载时用固定 {@code clientId} 建连，任务进度、实时日志、
 * 操作日志都经它推送，页面内部不再各自轮询后端。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Tag(name = "发布管理-全局SSE", description = "发布管理页面的唯一 SSE 长连接")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ms/server/release")
public class ReleaseSseController {

    /**
     * 发布管理 SSE 通道
     */
    private final ReleaseSseHub sseHub;

    /**
     * 建立发布管理全局 SSE 连接。
     *
     * @param clientId 前端生成的客户端标识
     * @return SSE 发射器
     */
    @Operation(summary = "发布管理全局 SSE 连接", description = "发布管理唯一长连接；建连后推送 connected 事件")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String clientId) {
        return sseHub.connect(clientId);
    }
}
