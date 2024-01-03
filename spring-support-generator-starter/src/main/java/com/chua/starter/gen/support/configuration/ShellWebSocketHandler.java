package com.chua.starter.gen.support.configuration;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.service.SysGenService;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author CH
 */
@ServerEndpoint(value = "/channel/shell/{channelId}", configurator = ShellWebSocketConfiguration.class)
@Slf4j
public class ShellWebSocketHandler {
    private static final ConcurrentHashMap<Session, HandlerItem> HANDLER_ITEM_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    private static final AtomicInteger count = new AtomicInteger(0);

    private SysGenService sysGenService;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("channelId") String channelId) throws Exception {
        SysGen sysGen = getSysGen(channelId);
        if(!check(session, sysGen)) {
            sendText(session, "@auth 无权限访问");
            IoUtils.closeQuietly(session);
            return;
        }
        int cnt = count.incrementAndGet();
        log.info("有连接加入，当前连接数为：{}", cnt);

        HandlerItem handlerItem = new HandlerItem(session, sysGen);
        HANDLER_ITEM_CONCURRENT_HASH_MAP.put(session, handlerItem);
    }

    /**
     * 检查
     *
     * @param session   session
     * @param sysGen sysGen
     * @return boolean
     */
    private synchronized boolean check(Session session, SysGen sysGen) {
        return sysGen.getCreateBy().equals(session.getUserProperties().get(RequestUtils.SESSION_USERNAME));
    }


    /**
     * 获取系统生成
     *
     * @param channelId 通道id
     * @return {@link SysGen}
     */
    private SysGen getSysGen(String channelId) {
        if(null == sysGenService) {
            sysGenService = SpringBeanUtils.getApplicationContext().getBean(SysGenService.class);
        }
        return sysGenService.getById(channelId);
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
        private final SysGen sysGen;
        private final com.chua.common.support.session.Session sshSession;

        HandlerItem(Session session, SysGen sysGen) throws IOException {
            this.session = session;
            this.sysGen = sysGen;
            this.sshSession = ServiceProvider.of(com.chua.common.support.session.Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions());
            this.sshSession.setListener(s -> {
                if(session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText(s);
                    } catch (Exception e) {
                        try {
                            session.getBasicRemote().sendText(s);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });
        }


        @Override
        public void run() {
        }

        public void send(String data) {
            try {
                sshSession.executeQuery(data, null);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void close() throws Exception {
            try {
                session.close();
            } catch (IOException ignored) {
            }
            try {
                sshSession.close();
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

