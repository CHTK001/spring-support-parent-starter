package com.chua.starter.panel.cache;

import com.jcraft.jsch.Session;

import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH Session 注册表，供 Nginx / Project 等多个 Service 共享。
 *
 * @author CH
 */
public class PanelSshSessionRegistry {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, Session> getSessions() {
        return sessions;
    }

    public Session get(String connectionId) {
        return sessions.get(connectionId);
    }

    public void put(String connectionId, Session session) {
        sessions.put(connectionId, session);
    }

    public Session remove(String connectionId) {
        return sessions.remove(connectionId);
    }
}
