package com.chua.starter.sync.support.pojo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

/**
 * 客户端信息。
 *
 * @since 2024/12/05
 * @author CH
 */
public class ClientInfo implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /** 客户端 ID（sessionId） */
    private String clientId;

    /** 客户端应用名称 */
    private String clientApplicationName;

    /** 客户端应用环境（如 dev、test、prod） */
    private String clientApplicationActive;

    /** 客户端实例 ID */
    private String clientInstanceId;

    /** 客户端上下文路径 */
    private String clientContextPath;

    /** 客户端服务地址（http://ip:port/contextPath） */
    private String clientUrl;

    /** 客户端 Actuator 地址（/actuator） */
    private String clientActuatorPath;

    /** 客户端 IP 地址 */
    private String clientIpAddress;

    /** 客户端端口号 */
    private int clientPort;

    /** 客户端主机名 */
    private String clientHostname;

    /** 客户端操作系统名称 */
    private String clientOsName;

    /** 客户端 Java 版本 */
    private String clientJavaVersion;

    /** 客户端进程 ID */
    private long clientPid;

    /** 客户端启动时间（毫秒时间戳） */
    private long clientStartTime;

    /** 客户端运行时长（毫秒） */
    private long clientUptime;

    /** 客户端注册时间（毫秒时间戳） */
    private long clientRegisterTime;

    /** 客户端最后心跳时间（毫秒时间戳） */
    private long clientLastHeartbeatTime;

    /** 客户端是否在线 */
    private boolean clientOnline;

    /** 客户端扩展元数据（系统信息、自定义属性等） */
    private Map<String, Object> clientMetadata;

    /** 客户端支持的功能列表 */
    private String[] clientCapabilities;

    /** 获取ClientId */
    public String getClientId() {
        return clientId;
    }

    /** 设置ClientId */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /** 获取ClientApplicationName */
    public String getClientApplicationName() {
        return clientApplicationName;
    }

    /** 设置ClientApplicationName */
    public void setClientApplicationName(String clientApplicationName) {
        this.clientApplicationName = clientApplicationName;
    }

    /** 获取ClientApplicationActive */
    public String getClientApplicationActive() {
        return clientApplicationActive;
    }

    /** 设置ClientApplicationActive */
    public void setClientApplicationActive(String clientApplicationActive) {
        this.clientApplicationActive = clientApplicationActive;
    }

    /** 获取ClientInstanceId */
    public String getClientInstanceId() {
        return clientInstanceId;
    }

    /** 设置ClientInstanceId */
    public void setClientInstanceId(String clientInstanceId) {
        this.clientInstanceId = clientInstanceId;
    }

    /** 获取ClientContextPath */
    public String getClientContextPath() {
        return clientContextPath;
    }

    /** 设置ClientContextPath */
    public void setClientContextPath(String clientContextPath) {
        this.clientContextPath = clientContextPath;
    }

    /** 获取ClientUrl */
    public String getClientUrl() {
        return clientUrl;
    }

    /** 设置ClientUrl */
    public void setClientUrl(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    /** 获取ClientActuatorPath */
    public String getClientActuatorPath() {
        return clientActuatorPath;
    }

    /** 设置ClientActuatorPath */
    public void setClientActuatorPath(String clientActuatorPath) {
        this.clientActuatorPath = clientActuatorPath;
    }

    /** 获取ClientIpAddress */
    public String getClientIpAddress() {
        return clientIpAddress;
    }

    /** 设置ClientIpAddress */
    public void setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
    }

    /** 获取ClientPort */
    public int getClientPort() {
        return clientPort;
    }

    /** 设置ClientPort */
    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    /** 获取ClientHostname */
    public String getClientHostname() {
        return clientHostname;
    }

    /** 设置ClientHostname */
    public void setClientHostname(String clientHostname) {
        this.clientHostname = clientHostname;
    }

    /** 获取ClientOsName */
    public String getClientOsName() {
        return clientOsName;
    }

    /** 设置ClientOsName */
    public void setClientOsName(String clientOsName) {
        this.clientOsName = clientOsName;
    }

    /** 获取ClientJavaVersion */
    public String getClientJavaVersion() {
        return clientJavaVersion;
    }

    /** 设置ClientJavaVersion */
    public void setClientJavaVersion(String clientJavaVersion) {
        this.clientJavaVersion = clientJavaVersion;
    }

    /** 获取ClientPid */
    public long getClientPid() {
        return clientPid;
    }

    /** 设置ClientPid */
    public void setClientPid(long clientPid) {
        this.clientPid = clientPid;
    }

    /** 获取Client开始Time */
    public long getClientStartTime() {
        return clientStartTime;
    }

    /** 设置Client开始Time */
    public void setClientStartTime(long clientStartTime) {
        this.clientStartTime = clientStartTime;
    }

    /** 获取ClientUptime */
    public long getClientUptime() {
        return clientUptime;
    }

    /** 设置ClientUptime */
    public void setClientUptime(long clientUptime) {
        this.clientUptime = clientUptime;
    }

    /** 获取Client注册Time */
    public long getClientRegisterTime() {
        return clientRegisterTime;
    }

    /** 设置Client注册Time */
    public void setClientRegisterTime(long clientRegisterTime) {
        this.clientRegisterTime = clientRegisterTime;
    }

    /** 获取ClientLastHeartbeatTime */
    public long getClientLastHeartbeatTime() {
        return clientLastHeartbeatTime;
    }

    /** 设置ClientLastHeartbeatTime */
    public void setClientLastHeartbeatTime(long clientLastHeartbeatTime) {
        this.clientLastHeartbeatTime = clientLastHeartbeatTime;
    }

    /** 是否ClientOnline */
    public boolean isClientOnline() {
        return clientOnline;
    }

    /** 设置ClientOnline */
    public void setClientOnline(boolean clientOnline) {
        this.clientOnline = clientOnline;
    }

    /** 获取ClientMetadata */
    public Map<String, Object> getClientMetadata() {
        return clientMetadata;
    }

    /** 设置ClientMetadata */
    public void setClientMetadata(Map<String, Object> clientMetadata) {
        this.clientMetadata = clientMetadata;
    }

    /** 获取ClientCapabilities */
    public String[] getClientCapabilities() {
        return clientCapabilities;
    }

    /** 设置ClientCapabilities */
    public void setClientCapabilities(String[] clientCapabilities) {
        this.clientCapabilities = clientCapabilities;
    }

    @Override
    /** ToString */
    public String toString() {
        return "ClientInfo{" +
                "clientId='" + clientId + '\'' +
                ", clientApplicationName='" + clientApplicationName + '\'' +
                ", clientIpAddress='" + clientIpAddress + '\'' +
                ", clientPort=" + clientPort +
                ", clientOnline=" + clientOnline +
                '}';
    }
}
