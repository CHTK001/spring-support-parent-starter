package com.chua.starter.sync.support.pojo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

/**
 * 客户端信息。
 *
 * @author CH
 * @since 2024/12/05
 */
public class ClientInfo implements Serializable {

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientApplicationName() {
        return clientApplicationName;
    }

    public void setClientApplicationName(String clientApplicationName) {
        this.clientApplicationName = clientApplicationName;
    }

    public String getClientApplicationActive() {
        return clientApplicationActive;
    }

    public void setClientApplicationActive(String clientApplicationActive) {
        this.clientApplicationActive = clientApplicationActive;
    }

    public String getClientInstanceId() {
        return clientInstanceId;
    }

    public void setClientInstanceId(String clientInstanceId) {
        this.clientInstanceId = clientInstanceId;
    }

    public String getClientContextPath() {
        return clientContextPath;
    }

    public void setClientContextPath(String clientContextPath) {
        this.clientContextPath = clientContextPath;
    }

    public String getClientUrl() {
        return clientUrl;
    }

    public void setClientUrl(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    public String getClientActuatorPath() {
        return clientActuatorPath;
    }

    public void setClientActuatorPath(String clientActuatorPath) {
        this.clientActuatorPath = clientActuatorPath;
    }

    public String getClientIpAddress() {
        return clientIpAddress;
    }

    public void setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    public String getClientHostname() {
        return clientHostname;
    }

    public void setClientHostname(String clientHostname) {
        this.clientHostname = clientHostname;
    }

    public String getClientOsName() {
        return clientOsName;
    }

    public void setClientOsName(String clientOsName) {
        this.clientOsName = clientOsName;
    }

    public String getClientJavaVersion() {
        return clientJavaVersion;
    }

    public void setClientJavaVersion(String clientJavaVersion) {
        this.clientJavaVersion = clientJavaVersion;
    }

    public long getClientPid() {
        return clientPid;
    }

    public void setClientPid(long clientPid) {
        this.clientPid = clientPid;
    }

    public long getClientStartTime() {
        return clientStartTime;
    }

    public void setClientStartTime(long clientStartTime) {
        this.clientStartTime = clientStartTime;
    }

    public long getClientUptime() {
        return clientUptime;
    }

    public void setClientUptime(long clientUptime) {
        this.clientUptime = clientUptime;
    }

    public long getClientRegisterTime() {
        return clientRegisterTime;
    }

    public void setClientRegisterTime(long clientRegisterTime) {
        this.clientRegisterTime = clientRegisterTime;
    }

    public long getClientLastHeartbeatTime() {
        return clientLastHeartbeatTime;
    }

    public void setClientLastHeartbeatTime(long clientLastHeartbeatTime) {
        this.clientLastHeartbeatTime = clientLastHeartbeatTime;
    }

    public boolean isClientOnline() {
        return clientOnline;
    }

    public void setClientOnline(boolean clientOnline) {
        this.clientOnline = clientOnline;
    }

    public Map<String, Object> getClientMetadata() {
        return clientMetadata;
    }

    public void setClientMetadata(Map<String, Object> clientMetadata) {
        this.clientMetadata = clientMetadata;
    }

    public String[] getClientCapabilities() {
        return clientCapabilities;
    }

    public void setClientCapabilities(String[] clientCapabilities) {
        this.clientCapabilities = clientCapabilities;
    }

    @Override
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
