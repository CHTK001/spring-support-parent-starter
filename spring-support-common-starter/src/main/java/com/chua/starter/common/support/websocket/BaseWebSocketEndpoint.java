package com.chua.starter.common.support.websocket;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基本web套接字端点
 *
 * @author CH
 * @since 2023/09/12
 */
@Slf4j
public abstract class BaseWebSocketEndpoint {
    private static final ConcurrentHashMap<Session, HandlerItem> HANDLER_ITEM_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final AtomicInteger count = new AtomicInteger(0);


    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "event") String event) throws Exception {
        if(StringUtils.isEmpty(event)) {
            session.close();
            return;
        }

        int cnt = count.incrementAndGet();
        log.info("有连接加入，当前连接数为：{}", cnt);
        HandlerItem handlerItem = new HandlerItem(session, event);
        HANDLER_ITEM_CONCURRENT_HASH_MAP.put(session, handlerItem);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        HandlerItem handlerItem = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session);
        try {
            handlerItem.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        HANDLER_ITEM_CONCURRENT_HASH_MAP.remove(session);
        int cnt = count.decrementAndGet();
        if (log.isTraceEnabled()) {
            log.trace("有连接关闭，当前连接数为：{}", cnt);
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
    }

    /**
     * 出现错误时
     * 出现错误
     *
     * @param session 一场
     * @param error   错误
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误：{}，Session ID： {}", error.getMessage(), session.getId());
        error.printStackTrace();
    }



    private class HandlerItem implements Runnable, AutoCloseable {
        private final Session session;
        private final String event;

        HandlerItem(Session session, String event) throws IOException {
            this.session = session;
            this.event = event;
        }


        @Override
        public void run() {
        }

        @Override
        public void close() throws Exception {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }

        public boolean isMatch(String event) {
            return event.equalsIgnoreCase(this.event);
        }
    }
    private static void sendText(Session session, ChatMessage msg) {
        try {
            Collection<HandlerItem> values = HANDLER_ITEM_CONCURRENT_HASH_MAP.values();
            for (HandlerItem value : values) {
                if(value.isMatch(msg.event())) {
                    session.getBasicRemote().sendText(Json.toJson(msg));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
