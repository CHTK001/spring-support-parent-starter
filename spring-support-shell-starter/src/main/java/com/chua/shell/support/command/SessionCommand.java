package com.chua.shell.support.command;

import com.chua.shell.support.shell.ShellMapping;
import com.chua.shell.support.shell.ThreadManager;

import javax.websocket.Session;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author CH
 */
public class SessionCommand {

    /**
     * help
     *
     * @return help
     */
    @ShellMapping(value = {"jmem"}, describe = "测试实时时间")
    public String spring(
            Session session
    ) {

        Thread thread = ThreadManager.getInstance().createThread(session.getId(), new Runnable() {
            @Override
            public void run() {
                if (session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText("@flushStart");
                    } catch (IOException ignored) {
                    }

                }

                try {
                    for (int i = 0; i < 10; i++) {
                        if (Thread.interrupted()) {
                            break;
                        }
                        if (session.isOpen()) {
                            session.getBasicRemote().sendText("@flush " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        }
                        Thread.sleep(1000);
                    }
                } catch (Throwable ignored) {
                }
                if (session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText("@flushEnd");
                    } catch (IOException ignored) {
                    }
                }
            }
        });
        thread.start();
        return "";
    }
}
