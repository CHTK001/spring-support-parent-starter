package com.chua.starter.sync.support.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 客户端信息。
 *
 * @author CH
 * @since 2024/12/05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
