package com.chua.starter.account.manage.event;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 账号事件 SSE 端点。
 *
 * <p>账号页共用一条全局连接：前端拿到 emitter 后按
 * {@code subscribe("account", event, cb)} 分发业务事件。GET / POST 都开放，
 * 兼容原生 EventSource（GET）与前端 SSE 客户端默认的 POST + token 头。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/accounts/events")
@RequiredArgsConstructor
public class AccountEventController {

    /**
     * 账号事件服务
     */
    private final AccountEventSseService accountEventSseService;

    /**
     * 订阅账号事件流（GET，兼容原生 EventSource）。
     *
     * @return SSE emitter
     */
    @GetMapping
    public SseEmitter subscribeByGet() {
        return accountEventSseService.subscribe();
    }

    /**
     * 订阅账号事件流（POST，前端 SSE 客户端默认方式，token 走请求头）。
     *
     * @return SSE emitter
     */
    @PostMapping
    public SseEmitter subscribeByPost() {
        return accountEventSseService.subscribe();
    }
}
