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
     * 应用名称
     */
    private String appName;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 端口
     */
    private int port;

    /**
     * 服务端口 (Spring server.port)
     */
    private int serverPort;

    /**
     * 激活的 profile
     */
    private String activeProfiles;

    /**
     * 上下文路径
     */
    private String contextPath;

    /**
     * 服务地址 (http://ip:port/contextPath)
     */
    private String serviceUrl;

    /**
     * 进程ID
     */
    private long pid;

    /**
     * 操作系统
     */
    private String osName;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * 操作系统架构
     */
    private String osArch;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * Java版本
     */
    private String javaVersion;

    /**
     * JVM名称
     */
    private String jvmName;

    /**
     * JVM版本
     */
    private String jvmVersion;

    /**
     * CPU核心数
     */
    private int cpuCores;

    /**
     * 总内存
     */
    private long totalMemory;

    /**
     * 已使用堆内存
     */
    private long heapUsed;

    /**
     * 最大堆内存
     */
    private long heapMax;

    /**
     * 线程数
     */
    private int threadCount;

    /**
     * 启动时间
     */
    private long startTime;

    /**
     * 运行时长（毫秒）
     */
    private long uptime;

    /**
     * 注册时间
     */
    private long registerTime;

    /**
     * 最后心跳时间
     */
    private long lastHeartbeatTime;

    /**
     * 是否在线
     */
    private boolean online;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;

    /**
     * 支持的功能
     */
    private String[] capabilities;
}
