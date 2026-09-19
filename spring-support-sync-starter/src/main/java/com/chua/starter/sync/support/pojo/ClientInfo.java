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

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 客户端 标识（会话标识）
    */
    private String clientId;

    /**
     * 客户端应用名称
    */
    private String clientApplicationName;

    /**
     * 客户端应用环境（如 dev、测试、prod）
    */
    private String clientApplicationActive;

    /**
     * 客户端实例 标识
    */
    private String clientInstanceId;

    /**
     * 客户端上下文路径
    */
    private String clientContextPath;

    /**
     * 客户端服务地址（http://ip:port/contextPath）
    */
    private String clientUrl;

    /**
     * 客户端 Actuator 地址（/actuator）
    */
    private String clientActuatorPath;

    /**
     * 客户端 IP 地址
    */
    private String clientIpAddress;

    /**
     * 客户端端口号
    */
    private int clientPort;

    /**
     * 客户端主机名
    */
    private String clientHostname;

    /**
     * 客户端操作系统名称
    */
    private String clientOsName;

    /**
     * 客户端 Java 版本
    */
    private String clientJavaVersion;

    /**
     * 客户端进程 标识
    */
    private long clientPid;

    /**
     * 客户端启动时间（毫秒时间戳）
    */
    private long clientStartTime;

    /**
     * 客户端运行时长（毫秒）
    */
    private long clientUptime;

    /**
     * 客户端注册时间（毫秒时间戳）
    */
    private long clientRegisterTime;

    /**
     * 客户端最后心跳时间（毫秒时间戳）
    */
    private long clientLastHeartbeatTime;

    /**
     * 客户端是否在线
    */
    private boolean clientOnline;

    /**
     * 客户端扩展元数据（系统信息、自定义属性等）
    */
    private Map<String, Object> clientMetadata;

    /**
     * 客户端支持的功能列表
    */
    private String[] clientCapabilities;

    /**
     * 获取客户端标识。
     * @return 获取客户端id的结果
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置客户端标识。
     * @param clientId 客户端标识
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取客户端application名称。
     * @return 获取客户端application名称的结果
     */
    public String getClientApplicationName() {
        return clientApplicationName;
    }

    /**
     * 设置客户端application名称。
     * @param clientApplicationName 客户端application名称
     */
    public void setClientApplicationName(String clientApplicationName) {
        this.clientApplicationName = clientApplicationName;
    }

    /**
     * 获取客户端application活跃。
     * @return 获取客户端application活跃的结果
     */
    public String getClientApplicationActive() {
        return clientApplicationActive;
    }

    /**
     * 设置客户端application活跃。
     * @param clientApplicationActive 客户端application活跃
     */
    public void setClientApplicationActive(String clientApplicationActive) {
        this.clientApplicationActive = clientApplicationActive;
    }

    /**
     * 获取客户端instanceid。
     * @return 获取客户端instanceid的结果
     */
    public String getClientInstanceId() {
        return clientInstanceId;
    }

    /**
     * 设置客户端instanceid。
     * @param clientInstanceId 客户端instanceid
     */
    public void setClientInstanceId(String clientInstanceId) {
        this.clientInstanceId = clientInstanceId;
    }

    /**
     * 获取客户端上下文路径。
     * @return 获取客户端上下文路径的结果
     */
    public String getClientContextPath() {
        return clientContextPath;
    }

    /**
     * 设置客户端上下文路径。
     * @param clientContextPath 客户端上下文路径
     */
    public void setClientContextPath(String clientContextPath) {
        this.clientContextPath = clientContextPath;
    }

    /**
     * 获取客户端url。
     * @return 获取客户端url的结果
     */
    public String getClientUrl() {
        return clientUrl;
    }

    /**
     * 设置客户端url。
     * @param clientUrl 客户端url
     */
    public void setClientUrl(String clientUrl) {
        this.clientUrl = clientUrl;
    }

    /**
     * 获取客户端actuator路径。
     * @return 获取客户端actuator路径的结果
     */
    public String getClientActuatorPath() {
        return clientActuatorPath;
    }

    /**
     * 设置客户端actuator路径。
     * @param clientActuatorPath 客户端actuator路径
     */
    public void setClientActuatorPath(String clientActuatorPath) {
        this.clientActuatorPath = clientActuatorPath;
    }

    /**
     * 获取客户端ip地址。
     * @return 获取客户端ip地址的结果
     */
    public String getClientIpAddress() {
        return clientIpAddress;
    }

    /**
     * 设置客户端ip地址。
     * @param clientIpAddress 客户端ip地址
     */
    public void setClientIpAddress(String clientIpAddress) {
        this.clientIpAddress = clientIpAddress;
    }

    /**
     * 获取客户端端口。
     * @return 获取客户端端口的结果
     */
    public int getClientPort() {
        return clientPort;
    }

    /**
     * 设置客户端端口。
     * @param clientPort 客户端端口
     */
    public void setClientPort(int clientPort) {
        this.clientPort = clientPort;
    }

    /**
     * 获取客户端hostname。
     * @return 获取客户端hostname的结果
     */
    public String getClientHostname() {
        return clientHostname;
    }

    /**
     * 设置客户端hostname。
     * @param clientHostname 客户端hostname
     */
    public void setClientHostname(String clientHostname) {
        this.clientHostname = clientHostname;
    }

    /**
     * 获取客户端os名称。
     * @return 获取客户端os名称的结果
     */
    public String getClientOsName() {
        return clientOsName;
    }

    /**
     * 设置客户端os名称。
     * @param clientOsName 客户端os名称
     */
    public void setClientOsName(String clientOsName) {
        this.clientOsName = clientOsName;
    }

    /**
     * 获取客户端Java版本。
     * @return 获取客户端java版本的结果
     */
    public String getClientJavaVersion() {
        return clientJavaVersion;
    }

    /**
     * 设置客户端Java版本。
     * @param clientJavaVersion 客户端Java版本
     */
    public void setClientJavaVersion(String clientJavaVersion) {
        this.clientJavaVersion = clientJavaVersion;
    }

    /**
     * 获取客户端pid。
     * @return 获取客户端pid的结果
     */
    public long getClientPid() {
        return clientPid;
    }

    /**
     * 设置客户端pid。
     * @param clientPid 客户端pid
     */
    public void setClientPid(long clientPid) {
        this.clientPid = clientPid;
    }

    /**
     * 获取客户端启动时间。
     * @return 获取客户端启动时间的结果
     */
    public long getClientStartTime() {
        return clientStartTime;
    }

    /**
     * 设置客户端启动时间。
     * @param clientStartTime 客户端启动时间
     */
    public void setClientStartTime(long clientStartTime) {
        this.clientStartTime = clientStartTime;
    }

    /**
     * 获取客户端uptime。
     * @return 获取客户端uptime的结果
     */
    public long getClientUptime() {
        return clientUptime;
    }

    /**
     * 设置客户端uptime。
     * @param clientUptime 客户端uptime
     */
    public void setClientUptime(long clientUptime) {
        this.clientUptime = clientUptime;
    }

    /**
     * 获取客户端注册时间。
     * @return 获取客户端注册时间的结果
     */
    public long getClientRegisterTime() {
        return clientRegisterTime;
    }

    /**
     * 设置客户端注册时间。
     * @param clientRegisterTime 客户端注册时间
     */
    public void setClientRegisterTime(long clientRegisterTime) {
        this.clientRegisterTime = clientRegisterTime;
    }

    /**
     * 获取客户端最后一个心跳时间。
     * @return 获取客户端最后一个心跳时间的结果
     */
    public long getClientLastHeartbeatTime() {
        return clientLastHeartbeatTime;
    }

    /**
     * 设置客户端最后一个心跳时间。
     * @param clientLastHeartbeatTime 客户端最后一个心跳时间
     */
    public void setClientLastHeartbeatTime(long clientLastHeartbeatTime) {
        this.clientLastHeartbeatTime = clientLastHeartbeatTime;
    }

    /**
     * 是否客户端online。
     * @return 是否客户端online的结果
     */
    public boolean isClientOnline() {
        return clientOnline;
    }

    /**
     * 设置客户端online。
     * @param clientOnline 客户端online
     */
    public void setClientOnline(boolean clientOnline) {
        this.clientOnline = clientOnline;
    }

    /**
     * 获取客户端metadata。
     * @return 获取客户端metadata的结果
     */
    public Map<String, Object> getClientMetadata() {
        return clientMetadata;
    }

    /**
     * 设置客户端metadata。
     * @param clientMetadata 客户端metadata
     */
    public void setClientMetadata(Map<String, Object> clientMetadata) {
        this.clientMetadata = clientMetadata;
    }

    /**
     * 获取客户端capabilities。
     * @return 获取客户端capabilities的结果
     */
    public String[] getClientCapabilities() {
        return clientCapabilities;
    }

    /**
     * 设置客户端capabilities。
     * @param clientCapabilities 客户端capabilities
     */
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
