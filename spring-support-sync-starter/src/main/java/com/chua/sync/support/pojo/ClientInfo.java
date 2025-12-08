package com.chua.sync.support.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 客户端信息
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 * @since 2025/12/08 重构字段命名，所有字段统一以 client 前缀命名
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户端ID (sessionId)
     */
    private String clientId;

    /**
     * 客户端应用名称
     */
    private String clientAppName;

    /**
     * 客户端实例ID
     */
    private String clientInstanceId;

    /**
     * 客户端IP地址
     */
    private String clientIpAddress;

    /**
     * 客户端端口
     */
    private int clientPort;

    /**
     * 客户端激活的 profile
     */
    private String clientActiveProfiles;

    /**
     * 客户端上下文路径
     */
    private String clientContextPath;

    /**
     * 客户端服务地址 (http://ip:port/contextPath)
     */
    private String clientUrl;

    /**
     * 客户端进程ID
     */
    private long clientPid;

    /**
     * 客户端操作系统
     */
    private String clientOsName;

    /**
     * 客户端操作系统版本
     */
    private String clientOsVersion;

    /**
     * 客户端操作系统架构
     */
    private String clientOsArch;

    /**
     * 客户端主机名
     */
    private String clientHostname;

    /**
     * 客户端Java版本
     */
    private String clientJavaVersion;

    /**
     * 客户端JVM名称
     */
    private String clientJvmName;

    /**
     * 客户端JVM版本
     */
    private String clientJvmVersion;

    /**
     * 客户端CPU核心数
     */
    private int clientCpuCores;

    /**
     * 客户端总内存
     */
    private long clientTotalMemory;

    /**
     * 客户端已使用堆内存
     */
    private long clientHeapUsed;

    /**
     * 客户端最大堆内存
     */
    private long clientHeapMax;

    /**
     * 客户端线程数
     */
    private int clientThreadCount;

    /**
     * 客户端启动时间
     */
    private long clientStartTime;

    /**
     * 客户端运行时长（毫秒）
     */
    private long clientUptime;

    /**
     * 客户端注册时间
     */
    private long clientRegisterTime;

    /**
     * 客户端最后心跳时间
     */
    private long clientLastHeartbeatTime;

    /**
     * 客户端是否在线
     */
    private boolean clientOnline;

    /**
     * 客户端扩展元数据
     */
    private Map<String, Object> clientMetadata;

    /**
     * 客户端支持的功能
     */
    private String[] clientCapabilities;
}
