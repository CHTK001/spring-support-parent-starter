package com.chua.sync.support.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 客户端信息
 * <p>核心字段直接存储，详细的 JVM/OS 信息可通过 clientMetadata 扩展</p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 * @since 2025/12/08 重构字段命名，所有字段统一以 client 前缀命名；精简字段，详细信息放入 metadata
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
    private String clientApplicationName;

    /**
     * 客户端应用环境（如：dev, test, prod）
     */
    private String clientApplicationActive;

    /**
     * 客户端实例ID
     */
    private String clientInstanceId;

    /**
     * 客户端上下文路径
     */
    private String clientContextPath;

    /**
     * 客户端服务地址 (http://ip:port/contextPath)
     */
    private String clientUrl;

    /**
     * 客户端Actuator地址 (/actuator)
     */
    private String clientActuatorPath;

    /**
     * 客户端IP地址
     */
    private String clientIpAddress;

    /**
     * 客户端端口
     */
    private int clientPort;

    /**
     * 客户端主机名
     */
    private String clientHostname;

    /**
     * 客户端操作系统
     */
    private String clientOsName;

    /**
     * 客户端Java版本
     */
    private String clientJavaVersion;

    /**
     * 客户端进程ID
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
     * 客户端扩展元数据
     * <p>可存储详细的系统信息，如: osVersion, osArch, jvmName, cpuCores, totalMemory 等</p>
     */
    private Map<String, Object> clientMetadata;

    /**
     * 客户端支持的功能
     */
    private String[] clientCapabilities;

}
