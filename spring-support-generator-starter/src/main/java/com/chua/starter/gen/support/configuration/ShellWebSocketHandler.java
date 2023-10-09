package com.chua.starter.gen.support.configuration;

import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.IoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.chua.starter.gen.support.configuration.ShellWebSocketConfiguration.ADDRESS;

/**
 * @author CH
 */
@ServerEndpoint(value = "/channel/shell", configurator = ShellWebSocketConfiguration.class)
@Slf4j
public class ShellWebSocketHandler {
    private static final ConcurrentHashMap<Session, HandlerItem> HANDLER_ITEM_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    private final String prompt = "$ ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final AtomicInteger count = new AtomicInteger(0);

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) throws Exception {
        if(!check(session)) {
            sendText(session, "@auth 无权限访问");
            IoUtils.closeQuietly(session);
            return;
        }
        int cnt = count.incrementAndGet();
        log.info("有连接加入，当前连接数为：{}", cnt);

        HandlerItem handlerItem = new HandlerItem(session);
        HANDLER_ITEM_CONCURRENT_HASH_MAP.put(session, handlerItem);
    }

    /**
     * 检查
     *
     * @param session 一场
     * @return boolean
     */
    private boolean check(Session session) {
        Object o = session.getUserProperties().get(ADDRESS);
        if(o == null) {
            return false;
        }

        String ip = o.toString();
        if(NetUtils.getLocalHost().equals(ip) || NetUtils.LOCAL_HOST.equals(ip)) {
            return true;
        }

        return false;
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        HandlerItem handlerItem = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session);
        if(null == handlerItem) {
            return;
        }
        try {
            handlerItem.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        HANDLER_ITEM_CONCURRENT_HASH_MAP.remove(session);
        int cnt = count.decrementAndGet();
        log.info("有连接关闭，当前连接数为：{}", cnt);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        HandlerItem handlerItem = HANDLER_ITEM_CONCURRENT_HASH_MAP.get(session);
        if (Strings.isNullOrEmpty(message)) {
            this.sendCommand(handlerItem, "");
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("来自客户端的消息：{}", message);
        }
        this.sendCommand(handlerItem, message);
    }

    /**
     * 出现错误
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误：{}，Session ID： {}", error.getMessage(), session.getId());
        error.printStackTrace();
    }

    private void sendCommand(HandlerItem handlerItem, String data) throws Exception {
        handlerItem.send(data);
    }


    private class HandlerItem implements Runnable, AutoCloseable {
        private final Session session;

        HandlerItem(Session session) throws IOException {
            this.session = session;
            send("");
        }


        @Override
        public void run() {
        }

        public void send(String data) {

        }

        @Override
        public void close() throws Exception {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }

    }

    protected static void sendText(Session session, String msg) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

