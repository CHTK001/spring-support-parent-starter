package com.chua.shell.support.arthas;

import com.taobao.arthas.agent.attach.ArthasAgent;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * attach
 *
 * @author CH
 */

@Data
@Builder
public class Attach {

    private final AtomicBoolean status = new AtomicBoolean(false);
    private final ExecutorService main = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), new NamedThreadFactory("attach"));

    private String agentId;
    @Builder.Default
    private String appName = UUID.randomUUID().toString();

    private String ws;
    private String username;
    private String password;
    private String disabledCommands;
    /**
     * localConnectionNonAuth
     */
    @Builder.Default
    private boolean localConnectionNonAuth = true;
    /**
     * seconds
     */
    @Builder.Default
    private int sessionTimeout = 1800;
    @Builder.Default
    private int telnetPort = 13658;
    @Builder.Default
    private int httpPort = 18563;
    @Builder.Default
    private String ip = "127.0.0.1";

    public void start() {
        Map<String, String> tpl = new LinkedHashMap<>();
        Class<? extends Attach> aClass = this.getClass();
        for (Field declaredField : aClass.getDeclaredFields()) {
            declaredField.setAccessible(true);
            Object o = null;
            try {
                o = declaredField.get(this);
            } catch (IllegalAccessException ignored) {
            }

            if (null == o) {
                continue;
            }

            tpl.put("arthas." + declaredField.getName(), o.toString());
        }

        try {
            if (tpl.isEmpty()) {
                ArthasAgent.attach();
            } else {
                ArthasAgent.attach(tpl);
            }
            status.set(true);
            main.execute(() -> {
                while (status.get()) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }

    }


    public void stop() {
        main.shutdownNow();
    }


    public static void main(String[] args) {
        Attach.builder().build().start();
    }
}
