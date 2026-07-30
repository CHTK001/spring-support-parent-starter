package com.chua.starter.sync.support;

/**
 * 同步会话，封装客户端连接信息。
 *
 * @author CH
 * @since 4.0.0.41
 */
public class SyncSession {

    private final String sessionId;

    private String clientIp;

    private int clientPort;

    private long connectTime;

    public SyncSession(String sessionId) {
        this.sessionId = sessionId;
        this.connectTime = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public long getConnectTime() {
        return connectTime;
    }

    public void setConnectTime(long connectTime) {
        this.connectTime = connectTime;
    }
}