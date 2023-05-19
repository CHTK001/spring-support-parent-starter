package com.chua.shell.support.shell;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author CH
 */
public class ThreadManager {
    private static final ThreadManager BEAN_MANAGER = new ThreadManager();

    private static final Map<String, List<Thread>> CACHE = new ConcurrentHashMap<>();

    public static ThreadManager getInstance() {
        return BEAN_MANAGER;
    }


    public Thread createThread(String name, Runnable runnable) {
        Thread thread = new Thread(runnable);
        CACHE.computeIfAbsent(name, it -> new LinkedList<>()).add(thread);
        return thread;
    }


    public void closeThread(String name) {
        if (!CACHE.containsKey(name)) {
            return;
        }

        List<Thread> threads = CACHE.get(name);
        for (Thread thread : threads) {
            try {
                thread.interrupt();
            } catch (Exception ignored) {
            }
        }

        CACHE.remove(name);
    }


}
