package com.chua.starter.server.support.controller;

import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.sse.HostAssetsSseHub;
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
 * 主机资产全局 SSE 连接
 *
 * <p>整个主机资产页面只保留这一条 SSE 长连接：连接建立后，底部资源摘要
 * （句柄数 / inode）与邮箱新邮件都经它推送。前端用固定 {@code clientId} 建连，
 * 再通过订阅接口声明关注对象；其它页面不再单独建立 SSE。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Tag(name = "服务器资产-全局SSE", description = "主机资产页面的唯一 SSE 长连接")
@RestController
@RequiredArgsConstructor
@RequestMapping(ServerConstants.API_PREFIX + "/host/assets")
public class HostAssetsSseController {

    /**
     * 主机资产 SSE 通道
     */
    private final HostAssetsSseHub sseHub;

    /**
     * 建立主机资产全局 SSE 连接。
     *
     * @param clientId 前端生成的客户端标识
     * @return SSE 发射器
     */
    @Operation(summary = "主机资产全局 SSE 连接", description = "整个主机资产页面唯一长连接；建连后推送 connected 事件")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String clientId) {
        return sseHub.connect(clientId);
    }
}
