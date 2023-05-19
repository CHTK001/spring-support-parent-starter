package com.chua.shell.support.spring;

import com.chua.shell.support.command.ArthasCommand;
import com.chua.shell.support.command.SpringCommand;
import com.chua.shell.support.oshi.OshiCommand;
import com.chua.shell.support.shell.Shell;
import com.chua.shell.support.shell.ThreadManager;
import com.chua.shell.support.shell.WebShell;
import com.chua.shell.support.shell.mapping.DelegateCommand;
import com.chua.shell.support.shell.mapping.SystemCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author CH
 */
@ServerEndpoint("/channel/shell")
@Slf4j
public class ShellWebSocketHandler {
    private static final ConcurrentHashMap<Session, HandlerItem> HANDLER_ITEM_CONCURRENT_HASH_MAP = new ConcurrentHashMap<>();
    private final String prompt = "$ ";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Shell shell;

    private static final AtomicInteger count = new AtomicInteger(0);


    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) throws Exception {
        int cnt = count.incrementAndGet();
        log.info("有连接加入，当前连接数为：{}", cnt);

        HandlerItem handlerItem = new HandlerItem(session);
        HANDLER_ITEM_CONCURRENT_HASH_MAP.put(session, handlerItem);
        handlerItem.help();
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
        private WebShell shell;

        HandlerItem(Session session) throws IOException {
            this.session = session;
            send("");
        }


        @Override
        public void run() {
        }

        public void send(String data) {
            try {
                openShell();

                if (null == data) {
                    return;
                }

                if ("@close".equals(data)) {
                    ThreadManager.getInstance().closeThread(session.getId());
                    return;
                }

                if ("".equals(data)) {
                    session.getBasicRemote().sendText(prompt + " ");
                    return;
                }

                String execute = shell.handlerAnalysis(data, session);
                if (Strings.isNullOrEmpty(execute)) {
                    session.getBasicRemote().sendText("");
                    return;
                }
                session.getBasicRemote().sendText(execute);
            } catch (IOException ignored) {
            }
        }

        private void openShell() {
            if (null == shell) {
                synchronized (this) {
                    if (null == shell) {
                        shell = new WebShell(
                                new SystemCommand(),
                                new ArthasCommand(),
                                new DelegateCommand(),
                                new OshiCommand(),
                                new SpringCommand()
                        );
                    }
                }
            }
        }

        @Override
        public void close() throws Exception {
            ThreadManager.getInstance().closeThread(session.getId());
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }

        public void help() {
            send(null);
            try {
                session.getBasicRemote().sendText("@help" + objectMapper.writeValueAsString(shell.usageCommand()));
            } catch (IOException ignored) {
            }
        }
    }

    private static void sendText(Session session, String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

