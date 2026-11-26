// [P3C A20 豁免] A20/A30/A31: traceId 生成含 "-" 剥离等非业务字面量，与 MdcHandlerFilter 同口径，豁免。
package com.chua.starter.common.support.log;

import com.chua.starter.logger.constant.MdcConstant;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket MDC 链路装饰器：为每条 WS 消息注入独立的 traceId。
 * <p>
 * WS 消息回调运行在容器自己的 I/O 线程上，不经过 {@link MdcHandlerFilter}，
 * 线程上没有请求级 MDC 上下文。本装饰器在每条消息入口生成新的 traceId
 * （与 HTTP「每请求一个 traceId」的口径对齐），消息处理结束后还原现场，
 * 不污染容器复用线程的下一次回调。
 * </p>
 * <p>
 * handler 内部若再提交异步任务，请配合 {@link MdcWrap#wrap(Runnable)} /
 * {@link MdcWrap#decorate(java.util.concurrent.ExecutorService)} 使用：
 * 本装饰器已把 traceId 放进当前线程 MDC，wrap 快照会自动把它带给异步线程。
 * </p>
 * <h3>用法</h3>
 * <pre>
 * registry.addHandler(MdcWebSocketHandlerDecorator.wrap(handler), "/ws/xxx")
 *         .setAllowedOriginPatterns("*");
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
public class MdcWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    /**
     * 构造函数
     * @param delegate 被装饰的 WS 处理器
     */
    public MdcWebSocketHandlerDecorator(WebSocketHandler delegate) {
        super(delegate);
    }

    /**
     * 包装 WS 处理器：此后每条消息回调都带独立 traceId。
     * @param delegate 被装饰的 WS 处理器
     * @return 带 MDC 注入能力的 WS 处理器
     */
    public static WebSocketHandler wrap(WebSocketHandler delegate) {
        return new MdcWebSocketHandlerDecorator(delegate);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        withNewTrace(() -> super.handleMessage(session, message));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        withNewTrace(() -> super.afterConnectionEstablished(session));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        withNewTrace(() -> super.handleTransportError(session, exception));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        withNewTrace(() -> super.afterConnectionClosed(session, closeStatus));
    }

    /**
     * 在新的 traceId 下执行回调，结束后还原现场。
     * @param task WS 回调任务
     * @throws Exception 回调透传的异常
     */
    private void withNewTrace(ThrowingRunnable task) throws Exception {
        Map<String, String> backup = MDC.getCopyOfContextMap();
        try {
            MDC.put(MdcConstant.TRACE_ID, UUID.randomUUID().toString().replace("-", ""));
            task.run();
        } finally {
            if (backup == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(backup);
            }
        }
    }

    /**
     * 可抛异常的任务
     */
    @FunctionalInterface
    private interface ThrowingRunnable {

        /**
         * 执行任务
         * @throws Exception 任务抛出的异常
         */
        void run() throws Exception;
    }
}
